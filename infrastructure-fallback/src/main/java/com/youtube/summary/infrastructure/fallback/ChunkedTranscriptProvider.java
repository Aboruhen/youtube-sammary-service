package com.youtube.summary.infrastructure.fallback;

import com.youtube.summary.domain.Transcript;
import com.youtube.summary.domain.TranscriptSegment;
import com.youtube.summary.domain.VideoId;
import com.youtube.summary.domain.port.TranscriptProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fallback transcript provider when YouTube captions are not available.
 * <p>
 * Placeholder implementation: in a full solution this would download the video in chunks,
 * run speech-to-text (e.g. Whisper, or a cloud API) on each chunk, and merge segments.
 * For the minimal project we return empty so the use case can try the primary provider first
 * and only use this when no transcript is available (or we could stub with a single segment).
 * </p>
 * To implement chunked processing:
 * <ul>
 *   <li>Use a video download adapter (e.g. yt-dlp or YouTube extractor) to get audio/video in chunks</li>
 *   <li>Send each chunk to a SpeechToText port (e.g. OpenAI Whisper, local Whisper, or other STT)</li>
 *   <li>Map timing from chunk offset + segment offsets to build TranscriptSegment list</li>
 *   <li>Return a Transcript built from those segments</li>
 * </ul>
 */
public class ChunkedTranscriptProvider implements TranscriptProvider {

    private static final Logger log = LoggerFactory.getLogger(ChunkedTranscriptProvider.class);

    @Override
    public Optional<Transcript> fetch(VideoId videoId) {
        log.info("ChunkedTranscriptProvider: no captions fallback for video {} (chunked processing not implemented)", videoId.getValue());
        // Placeholder: return empty so caller knows fallback was tried but produced nothing.
        // Replace with real chunked download + STT when implementing.
        return Optional.empty();
    }

    /**
     * Helper to build a transcript from chunk-based segments (e.g. from STT per chunk).
     * Each segment has start/duration in seconds relative to the full video.
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
