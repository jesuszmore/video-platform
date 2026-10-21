package com.example.video.repo;

import com.example.video.domain.Video;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {

    List<Video> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<Video> findByIdAndOwnerId(UUID id, String ownerId);
}
