package com.example.worker.service;

import java.util.UUID;

public final class VideoKey {

    private VideoKey() {
    }

    public static UUID videoId(String key) {
        if (key == null) {
            return null;
        }
        String[] parts = key.split("/");
        if (parts.length < 4) {
            return null;
        }
        try {
            return UUID.fromString(parts[2]);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
