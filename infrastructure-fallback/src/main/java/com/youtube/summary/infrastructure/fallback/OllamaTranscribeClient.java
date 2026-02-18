package com.youtube.summary.infrastructure.fallback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Transcribes audio using a local whisper-compatible CLI (e.g. openai-whisper).
 * Fits the "Ollama stack": no API key, runs on the same machine as Ollama.
 * <p>
 * Install: {@code pip install openai-whisper} then ensure {@code whisper} is on PATH,
 * or set {@code youtube.summary.transcribe.command} to the full path.
 * </p>
 */
public class OllamaTranscribeClient implements TranscribeClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long TRANSCRIBE_TIMEOUT_MINUTES = 15;

    private final String whisperCommand;
    private final String ffmpegPath;
    private final Path workDir;

    public OllamaTranscribeClient(String whisperCommand, String ffmpegPath, Path workDir) {
        this.whisperCommand = whisperCommand != null && !whisperCommand.isBlank() ? whisperCommand : "whisper";
        this.ffmpegPath = ffmpegPath != null && !ffmpegPath.isBlank() ? ffmpegPath : "ffmpeg";
        this.workDir = workDir != null ? workDir : Path.of(System.getProperty("java.io.tmpdir"), "youtube-summary-transcribe");
    }

    @Override
    public List<Segment> transcribe(Path audioFile) throws IOException, InterruptedException {
        if (audioFile == null || !Files.exists(audioFile) || Files.size(audioFile) == 0) {
            return List.of();
        }
        Path outDir = Files.createDirectories(workDir.resolve("run-" + System.currentTimeMillis()));
        try {
            // whisper <file> -f json --output_dir <dir> (openai-whisper CLI)
            ProcessBuilder pb = new ProcessBuilder(
                    whisperCommand,
                    audioFile.toAbsolutePath().toString(),
                    "-f", "json",
                    "--output_dir", outDir.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(TRANSCRIBE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                p.destroyForcibly();
                throw new IOException("whisper CLI timed out");
            }
            if (p.exitValue() != 0) {
                throw new IOException("whisper CLI exited with " + p.exitValue());
            }
            Path jsonFile = resolveJsonOutput(outDir, audioFile);
            if (jsonFile == null || !Files.exists(jsonFile)) {
                return List.of();
            }
            return parseWhisperJson(Files.readString(jsonFile));
        } finally {
            deleteRecursively(outDir);
        }
    }

    private Path resolveJsonOutput(Path outDir, Path audioFile) throws IOException {
        String base = audioFile.getFileName().toString().replaceAll("\\.[^.]+$", "");
        Path withBase = outDir.resolve(base + ".json");
        if (Files.exists(withBase)) {
            return withBase;
        }
        try (var stream = Files.list(outDir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".json")).findFirst().orElse(null);
        }
    }

    private static List<Segment> parseWhisperJson(String json) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        JsonNode segmentsNode = root.get("segments");
        List<Segment> list = new ArrayList<>();
        if (segmentsNode == null || !segmentsNode.isArray()) {
            return list;
        }
        for (JsonNode seg : segmentsNode) {
            double start = seg.has("start") ? seg.get("start").asDouble() : 0;
            double end = seg.has("end") ? seg.get("end").asDouble() : start;
            String text = seg.has("text") ? seg.get("text").asText("").trim() : "";
            if (!text.isEmpty()) {
                list.add(new Segment(start, end - start, text));
            }
        }
        return list;
    }

    private static void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var list = Files.list(path)) {
                    list.forEach(OllamaTranscribeClient::deleteRecursively);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
