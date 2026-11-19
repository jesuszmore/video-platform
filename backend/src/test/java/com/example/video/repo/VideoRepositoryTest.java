package com.example.video.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.reaper.enabled=false")
@Transactional
class VideoRepositoryTest {

    @Autowired
    private VideoRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void requeuesOnlyStaleProcessingRows() {
        Instant now = Instant.now();
        UUID stale = insert("PROCESSING", now.minus(1, ChronoUnit.HOURS));
        UUID fresh = insert("PROCESSING", now);
        Instant cutoff = now.minus(30, ChronoUnit.MINUTES);

        assertThat(repository.requeueIfStale(stale, cutoff)).isEqualTo(1);
        assertThat(statusOf(stale)).isEqualTo("QUEUED");

        assertThat(repository.requeueIfStale(fresh, cutoff)).isZero();
        assertThat(statusOf(fresh)).isEqualTo("PROCESSING");
    }

    private UUID insert(String status, Instant updatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into videos (id, owner_id, s3_key, status, size_bytes, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                id, "test", "uploads/test/" + id + "/original.mp4", status, 1L, updatedAt, updatedAt);
        return id;
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("select status from videos where id = ?", String.class, id);
    }
}
