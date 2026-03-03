package com.youtube.summary.infrastructure.llm;

import com.youtube.summary.domain.Agenda;
import com.youtube.summary.domain.AgendaItem;
import com.youtube.summary.domain.Transcript;
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
            You are an agenda generator. Output ONLY lines in this exact format, one per topic:
            M:SS - Topic title
            Examples: 0:00 - Introduction
            1:30 - Setting up the project
            5:20 - API design
            Use minutes and seconds (e.g. 0:00, 1:30, 12:05). Write topic titles in English (translate if the \
            transcript is in another language). You may add 1-3 #hashtags at the end of a title when they fit. \
            Do not output any other text, no introduction, no explanation.
            """;

    /** Matches "M:SS - Title" or "M:S - Title" (1 or 2 digit seconds), dash or en-dash. */
    private static final Pattern LINE_PATTERN = Pattern.compile("(\\d+):(\\d{1,2})\\s*[-–]\\s*(.+)");

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
        String truncated = text.length() > 12000 ? text.substring(0, 12000) + "..." : text;
        String userPrompt = "List each topic with its timestamp. One line per topic, format: M:SS - Title (e.g. 0:00 - Introduction). " +
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
        for (String line : response.split("\n")) {
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
