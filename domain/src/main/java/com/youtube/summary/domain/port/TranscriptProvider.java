package com.youtube.summary.domain.port;

import com.youtube.summary.domain.Transcript;
import com.youtube.summary.domain.VideoId;

import java.util.Optional;

/**
 * Port: fetch transcript for a YouTube video.
 * Implementations: YouTube transcript API adapter, or chunk-based fallback when no captions.
 */
public interface TranscriptProvider {

    /**
     * Fetch transcript for the given video.
     *
     * @param videoId video identifier
     * @return optional transcript; empty if not available or on error
     */
    Optional<Transcript> fetch(VideoId videoId);
}
