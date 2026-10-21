package com.example.video.web;

import com.example.video.service.VideoService;
import com.example.video.web.dto.VideoDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videos;

    public VideoController(VideoService videos) {
        this.videos = videos;
    }

    @GetMapping
    public List<VideoDto> list(Principal principal) {
        return videos.list(principal.getName());
    }

    @GetMapping("/{videoId}")
    public VideoDto get(@PathVariable UUID videoId, Principal principal) {
        return videos.get(videoId, principal.getName());
    }

    @PostMapping("/{videoId}/retry")
    public VideoDto retry(@PathVariable UUID videoId, Principal principal) {
        return videos.retry(videoId, principal.getName());
    }

    @DeleteMapping("/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID videoId, Principal principal) {
        videos.delete(videoId, principal.getName());
    }
}
