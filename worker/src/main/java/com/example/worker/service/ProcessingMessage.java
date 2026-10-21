package com.example.worker.service;

import java.util.UUID;

public record ProcessingMessage(UUID videoId, String bucket, String key) {
}
