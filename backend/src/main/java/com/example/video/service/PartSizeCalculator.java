package com.example.video.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PartSizeCalculator {

    private static final long MIB = 1024L * 1024L;

    private final long defaultPartSize;
    private final long minPartSize;
    private final int maxParts;

    public PartSizeCalculator(
            @Value("${app.upload.default-part-size:8388608}") long defaultPartSize,
            @Value("${app.upload.min-part-size:5242880}") long minPartSize,
            @Value("${app.upload.max-parts:10000}") int maxParts) {
        this.defaultPartSize = defaultPartSize;
        this.minPartSize = minPartSize;
        this.maxParts = maxParts;
    }

    public long partSizeFor(long totalBytes) {
        if (totalBytes <= 0) {
            return minPartSize;
        }
        long neededForPartLimit = (long) Math.ceil(totalBytes / (double) maxParts);
        long partSize = Math.max(defaultPartSize, neededForPartLimit);
        partSize = Math.max(partSize, minPartSize);
        return ((partSize + MIB - 1) / MIB) * MIB;
    }

    public int totalParts(long totalBytes, long partSize) {
        if (totalBytes <= 0) {
            return 1;
        }
        return (int) Math.ceil(totalBytes / (double) partSize);
    }

    public int maxParts() {
        return maxParts;
    }
}
