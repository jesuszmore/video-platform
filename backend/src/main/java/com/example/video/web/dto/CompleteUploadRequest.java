package com.example.video.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CompleteUploadRequest(@NotEmpty List<@Valid PartDescriptor> parts) {
}
