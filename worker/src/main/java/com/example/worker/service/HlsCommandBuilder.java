package com.example.worker.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class HlsCommandBuilder {

    private HlsCommandBuilder() {
    }

    public static List<String> hls(Path input, Path outDir, String preset, boolean hasAudio) {
        String filter = "[0:v]split=3[v1][v2][v3];"
                + "[v1]scale=w=1920:h=1080:force_original_aspect_ratio=decrease:force_divisible_by=2[v1o];"
                + "[v2]scale=w=1280:h=720:force_original_aspect_ratio=decrease:force_divisible_by=2[v2o];"
                + "[v3]scale=w=854:h=480:force_original_aspect_ratio=decrease:force_divisible_by=2[v3o]";

        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(input.toString());

        // Si el origen no tiene audio, generamos una pista silenciosa para que el
        // HLS sea uniforme (y para que -map a:0 no falle).
        if (!hasAudio) {
            cmd.add("-f");
            cmd.add("lavfi");
            cmd.add("-i");
            cmd.add("anullsrc=channel_layout=stereo:sample_rate=48000");
        }

        cmd.add("-filter_complex");
        cmd.add(filter);

        addVideoStream(cmd, "[v1o]", "0", "5000k", preset);
        addVideoStream(cmd, "[v2o]", "1", "2800k", preset);
        addVideoStream(cmd, "[v3o]", "2", "1400k", preset);

        String audio = hasAudio ? "0:a" : "1:a";
        for (int i = 0; i < 3; i++) {
            cmd.add("-map");
            cmd.add(audio);
        }
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("128k");
        cmd.add("-ac");
        cmd.add("2");
        if (!hasAudio) {
            cmd.add("-shortest");
        }

        cmd.add("-f");
        cmd.add("hls");
        cmd.add("-hls_time");
        cmd.add("6");
        cmd.add("-hls_playlist_type");
        cmd.add("vod");
        cmd.add("-hls_segment_filename");
        cmd.add(outDir.resolve("v%v/seg_%03d.ts").toString());
        cmd.add("-master_pl_name");
        cmd.add("master.m3u8");
        cmd.add("-var_stream_map");
        cmd.add("v:0,a:0 v:1,a:1 v:2,a:2");
        cmd.add(outDir.resolve("v%v/index.m3u8").toString());
        return cmd;
    }

    private static void addVideoStream(List<String> cmd, String label, String index, String bitrate, String preset) {
        cmd.add("-map");
        cmd.add(label);
        cmd.add("-c:v:" + index);
        cmd.add("libx264");
        cmd.add("-b:v:" + index);
        cmd.add(bitrate);
        cmd.add("-preset");
        cmd.add(preset);
        cmd.add("-g");
        cmd.add("48");
    }

    public static List<String> thumbnail(Path input, Path output) {
        return List.of("ffmpeg", "-y", "-ss", "00:00:05", "-i", input.toString(),
                "-frames:v", "1", "-vf", "scale=640:-1", output.toString());
    }

    public static List<String> probeDuration(Path input) {
        return List.of("ffprobe", "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", input.toString());
    }

    public static List<String> probeHasAudio(Path input) {
        return List.of("ffprobe", "-v", "error", "-select_streams", "a",
                "-show_entries", "stream=index", "-of", "csv=p=0", input.toString());
    }
}
