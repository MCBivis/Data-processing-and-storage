package org.example;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

class KeyClient {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: KeyClient <server> <port> <name> [--delay seconds] [--exit-before-read]");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String name = args[2];
        int delay = 0;
        boolean exitBefore = false;
        for (int i = 3; i < args.length; i++) {
            if ("--delay".equals(args[i])) { delay = Integer.parseInt(args[++i]); }
            if ("--exit-before-read".equals(args[i])) exitBefore = true;
        }


        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 5000);
            OutputStream out = s.getOutputStream();
            out.write(name.getBytes(StandardCharsets.US_ASCII));
            out.write(0);
            out.flush();


            if (exitBefore) {
                System.out.println("Exiting before reading response (simulating crash)");
                return;
            }


            if (delay > 0) {
                System.out.println("Sleeping " + delay + "s before reading response");
                Thread.sleep(delay * 1000L);
            }


            InputStream in = s.getInputStream();
            int keyLen = readInt(in);
            byte[] keyBytes = in.readNBytes(keyLen);
            int certLen = readInt(in);
            byte[] certBytes = in.readNBytes(certLen);


            try (FileOutputStream kf = new FileOutputStream(name + ".key")) { kf.write(keyBytes); }
            try (FileOutputStream cf = new FileOutputStream(name + ".crt")) { cf.write(certBytes); }
            System.out.println("Saved " + name + ".key and " + name + ".crt");
        }
    }


    private static int readInt(InputStream in) throws IOException {
        int a = in.read();
        int b = in.read();
        int c = in.read();
        int d = in.read();
        if (a < 0 || b < 0 || c < 0 || d < 0) throw new EOFException();
        return (a << 24) | (b << 16) | (c << 8) | d;
    }
}