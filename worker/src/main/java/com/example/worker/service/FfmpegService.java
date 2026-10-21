package com.example.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Service
public class FfmpegService {

    private static final Logger log = LoggerFactory.getLogger(FfmpegService.class);
    private static final int RENDITIONS = 3;

    private final String preset;

    public FfmpegService(@Value("${app.ffmpeg.preset:veryfast}") String preset) {
        this.preset = preset;
    }

    public void transcodeToHls(Path input, Path outDir) {
        try {
            for (int i = 0; i < RENDITIONS; i++) {
                Files.createDirectories(outDir.resolve("v" + i));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudieron crear los directorios HLS", ex);
        }
        run(HlsCommandBuilder.hls(input, outDir, preset, probeHasAudio(input)));
    }

    public boolean probeHasAudio(Path input) {
        try {
            return !capture(HlsCommandBuilder.probeHasAudio(input)).isBlank();
        } catch (Exception ex) {
            log.warn("No se pudo detectar audio en {}: {}", input, ex.getMessage());
            return false;
        }
    }

    public void generateThumbnail(Path input, Path output) {
        run(HlsCommandBuilder.thumbnail(input, output));
    }

    public int probeDurationSeconds(Path input) {
        try {
            String out = capture(HlsCommandBuilder.probeDuration(input));
            return (int) Math.round(Double.parseDouble(out.trim()));
        } catch (Exception ex) {
            log.warn("No se pudo obtener la duracion de {}: {}", input, ex.getMessage());
            return 0;
        }
    }

    private void run(java.util.List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            Deque<String> tail = new ArrayDeque<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    tail.addLast(line);
                    if (tail.size() > 40) {
                        tail.removeFirst();
                    }
                }
            }
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("FFmpeg termino con codigo " + exit
                        + ". Ultimas lineas: " + String.join(" | ", tail));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo ejecutar FFmpeg: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Transcodificacion interrumpida", ex);
        }
    }

    private String capture(java.util.List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().reduce("", (a, b) -> a + b + "\n");
        }
        process.waitFor();
        return output;
    }

    static void drain(Process process) throws IOException {
        process.getInputStream().transferTo(OutputStream.nullOutputStream());
    }
}
