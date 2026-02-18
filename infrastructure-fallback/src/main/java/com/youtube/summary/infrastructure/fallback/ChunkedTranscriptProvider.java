package com.youtube.summary.infrastructure.fallback;

import com.youtube.summary.domain.Transcript;
import com.youtube.summary.domain.TranscriptSegment;
import com.youtube.summary.domain.VideoId;
import com.youtube.summary.domain.port.TranscriptProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Fallback transcript provider when YouTube captions are not available.
 * Downloads audio via yt-dlp, transcribes via OpenAI Whisper API (optionally in chunks),
 * and returns a timed transcript.
 * <p>
 * Requires: yt-dlp and ffmpeg on PATH (or configured paths), and OPENAI_API_KEY for Whisper.
 * </p>
 */
public class ChunkedTranscriptProvider implements TranscriptProvider {

    private static final Logger log = LoggerFactory.getLogger(ChunkedTranscriptProvider.class);
    private static final String VIDEO_BASE = "https://www.youtube.com/watch?v=";
    private static final long WHISPER_MAX_BYTES = 24L * 1024 * 1024; // 24 MB
    private static final int CHUNK_DURATION_SECONDS = 600; // 10 min chunks when splitting
    private static final long DOWNLOAD_TIMEOUT_SECONDS = 600; // 10 min

    private final String openaiApiKey;
    private final String ytDlpPath;
    private final String ffmpegPath;
    private final Path tempDir;

    public ChunkedTranscriptProvider(String openaiApiKey, String ytDlpPath, String ffmpegPath, Path tempDir) {
        this.openaiApiKey = openaiApiKey;
        this.ytDlpPath = ytDlpPath != null && !ytDlpPath.isBlank() ? ytDlpPath : "yt-dlp";
        this.ffmpegPath = ffmpegPath != null && !ffmpegPath.isBlank() ? ffmpegPath : "ffmpeg";
        this.tempDir = tempDir != null ? tempDir : Path.of(System.getProperty("java.io.tmpdir"), "youtube-summary");
    }

    /**
     * Creates a provider that will return empty (chunked processing disabled) when API key is missing.
     */
    public static ChunkedTranscriptProvider createOptional(String openaiApiKey, String ytDlpPath, String ffmpegPath, Path tempDir) {
        return new ChunkedTranscriptProvider(openaiApiKey, ytDlpPath, ffmpegPath, tempDir);
    }

    @Override
    public Optional<Transcript> fetch(VideoId videoId) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            log.debug("ChunkedTranscriptProvider: OpenAI API key not set, skipping");
            return Optional.empty();
        }
        Path workDir = null;
        try {
            workDir = Files.createDirectories(tempDir.resolve("v-" + videoId.getValue()));
            Path audioFile = downloadAudio(videoId, workDir);
            if (audioFile == null || !Files.exists(audioFile) || Files.size(audioFile) == 0) {
                return Optional.empty();
            }
            List<TranscriptSegment> allSegments = new ArrayList<>();
            long size = Files.size(audioFile);
            if (size <= WHISPER_MAX_BYTES) {
                List<WhisperApiClient.WhisperSegment> segments = new WhisperApiClient(openaiApiKey).transcribe(audioFile);
                for (WhisperApiClient.WhisperSegment s : segments) {
                    allSegments.add(new TranscriptSegment(s.startSeconds(), s.durationSeconds(), s.text()));
                }
            } else {
                List<Path> chunks = splitAudio(audioFile, workDir);
                WhisperApiClient client = new WhisperApiClient(openaiApiKey);
                double offsetSeconds = 0;
                for (Path chunk : chunks) {
                    try {
                        List<WhisperApiClient.WhisperSegment> segments = client.transcribe(chunk);
                        double chunkDuration = 0;
                        for (WhisperApiClient.WhisperSegment s : segments) {
                            double start = offsetSeconds + s.startSeconds();
                            allSegments.add(new TranscriptSegment(start, s.durationSeconds(), s.text()));
                            chunkDuration = Math.max(chunkDuration, s.startSeconds() + s.durationSeconds());
                        }
                        if (chunkDuration <= 0 && !segments.isEmpty()) {
                            chunkDuration = CHUNK_DURATION_SECONDS;
                        } else if (chunkDuration <= 0) {
                            chunkDuration = CHUNK_DURATION_SECONDS;
                        }
                        offsetSeconds += chunkDuration;
                    } finally {
                        try { Files.deleteIfExists(chunk); } catch (IOException ignored) {}
                    }
                }
            }
            if (allSegments.isEmpty()) {
                log.warn("ChunkedTranscriptProvider: no segments from Whisper for video {}", videoId.getValue());
                return Optional.empty();
            }
            allSegments.sort(Comparator.comparingDouble(TranscriptSegment::getStartSeconds));
            Transcript transcript = new Transcript(videoId, allSegments);
            log.info("ChunkedTranscriptProvider: produced transcript for video {} ({} segments)", videoId.getValue(), allSegments.size());
            return Optional.of(transcript);
        } catch (Exception e) {
            log.warn("ChunkedTranscriptProvider: failed for video {}: {}", videoId.getValue(), e.getMessage());
            return Optional.empty();
        } finally {
            if (workDir != null) {
                deleteRecursively(workDir);
            }
        }
    }

    private Path downloadAudio(VideoId videoId, Path workDir) throws IOException, InterruptedException {
        String url = VIDEO_BASE + videoId.getValue();
        Path out = workDir.resolve("audio.m4a");
        ProcessBuilder pb = new ProcessBuilder(
                ytDlpPath,
                "-x",
                "--audio-format", "m4a",
                "--no-playlist",
                "-o", out.toAbsolutePath().toString(),
                url
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        boolean finished = p.waitFor(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            log.warn("ChunkedTranscriptProvider: yt-dlp timed out for {}", videoId.getValue());
            return null;
        }
        if (p.exitValue() != 0) {
            log.warn("ChunkedTranscriptProvider: yt-dlp failed for {} (exit {})", videoId.getValue(), p.exitValue());
            return null;
        }
        if (!Files.exists(out)) {
            Path maybeM4a = workDir.resolve("audio.m4a.m4a");
            if (Files.exists(maybeM4a)) {
                return maybeM4a;
            }
            return null;
        }
        return out;
    }

    private List<Path> splitAudio(Path audioFile, Path workDir) throws IOException, InterruptedException {
        Path chunkPattern = workDir.resolve("chunk_%03d.m4a");
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-y",
                "-i", audioFile.toAbsolutePath().toString(),
                "-f", "segment",
                "-segment_time", String.valueOf(CHUNK_DURATION_SECONDS),
                "-c", "copy",
                "-reset_timestamps", "1",
                chunkPattern.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor(5, TimeUnit.MINUTES);
        List<Path> chunks = new ArrayList<>();
        try (Stream<Path> list = Files.list(workDir)) {
            list.filter(path -> path.getFileName().toString().startsWith("chunk_") && path.getFileName().toString().endsWith(".m4a"))
                    .sorted()
                    .forEach(chunks::add);
        }
        return chunks;
    }

    private static void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> list = Files.list(path)) {
                    list.forEach(ChunkedTranscriptProvider::deleteRecursively);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {}
    }

    /**
     * Helper to build a transcript from chunk-based segments (e.g. from STT per chunk).
     */
    public static Transcript buildFromChunkedSegments(VideoId videoId, List<ChunkSegment> segments) {
        List<TranscriptSegment> list = new ArrayList<>();
        for (ChunkSegment s : segments) {
            list.add(new TranscriptSegment(s.startSeconds(), s.durationSeconds(), s.text()));
        }
        return new Transcript(videoId, list);
    }

    public record ChunkSegment(double startSeconds, double durationSeconds, String text) {}
}
