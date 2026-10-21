package com.example.worker.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VideoKeyTest {

    @Test
    void extractsVideoIdFromKey() {
        UUID id = UUID.randomUUID();
        assertThat(VideoKey.videoId("uploads/user-1/" + id + "/original.mp4")).isEqualTo(id);
    }

    @Test
    void returnsNullForMalformedKey() {
        assertThat(VideoKey.videoId("nonsense")).isNull();
        assertThat(VideoKey.videoId("uploads/user-1/not-a-uuid/original.mp4")).isNull();
        assertThat(VideoKey.videoId(null)).isNull();
    }
}
