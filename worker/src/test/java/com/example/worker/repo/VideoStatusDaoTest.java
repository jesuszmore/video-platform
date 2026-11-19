package com.example.worker.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.worker.enabled=false")
class VideoStatusDaoTest {

    @Autowired
    private VideoStatusDao dao;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void claimsQueuedAndStaleProcessingJobsOnly() {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(30, ChronoUnit.MINUTES);
        UUID queued = insert("QUEUED", now);
        UUID fresh = insert("PROCESSING", now);
        UUID stale = insert("PROCESSING", now.minus(1, ChronoUnit.HOURS));

        assertThat(dao.claim(queued, staleBefore)).isTrue();
        assertThat(dao.statusOf(queued)).isEqualTo("PROCESSING");

        assertThat(dao.claim(fresh, staleBefore)).isFalse();
        assertThat(dao.statusOf(fresh)).isEqualTo("PROCESSING");

        assertThat(dao.claim(stale, staleBefore)).isTrue();
        assertThat(dao.statusOf(stale)).isEqualTo("PROCESSING");
    }

    @Test
    void touchKeepsProcessingJobAlive() {
        Instant now = Instant.now();
        UUID id = insert("PROCESSING", now.minus(1, ChronoUnit.HOURS));

        dao.touch(id);

        assertThat(dao.claim(id, now.minus(30, ChronoUnit.MINUTES))).isFalse();
        assertThat(dao.statusOf(id)).isEqualTo("PROCESSING");
    }

    private UUID insert(String status, Instant updatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into videos (id, owner_id, s3_key, status, size_bytes, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                id, "test", "uploads/test/" + id + "/original.mp4", status, 1L, updatedAt, updatedAt);
        return id;
    }
}
