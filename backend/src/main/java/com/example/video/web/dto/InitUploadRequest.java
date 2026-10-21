package com.example.video.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InitUploadRequest(
        @NotBlank @Size(max = 255) String filename,
        @Positive long sizeBytes,
        @NotBlank @Size(max = 128) String contentType) {
}
