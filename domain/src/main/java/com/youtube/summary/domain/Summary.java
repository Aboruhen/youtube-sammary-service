package com.youtube.summary.domain;

import java.util.Objects;

/**
 * Generated summary of a video transcript.
 */
public final class Summary {

    private final VideoId videoId;
    private final String text;

    public Summary(VideoId videoId, String text) {
        this.videoId = Objects.requireNonNull(videoId, "videoId");
        this.text = text != null ? text.trim() : "";
    }

    public VideoId getVideoId() {
        return videoId;
    }

    public String getText() {
        return text;
    }
}
