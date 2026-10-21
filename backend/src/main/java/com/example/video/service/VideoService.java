package com.example.video.service;

import com.example.video.domain.Video;
import com.example.video.domain.VideoStatus;
import com.example.video.repo.VideoRepository;
import com.example.video.web.dto.CompleteUploadRequest;
import com.example.video.web.dto.InitUploadRequest;
import com.example.video.web.dto.InitUploadResponse;
import com.example.video.web.dto.PartDescriptor;
import com.example.video.web.dto.PartUrlResponse;
import com.example.video.web.dto.VideoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class VideoService {

    private final VideoRepository repository;
    private final MultipartUploadService multipart;
    private final VideoContentCleaner cleaner;
    private final ApplicationEventPublisher events;
    private final PartSizeCalculator partSizeCalculator;
    private final Duration partTtl;
    private final int maxPartUrlsPerRequest;

    public VideoService(VideoRepository repository,
                        MultipartUploadService multipart,
                        VideoContentCleaner cleaner,
                        ApplicationEventPublisher events,
                        PartSizeCalculator partSizeCalculator,
                        @Value("${app.presign.part-ttl:PT15M}") Duration partTtl,
                        @Value("${app.presign.max-urls-per-request:1000}") int maxPartUrlsPerRequest) {
        this.repository = repository;
        this.multipart = multipart;
        this.cleaner = cleaner;
        this.events = events;
        this.partSizeCalculator = partSizeCalculator;
        this.partTtl = partTtl;
        this.maxPartUrlsPerRequest = maxPartUrlsPerRequest;
    }

    @Transactional
    public InitUploadResponse start(String ownerId, InitUploadRequest request) {
        if (!request.contentType().startsWith("video/")) {
            throw new IllegalArgumentException("contentType debe ser video/*");
        }
        long partSize = partSizeCalculator.partSizeFor(request.sizeBytes());
        int totalParts = partSizeCalculator.totalParts(request.sizeBytes(), partSize);

        UUID videoId = UUID.randomUUID();
        String key = "uploads/%s/%s/original.%s".formatted(ownerId, videoId, extension(request.filename()));
        String uploadId = multipart.initiate(key, request.contentType());

        Video video = new Video(videoId, ownerId, key, uploadId, request.sizeBytes(), request.contentType());
        repository.save(video);

        return new InitUploadResponse(videoId, key, uploadId, partSize, totalParts, partTtl.toSeconds());
    }

    @Transactional(readOnly = true)
    public List<PartUrlResponse> presignParts(UUID videoId, String ownerId, List<Integer> numbers) {
        if (numbers.isEmpty() || numbers.size() > maxPartUrlsPerRequest) {
            throw new IllegalArgumentException("Se deben pedir entre 1 y " + maxPartUrlsPerRequest + " partes");
        }
        Video video = requireUploading(videoId, ownerId);
        List<PartUrlResponse> urls = new ArrayList<>(numbers.size());
        for (int partNumber : numbers) {
            if (partNumber < 1 || partNumber > partSizeCalculator.maxParts()) {
                throw new IllegalArgumentException("partNumber fuera de rango: " + partNumber);
            }
            urls.add(new PartUrlResponse(partNumber,
                    multipart.presignPart(video.getS3Key(), video.getUploadId(), partNumber, partTtl)));
        }
        return urls;
    }

    @Transactional(readOnly = true)
    public List<PartUrlResponse> alreadyUploaded(UUID videoId, String ownerId) {
        Video video = requireUploading(videoId, ownerId);
        return multipart.listParts(video.getS3Key(), video.getUploadId()).stream()
                .map(p -> new PartUrlResponse(p.partNumber(), p.eTag()))
                .sorted(Comparator.comparingInt(PartUrlResponse::partNumber))
                .toList();
    }

    @Transactional
    public VideoDto complete(UUID videoId, String ownerId, CompleteUploadRequest request) {
        Video video = requireUploading(videoId, ownerId);
        List<CompletedPart> parts = request.parts().stream()
                .sorted(Comparator.comparingInt(PartDescriptor::partNumber))
                .map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.eTag()).build())
                .toList();
        multipart.complete(video.getS3Key(), video.getUploadId(), parts);
        video.setStatus(VideoStatus.QUEUED);
        repository.save(video);
        events.publishEvent(new VideoQueuedEvent(video.getId(), multipart.rawBucket(), video.getS3Key()));
        return VideoDto.from(video);
    }

    @Transactional
    public VideoDto retry(UUID videoId, String ownerId) {
        Video video = require(videoId, ownerId);
        if (video.getStatus() != VideoStatus.FAILED && video.getStatus() != VideoStatus.QUEUED) {
            throw new IllegalStateException("No se puede reintentar un video en estado " + video.getStatus());
        }
        video.resetForProcessing();
        repository.save(video);
        events.publishEvent(new VideoQueuedEvent(video.getId(), multipart.rawBucket(), video.getS3Key()));
        return VideoDto.from(video);
    }

    @Transactional
    public void delete(UUID videoId, String ownerId) {
        Video video = require(videoId, ownerId);
        if (video.getStatus() == VideoStatus.UPLOADING) {
            multipart.abort(video.getS3Key(), video.getUploadId());
        }
        cleaner.deleteAll(videoId.toString(), video.getS3Key());
        repository.delete(video);
    }

    @Transactional
    public void abort(UUID videoId, String ownerId) {
        Video video = require(videoId, ownerId);
        multipart.abort(video.getS3Key(), video.getUploadId());
        video.setStatus(VideoStatus.ABORTED);
        repository.save(video);
    }

    @Transactional(readOnly = true)
    public VideoDto get(UUID videoId, String ownerId) {
        return VideoDto.from(require(videoId, ownerId));
    }

    @Transactional(readOnly = true)
    public List<VideoDto> list(String ownerId) {
        return repository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(VideoDto::from)
                .toList();
    }

    private Video requireUploading(UUID videoId, String ownerId) {
        Video video = require(videoId, ownerId);
        if (video.getStatus() != VideoStatus.UPLOADING) {
            throw new IllegalStateException("El video no esta en estado UPLOADING: " + video.getStatus());
        }
        return video;
    }

    private Video require(UUID videoId, String ownerId) {
        return repository.findByIdAndOwnerId(videoId, ownerId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Video no encontrado: " + videoId));
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "mp4";
        }
        String ext = filename.substring(dot + 1).toLowerCase();
        return ext.replaceAll("[^a-z0-9]", "");
    }
}
