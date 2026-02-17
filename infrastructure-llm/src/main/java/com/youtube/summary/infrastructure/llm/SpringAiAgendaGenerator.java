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
            You are an agenda generator. Given a video transcript, produce a timed agenda.
            Output exactly one line per topic in this format: M:SS - Topic title
            Use minutes:seconds (e.g. 0:00, 1:30, 12:05). Output only these lines, no other text.
            """;

    private static final Pattern LINE_PATTERN = Pattern.compile("(\\d+):(\\d{2})\\s*-\\s*(.+)");

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
        String userPrompt = "Create a timed agenda (M:SS - Topic) for this transcript:\n\n" + truncated;
        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
        List<AgendaItem> items = parseAgendaLines(response);
        return new Agenda(transcript.getVideoId(), items);
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
                String title = m.group(3).trim();
                double startSeconds = minutes * 60.0 + seconds;
                items.add(new AgendaItem(title, startSeconds));
            }
        }
        return items;
    }
}
