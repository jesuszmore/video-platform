package com.example.video.service;

import java.util.UUID;

public record VideoQueuedEvent(UUID videoId, String bucket, String key) {
}
