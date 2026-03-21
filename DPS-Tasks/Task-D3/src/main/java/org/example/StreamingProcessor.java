package org.example;

import org.xml.sax.InputSource;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Handles the streaming phase of data import using multi-threaded SAX parsing.
 */
public class StreamingProcessor {
    
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final File xmlFile;
    private final int numThreads;
    
    public StreamingProcessor(String dbUrl, String dbUser, String dbPassword, File xmlFile, int numThreads) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.xmlFile = xmlFile;
        this.numThreads = numThreads;
    }
    
    /**
     * Executes the streaming phase: splits file into segments and processes them in parallel.
     */
    public void execute() throws Exception {
        System.out.println("Starting streaming phase with " + numThreads + " threads...");
        
        // Calculate segments
        XMLSegmentSplitter splitter = new XMLSegmentSplitter(xmlFile, numThreads);
        List<XMLSegmentSplitter.Segment> segments = splitter.calculateSegments();
        
        System.out.println("File split into " + segments.size() + " segments");
        for (int i = 0; i < segments.size(); i++) {
            XMLSegmentSplitter.Segment seg = segments.get(i);
            System.out.printf("Segment %d: %d - %d (length: %d bytes)%n", 
                i + 1, seg.getStart(), seg.getEnd(), seg.getLength());
        }
        
        // Create temporary schema
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            conn.setAutoCommit(false);
            DatabaseSchema.createTemporarySchema(conn);
            conn.commit();
        }
        
        // Process segments in parallel
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(segments.size());
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < segments.size(); i++) {
            final int segmentIndex = i;
            final XMLSegmentSplitter.Segment segment = segments.get(i);
            
            executor.submit(() -> {
                try {
                    processSegment(segmentIndex, segment);
                    latch.countDown();
                } catch (Exception e) {
                    System.err.println("Error processing segment " + segmentIndex + ": " + e.getMessage());
                    e.printStackTrace();
                    latch.countDown();
                }
            });
        }
        
        // Wait for all segments to complete
        latch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
        
        long endTime = System.currentTimeMillis();
        System.out.println("Streaming phase completed in " + (endTime - startTime) + " ms");
        
        // Create indexes on temporary tables
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            DatabaseSchema.createTemporaryIndexes(conn);
        }
    }
    
    /**
     * Processes a single segment of the XML file in a truly streaming way:
     * we build a composite InputStream = [XML header] + [segment bytes] + [XML footer]
     * and feed it directly to the SAX parser without buffering the whole segment in memory.
     */
    private void processSegment(int segmentIndex, XMLSegmentSplitter.Segment segment) throws Exception {
        System.out.println("Thread " + Thread.currentThread().getId()
            + " processing segment " + segmentIndex);

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            conn.setAutoCommit(false);

            // Prepare small in‑memory streams for header and footer
            byte[] headerBytes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><people>"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] footerBytes = "</people>"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

            try (FileInputStream fis = new FileInputStream(xmlFile)) {
                // Skip to the start of this segment
                long toSkip = segment.getStart();
                while (toSkip > 0) {
                    long skipped = fis.skip(toSkip);
                    if (skipped <= 0) {
                        // EOF or cannot skip further
                        System.out.println("Segment " + segmentIndex + " reached EOF while skipping, skipping segment");
                        return;
                    }
                    toSkip -= skipped;
                }

                // Limited stream over the actual segment bytes
                InputStream segmentStream = new LimitedInputStream(fis, segment.getLength());

                // Chain [header] + [segment] + [footer] as one continuous stream
                InputStream headerStream = new ByteArrayInputStream(headerBytes);
                InputStream footerStream = new ByteArrayInputStream(footerBytes);
                InputStream headerPlusSegment = new SequenceInputStream(headerStream, segmentStream);
                InputStream fullStream = new SequenceInputStream(headerPlusSegment, footerStream);

                // Create SAX parser
                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setNamespaceAware(false);
                SAXParser saxParser = factory.newSAXParser();
                PersonSAXHandler handler = new PersonSAXHandler(conn);

                // Parse directly from the streaming InputStream
                InputSource inputSource = new InputSource(fullStream);
                saxParser.parse(inputSource, handler);

                handler.finish();
                handler.close();

                int personCount = handler.getPersonCount();
                System.out.println("Segment " + segmentIndex + " processed " + personCount + " persons");
            }
        }
    }

    /**
     * Simple wrapper that limits how many bytes can be read from the underlying stream.
     * This allows each thread to see only its own segment without loading it into memory.
     */
    private static class LimitedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        LimitedInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read() throws java.io.IOException {
            if (remaining <= 0) {
                return -1;
            }
            int result = delegate.read();
            if (result != -1) {
                remaining--;
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int result = delegate.read(b, off, toRead);
            if (result > 0) {
                remaining -= result;
            }
            return result;
        }
    }
}
