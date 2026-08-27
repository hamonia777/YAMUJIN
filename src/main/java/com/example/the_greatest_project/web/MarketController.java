package com.example.the_greatest_project.web;

import com.example.the_greatest_project.service.MarketService;
import com.example.the_greatest_project.service.TruthService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 시세 보드와 TRUMP WATCH. */
@RestController
@RequestMapping("/api")
public class MarketController {

    private final MarketService market;
    private final TruthService truth;

    public MarketController(MarketService market, TruthService truth) {
        this.market = market;
        this.truth = truth;
    }

    @GetMapping("/market")
    public Map<String, Object> board() {
        List<Map<String, Object>> board = market.board();
        // the board payload gets embedded on every dashboard load - drop the full series there
        List<Map<String, Object>> slim = board.stream().map(m -> {
            Map<String, Object> c = new LinkedHashMap<>(m);
            c.remove("series");
            c.remove("timestamps");
            return c;
        }).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", slim);
        out.put("mood", market.mood(board));
        out.put("source", "Yahoo Finance (공개 chart 엔드포인트, 키 불필요)");
        out.put("disclaimer", "지연 시세일 수 있습니다. 투자 판단의 근거로 쓰지 마세요.");
        return out;
    }

    @GetMapping("/market/{code}")
    public Map<String, Object> detail(@PathVariable String code,
                                      @RequestParam(defaultValue = "1d") String range,
                                      @RequestParam(defaultValue = "5m") String interval) {
        Map<String, Object> q = market.quote(code, range, interval);
        if (q == null) return Map.of("error", "시세를 가져오지 못했습니다: " + code);
        return q;
    }

    @GetMapping("/trump")
    public Map<String, Object> trump(@RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("posts", truth.posts(limit));
        out.put("source", "trumpstruth.org (Truth Social 공개 아카이브)");
        return out;
    }

    @GetMapping("/trump/digest")
    public Map<String, Object> trumpDigest(@RequestParam(defaultValue = "false") boolean force) {
        return truth.digest(force);
    }
}
