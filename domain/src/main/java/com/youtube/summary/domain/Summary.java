package com.youtube.summary.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Generated summary of a video transcript, with optional hashtags.
 */
public final class Summary {

    private final VideoId videoId;
    private final String text;
    private final List<String> tags;

    public Summary(VideoId videoId, String text) {
        this(videoId, text, List.of());
    }

    public Summary(VideoId videoId, String text, List<String> tags) {
        this.videoId = Objects.requireNonNull(videoId, "videoId");
        this.text = text != null ? text.trim() : "";
        this.tags = tags != null ? List.copyOf(tags) : Collections.emptyList();
    }

    public VideoId getVideoId() {
        return videoId;
    }

    public String getText() {
        return text;
    }

    public List<String> getTags() {
        return tags;
    }
}
