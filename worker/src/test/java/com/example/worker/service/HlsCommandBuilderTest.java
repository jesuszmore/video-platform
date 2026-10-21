package com.example.worker.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HlsCommandBuilderTest {

    @Test
    void buildsHlsCommandWithThreeRenditionsAndMasterPlaylist() {
        List<String> cmd = HlsCommandBuilder.hls(Path.of("/tmp/in.mp4"), Path.of("/tmp/out"), "veryfast", true);

        assertThat(cmd).startsWith("ffmpeg", "-y", "-i");
        assertThat(cmd).containsSequence("-master_pl_name", "master.m3u8");
        assertThat(cmd).contains("libx264");
        assertThat(cmd).contains("5000k", "2800k", "1400k");
        assertThat(cmd).contains("0:a");
        assertThat(cmd).doesNotContain("anullsrc");
        assertThat(cmd).containsSequence("-var_stream_map", "v:0,a:0 v:1,a:1 v:2,a:2");
        assertThat(cmd.get(cmd.size() - 1)).endsWith("index.m3u8");
    }

    @Test
    void synthAudioWhenSourceHasNoAudioTrack() {
        List<String> cmd = HlsCommandBuilder.hls(Path.of("/tmp/in.mp4"), Path.of("/tmp/out"), "veryfast", false);

        assertThat(cmd).contains("anullsrc=channel_layout=stereo:sample_rate=48000");
        assertThat(cmd).contains("1:a");
        assertThat(cmd).contains("-shortest");
    }

    @Test
    void buildsThumbnailCommand() {
        List<String> cmd = HlsCommandBuilder.thumbnail(Path.of("/tmp/in.mp4"), Path.of("/tmp/thumb.jpg"));
        assertThat(cmd).contains("scale=640:-1", "-frames:v", "1");
        assertThat(cmd.get(cmd.size() - 1)).endsWith("thumb.jpg");
    }

    @Test
    void buildsProbeCommand() {
        List<String> cmd = HlsCommandBuilder.probeDuration(Path.of("/tmp/in.mp4"));
        assertThat(cmd).startsWith("ffprobe");
        assertThat(cmd).contains("format=duration");
    }
}
