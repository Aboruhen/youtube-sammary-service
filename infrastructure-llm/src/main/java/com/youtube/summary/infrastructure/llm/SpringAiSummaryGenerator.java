package com.youtube.summary.infrastructure.llm;

import com.youtube.summary.domain.Summary;
import com.youtube.summary.domain.Transcript;
import com.youtube.summary.domain.port.SummaryGenerator;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Summary generator using Spring AI ChatClient (OpenAI, Ollama, etc.).
 */
public class SpringAiSummaryGenerator implements SummaryGenerator {

    private static final String SYSTEM_PROMPT = """
            You are a concise summarizer. Given a video transcript, produce a clear, short summary (2-4 paragraphs).
            Output only the summary text, no headings or meta-commentary.
            """;

    private final ChatClient chatClient;

    public SpringAiSummaryGenerator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Summary generate(Transcript transcript) {
        String text = transcript.getFullText();
        if (text == null || text.isBlank()) {
            return new Summary(transcript.getVideoId(), "(No content to summarize)");
        }
        String truncated = text.length() > 12000 ? text.substring(0, 12000) + "..." : text;
        String userPrompt = "Summarize the following video transcript:\n\n" + truncated;
        String summaryText = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
        return new Summary(transcript.getVideoId(), summaryText != null ? summaryText.trim() : "");
    }
}
