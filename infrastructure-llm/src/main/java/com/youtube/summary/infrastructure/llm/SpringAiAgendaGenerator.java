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
            You are an agenda generator. The transcript includes timestamps like [0:00] [1:30]. Output ONLY lines in this format:
            M:SS - Topic title
            Use the same M:SS values that appear in the transcript (pick key timestamps for each topic). Examples:
            0:00 - Introduction
            1:30 - Setting up the project
            5:20 - API design
            One line per topic. Titles in English. No other text, no intro, no numbering prefix.
            """;

    /** Matches "M:SS - Title" or "M:S - Title" (1 or 2 digit seconds), dash or en-dash. */
    private static final Pattern LINE_PATTERN = Pattern.compile("(\\d+):(\\d{1,2})\\s*[-–]\\s*(.+)");
    /** Optional leading list numbering: "1. " or "1) " */
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*\\d+[.)]\\s*");

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
        String userPrompt = "Using the timestamps in brackets [M:SS] from the transcript, list each main topic with its start time. " +
                "One line per topic, format: M:SS - Topic title (e.g. 0:00 - Introduction). Use the exact timestamps from the transcript. " +
                "Titles in English.\n\n" + truncated;
        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
        String raw = response != null ? stripMarkdownCodeBlocks(response.trim()) : "";
        List<AgendaItem> items = parseAgendaLines(raw);
        return new Agenda(transcript.getVideoId(), items);
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
            line = LEADING_NUMBER.matcher(line.trim()).replaceFirst("");
            line = line.trim();
            if (line.isEmpty()) continue;
            Matcher m = LINE_PATTERN.matcher(line);
            if (m.matches()) {
                int minutes = Integer.parseInt(m.group(1));
                int seconds = Integer.parseInt(m.group(2));
                if (seconds > 59) continue; // invalid, skip
                String title = m.group(3).trim();
                double startSeconds = minutes * 60.0 + seconds;
                items.add(new AgendaItem(title, startSeconds));
            }
        }
        return items;
    }
}
