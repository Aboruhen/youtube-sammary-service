package com.youtube.summary.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Agenda with timed items for a video.
 */
public final class Agenda {

    private final VideoId videoId;
    private final List<AgendaItem> items;

    public Agenda(VideoId videoId, List<AgendaItem> items) {
        this.videoId = Objects.requireNonNull(videoId, "videoId");
        this.items = items != null ? List.copyOf(items) : List.of();
    }

    public VideoId getVideoId() {
        return videoId;
    }

    public List<AgendaItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
