package com.example.the_greatest_project.web;

import com.example.the_greatest_project.security.Auth;
import com.example.the_greatest_project.service.ClaudeService;
import com.example.the_greatest_project.service.FunService;
import com.example.the_greatest_project.service.NewsService;
import com.example.the_greatest_project.service.PoliticsService;
import com.example.the_greatest_project.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything that is not news or auth: 대한민국 정세, 최저가, and the deliberately silly wing.
 */
@RestController
@RequestMapping("/api")
public class SideController {

    public record ToneReq(String text, String tone) {
    }

    public record ExcuseReq(String situation, String level) {
    }

    private final PoliticsService politics;
    private final ProductService products;
    private final FunService fun;
    private final ClaudeService claude;
    private final NewsService news;

    public SideController(PoliticsService politics, ProductService products, FunService fun,
                          ClaudeService claude, NewsService news) {
        this.politics = politics;
        this.products = products;
        this.fun = fun;
        this.claude = claude;
        this.news = news;
    }

    // ---------------------------------------------------------------- 대한민국 정세

    @GetMapping("/korea/pulse")
    public Map<String, Object> koreaPulse(@RequestParam(defaultValue = "false") boolean force) {
        return politics.snapshot(force);
    }

    @GetMapping("/korea/history")
    public List<Map<String, Object>> koreaHistory() {
        return politics.history();
    }

    @GetMapping("/korea/hot")
    public List<String> koreaHot(@RequestParam(defaultValue = "8") int n) {
        return politics.hotIssues(n);
    }

    // ---------------------------------------------------------------- 최저가

    @GetMapping("/shop/search")
    public Map<String, Object> shopSearch(@RequestParam String q,
                                          @RequestParam(defaultValue = "16") int limit) {
        return products.search(q, limit);
    }

    @GetMapping("/shop/links")
    public List<Map<String, String>> shopLinks(@RequestParam String q) {
        return products.deepLinks(q);
    }

    @GetMapping("/shop/trending")
    public Map<String, Object> shopTrending() {
        return products.trendingPicks();
    }

    // ---------------------------------------------------------------- 재미 (opt-in only)

    @GetMapping("/fun/joseon")
    public Map<String, Object> joseon() {
        return fun.joseon();
    }

    @PostMapping("/fun/tone")
    public Map<String, Object> tone(@RequestBody ToneReq body) {
        return fun.tone(body.text(), body.tone() == null ? "아나운서" : body.tone());
    }

    @GetMapping("/fun/gacha")
    public Map<String, Object> gacha(@RequestParam(defaultValue = "anon") String seed) {
        return fun.gacha(seed);
    }

    @GetMapping("/fun/fortune")
    public Map<String, Object> fortune(HttpServletRequest req,
                                       @RequestParam(defaultValue = "") String who) {
        Long uid = Auth.uidOrNull(req);
        String key = uid != null ? "u" + uid : (who.isBlank() ? "guest" : who);
        return fun.fortune(key);
    }

    @PostMapping("/fun/excuse")
    public Map<String, Object> excuse(@RequestBody ExcuseReq body) {
        return fun.excuse(body.situation() == null ? "마감 지연" : body.situation(),
                body.level() == null ? "보통" : body.level());
    }

    @GetMapping("/fun/doomsday")
    public Map<String, Object> doomsday() {
        return fun.doomsday();
    }

    // ---------------------------------------------------------------- 미니게임

    @GetMapping("/game/realfake")
    public Map<String, Object> realFake() {
        return fun.realOrFake();
    }

    @GetMapping("/game/heat")
    public Map<String, Object> heatGuess() {
        return fun.heatGuess();
    }

    // ---------------------------------------------------------------- meta

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("app", "MUJIN");
        m.put("tagline", "전세계 뉴스를 긁어와 하나로 읽는 개인 인텔리전스 데스크");
        m.put("serverTime", ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toString());
        m.put("uptimeSince", START);
        m.put("claude", Map.of(
                "live", claude.live(),
                "engine", claude.engineName(),
                "source", claude.credentialSource(),
                "hint", claude.live()
                        ? "Claude 연동됨 · " + claude.credentialSource()
                        : "자격증명 없음 - 로컬 엔진 동작 중 (claude 로그인, ant auth login, 또는 ANTHROPIC_API_KEY)"));
        m.put("shopping", Map.of(
                "live", products.liveShopping(),
                "hint", products.liveShopping() ? "네이버 쇼핑 API 연동됨"
                        : "NAVER_CLIENT_ID/SECRET 미설정 - 실가격 대신 몰별 딥링크 제공"));
        m.put("crawler", news.health());
        return m;
    }

    /** Drops the cached credential probe so a fresh `ant auth login` takes effect without a restart. */
    @PostMapping("/meta/recheck")
    public Map<String, Object> recheck() {
        claude.recheck();
        boolean live = claude.live();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("live", live);
        m.put("engine", claude.engineName());
        m.put("source", claude.credentialSource());
        m.put("message", live
                ? "Claude 연결됨 · " + claude.credentialSource()
                : "아직 자격증명을 찾지 못했습니다. claude 로그인 상태, ant auth login, ANTHROPIC_API_KEY 중 하나를 확인하세요.");
        return m;
    }

    private static final Instant START = Instant.now();
}
