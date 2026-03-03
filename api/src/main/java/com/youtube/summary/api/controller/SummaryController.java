package com.youtube.summary.api.controller;

import com.youtube.summary.application.VideoSummaryResult;
import com.youtube.summary.application.VideoSummaryUseCase;
import com.youtube.summary.domain.AgendaItem;
import com.youtube.summary.domain.VideoId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private final VideoSummaryUseCase videoSummaryUseCase;

    public SummaryController(VideoSummaryUseCase videoSummaryUseCase) {
        this.videoSummaryUseCase = videoSummaryUseCase;
    }

    /**
     * Generate summary and agenda for a YouTube video.
     * Query param: videoUrl or videoId (e.g. https://www.youtube.com/watch?v=VIDEO_ID or VIDEO_ID).
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSummary(
            @RequestParam(required = false) String videoUrl,
            @RequestParam(required = false) String videoId) {
        String input = videoUrl != null ? videoUrl : videoId;
        if (input == null || input.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing videoUrl or videoId"));
        }
        VideoId id;
        try {
            id = new VideoId(input);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        Optional<VideoSummaryResult> result = videoSummaryUseCase.run(id);
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        VideoSummaryResult r = result.get();
        return ResponseEntity.ok(Map.of(
                "videoId", r.getTranscript().getVideoId().getValue(),
                "summary", r.getSummary().getText(),
                "tags", r.getSummary().getTags(),
                "agenda", toAgendaDto(r.getAgenda().getItems())
        ));
    }

    private static List<Map<String, Object>> toAgendaDto(List<AgendaItem> items) {
        return items.stream()
                .map(i -> Map.<String, Object>of(
                        "title", i.getTitle(),
                        "startTime", i.getStartTime(),
                        "endTime", i.getEndTime(),
                        "startSeconds", i.getStartSeconds(),
                        "endSeconds", i.getEndSeconds()))
                .toList();
    }
}
