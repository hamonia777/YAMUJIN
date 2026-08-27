package com.example.the_greatest_project.web;

import com.example.the_greatest_project.domain.ChatTurn;
import com.example.the_greatest_project.repo.ChatTurnRepository;
import com.example.the_greatest_project.security.Auth;
import com.example.the_greatest_project.service.BriefingService;
import com.example.the_greatest_project.service.ClaudeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    public record ChatReq(String question, String context) {
    }

    private final BriefingService briefing;
    private final ClaudeService claude;
    private final ChatTurnRepository chats;
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    public AiController(BriefingService briefing, ClaudeService claude, ChatTurnRepository chats) {
        this.briefing = briefing;
        this.claude = claude;
        this.chats = chats;
    }

    @GetMapping("/briefing")
    public Map<String, Object> briefing(@RequestParam(defaultValue = "false") boolean force) {
        return briefing.globalBriefing(force);
    }

    @GetMapping("/region/{region}")
    public Map<String, Object> region(@PathVariable String region,
                                      @RequestParam(defaultValue = "false") boolean force) {
        return briefing.regionDigest(region, force);
    }

    @GetMapping("/prism")
    public Map<String, Object> prism(@RequestParam String q,
                                     @RequestParam(defaultValue = "false") boolean force) {
        return briefing.prism(q, force);
    }

    @GetMapping("/podcast")
    public Map<String, Object> podcast(@RequestParam(defaultValue = "false") boolean force) {
        return briefing.podcast(force);
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(HttpServletRequest req, @RequestBody ChatReq body) {
        String q = body.question() == null ? "" : body.question().trim();
        if (q.isEmpty()) return Map.of("error", "질문을 입력하세요");
        String answer = briefing.chat(q, body.context());

        Long uid = Auth.uidOrNull(req);
        if (uid != null) {
            ChatTurn t = new ChatTurn();
            t.setUserId(uid);
            t.setQuestion(q);
            t.setAnswer(answer);
            t.setEngine(claude.engineName());
            chats.save(t);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("answer", answer);
        m.put("engine", claude.engineName());
        m.put("live", claude.live());
        return m;
    }

    @GetMapping("/history")
    public Object history(HttpServletRequest req) {
        Long uid = Auth.require(req);
        return chats.findTop30ByUserIdOrderByCreatedAtDesc(uid);
    }

    // ------------------------------------------------------------ streaming

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam String q) {
        SseEmitter emitter = new SseEmitter(180_000L);
        pool.submit(() -> briefing.chatStream(q,
                delta -> send(emitter, "delta", delta),
                () -> {
                    send(emitter, "done", "ok");
                    emitter.complete();
                }));
        return emitter;
    }

    @GetMapping(value = "/briefing/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter briefingStream() {
        SseEmitter emitter = new SseEmitter(240_000L);
        pool.submit(() -> briefing.briefingStream(
                delta -> send(emitter, "delta", delta),
                () -> {
                    send(emitter, "done", "ok");
                    emitter.complete();
                }));
        return emitter;
    }

    /** Deltas are JSON-encoded so newlines and trailing spaces survive the SSE wire format. */
    private void send(SseEmitter emitter, String name, String data) {
        if (data == null || data.isEmpty()) return;
        try {
            emitter.send(SseEmitter.event().name(name)
                    .data(JSON.writeValueAsString(data), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            emitter.complete();
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();
}
