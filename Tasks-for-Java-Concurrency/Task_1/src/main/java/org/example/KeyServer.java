package org.example;

import org.bouncycastle.asn1.x500.X500Name;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class KeyServer {
    private final int port;
    private final ExecutorService genPool;
    private final BlockingQueue<SendTask> sendQueue = new LinkedBlockingQueue<>();
    private final ConcurrentMap<String, CompletableFuture<KeyRecord>> nameMap = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final PrivateKey issuerKey;
    private final X500Name issuerName;

    public KeyServer(int port, int genThreads, PrivateKey issuerKey, X500Name issuerName) {
        this.port = port;
        this.genPool = Executors.newFixedThreadPool(Math.max(1, genThreads));
        this.issuerKey = issuerKey;
        this.issuerName = issuerName;
    }

    public void start() throws IOException {
        Thread sender = new Thread(this::senderLoop, "sender-thread");
        sender.setDaemon(true);
        sender.start();

        Selector selector = Selector.open();
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.configureBlocking(false);
        ssc.bind(new InetSocketAddress(port));
        ssc.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server listening on port " + port);

        while (running.get()) {
            selector.select(); // блокирует до события
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();

                try {
                    if (key.isAcceptable()) {
                        doAccept((ServerSocketChannel) key.channel(), selector);
                    } else if (key.isReadable()) {
                        doRead(key);
                    }
                } catch (CancelledKeyException cke) {
                    // клиент мог закрыть соединение — игнорируем
                } catch (IOException ex) {
                    closeSilently((SocketChannel) key.channel());
                }
            }
        }
        // очистка
        ssc.close();
        selector.close();
        genPool.shutdownNow();
    }

    private void doAccept(ServerSocketChannel ssc, Selector selector) throws IOException {
        SocketChannel sc = ssc.accept();
        sc.configureBlocking(false);
        // привязываем к ключу объект для накопления байтов имени
        SelectionKey key = sc.register(selector, SelectionKey.OP_READ);
        key.attach(new ClientAttachment());
        System.out.println("Accepted connection from " + sc.getRemoteAddress());
    }

    // Читаем нуль-терминированную ASCII-строку имени. Как только видим '\0', оформляем задачу.
    private void doRead(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        ClientAttachment att = (ClientAttachment) key.attachment();
        ByteBuffer buf = att.buffer;

        int read = sc.read(buf);
        if (read == -1) { // EOF — клиент закрыл соединение
            closeSilently(sc);
            return;
        }

        buf.flip();
        while (buf.hasRemaining()) {
            byte b = buf.get();
            if (b == 0) { // конец имени
                String name = new String(att.nameBytes.toByteArray(), StandardCharsets.US_ASCII);
                System.out.println("Received name='" + name + "' from " + sc.getRemoteAddress());

                CompletableFuture<KeyRecord> future = nameMap.computeIfAbsent(name, n -> {
                    CompletableFuture<KeyRecord> f = new CompletableFuture<>();
                    genPool.submit(() -> generateKeyAndCertificate(n, f));
                    return f;
                });

                key.cancel();

                sendQueue.offer(new SendTask(sc, future));

                return;
            } else {
                att.nameBytes.write(b);
                if (att.nameBytes.size() > 4096) {
                    // защита
                    System.out.println("Name too long, closing " + sc.getRemoteAddress());
                    closeSilently(sc);
                    return;
                }
            }
        }
        buf.clear();
    }

    private void generateKeyAndCertificate(String name, CompletableFuture<KeyRecord> future) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(8192);
            KeyPair kp = kpg.generateKeyPair();

            Security.addProvider(new BouncyCastleProvider());

            X500Name subject = new X500Name("CN=" + name);
            BigInteger serial = new BigInteger(160, new SecureRandom());
            Date notBefore = Date.from(ZonedDateTime.now().minus(1, ChronoUnit.MINUTES).toInstant());
            Date notAfter = Date.from(ZonedDateTime.now().plus(365, ChronoUnit.DAYS).toInstant());

            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuerName,
                    serial,
                    notBefore,
                    notAfter,
                    subject,
                    kp.getPublic()
            );


            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(issuerKey);
            X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(signer));


            KeyRecord kr = new KeyRecord(kp.getPrivate(), cert);
            future.complete(kr);
            System.out.println("Generated key+cert for '" + name + "'");
        } catch (Exception e) {
            future.completeExceptionally(e);
            e.printStackTrace();
        }
    }

    private void senderLoop() {
        while (running.get()) {
            try {
                SendTask task = sendQueue.take();
                SocketChannel sc = task.channel;
                CompletableFuture<KeyRecord> future = task.future;

                try {
                    // блокируем здесь до готовности ключей
                    KeyRecord kr = future.get();

                    sc.configureBlocking(true);
                    OutputStream out = sc.socket().getOutputStream();

                    // сначала 4 байта длины приватного ключа, затем ключ, затем 4 байта длины сертификата и сертификат.
                    byte[] keyBytes = kr.privateKey.getEncoded();
                    byte[] certBytes = kr.cert.getEncoded();

                    writeInt(out, keyBytes.length);
                    out.write(keyBytes);
                    writeInt(out, certBytes.length);
                    out.write(certBytes);
                    out.flush();

                    System.out.println("Sent key+cert to " + sc.getRemoteAddress());
                } catch (ExecutionException ee) {
                    System.err.println("Generation failed: " + ee.getCause());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (CertificateEncodingException e) {
                    throw new RuntimeException(e);
                } finally {
                    closeSilently(sc);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException io) {
                io.printStackTrace();
            }
        }
    }

    private static void writeInt(OutputStream out, int v) throws IOException {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v) & 0xFF);
    }

    private static void closeSilently(SocketChannel sc) {
        if (sc == null) return;
        try {
            System.out.println("Closing " + sc.getRemoteAddress());
            sc.close();
        } catch (IOException ignored) {}
    }

    private static class ClientAttachment {
        final ByteBuffer buffer = ByteBuffer.allocate(1024);
        final ByteArrayOutputStream nameBytes = new ByteArrayOutputStream();
    }

    private static class SendTask {
        final SocketChannel channel;
        final CompletableFuture<KeyRecord> future;
        SendTask(SocketChannel ch, CompletableFuture<KeyRecord> f) { channel = ch; future = f; }
    }

    private static class KeyRecord {
        final PrivateKey privateKey;
        final X509Certificate cert;
        KeyRecord(PrivateKey p, X509Certificate c) { privateKey = p; cert = c; }
    }

    public static void main(String[] args) throws Exception {
        int port = 5555;
        int gens = Runtime.getRuntime().availableProcessors();
        File issuerKeyFile = null;
        File issuerCertFile = null;
        String issuerCn = "CN=TestIssuer";


        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port": port = Integer.parseInt(args[++i]); break;
                case "--gens": gens = Integer.parseInt(args[++i]); break;
                case "--issuer-key": issuerKeyFile = new File(args[++i]); break;
                case "--issuer-cert": issuerCertFile = new File(args[++i]); break;
                case "--issuer-cn": issuerCn = args[++i]; break;
                default: System.err.println("Unknown arg " + args[i]); System.exit(1);
            }
        }


        if (issuerKeyFile == null || issuerCertFile == null) {
            System.err.println("You must provide --issuer-key and --issuer-cert PEM files");
            System.exit(2);
        }


        Security.addProvider(new BouncyCastleProvider());


        PrivateKey issuerKey = loadPrivateKey(issuerKeyFile);
        X500Name issuerName = new X500Name(issuerCn);


        KeyServer server = new KeyServer(port, gens, issuerKey, issuerName);
        server.start();
    }

    public static PrivateKey loadPrivateKey(File pemFile) throws Exception {
        String pem = new String(Files.readAllBytes(Paths.get(pemFile.toURI())));
        pem = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

}
