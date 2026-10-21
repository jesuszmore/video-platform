package com.example.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final S3Client s3;
    private final String rawBucket;
    private final String processedBucket;
    private final String workDir;
    private final String cloudfrontBase;

    public StorageService(S3Client s3,
                          @Value("${app.s3.raw-bucket}") String rawBucket,
                          @Value("${app.s3.processed-bucket}") String processedBucket,
                          @Value("${app.worker.work-dir:}") String workDir,
                          @Value("${app.cloudfront.base-url:}") String cloudfrontBase) {
        this.s3 = s3;
        this.rawBucket = rawBucket;
        this.processedBucket = processedBucket;
        this.workDir = workDir;
        this.cloudfrontBase = cloudfrontBase;
    }

    public Download downloadOriginal(UUID videoId, String bucket, String key) throws IOException {
        Path dir = workDir.isBlank()
                ? Files.createTempDirectory("video-" + videoId + "-")
                : Files.createDirectories(Path.of(workDir).resolve(videoId.toString()));
        // El original va a un subdirectorio que no se sube a processed/.
        Path sourceDir = Files.createDirectories(dir.resolve("source"));
        String filename = key.substring(key.lastIndexOf('/') + 1);
        Path input = sourceDir.resolve(filename);
        s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), input);
        log.info("Descargado s3://{}/{} -> {}", bucket, key, input);
        return new Download(input, dir);
    }

    public Uploaded uploadOutputs(UUID videoId, Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(file -> !dir.relativize(file).startsWith("source"))
                    .toList();
            for (Path file : files) {
                String relative = dir.relativize(file).toString().replace('\\', '/');
                String key = "videos/%s/%s".formatted(videoId, relative);
                s3.putObject(PutObjectRequest.builder()
                        .bucket(processedBucket)
                        .key(key)
                        .contentType(contentType(relative))
                        .build(), file);
            }
        }
        return new Uploaded(manifestUrl(videoId), thumbnailUrl(videoId));
    }

    private String manifestUrl(UUID videoId) {
        return join(cloudfrontBase, "videos/%s/master.m3u8".formatted(videoId));
    }

    private String thumbnailUrl(UUID videoId) {
        return join(cloudfrontBase, "videos/%s/thumb.jpg".formatted(videoId));
    }

    private static String join(String base, String path) {
        if (base == null || base.isBlank()) {
            return path;
        }
        return base.endsWith("/") ? base + path : base + "/" + path;
    }

    private static String contentType(String name) {
        if (name.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (name.endsWith(".ts")) {
            return "video/mp2t";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    public String rawBucket() {
        return rawBucket;
    }

    public record Download(Path input, Path workDir) {
    }

    public record Uploaded(String hlsManifestUrl, String thumbnailUrl) {
    }
}
