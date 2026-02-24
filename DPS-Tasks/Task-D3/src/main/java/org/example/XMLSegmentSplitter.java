package org.example;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits an XML file into N segments of approximately equal size,
 * ensuring that <person> elements are not split across segments.
 */
public class XMLSegmentSplitter {
    
    private final File xmlFile;
    private final int numSegments;
    
    public XMLSegmentSplitter(File xmlFile, int numSegments) {
        this.xmlFile = xmlFile;
        this.numSegments = numSegments;
    }
    
    /**
     * Calculates segment boundaries that avoid splitting <person> elements.
     * Returns a list of Segment objects containing start and end positions.
     */
    public List<Segment> calculateSegments() throws IOException {
        List<Segment> segments = new ArrayList<>();
        long fileLength = xmlFile.length();
        long segmentSize = fileLength / numSegments;
        
        try (RandomAccessFile raf = new RandomAccessFile(xmlFile, "r")) {
            // Find the start of the first <person> element (after XML declaration and <people> tag)
            long dataStart = findFirstPersonStart(raf);
            long segmentStart = dataStart;
            
            for (int i = 0; i < numSegments; i++) {
                long targetEnd = segmentStart + segmentSize;
                
                // For the last segment, go to just before </people>
                if (i == numSegments - 1) {
                    // Find the position just before </people>
                    raf.seek(fileLength - 100); // Check last 100 bytes
                    byte[] tail = new byte[100];
                    raf.read(tail);
                    String tailStr = new String(tail, java.nio.charset.StandardCharsets.UTF_8);
                    int peopleEndPos = tailStr.lastIndexOf("</people>");
                    if (peopleEndPos != -1) {
                        long peopleEnd = fileLength - 100 + peopleEndPos;
                        segments.add(new Segment(segmentStart, peopleEnd));
                    } else {
                        segments.add(new Segment(segmentStart, fileLength));
                    }
                    break;
                }
                
                // Move to target position and find the end of the current person element
                raf.seek(targetEnd);
                long segmentEnd = findNextPersonEnd(raf);
                
                segments.add(new Segment(segmentStart, segmentEnd));
                segmentStart = segmentEnd;
            }
        }
        
        return segments;
    }
    
    /**
     * Finds the start position of the first <person> element.
     */
    private long findFirstPersonStart(RandomAccessFile raf) throws IOException {
        raf.seek(0);
        byte[] buffer = new byte[8192];
        int bytesRead = raf.read(buffer);
        String content = new String(buffer, 0, bytesRead, java.nio.charset.StandardCharsets.UTF_8);
        
        int personStart = content.indexOf("<person");
        if (personStart != -1) {
            return personStart;
        }
        
        // If not found in first chunk, search more
        while ((bytesRead = raf.read(buffer)) != -1) {
            content = new String(buffer, 0, bytesRead, java.nio.charset.StandardCharsets.UTF_8);
            personStart = content.indexOf("<person");
            if (personStart != -1) {
                return raf.getFilePointer() - bytesRead + personStart;
            }
        }
        
        return 0; // Fallback
    }
    
    /**
     * Finds the end of the current <person> element by looking for </person> tag.
     * Starts from current position and searches forward.
     */
    private long findNextPersonEnd(RandomAccessFile raf) throws IOException {
        long startPos = raf.getFilePointer();
        byte[] buffer = new byte[8192];
        StringBuilder contentBuilder = new StringBuilder();
        
        // Read forward until we find </person>
        while (true) {
            int bytesRead = raf.read(buffer);
            if (bytesRead == -1) {
                break;
            }
            
            String chunk = new String(buffer, 0, bytesRead, java.nio.charset.StandardCharsets.UTF_8);
            contentBuilder.append(chunk);
            
            String content = contentBuilder.toString();
            int personEnd = content.indexOf("</person>");
            
            if (personEnd != -1) {
                // Found the end, return the position
                long endPos = startPos + personEnd + "</person>".length();
                return endPos;
            }
            
            // Keep only the last part of buffer to avoid memory issues
            if (contentBuilder.length() > 10000) {
                String lastPart = contentBuilder.substring(contentBuilder.length() - 5000);
                contentBuilder = new StringBuilder(lastPart);
                startPos = raf.getFilePointer() - 5000;
            }
        }
        
        // If we reach here, return current position
        return raf.getFilePointer();
    }
    
    /**
     * Represents a segment of the XML file with start and end byte positions.
     */
    public static class Segment {
        private final long start;
        private final long end;
        
        public Segment(long start, long end) {
            this.start = start;
            this.end = end;
        }
        
        public long getStart() {
            return start;
        }
        
        public long getEnd() {
            return end;
        }
        
        public long getLength() {
            return end - start;
        }
    }
}
