package com.youtube.summary.api.config;

import com.youtube.summary.domain.port.TranscriptProvider;
import com.youtube.summary.infrastructure.fallback.ChunkedTranscriptProvider;
import com.youtube.summary.infrastructure.transcript.YouTubeTranscriptAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * Registers transcript providers in order: YouTube captions first, then chunked fallback.
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
    @ConditionalOnMissingBean(name = "chunkedTranscriptProvider")
    public TranscriptProvider chunkedTranscriptProvider() {
        return new ChunkedTranscriptProvider();
    }
}
