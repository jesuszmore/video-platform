package com.example.video.web;

import com.example.video.service.VideoService;
import com.example.video.web.dto.CompleteUploadRequest;
import com.example.video.web.dto.InitUploadRequest;
import com.example.video.web.dto.InitUploadResponse;
import com.example.video.web.dto.PartUrlResponse;
import com.example.video.web.dto.VideoDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final VideoService videos;

    public UploadController(VideoService videos) {
        this.videos = videos;
    }

    @PostMapping("/init")
    @ResponseStatus(HttpStatus.CREATED)
    public InitUploadResponse init(@Valid @RequestBody InitUploadRequest request, Principal principal) {
        return videos.start(principal.getName(), request);
    }

    @GetMapping("/{videoId}/parts")
    public List<PartUrlResponse> parts(@PathVariable UUID videoId,
                                       @RequestParam List<Integer> numbers,
                                       Principal principal) {
        return videos.presignParts(videoId, principal.getName(), numbers);
    }

    @GetMapping("/{videoId}/parts/uploaded")
    public List<PartUrlResponse> uploaded(@PathVariable UUID videoId, Principal principal) {
        return videos.alreadyUploaded(videoId, principal.getName());
    }

    @PostMapping("/{videoId}/complete")
    public VideoDto complete(@PathVariable UUID videoId,
                             @Valid @RequestBody CompleteUploadRequest request,
                             Principal principal) {
        return videos.complete(videoId, principal.getName(), request);
    }

    @DeleteMapping("/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID videoId, Principal principal) {
        videos.abort(videoId, principal.getName());
    }
}
