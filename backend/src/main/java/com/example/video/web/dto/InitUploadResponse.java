package com.example.video.web.dto;

import java.util.UUID;

public record InitUploadResponse(
        UUID videoId,
        String key,
        String uploadId,
        long partSize,
        int totalParts,
        long partUrlTtlSeconds) {
}
