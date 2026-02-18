package com.youtube.summary.infrastructure.fallback;

import java.nio.file.Path;
import java.util.List;

/**
 * Client for transcribing an audio file into timed segments.
 * Implementations may use a local tool (e.g. whisper CLI) or a remote API.
 */
public interface TranscribeClient {

    /**
     * Segment with start time (seconds), duration (seconds), and text.
     */
    record Segment(double startSeconds, double durationSeconds, String text) {}

    /**
     * Transcribe a single audio file. Returns segments with start, duration, and text.
     *
     * @param audioFile path to the audio file (e.g. m4a, wav)
     * @return list of segments; empty if transcription failed or produced nothing
     */
    List<Segment> transcribe(Path audioFile) throws Exception;
}
