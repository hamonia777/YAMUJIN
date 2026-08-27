package com.example.the_greatest_project.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * The brain. Wraps the official Anthropic Java SDK.
 *
 * <p>If no ANTHROPIC_API_KEY is present the whole site still works - every call degrades to a
 * local extractive engine so the dashboard never shows an empty box. {@link #live()} tells the UI
 * which engine actually answered.
 */
@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);

    private final String apiKey;
    private final String model;
    private volatile AnthropicClient client;

    public ClaudeService(@Value("${yamujin.claude.api-key:}") String apiKey,
                         @Value("${yamujin.claude.model:claude-opus-5}") String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.isBlank()) ? "claude-opus-5" : model.trim();
    }

    public boolean live() {
        return !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    public String engineName() {
        return live() ? model : "local-extractive-v1";
    }

    private AnthropicClient client() {
        AnthropicClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
                }
                c = client;
            }
        }
        return c;
    }

    /** One-shot completion. Returns the fallback text when Claude is unavailable. */
    public String ask(String system, String user, int maxTokens, String fallback) {
        if (!live()) return fallback;
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .system(system)
                    .thinking(ThinkingConfigAdaptive.builder().build())
                    .addUserMessage(user)
                    .build();
            Message res = client().messages().create(params);
            StringBuilder sb = new StringBuilder();
            for (ContentBlock b : res.content()) {
                b.text().ifPresent(t -> sb.append(t.text()));
            }
            String out = sb.toString().trim();
            return out.isEmpty() ? fallback : out;
        } catch (Exception e) {
            log.warn("Claude call failed, using fallback: {}", e.toString());
            return fallback;
        }
    }

    /** Streaming completion - each text delta is pushed to {@code onDelta} as it arrives. */
    public void stream(String system, String user, int maxTokens, String fallback,
                       Consumer<String> onDelta, Runnable onDone) {
        if (!live()) {
            for (String chunk : chunks(fallback, 42)) {
                onDelta.accept(chunk);
                sleep(18);
            }
            onDone.run();
            return;
        }
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .system(system)
                    .thinking(ThinkingConfigAdaptive.builder().build())
                    .addUserMessage(user)
                    .build();
            try (StreamResponse<RawMessageStreamEvent> res = client().messages().createStreaming(params)) {
                res.stream()
                        .flatMap(ev -> ev.contentBlockDelta().stream())
                        .flatMap(d -> d.delta().text().stream())
                        .forEach(t -> onDelta.accept(t.text()));
            }
        } catch (Exception e) {
            log.warn("Claude stream failed, using fallback: {}", e.toString());
            for (String chunk : chunks(fallback, 42)) {
                onDelta.accept(chunk);
                sleep(12);
            }
        } finally {
            onDone.run();
        }
    }

    private static java.util.List<String> chunks(String s, int size) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (s == null) return out;
        for (int i = 0; i < s.length(); i += size) {
            out.add(s.substring(i, Math.min(s.length(), i + size)));
        }
        return out;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
