package com.youtube.summary.api.config;

import com.youtube.summary.domain.port.TranscriptProvider;
import com.youtube.summary.infrastructure.fallback.ChunkedTranscriptProvider;
import com.youtube.summary.infrastructure.fallback.OllamaTranscribeClient;
import com.youtube.summary.infrastructure.fallback.TranscribeClient;
import com.youtube.summary.infrastructure.transcript.YouTubeTranscriptAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.nio.file.Path;

/**
 * Registers transcript providers in order: YouTube captions first, then chunked fallback (yt-dlp + Ollama/local whisper).
 */
@Configuration
public class TranscriptProviderConfig {

    @Bean
    @Order(0)
    public TranscriptProvider youtubeTranscriptProvider() {
        return new YouTubeTranscriptAdapter();
    }

    @Bean
    @Order(1)
    public TranscriptProvider chunkedTranscriptProvider(
            @Value("${youtube.summary.transcribe.command:whisper}") String transcribeCommand,
            @Value("${youtube.summary.yt-dlp.path:yt-dlp}") String ytDlpPath,
            @Value("${youtube.summary.ffmpeg.path:ffmpeg}") String ffmpegPath,
            @Value("${youtube.summary.chunked.temp-dir:${java.io.tmpdir}/youtube-summary}") String tempDir) {
        Path tempPath = Path.of(tempDir);
        TranscribeClient transcribeClient = transcribeCommand != null && !transcribeCommand.isBlank()
                ? new OllamaTranscribeClient(transcribeCommand, ffmpegPath, tempPath)
                : null;
        return ChunkedTranscriptProvider.createOptional(transcribeClient, ytDlpPath, ffmpegPath, tempPath);
    }
}
