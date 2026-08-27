package com.example.the_greatest_project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 시장 지표. Yahoo Finance 의 공개 chart 엔드포인트만 사용합니다 (키 불필요).
 *
 * <p>가져오지 못한 심볼은 조용히 빠집니다. 값을 추정하거나 마지막 값을 재사용하지 않습니다 -
 * 시세는 틀리면 안 되는 숫자라서요.
 */
@Service
public class MarketService {

    private static final Logger log = LoggerFactory.getLogger(MarketService.class);
    private static final String CHART = "https://query1.finance.yahoo.com/v8/finance/chart/";

    public record Symbol(String code, String label, String group, String unit) {
    }

    /** 메인 화면 티커 순서. 앞쪽이 더 중요합니다. */
    public static final List<Symbol> BOARD = List.of(
            new Symbol("^IXIC", "나스닥", "해외지수", "pt"),
            new Symbol("^GSPC", "S&P 500", "해외지수", "pt"),
            new Symbol("^DJI", "다우존스", "해외지수", "pt"),
            new Symbol("^KS11", "코스피", "국내지수", "pt"),
            new Symbol("^KQ11", "코스닥", "국내지수", "pt"),
            new Symbol("KRW=X", "원/달러", "환율", "원"),
            new Symbol("BTC-USD", "비트코인", "가상자산", "$"),
            new Symbol("CL=F", "WTI 유가", "원자재", "$"),
            new Symbol("GC=F", "금", "원자재", "$"),
            new Symbol("^VIX", "VIX 공포지수", "리스크", "")
    );

    private final Http http;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
    private final Map<String, Instant> cachedAt = new ConcurrentHashMap<>();

    public MarketService(Http http) {
        this.http = http;
    }

    /** 티커 보드 전체. 60초 캐시. */
    public List<Map<String, Object>> board() {
        List<Map<String, Object>> out = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<Map<String, Object>>> futures = BOARD.stream()
                    .map(s -> pool.submit(() -> quote(s, "1d", "5m")))
                    .toList();
            for (var f : futures) {
                try {
                    Map<String, Object> q = f.get(15, TimeUnit.SECONDS);
                    if (q != null) out.add(q);
                } catch (Exception ignored) {
                    // a dead symbol must not take the board down
                }
            }
        }
        return out;
    }

    public Map<String, Object> quote(String code, String range, String interval) {
        Symbol s = BOARD.stream().filter(x -> x.code().equalsIgnoreCase(code)).findFirst()
                .orElse(new Symbol(code, code, "기타", ""));
        return quote(s, range, interval);
    }

    private Map<String, Object> quote(Symbol sym, String range, String interval) {
        String key = sym.code() + "|" + range + "|" + interval;
        Instant at = cachedAt.get(key);
        if (at != null && Duration.between(at, Instant.now()).toSeconds() < 60) {
            Map<String, Object> hit = cache.get(key);
            if (hit != null) return hit;
        }

        String url = CHART + URLEncoder.encode(sym.code(), StandardCharsets.UTF_8)
                + "?range=" + range + "&interval=" + interval;
        String body = http.get(url, 12);
        if (body == null) return null;

        try {
            JsonNode result = json.readTree(body).path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) return null;
            JsonNode r = result.get(0);
            JsonNode meta = r.path("meta");

            double price = meta.path("regularMarketPrice").asDouble(Double.NaN);
            double prev = meta.path("chartPreviousClose").asDouble(
                    meta.path("previousClose").asDouble(Double.NaN));

            JsonNode closes = r.path("indicators").path("quote").get(0).path("close");
            JsonNode stamps = r.path("timestamp");

            List<Double> series = new ArrayList<>();
            List<Long> times = new ArrayList<>();
            if (closes.isArray()) {
                for (int i = 0; i < closes.size(); i++) {
                    JsonNode c = closes.get(i);
                    if (c == null || c.isNull()) continue;
                    series.add(c.asDouble());
                    if (stamps.isArray() && i < stamps.size()) times.add(stamps.get(i).asLong());
                }
            }
            if (Double.isNaN(price) && !series.isEmpty()) price = series.get(series.size() - 1);
            if (Double.isNaN(price)) return null;

            double change = Double.isNaN(prev) ? 0 : price - prev;
            double changePct = (Double.isNaN(prev) || prev == 0) ? 0 : change / prev * 100;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", sym.code());
            m.put("label", sym.label());
            m.put("group", sym.group());
            m.put("unit", sym.unit());
            m.put("price", round(price));
            m.put("previousClose", Double.isNaN(prev) ? null : round(prev));
            m.put("change", round(change));
            m.put("changePct", Math.round(changePct * 100) / 100.0);
            m.put("direction", change > 0 ? "up" : change < 0 ? "down" : "flat");
            m.put("currency", meta.path("currency").asText(""));
            m.put("marketState", meta.path("marketState").asText(""));
            m.put("exchange", meta.path("fullExchangeName").asText(""));
            m.put("spark", downsample(series, 48));
            m.put("series", series);
            m.put("timestamps", times);
            m.put("range", range);
            m.put("interval", interval);
            m.put("fetchedAt", Instant.now());

            cache.put(key, m);
            cachedAt.put(key, Instant.now());
            return m;
        } catch (Exception e) {
            log.debug("market parse failed for {}: {}", sym.code(), e.toString());
            return null;
        }
    }

    /** 시장 한 줄 요약 - 대시보드 상단 배지용. */
    public Map<String, Object> mood(List<Map<String, Object>> board) {
        long up = board.stream().filter(m -> "up".equals(m.get("direction"))).count();
        long down = board.stream().filter(m -> "down".equals(m.get("direction"))).count();
        String verdict;
        if (board.isEmpty()) verdict = "시세를 불러오지 못했습니다";
        else if (up > down * 2) verdict = "위험자산 선호 우세";
        else if (down > up * 2) verdict = "위험 회피 우세";
        else verdict = "혼조";

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("up", up);
        m.put("down", down);
        m.put("total", board.size());
        m.put("verdict", verdict);
        return m;
    }

    private static List<Double> downsample(List<Double> series, int target) {
        if (series.size() <= target) return series;
        List<Double> out = new ArrayList<>(target);
        double step = (double) series.size() / target;
        for (int i = 0; i < target; i++) out.add(series.get((int) Math.floor(i * step)));
        out.set(target - 1, series.get(series.size() - 1));
        return out;
    }

    private static double round(double d) {
        return Math.abs(d) >= 100 ? Math.round(d * 100) / 100.0 : Math.round(d * 10000) / 10000.0;
    }
}
