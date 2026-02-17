package com.youtube.summary.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Full transcript of a video: ordered list of segments with timing.
 */
public final class Transcript {

    private final VideoId videoId;
    private final List<TranscriptSegment> segments;
    private final String fullText;

    public Transcript(VideoId videoId, List<TranscriptSegment> segments) {
        this.videoId = Objects.requireNonNull(videoId, "videoId");
        this.segments = segments != null ? List.copyOf(segments) : List.of();
        this.fullText = this.segments.stream()
                .map(TranscriptSegment::getText)
                .reduce("", (a, b) -> a + " " + b)
                .trim();
    }

    public VideoId getVideoId() {
        return videoId;
    }

    public List<TranscriptSegment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    public String getFullText() {
        return fullText;
    }

    public boolean isEmpty() {
        return segments.isEmpty() || fullText.isBlank();
    }
}
