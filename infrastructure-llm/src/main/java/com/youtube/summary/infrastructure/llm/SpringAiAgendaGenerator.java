package com.youtube.summary.infrastructure.llm;

import com.youtube.summary.domain.Agenda;
import com.youtube.summary.domain.AgendaItem;
import com.youtube.summary.domain.Transcript;
import com.youtube.summary.domain.TranscriptSegment;
import com.youtube.summary.domain.port.AgendaGenerator;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agenda generator using Spring AI. Asks LLM for "MM:SS - Title" lines and parses them.
 */
public class SpringAiAgendaGenerator implements AgendaGenerator {

    private static final String SYSTEM_PROMPT = """
            MANDATORY: All section titles MUST be in English. If the transcript is in another language, translate \
            every section title into English. Never output titles in any other language.
            You are an agenda generator. The transcript includes timestamps like [0:00] [1:30].
            Your job: group the content by context into logical sections (e.g. Introduction, Setup, Main topic 1, Demo, Conclusion).
            Output ONLY lines in this format: M:SS - Section title
            Rules:
            - Group by context: one line per logical section, not every small subtopic. Typical video = 5-15 sections.
            - Use the start timestamp of each section from the transcript [M:SS]. Pick the timestamp where that section begins.
            - Section titles must be in English only (translate if needed). Clear and high-level (e.g. "Introduction", "Configuring the API").
            - No other text, no intro, no numbering prefix.
            """;

    /** Matches "M:SS - Title" or "M:S - Title"; flexible separators (dash, en-dash, em-dash, colon). */
    private static final Pattern LINE_PATTERN = Pattern.compile("(\\d+):(\\d{1,2})\\s*[-–—:\\s]+\\s*(.+)");
    /** Strip leading list style: "1. ", "1) ", "- ", "* " */
    private static final Pattern LEADING_PREFIX = Pattern.compile("^\\s*(?:\\d+[.)]\\s*|[-*]\\s*)");

    private final ChatClient chatClient;

    public SpringAiAgendaGenerator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Agenda generate(Transcript transcript) {
        String text = transcript.getFullText();
        if (text == null || text.isBlank()) {
            return new Agenda(transcript.getVideoId(), List.of());
        }
        String transcriptWithTimings = buildTranscriptWithTimings(transcript);
        String truncated = transcriptWithTimings.length() > 15000 ? transcriptWithTimings.substring(0, 15000) + "..." : transcriptWithTimings;
        String userPrompt = "Output the agenda in English only (translate all section titles to English if the transcript is not in English). " +
                "Group this transcript into logical sections by context. For each section output one line: M:SS - Section title. " +
                "Use timestamps from the brackets [M:SS]. Aim for 5-15 sections. " +
                "Example: 0:00 - Introduction\n2:30 - Project setup\n5:00 - API design and implementation\n\nTranscript:\n\n" + truncated;
        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
        String raw = response != null ? stripMarkdownCodeBlocks(response.trim()) : "";
        List<AgendaItem> items = parseAgendaLines(raw);
        if (items.isEmpty() && !transcript.getSegments().isEmpty()) {
            items = fallbackAgendaFromSegments(transcript);
        } else if (!items.isEmpty()) {
            items = mergeCloseItems(items, 45.0); // merge entries within 45 seconds
        }
        return new Agenda(transcript.getVideoId(), items);
    }

    /** Merge agenda items that are very close in time; keep first timestamp, prefer first title. */
    private static List<AgendaItem> mergeCloseItems(List<AgendaItem> items, double minGapSeconds) {
        if (items.size() <= 1) return items;
        List<AgendaItem> merged = new ArrayList<>();
        AgendaItem current = items.get(0);
        for (int i = 1; i < items.size(); i++) {
            AgendaItem next = items.get(i);
            if (next.getStartSeconds() - current.getStartSeconds() < minGapSeconds) {
                // Merge: keep current start, use current title (section start)
                continue;
            }
            merged.add(current);
            current = next;
        }
        merged.add(current);
        return merged;
    }

    private static String buildTranscriptWithTimings(Transcript transcript) {
        if (transcript.getSegments().isEmpty()) {
            return transcript.getFullText();
        }
        StringBuilder sb = new StringBuilder();
        for (TranscriptSegment seg : transcript.getSegments()) {
            int min = (int) (seg.getStartSeconds() / 60);
            int sec = (int) (seg.getStartSeconds() % 60);
            if (sb.length() > 0) sb.append(" ");
            sb.append("[").append(min).append(":").append(String.format("%02d", sec)).append("] ");
            sb.append(seg.getText());
        }
        return sb.toString().trim();
    }

    private static String stripMarkdownCodeBlocks(String text) {
        if (text == null || text.isEmpty()) return text;
        String s = text.strip();
        if (s.startsWith("```") && s.endsWith("```")) {
            int first = s.indexOf('\n');
            int last = s.lastIndexOf('\n');
            if (first > 0 && last > first) {
                s = s.substring(first + 1, last).trim();
            } else {
                s = s.replaceAll("^```\\w*\\n?|\\n?```$", "").trim();
            }
        }
        return s;
    }

    static List<AgendaItem> parseAgendaLines(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        List<AgendaItem> items = new ArrayList<>();
        for (String line : response.split("\\r?\\n")) {
            line = LEADING_PREFIX.matcher(line.trim()).replaceFirst("");
            line = line.trim();
            if (line.isEmpty()) continue;
            Matcher m = LINE_PATTERN.matcher(line);
            if (m.matches()) {
                int minutes = Integer.parseInt(m.group(1));
                int seconds = Integer.parseInt(m.group(2));
                if (seconds > 59) continue;
                String title = m.group(3).trim();
                if (title.isEmpty()) title = "Topic";
                double startSeconds = minutes * 60.0 + seconds;
                items.add(new AgendaItem(title, startSeconds));
            }
        }
        return items;
    }

    /** Build agenda from segments when LLM returns nothing; group by time windows for context-like sections. */
    private static List<AgendaItem> fallbackAgendaFromSegments(Transcript transcript) {
        if (transcript.getSegments().isEmpty()) return List.of();
        List<TranscriptSegment> segs = transcript.getSegments();
        double windowSeconds = 120.0; // ~2 min per section for fewer, context-grouped items
        List<AgendaItem> items = new ArrayList<>();
        double windowStart = -windowSeconds - 1;
        for (TranscriptSegment seg : segs) {
            double start = seg.getStartSeconds();
            if (start >= windowStart + windowSeconds || items.isEmpty()) {
                windowStart = start;
                String text = seg.getText().trim();
                String title = text.length() > 60 ? text.substring(0, 57).trim() + "..." : text;
                if (title.isEmpty()) title = "Section at " + formatTimestamp(start);
                items.add(new AgendaItem(title, start));
            }
        }
        return items;
    }

    private static String formatTimestamp(double startSeconds) {
        int totalSec = (int) Math.floor(startSeconds);
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return min + ":" + String.format("%02d", sec);
    }
}
