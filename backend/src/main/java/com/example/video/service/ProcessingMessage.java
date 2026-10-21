package com.example.video.service;

import java.util.UUID;

public record ProcessingMessage(UUID videoId, String bucket, String key) {
}
