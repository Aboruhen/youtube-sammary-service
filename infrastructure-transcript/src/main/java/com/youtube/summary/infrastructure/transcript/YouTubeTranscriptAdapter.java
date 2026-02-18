package com.youtube.summary.infrastructure.transcript;

import com.youtube.summary.domain.Transcript;
import com.youtube.summary.domain.TranscriptSegment;
import com.youtube.summary.domain.VideoId;
import com.youtube.summary.domain.port.TranscriptProvider;
import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptList;
import io.github.thoroldvix.api.TranscriptApiFactory;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Transcript provider using youtube-transcript-api (Java).
 * Fetches captions from YouTube; no transcript available for some videos.
 */
public class YouTubeTranscriptAdapter implements TranscriptProvider {

    private static final Logger log = LoggerFactory.getLogger(YouTubeTranscriptAdapter.class);

    private final YoutubeTranscriptApi transcriptApi;

    public YouTubeTranscriptAdapter() {
        this(TranscriptApiFactory.createDefault());
    }

    public YouTubeTranscriptAdapter(YoutubeTranscriptApi transcriptApi) {
        this.transcriptApi = transcriptApi;
    }

    /** Try English first, then common fallback languages when captions are not in en. */
    private static final String[] LANGUAGE_FALLBACKS = { "en", "uk", "de", "es", "fr", "pt", "ru" };

    @Override
    public Optional<Transcript> fetch(VideoId videoId) {
        try {
            TranscriptList transcriptList = transcriptApi.listTranscripts(videoId.getValue());
            var transcript = transcriptList.findTranscript(LANGUAGE_FALLBACKS);
            if (transcript == null) {
                return Optional.empty();
            }
            TranscriptContent content = transcript.fetch();
            if (content == null || content.getContent() == null || content.getContent().isEmpty()) {
                return Optional.empty();
            }
            List<TranscriptSegment> segments = new ArrayList<>();
            for (var fragment : content.getContent()) {
                segments.add(new TranscriptSegment(
                        fragment.getStart(),
                        fragment.getDur(),
                        fragment.getText()
                ));
            }
            return Optional.of(new Transcript(videoId, segments));
        } catch (Exception e) {
            log.warn("Failed to fetch transcript for video {}: {}", videoId.getValue(), e.getMessage());
            return Optional.empty();
        }
    }
}
