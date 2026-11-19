package com.example.video.repo;

import com.example.video.domain.Video;
import com.example.video.domain.VideoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {

    List<Video> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<Video> findByIdAndOwnerId(UUID id, String ownerId);

    List<Video> findByStatusAndUpdatedAtBefore(VideoStatus status, Instant cutoff);

    @Modifying
    @Query(value = "update videos set status = 'QUEUED', updated_at = now() "
            + "where id = :id and status = 'PROCESSING' and updated_at < :cutoff", nativeQuery = true)
    int requeueIfStale(@Param("id") UUID id, @Param("cutoff") Instant cutoff);
}
