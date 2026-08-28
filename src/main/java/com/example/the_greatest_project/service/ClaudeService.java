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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * The brain. Answers come from whichever credential this machine actually has, in this order:
 *
 * <ol>
 *   <li><b>SDK</b> - {@code ANTHROPIC_API_KEY} if set, otherwise whatever
 *       {@code AnthropicOkHttpClient.fromEnv()} resolves (auth token, {@code ant auth login}
 *       OAuth profile, workload identity, default profile). Billed to the API organisation.</li>
 *   <li><b>Claude Code CLI</b> - {@code claude -p} run as a subprocess. This is the no-key path:
 *       it rides the Claude subscription that is already logged in on this machine. See
 *       {@link ClaudeCliService} for why we spawn the CLI instead of reading its token.</li>
 *   <li><b>Local extractive engine</b> - no network, no credentials. Keeps the dashboard populated
 *       instead of showing empty boxes.</li>
 * </ol>
 *
 * <p>Which tier is live is settled by probing, not by guessing from environment variables, and
 * {@link #engineName()} / {@link #credentialSource()} report the truth to the UI. The SDK probe is
 * free (Models API); the CLI probe costs one trivial round trip, so it only runs when the SDK tier
 * has already failed, and both are cached for ten minutes.
 */
@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);
    private static final Duration PROBE_TTL = Duration.ofMinutes(10);

    private enum Mode {SDK, CLI, NONE}

    private final String apiKey;
    private final String model;
    private final boolean cliEnabled;
    private final ClaudeCliService cli;

    private volatile AnthropicClient client;
    private volatile boolean clientTried = false;

    private volatile Mode mode = Mode.NONE;
    private volatile Boolean probeResult = null;
    private volatile Instant probedAt = Instant.EPOCH;
    private volatile String credentialSource = "none";

    public ClaudeService(@Value("${mujin.claude.api-key:}") String apiKey,
                         @Value("${mujin.claude.model:claude-opus-5}") String model,
                         @Value("${mujin.claude.use-cli:true}") boolean cliEnabled,
                         ClaudeCliService cli) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.isBlank()) ? "claude-opus-5" : model.trim();
        this.cliEnabled = cliEnabled;
        this.cli = cli;
    }

    public String model() {
        return model;
    }

    /** Where the working credential came from - shown in the UI so the state is never a mystery. */
    public String credentialSource() {
        live();
        return credentialSource;
    }

    public String engineName() {
        return live() ? model : "local-extractive-v1";
    }

    /** True only if some tier authenticated. Cached, re-probed every 10 minutes. */
    public boolean live() {
        Boolean cached = probeResult;
        if (cached != null && Duration.between(probedAt, Instant.now()).compareTo(PROBE_TTL) < 0) {
            return cached;
        }
        synchronized (this) {
            if (probeResult != null && Duration.between(probedAt, Instant.now()).compareTo(PROBE_TTL) < 0) {
                return probeResult;
            }
            boolean ok = probe();
            probeResult = ok;
            probedAt = Instant.now();
            return ok;
        }
    }

    /** Forget the cached probe - call after logging in so the badge flips without a restart. */
    public void recheck() {
        synchronized (this) {
            probeResult = null;
            probedAt = Instant.EPOCH;
            client = null;
            clientTried = false;
            mode = Mode.NONE;
        }
    }

    private boolean probe() {
        AnthropicClient c = client();
        if (c != null) {
            try {
                c.models().list();   // cheapest authenticated call there is - no tokens billed
                if (!apiKey.isBlank()) credentialSource = "ANTHROPIC_API_KEY";
                else if (hasOauthProfile()) credentialSource = "ant auth login (OAuth 프로필)";
                else credentialSource = "환경 자격증명";
                mode = Mode.SDK;
                log.info("Claude 연결 확인됨 · {} · model={}", credentialSource, model);
                return true;
            } catch (Exception e) {
                log.debug("SDK 자격증명 없음 ({}) - CLI 경로를 확인합니다", e.getClass().getSimpleName());
            }
        }

        // No API credential. Fall back to the copy of Claude Code already logged in here.
        if (cliEnabled && cli.installed() && cli.probe(model)) {
            credentialSource = "Claude Code CLI (구독 계정)";
            mode = Mode.CLI;
            log.info("Claude 연결 확인됨 · {} · {} · model={}", credentialSource, cli.executablePath(), model);
            return true;
        }

        credentialSource = "none";
        mode = Mode.NONE;
        log.info("Claude 자격증명 없음 - 로컬 엔진으로 동작합니다.");
        return false;
    }

    /** Windows keeps the profile under %APPDATA%\Anthropic; POSIX under ~/.config/anthropic. */
    private static boolean hasOauthProfile() {
        try {
            String override = System.getenv("ANTHROPIC_CONFIG_DIR");
            if (override != null && !override.isBlank() && Files.isDirectory(Path.of(override))) return true;
            String appData = System.getenv("APPDATA");
            if (appData != null && Files.isDirectory(Path.of(appData, "Anthropic"))) return true;
            String home = System.getProperty("user.home");
            return home != null && Files.isDirectory(Path.of(home, ".config", "anthropic"));
        } catch (Exception e) {
            return false;
        }
    }

    private AnthropicClient client() {
        if (client != null) return client;
        synchronized (this) {
            if (client != null) return client;
            if (clientTried) return null;
            clientTried = true;
            try {
                client = apiKey.isBlank()
                        // resolves API key -> auth token -> OAuth profile -> WIF -> default profile
                        ? AnthropicOkHttpClient.fromEnv()
                        : AnthropicOkHttpClient.builder().apiKey(apiKey).build();
            } catch (Exception e) {
                log.debug("Anthropic 클라이언트를 만들지 못했습니다: {}", e.toString());
                client = null;
            }
            return client;
        }
    }

    /** One-shot completion. Returns the fallback text when no tier can answer. */
    public String ask(String system, String user, int maxTokens, String fallback) {
        if (!live()) return fallback;

        if (mode == Mode.CLI) {
            // maxTokens has no CLI equivalent; length is steered by the prompt instead.
            String out = cli.ask(model, system, user);
            return (out == null || out.isBlank()) ? fallback : out;
        }

        AnthropicClient c = client();
        if (c == null) return fallback;
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .system(system)
                    .thinking(ThinkingConfigAdaptive.builder().build())
                    .addUserMessage(user)
                    .build();
            Message res = c.messages().create(params);
            StringBuilder sb = new StringBuilder();
            for (ContentBlock b : res.content()) {
                b.text().ifPresent(t -> sb.append(t.text()));
            }
            String out = sb.toString().trim();
            return out.isEmpty() ? fallback : out;
        } catch (Exception e) {
            log.warn("Claude 호출 실패, 폴백 사용: {}", e.toString());
            return fallback;
        }
    }

    /** Streaming completion - each text delta is pushed to {@code onDelta} as it arrives. */
    public void stream(String system, String user, int maxTokens, String fallback,
                       Consumer<String> onDelta, Runnable onDone) {
        if (!live()) {
            typeOut(fallback, onDelta, 18);
            onDone.run();
            return;
        }

        if (mode == Mode.CLI) {
            try {
                if (!cli.stream(model, system, user, onDelta)) typeOut(fallback, onDelta, 12);
            } finally {
                onDone.run();
            }
            return;
        }

        AnthropicClient c = client();
        if (c == null) {
            typeOut(fallback, onDelta, 18);
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
            try (StreamResponse<RawMessageStreamEvent> res = c.messages().createStreaming(params)) {
                res.stream()
                        .flatMap(ev -> ev.contentBlockDelta().stream())
                        .flatMap(d -> d.delta().text().stream())
                        .forEach(t -> onDelta.accept(t.text()));
            }
        } catch (Exception e) {
            log.warn("Claude 스트리밍 실패, 폴백 사용: {}", e.toString());
            typeOut(fallback, onDelta, 12);
        } finally {
            onDone.run();
        }
    }

    /** Replays canned text at typing speed so the fallback still feels like a live answer. */
    private static void typeOut(String text, Consumer<String> onDelta, long delayMs) {
        for (String chunk : chunks(text, 42)) {
            onDelta.accept(chunk);
            sleep(delayMs);
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
