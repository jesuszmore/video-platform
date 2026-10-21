package com.example.video.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

import java.util.List;

/**
 * Borra de S3 el original y todas las salidas HLS/miniatura de un video.
 */
@Service
public class VideoContentCleaner {

    private static final Logger log = LoggerFactory.getLogger(VideoContentCleaner.class);

    private final S3Client s3;
    private final String rawBucket;
    private final String processedBucket;

    public VideoContentCleaner(S3Client s3,
                               @Value("${app.s3.raw-bucket}") String rawBucket,
                               @Value("${app.s3.processed-bucket}") String processedBucket) {
        this.s3 = s3;
        this.rawBucket = rawBucket;
        this.processedBucket = processedBucket;
    }

    public void deleteAll(String videoId, String rawKey) {
        deletePrefix(processedBucket, "videos/" + videoId + "/");
        if (rawKey != null && !rawKey.isBlank()) {
            s3.deleteObject(b -> b.bucket(rawBucket).key(rawKey));
            log.info("Borrado original s3://{}/{}", rawBucket, rawKey);
        }
    }

    private void deletePrefix(String bucket, String prefix) {
        var request = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build();
        for (var page : s3.listObjectsV2Paginator(request)) {
            List<ObjectIdentifier> keys = page.contents().stream()
                    .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                    .toList();
            if (keys.isEmpty()) {
                continue;
            }
            s3.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(keys).build())
                    .build());
            log.info("Borrados {} objetos de s3://{}/{}", keys.size(), bucket, prefix);
        }
    }
}
