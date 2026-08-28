package com.example.the_greatest_project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs prompts through the locally installed Claude Code CLI instead of an API key.
 *
 * <p>Why this exists: the user has a Claude subscription and no desire to mint and paste an API
 * key. Claude Code is already authenticated on this machine, and its headless mode
 * ({@code claude -p}) is a supported entry point, so we shell out to it. We deliberately do NOT
 * read {@code ~/.claude/.credentials.json} - that token belongs to Claude Code, and lifting it
 * into a third-party HTTP client is not what it is for. Spawning the CLI keeps the credential
 * inside the tool that owns it.
 *
 * <p>The invocation is locked down so the CLI behaves like a plain completion endpoint rather than
 * an agent loose in this repo:
 * <ul>
 *   <li>{@code --safe-mode} - no CLAUDE.md, skills, plugins, hooks, or custom agents</li>
 *   <li>{@code --strict-mcp-config} with no config - no MCP servers attach</li>
 *   <li>{@code --max-turns 1} - one answer, never a tool-using loop</li>
 *   <li>{@code --exclude-dynamic-system-prompt-sections} - drops the agent preamble, so our
 *       system prompt is essentially the whole context (measured: 2 input tokens of overhead)</li>
 *   <li>working directory is a scratch dir, not the project</li>
 * </ul>
 *
 * <p>The prompt goes in over stdin, not argv: Windows caps a command line near 32k characters and
 * a day's news digest blows straight through that. Both pipes are pinned to UTF-8 - the console
 * code page would otherwise mangle Korean.
 */
@Service
public class ClaudeCliService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCliService.class);

    /** Long enough for an Opus answer on a big digest, short enough that a wedged process dies. */
    private static final Duration CALL_TIMEOUT = Duration.ofMinutes(4);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(60);

    private final ObjectMapper json = new ObjectMapper();
    private final String configuredPath;

    private volatile String exe;
    private volatile boolean exeResolved = false;
    private volatile Path workDir;

    public ClaudeCliService(@Value("${yamujin.claude.cli-path:}") String configuredPath) {
        this.configuredPath = configuredPath == null ? "" : configuredPath.trim();
    }

    /** True when a {@code claude} binary is on disk. Says nothing about whether it is logged in. */
    public boolean installed() {
        return exe() != null;
    }

    public String executablePath() {
        return exe();
    }

    /**
     * A real one-token round trip. There is no free "am I authenticated" call for the CLI the way
     * the Models API is for the SDK, so we pay for the cheapest possible answer rather than guess
     * from the presence of a credentials file that may have expired.
     */
    public boolean probe(String model) {
        if (exe() == null) return false;
        try {
            Result r = run(args(model, "너는 계산기다.", "json", false), "1+1은? 숫자만.", PROBE_TIMEOUT);
            if (!r.ok()) {
                log.info("Claude CLI 인증 확인 실패 (exit={}): {}", r.exit, trim(r.stderr));
                return false;
            }
            JsonNode node = json.readTree(r.stdout);
            boolean success = "success".equals(node.path("subtype").asText())
                    && !node.path("is_error").asBoolean(false);
            if (!success) log.info("Claude CLI 응답이 성공이 아닙니다: {}", trim(r.stdout));
            return success;
        } catch (Exception e) {
            log.info("Claude CLI 프로브 실패: {}", e.toString());
            return false;
        }
    }

    /** One-shot completion. Returns null when the CLI could not answer, so the caller can fall back. */
    public String ask(String model, String system, String user) {
        if (exe() == null) return null;
        try {
            Result r = run(args(model, system, "json", false), user, CALL_TIMEOUT);
            if (!r.ok()) {
                log.warn("Claude CLI 호출 실패 (exit={}): {}", r.exit, trim(r.stderr));
                return null;
            }
            JsonNode node = json.readTree(r.stdout);
            if (node.path("is_error").asBoolean(false)) {
                log.warn("Claude CLI 오류 응답: {}", trim(r.stdout));
                return null;
            }
            String out = node.path("result").asText("").trim();
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            log.warn("Claude CLI 호출 예외: {}", e.toString());
            return null;
        }
    }

    /**
     * Streaming completion. Text deltas are handed to {@code onDelta} as the CLI emits them.
     *
     * @return true if the stream produced any text; false means the caller should use its fallback.
     */
    public boolean stream(String model, String system, String user, Consumer<String> onDelta) {
        String bin = exe();
        if (bin == null) return false;

        Process p = null;
        boolean any = false;
        try {
            ProcessBuilder pb = new ProcessBuilder(args(model, system, "stream-json", true));
            pb.directory(workDir().toFile());
            p = pb.start();
            writeStdin(p, user);

            try (BufferedReader out = reader(p.getInputStream())) {
                String line;
                while ((line = out.readLine()) != null) {
                    String text = deltaText(line);
                    if (text != null && !text.isEmpty()) {
                        onDelta.accept(text);
                        any = true;
                    }
                }
            }
            if (!p.waitFor(CALL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) p.destroyForcibly();
        } catch (Exception e) {
            log.warn("Claude CLI 스트리밍 실패: {}", e.toString());
        } finally {
            if (p != null && p.isAlive()) p.destroyForcibly();
        }
        return any;
    }

    /** Pulls the text out of one stream-json line, or null if the line is not a text delta. */
    private String deltaText(String line) {
        if (line == null || line.isBlank() || line.charAt(0) != '{') return null;
        try {
            JsonNode node = json.readTree(line);
            if (!"stream_event".equals(node.path("type").asText())) return null;
            JsonNode ev = node.path("event");
            if (!"content_block_delta".equals(ev.path("type").asText())) return null;
            JsonNode delta = ev.path("delta");
            if (!"text_delta".equals(delta.path("type").asText())) return null;
            return delta.path("text").asText("");
        } catch (Exception e) {
            return null;   // a malformed line is not worth failing the whole stream over
        }
    }

    // ---------------------------------------------------------------- process plumbing

    private List<String> args(String model, String system, String outputFormat, boolean streaming) {
        List<String> a = new ArrayList<>(List.of(
                exe(), "-p",
                "--model", model,
                "--system-prompt", system,
                "--exclude-dynamic-system-prompt-sections",
                "--safe-mode",
                "--strict-mcp-config",
                "--no-session-persistence",
                "--max-turns", "1",
                "--output-format", outputFormat));
        if (streaming) {
            a.add("--verbose");                    // stream-json refuses to run without it
            a.add("--include-partial-messages");   // without this only whole blocks arrive
        }
        return a;
    }

    private Result run(List<String> args, String stdin, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(workDir().toFile());
        Process p = pb.start();
        writeStdin(p, stdin);

        // stderr must be drained on its own thread; a full pipe buffer would deadlock the child
        StringBuilder err = new StringBuilder();
        Thread drain = Thread.ofVirtual().start(() -> {
            try (BufferedReader r = reader(p.getErrorStream())) {
                String l;
                while ((l = r.readLine()) != null) err.append(l).append('\n');
            } catch (IOException ignored) {
                // the child died mid-read; the exit code already tells the story
            }
        });

        String stdout;
        try (BufferedReader r = reader(p.getInputStream())) {
            stdout = r.lines().reduce(new StringBuilder(), (sb, l) -> sb.append(l).append('\n'),
                    StringBuilder::append).toString();
        }
        if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            p.destroyForcibly();
            return new Result(-1, stdout, "timeout after " + timeout.toSeconds() + "s");
        }
        drain.join(2000);
        return new Result(p.exitValue(), stdout, err.toString());
    }

    private static void writeStdin(Process p, String text) throws IOException {
        try (OutputStream os = p.getOutputStream()) {
            os.write(text.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
    }

    private static BufferedReader reader(java.io.InputStream in) {
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /** The CLI inherits its cwd; point it somewhere neutral so it never reads this project. */
    private Path workDir() {
        Path w = workDir;
        if (w != null) return w;
        synchronized (this) {
            if (workDir != null) return workDir;
            try {
                Path dir = Path.of(System.getProperty("java.io.tmpdir"), "yamujin-cli");
                Files.createDirectories(dir);
                workDir = dir;
            } catch (Exception e) {
                workDir = Path.of(System.getProperty("user.home"));
            }
            return workDir;
        }
    }

    /**
     * Finds the CLI. An explicit setting wins; otherwise we walk PATH ourselves rather than trust
     * the app to have inherited the user's PATH - a service started from an IDE or a scheduler
     * often has not.
     */
    private String exe() {
        if (exeResolved) return exe;
        synchronized (this) {
            if (exeResolved) return exe;
            exeResolved = true;
            exe = locate();
            if (exe == null) log.info("claude CLI를 찾지 못했습니다 - CLI 경유 호출은 비활성화됩니다.");
            else log.info("claude CLI 발견: {}", exe);
            return exe;
        }
    }

    private String locate() {
        if (!configuredPath.isBlank() && Files.isRegularFile(Path.of(configuredPath))) {
            return configuredPath;
        }
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        List<String> names = win ? List.of("claude.exe", "claude.cmd", "claude.bat") : List.of("claude");

        String path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(java.io.File.pathSeparator)) {
                if (entry.isBlank()) continue;
                for (String n : names) {
                    try {
                        Path c = Path.of(entry.trim(), n);
                        if (Files.isRegularFile(c)) return c.toString();
                    } catch (Exception ignored) {
                        // a malformed PATH entry is not fatal - keep scanning the rest
                    }
                }
            }
        }
        // the standard installer location, in case PATH was not inherited
        String home = System.getProperty("user.home");
        if (home != null) {
            for (String n : names) {
                Path c = Path.of(home, ".local", "bin", n);
                if (Files.isRegularFile(c)) return c.toString();
            }
        }
        return null;
    }

    private static String trim(String s) {
        if (s == null) return "";
        String t = s.strip();
        return t.length() > 400 ? t.substring(0, 400) + "…" : t;
    }

    private record Result(int exit, String stdout, String stderr) {
        boolean ok() {
            return exit == 0 && stdout != null && !stdout.isBlank();
        }
    }
}
