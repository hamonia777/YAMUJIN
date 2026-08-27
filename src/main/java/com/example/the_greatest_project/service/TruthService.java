package com.example.the_greatest_project.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TRUMP WATCH.
 *
 * <p>트럼프의 Truth Social 게시물을 공개 아카이브(trumpstruth.org)의 RSS로 가져옵니다.
 * X(트위터)는 API 유료화 + 스크래핑 차단이라 키 없이 접근할 방법이 없어서, 실제로 그가 글을 쓰는
 * 플랫폼을 직접 봅니다. 원문 링크는 truthsocial.com 본문으로 연결됩니다.
 *
 * <p>번역과 요약은 Claude가 하고, 키가 없으면 원문만 그대로 보여줍니다.
 */
@Service
public class TruthService {

    private static final Logger log = LoggerFactory.getLogger(TruthService.class);
    private static final String FEED = "https://trumpstruth.org/feed";

    private final Http http;
    private final ClaudeService claude;

    private volatile List<Map<String, Object>> cache = List.of();
    private volatile Instant cachedAt = Instant.EPOCH;
    private volatile Map<String, Object> digestCache = null;
    private volatile Instant digestAt = Instant.EPOCH;

    public TruthService(Http http, ClaudeService claude) {
        this.http = http;
        this.claude = claude;
    }

    public List<Map<String, Object>> posts(int limit) {
        if (Duration.between(cachedAt, Instant.now()).toMinutes() >= 6 || cache.isEmpty()) {
            List<Map<String, Object>> fresh = fetch();
            if (!fresh.isEmpty()) {
                cache = fresh;
                cachedAt = Instant.now();
            }
        }
        return cache.stream().limit(limit).toList();
    }

    private List<Map<String, Object>> fetch() {
        String xml = http.get(FEED, 15);
        if (xml == null) {
            log.debug("trump feed unavailable");
            return List.of();
        }
        try {
            Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
            List<Map<String, Object>> out = new ArrayList<>();
            for (Element item : doc.select("item")) {
                String raw = item.selectFirst("description") == null ? "" : item.selectFirst("description").text();
                String text = TextKit.stripHtml(raw);
                boolean retruth = text.startsWith("RT:") || raw.contains("quote-inline");

                Element orig = item.selectFirst("originalUrl");
                if (orig == null) orig = item.selectFirst("truth|originalUrl");
                String link = orig != null ? orig.text()
                        : (item.selectFirst("link") != null ? item.selectFirst("link").text() : "");

                Instant at = parseDate(item.selectFirst("pubDate") == null ? "" : item.selectFirst("pubDate").text());

                if (text.isBlank() && !retruth) continue;

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("text", text.isBlank() ? "(본문 없이 링크만 올린 글)" : text);
                m.put("link", link);
                m.put("retruth", retruth);
                m.put("publishedAt", at);
                m.put("relative", BriefingService.relative(at));
                m.put("shouty", shoutRatio(text));      // 대문자 비율 - 이 사람 특유의 신호
                m.put("sentiment", TextKit.sentiment(text));
                m.put("length", text.length());
                out.add(m);
            }
            return out;
        } catch (Exception e) {
            log.debug("trump feed parse failed: {}", e.toString());
            return List.of();
        }
    }

    /** ALL CAPS 비율. 그가 소리지르는 중인지 정량화합니다. */
    private static double shoutRatio(String s) {
        if (s == null || s.isBlank()) return 0;
        long letters = s.chars().filter(Character::isLetter).count();
        if (letters == 0) return 0;
        long upper = s.chars().filter(Character::isUpperCase).count();
        return Math.round((double) upper / letters * 1000) / 10.0;
    }

    /** 오늘의 트럼프: 한국어 요약 + 시장/한국 영향 판단. */
    public Map<String, Object> digest(boolean force) {
        if (!force && digestCache != null && Duration.between(digestAt, Instant.now()).toMinutes() < 25) {
            Map<String, Object> copy = new LinkedHashMap<>(digestCache);
            copy.put("cached", true);
            return copy;
        }

        List<Map<String, Object>> list = posts(30);
        if (list.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("markdown", "## 연결 실패\ntrumpstruth.org 피드에 접근하지 못했습니다. 잠시 후 다시 시도하세요.");
            empty.put("posts", List.of());
            empty.put("live", claude.live());
            return empty;
        }

        String dossier = list.stream().limit(25)
                .map(p -> "- (" + p.get("relative") + (Boolean.TRUE.equals(p.get("retruth")) ? ", 리트루스" : "")
                        + ") " + TextKit.clip(String.valueOf(p.get("text")), 400))
                .collect(Collectors.joining("\n"));

        double avgShout = list.stream().mapToDouble(p -> ((Number) p.get("shouty")).doubleValue()).average().orElse(0);

        String fallback = """
                ## 최근 게시물
                최근 %d건을 수집했습니다. 평균 대문자 비율은 %.1f%% 입니다.

                아래 원문 목록을 그대로 보여드립니다. 한국어 요약과 시장 영향 분석은
                ANTHROPIC_API_KEY 를 설정하면 Claude 가 직접 작성합니다.
                """.formatted(list.size(), avgShout);

        String text = claude.ask("""
                당신은 정치·시장 애널리스트입니다. 특정 진영을 편들지 않습니다.
                제공된 게시물에 실제로 있는 내용만 씁니다. 없는 발언을 지어내지 않습니다.
                게시물은 도널드 트럼프가 Truth Social 에 올린 원문입니다.
                한국어, 마크다운(h2 이하)만 사용.
                """, """
                아래는 최근 트럼프의 Truth Social 게시물입니다.

                형식:
                ## 오늘의 요지
                3문장. 그가 지금 무엇을 밀고 있는지.

                ## 주요 발언
                최대 4개. 각각 **주제** - 무슨 말을 했는지 한 줄 (원문 의미를 왜곡하지 말 것).

                ## 시장에서 볼 지점
                2불릿. 관세/금리/에너지/방산 등 실제 자산가격과 연결되는 지점만.

                ## 한국 관련
                직접 언급이 있으면 인용, 없으면 '직접 언급 없음'이라고 쓰고 간접 영향 한 줄.

                ---- 게시물 ----
                %s
                """.formatted(dossier), 2600, fallback);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("markdown", text);
        m.put("posts", list.stream().limit(12).toList());
        m.put("count", list.size());
        m.put("avgShout", Math.round(avgShout * 10) / 10.0);
        m.put("engine", claude.engineName());
        m.put("live", claude.live());
        m.put("source", "trumpstruth.org (Truth Social 공개 아카이브)");
        m.put("generatedAt", Instant.now());
        m.put("cached", false);
        digestCache = m;
        digestAt = Instant.now();
        return m;
    }

    private static Instant parseDate(String raw) {
        if (raw == null || raw.isBlank()) return Instant.now();
        try {
            return ZonedDateTime.parse(raw.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception ignored) {
        }
        return Instant.now();
    }
}
