package com.example.video.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "videos")
public class Video {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "upload_id")
    private String uploadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoStatus status;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "hls_manifest_url")
    private String hlsManifestUrl;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Video() {
    }

    public Video(UUID id, String ownerId, String s3Key, String uploadId, long sizeBytes, String contentType) {
        this.id = id;
        this.ownerId = ownerId;
        this.s3Key = s3Key;
        this.uploadId = uploadId;
        this.sizeBytes = sizeBytes;
        this.contentType = contentType;
        this.status = VideoStatus.UPLOADING;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getS3Key() {
        return s3Key;
    }

    public String getUploadId() {
        return uploadId;
    }

    public VideoStatus getStatus() {
        return status;
    }

    public void setStatus(VideoStatus status) {
        this.status = status;
    }

    public void resetForProcessing() {
        this.status = VideoStatus.QUEUED;
        this.errorMessage = null;
        this.hlsManifestUrl = null;
        this.thumbnailUrl = null;
        this.durationSeconds = null;
        this.updatedAt = Instant.now();
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getContentType() {
        return contentType;
    }

    public String getHlsManifestUrl() {
        return hlsManifestUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
