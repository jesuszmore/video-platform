package com.example.video.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class MultipartUploadService {

    private static final Logger log = LoggerFactory.getLogger(MultipartUploadService.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String rawBucket;

    public MultipartUploadService(S3Client s3, S3Presigner presigner,
                                  @Value("${app.s3.raw-bucket}") String rawBucket) {
        this.s3 = s3;
        this.presigner = presigner;
        this.rawBucket = rawBucket;
    }

    public String initiate(String key, String contentType) {
        var request = CreateMultipartUploadRequest.builder()
                .bucket(rawBucket)
                .key(key)
                .contentType(contentType)
                .build();
        String uploadId = s3.createMultipartUpload(request).uploadId();
        log.info("Multipart iniciado key={} uploadId={}", key, uploadId);
        return uploadId;
    }

    public String presignPart(String key, String uploadId, int partNumber, Duration ttl) {
        var uploadPart = UploadPartRequest.builder()
                .bucket(rawBucket)
                .key(key)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();
        var presignRequest = UploadPartPresignRequest.builder()
                .signatureDuration(ttl)
                .uploadPartRequest(uploadPart)
                .build();
        return presigner.presignUploadPart(presignRequest).url().toString();
    }

    public List<Part> listParts(String key, String uploadId) {
        var parts = new ArrayList<Part>();
        var request = ListPartsRequest.builder()
                .bucket(rawBucket)
                .key(key)
                .uploadId(uploadId)
                .build();
        for (var page : s3.listPartsPaginator(request)) {
            parts.addAll(page.parts());
        }
        return parts;
    }

    public String complete(String key, String uploadId, List<CompletedPart> parts) {
        var request = CompleteMultipartUploadRequest.builder()
                .bucket(rawBucket)
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                .build();
        String eTag = s3.completeMultipartUpload(request).eTag();
        log.info("Multipart completado key={} uploadId={}", key, uploadId);
        return eTag;
    }

    public void abort(String key, String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            return;
        }
        s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(rawBucket)
                .key(key)
                .uploadId(uploadId)
                .build());
        log.info("Multipart abortado key={} uploadId={}", key, uploadId);
    }

    public String rawBucket() {
        return rawBucket;
    }
}
