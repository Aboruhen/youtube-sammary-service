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
            MANDATORY: All section titles MUST be in English only. If the transcript is in another language, \
            translate the meaning of each section into English for the title.
            You are an agenda generator. The transcript includes timestamps like [0:00] [1:30].
            Your job: group the content by context into logical sections and give each section a TOPIC-style title \
            that describes what that section is about, in English.
            Output ONLY lines in this format: M:SS - Section title
            Rules:
            - Group by context: one line per logical section. Typical video = 5-15 sections.
            - Use the start timestamp of each section from the transcript [M:SS].
            - Each title must be in English: a short TOPIC that captures the context (e.g. "Setting up the project", \
            "How authentication works", "Configuring the database"). Be specific to the content, not generic.
            - Do NOT use generic labels like "Part 1", "Part 2", "Main Topic", or "Section 3". Never use "Part" with a number.
            - Keep titles concise but descriptive (roughly 3-8 words). No intro, no numbering prefix.
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
        String userPrompt = "Output the agenda in English only: if the transcript is not in English, translate each section title to English. " +
                "Group this transcript into logical sections. For each section output one line: M:SS - Section title. " +
                "Use timestamps from the brackets [M:SS]. Each title must be a topic in English that describes that section's content (e.g. 'Installing dependencies', 'Explaining the algorithm'). " +
                "Do not use 'Part 1', 'Part 2' or other generic labels. Aim for 5-15 sections. " +
                "Example:\n0:00 - Introduction to the project\n2:30 - Installing and configuring the tools\n5:00 - How the API handles requests\n12:00 - Running the demo\n18:00 - Summary and next steps\n\nTranscript:\n\n" + truncated;
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
        double videoEndSeconds = getVideoEndSeconds(transcript);
        items = assignEndTimes(items, videoEndSeconds);
        return new Agenda(transcript.getVideoId(), items);
    }

    private static double getVideoEndSeconds(Transcript transcript) {
        if (transcript.getSegments().isEmpty()) return 0;
        TranscriptSegment last = transcript.getSegments().get(transcript.getSegments().size() - 1);
        return last.getEndSeconds();
    }

    /** Set each item's end time to the next item's start, or video end for the last item. */
    private static List<AgendaItem> assignEndTimes(List<AgendaItem> items, double videoEndSeconds) {
        if (items.isEmpty()) return items;
        List<AgendaItem> result = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            AgendaItem item = items.get(i);
            double end = (i + 1 < items.size())
                    ? items.get(i + 1).getStartSeconds()
                    : Math.max(item.getStartSeconds(), videoEndSeconds);
            result.add(new AgendaItem(item.getTitle(), item.getStartSeconds(), end));
        }
        return result;
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

    /** English-only fallback titles when LLM returns nothing (segment text may be in any language). */
    private static final String[] FALLBACK_ENGLISH_TITLES = {
            "Introduction", "Overview", "Main content", "Key points", "Details and examples",
            "Implementation", "Summary", "Conclusion"
    };

    /** Build agenda from segments when LLM returns nothing; use English titles only. */
    private static List<AgendaItem> fallbackAgendaFromSegments(Transcript transcript) {
        if (transcript.getSegments().isEmpty()) return List.of();
        List<TranscriptSegment> segs = transcript.getSegments();
        double windowSeconds = 120.0; // ~2 min per section
        double videoEnd = segs.get(segs.size() - 1).getEndSeconds();
        List<AgendaItem> items = new ArrayList<>();
        double windowStart = -windowSeconds - 1;
        for (TranscriptSegment seg : segs) {
            double start = seg.getStartSeconds();
            if (start >= windowStart + windowSeconds || items.isEmpty()) {
                windowStart = start;
                String title = FALLBACK_ENGLISH_TITLES[items.size() % FALLBACK_ENGLISH_TITLES.length];
                double end = Math.max(start, videoEnd);
                items.add(new AgendaItem(title, start, end));
            }
        }
        return assignEndTimes(items, videoEnd);
    }
}
