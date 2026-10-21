package com.example.video.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PartDescriptor(
        @Min(1) int partNumber,
        @NotBlank String eTag) {
}
