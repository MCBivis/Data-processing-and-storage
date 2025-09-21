package org.example;

import org.bouncycastle.asn1.x500.X500Name;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.CertificateFactory;

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
    // ==== Конфигурация и основные структуры ====

    private final int port; // TCP-порт для прослушивания

    // Пул нитей, которые генерируют ключи (compute-heavy tasks)
    private final ExecutorService genPool;

    // Очередь задач на отправку — сюда кладём (SocketChannel, CompletableFuture<KeyRecord>)
    // Отдельный отправляющий поток будет доставлять готовые ключи клиентам
    private final BlockingQueue<SendTask> sendQueue = new LinkedBlockingQueue<>();

    // Map имени -> Future записи с ключами. Если одна и та же строка придёт несколько раз,
    // мы возвращаем один и тот же Future, чтобы не генерировать ключи дважды.
    private final ConcurrentMap<String, CompletableFuture<KeyRecord>> nameMap = new ConcurrentHashMap<>();

    // Флаги жизненного цикла
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final PrivateKey issuerKey;
    private final X500Name issuerName;
    private final X509Certificate issuerCert;

    // ====== Конструктор ======
    public KeyServer(int port, int genThreads, PrivateKey issuerKey, X500Name issuerName, X509Certificate issuerCert) {
        this.port = port;
        this.genPool = Executors.newFixedThreadPool(Math.max(1, genThreads));
        this.issuerKey = issuerKey;
        this.issuerName = issuerName;
        this.issuerCert = issuerCert;
    }

    // ====== Основной цикл сервера (Selector) ======
    public void start() throws IOException {
        // Sender thread — читает из sendQueue и отсылает байты клиентам в блокирующем режиме
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
                    // логирование и закрытие канала
                    closeSilently((SocketChannel) key.channel());
                }
            }
        }

        // tidy up
        ssc.close();
        selector.close();
        genPool.shutdownNow();
    }

    private void doAccept(ServerSocketChannel ssc, Selector selector) throws IOException {
        SocketChannel sc = ssc.accept();
        sc.configureBlocking(false);
        // привязываем к ключу объект-аттачмент для накопления байтов имени
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

                // Получаем/создаём Future для этого имени и (если нужно) ставим задачу на генерацию
                CompletableFuture<KeyRecord> future = nameMap.computeIfAbsent(name, n -> {
                    CompletableFuture<KeyRecord> f = new CompletableFuture<>();
                    // Запускаем генерацию в пуле генераторов
                    genPool.submit(() -> generateKeyAndCertificate(n, f));
                    return f;
                });

                // Отменяем регистрацию на selector — дальше передачу результата делает senderLoop
                key.cancel();

                // Помещаем канал и future в очередь отправки
                sendQueue.offer(new SendTask(sc, future));

                // Готовим attachment для возможного следующего имени (если клиент останется на этом соединении)
                return; // выходим, чтобы не читать лишние данные сейчас
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


// Build X509 certificate with BouncyCastle
            Security.addProvider(new BouncyCastleProvider());


            X500Name subject = new X500Name("CN=" + name);
            BigInteger serial = new BigInteger(160, new SecureRandom());
            Date notBefore = Date.from(ZonedDateTime.now().minus(1, ChronoUnit.MINUTES).toInstant());
            Date notAfter = Date.from(ZonedDateTime.now().plus(365, ChronoUnit.DAYS).toInstant());


            SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(kp.getPublic().getEncoded());
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


            KeyRecord kr = new KeyRecord(kp.getPrivate(), kp.getPublic(), cert);
            future.complete(kr);
            System.out.println("Generated key+cert for '" + name + "'");
        } catch (Exception e) {
            future.completeExceptionally(e);
            e.printStackTrace();
        }
    }

    // ====== Отправляющий поток: берёт (SocketChannel, Future) и отсылает результат клиенту ======
    private void senderLoop() {
        while (running.get()) {
            try {
                SendTask task = sendQueue.take();
                SocketChannel sc = task.channel;
                CompletableFuture<KeyRecord> future = task.future;

                try {
                    // блокируем здесь до готовности ключей
                    KeyRecord kr = future.get();

                    // Переключаемся в блокирующий режим, чтобы удобно писать в OutputStream
                    sc.configureBlocking(true);
                    OutputStream out = sc.socket().getOutputStream();

                    // Простой протокол (пример): сначала 4 байта длины приватного ключа (big-endian), затем ключ DER,
                    // затем 4 байта длины сертификата и сертификат (DER). Если сертификата нет — длина 0.
                    byte[] keyBytes = kr.privateKey.getEncoded();
                    byte[] certBytes = (kr.cert == null) ? new byte[0] : kr.cert.getEncoded();

                    writeInt(out, keyBytes.length);
                    out.write(keyBytes);
                    writeInt(out, certBytes.length);
                    out.write(certBytes);
                    out.flush();

                    System.out.println("Sent key+cert to " + sc.getRemoteAddress());
                } catch (ExecutionException ee) {
                    // Генерация упала: можно отправить ошибку в протоколе (в этом примере просто закрываем)
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
                // при ошибке с каналом — лог и пропуск
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

    // ====== Вспомогательные структуры ======
    private static class ClientAttachment {
        // Накопительный буфер для чтения имени
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
        final PublicKey publicKey;
        final X509Certificate cert; // может быть null до тех пор, пока вы не добавите генерацию
        KeyRecord(PrivateKey p, PublicKey pub, X509Certificate c) { privateKey = p; publicKey = pub; cert = c; }
    }

    // ====== Простой main (парсинг аргументов — минимально) ======
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
        X509Certificate issuerCert = loadCertificate(issuerCertFile);
        X500Name issuerName = new X500Name(issuerCn);


        KeyServer server = new KeyServer(port, gens, issuerKey, issuerName, issuerCert);
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

    public static X509Certificate loadCertificate(File certFile) throws Exception {
        try (FileInputStream fis = new FileInputStream(certFile)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(fis);
        }
    }

}
