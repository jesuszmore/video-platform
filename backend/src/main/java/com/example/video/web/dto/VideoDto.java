package com.example.video.web.dto;

import com.example.video.domain.Video;

import java.time.Instant;
import java.util.UUID;

public record VideoDto(
        UUID id,
        String status,
        long sizeBytes,
        String hlsManifestUrl,
        String thumbnailUrl,
        Integer durationSeconds,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public static VideoDto from(Video video) {
        return new VideoDto(
                video.getId(),
                video.getStatus().name(),
                video.getSizeBytes(),
                video.getHlsManifestUrl(),
                video.getThumbnailUrl(),
                video.getDurationSeconds(),
                video.getErrorMessage(),
                video.getCreatedAt(),
                video.getUpdatedAt());
    }
}
