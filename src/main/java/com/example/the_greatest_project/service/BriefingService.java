package com.example.the_greatest_project.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Turns raw crawled headlines into things a human actually wants to read:
 * a global briefing, per-region digests, the cross-country "prism" (same story, six
 * national framings), issue timelines and a podcast script.
 */
@Service
public class BriefingService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm").withLocale(Locale.KOREAN);

    private static final String PERSONA = """
            당신은 '야무진(MUJIN)' 글로벌 인텔리전스 데스크의 수석 애널리스트입니다.
            한국어로, 군더더기 없이, 근거 중심으로 씁니다.

            규칙:
            - 제공된 헤드라인 목록에 실제로 존재하는 내용만 사용합니다. 없는 사실을 지어내지 않습니다.
            - 불확실하면 '보도에 따르면' 같은 완충 표현 대신 '확인 필요'라고 명시합니다.
            - 숫자, 고유명사, 국가명을 우선합니다. 형용사는 줄입니다.
            - 결론을 맨 앞에 둡니다. 독자는 바쁜 사람입니다.
            - 마크다운을 사용하되 h1은 쓰지 않습니다. h2/h3, 불릿, 굵게만 사용합니다.
            """;

    private final NewsService news;
    private final ClaudeService claude;

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(Instant at, Map<String, Object> body) {
    }

    public BriefingService(NewsService news, ClaudeService claude) {
        this.news = news;
        this.claude = claude;
    }

    // ------------------------------------------------------------ global briefing

    public Map<String, Object> globalBriefing(boolean force) {
        return cached("global", force, Duration.ofMinutes(20), () -> {
            List<Article> arts = news.all();
            List<Article> top = arts.stream().limit(70).toList();
            String dossier = dossier(top);
            String prompt = """
                    아래는 지금 이 시각 6개 권역(대한민국/미국/유럽/일본/중국/세계)에서 수집한 헤드라인입니다.
                    이걸로 '오늘의 글로벌 브리핑'을 작성하세요.

                    형식:
                    ## 한 줄 요약
                    (오늘 지구에서 가장 중요한 흐름 한 문장)

                    ## 오늘의 핵심 3가지
                    각 항목마다: **제목** - 무슨 일인지 2문장, 그리고 '왜 중요한가' 1문장.

                    ## 권역별 온도
                    대한민국/미국/유럽/일본/중국 각 한 줄씩. 해당 권역 헤드라인이 없으면 '특이사항 없음'.

                    ## 한국에 미치는 영향
                    3개 불릿. 경제/외교/산업 관점.

                    ## 지켜볼 것
                    앞으로 48시간 내 확인해야 할 것 2가지.

                    ---- 헤드라인 ----
                    %s
                    """.formatted(dossier);

            String fallback = localGlobalBriefing(arts);
            String text = claude.ask(PERSONA, prompt, 4000, fallback);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("markdown", text);
            m.put("engine", claude.engineName());
            m.put("live", claude.live());
            m.put("generatedAt", ZonedDateTime.now(KST).format(STAMP));
            m.put("sampled", top.size());
            m.put("totalCrawled", arts.size());
            m.put("pulse", news.pulse());
            m.put("headlines", top.stream().limit(12).map(this::card).toList());
            return m;
        });
    }

    // ------------------------------------------------------------ per-region digest

    public Map<String, Object> regionDigest(String region, boolean force) {
        String key = "region:" + region.toUpperCase(Locale.ROOT);
        return cached(key, force, Duration.ofMinutes(20), () -> {
            List<Article> arts = news.byRegion(region);
            List<Article> top = arts.stream().limit(45).toList();
            String label = RegionCatalog.label(region.toUpperCase(Locale.ROOT));
            String prompt = """
                    아래는 '%s' 권역에서 방금 수집한 헤드라인입니다.

                    형식:
                    ## %s 지금 상황
                    3문장 요약.

                    ## 주요 이슈
                    최대 4개. 각각 **제목** + 한 줄 설명.

                    ## 한국 관점
                    이 권역 상황이 한국(투자/수출/외교)에 주는 시사점 2불릿.

                    ---- 헤드라인 ----
                    %s
                    """.formatted(label, label, dossier(top));

            String text = claude.ask(PERSONA, prompt, 2600, localRegionDigest(label, arts));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("region", region.toUpperCase(Locale.ROOT));
            m.put("label", label);
            m.put("flag", RegionCatalog.flag(region.toUpperCase(Locale.ROOT)));
            m.put("markdown", text);
            m.put("engine", claude.engineName());
            m.put("live", claude.live());
            m.put("count", arts.size());
            m.put("generatedAt", ZonedDateTime.now(KST).format(STAMP));
            m.put("headlines", top.stream().limit(20).map(this::card).toList());
            return m;
        });
    }

    // ------------------------------------------------------------ the prism

    /**
     * PRISM: take one keyword, pull how each country's press is framing it, and ask Claude to
     * compare the framings side by side. This is the feature that makes the whole site worth it.
     */
    public Map<String, Object> prism(String keyword, boolean force) {
        String key = "prism:" + keyword.toLowerCase(Locale.ROOT);
        return cached(key, force, Duration.ofMinutes(25), () -> {
            List<Article> hits = news.search(keyword, 90);
            Map<String, List<Article>> byRegion = hits.stream()
                    .collect(Collectors.groupingBy(Article::region, LinkedHashMap::new, Collectors.toList()));

            StringBuilder sb = new StringBuilder();
            for (var e : byRegion.entrySet()) {
                sb.append("\n### ").append(RegionCatalog.label(e.getKey())).append("\n");
                e.getValue().stream().limit(8).forEach(a ->
                        sb.append("- [").append(a.source()).append("] ").append(a.title()).append("\n"));
            }

            String prompt = """
                    키워드: "%s"

                    아래는 같은 사안을 각국 매체가 어떻게 보도했는지 권역별로 묶은 것입니다.
                    같은 사건을 국가별로 어떻게 다르게 프레이밍하는지 비교 분석하세요.

                    형식:
                    ## 사안 정리
                    2문장.

                    ## 프레이밍 비교
                    권역별로: **권역명** - 이 나라 언론이 강조하는 것 / 의도적으로 덜 다루는 것.
                    데이터가 있는 권역만 작성.

                    ## 왜 다르게 쓰는가
                    구조적 이유 2~3불릿 (이해관계, 안보, 산업, 국내정치).

                    ## 한국 독자를 위한 결론
                    2문장. 어느 관점을 기본값으로 삼되 무엇을 보정해서 읽어야 하는지.

                    ---- 권역별 보도 ----
                    %s
                    """.formatted(keyword, sb);

            String fallback = localPrism(keyword, byRegion);
            String text = claude.ask(PERSONA, prompt, 3200, fallback);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("keyword", keyword);
            m.put("markdown", text);
            m.put("engine", claude.engineName());
            m.put("live", claude.live());
            m.put("regionsCovered", byRegion.keySet());
            m.put("byRegion", byRegion.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().stream().limit(6).map(this::card).toList(),
                    (a, b) -> a, LinkedHashMap::new)));
            m.put("total", hits.size());
            m.put("generatedAt", ZonedDateTime.now(KST).format(STAMP));
            return m;
        });
    }

    // ------------------------------------------------------------ timeline

    public Map<String, Object> timeline(String keyword) {
        List<Article> hits = news.search(keyword, 60).stream()
                .sorted((a, b) -> b.publishedAt().compareTo(a.publishedAt()))
                .toList();
        List<Map<String, Object>> steps = new ArrayList<>();
        for (Article a : hits) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("at", a.publishedAt());
            s.put("relative", relative(a.publishedAt()));
            s.put("title", a.title());
            s.put("source", a.source());
            s.put("flag", a.flag());
            s.put("region", a.regionLabel());
            s.put("link", a.link());
            s.put("sentiment", a.sentiment());
            steps.add(s);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("keyword", keyword);
        m.put("steps", steps);
        m.put("count", steps.size());
        return m;
    }

    // ------------------------------------------------------------ podcast

    /** A read-aloud script. The browser speaks it with the Web Speech API. */
    public Map<String, Object> podcast(boolean force) {
        return cached("podcast", force, Duration.ofMinutes(30), () -> {
            List<Article> top = news.all().stream().limit(40).toList();
            String prompt = """
                    아래 헤드라인으로 2분짜리 오디오 브리핑 대본을 쓰세요.
                    실제로 소리내어 읽을 대본이므로 마크다운 기호, 불릿, 이모지를 절대 쓰지 마세요.
                    문장은 짧게. 숫자는 읽는 방식대로.

                    구성: 인사 한 문장 -> 오늘의 헤드라인 3개 -> 한국 관련 한 단락 -> 마무리 한 문장.
                    시작은 "야무진 브리핑입니다."로.

                    ---- 헤드라인 ----
                    %s
                    """.formatted(dossier(top));

            StringBuilder fb = new StringBuilder("야무진 브리핑입니다. 지금 이 시각 주요 소식입니다. ");
            top.stream().limit(5).forEach(a -> fb.append(a.title()).append(". "));
            fb.append("이상 야무진 브리핑이었습니다.");

            String text = claude.ask(PERSONA, prompt, 1800, fb.toString());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("script", text);
            m.put("engine", claude.engineName());
            m.put("live", claude.live());
            m.put("generatedAt", ZonedDateTime.now(KST).format(STAMP));
            return m;
        });
    }

    // ------------------------------------------------------------ freeform chat

    public String chat(String question, String context) {
        List<Article> top = news.all().stream().limit(35).toList();
        String prompt = """
                사용자 질문: %s

                %s

                아래는 지금 이 시각 수집된 실시간 헤드라인입니다. 질문과 관련 있으면 근거로 쓰고,
                관련 없으면 무시하고 당신의 지식으로 답하세요. 답변은 한국어, 6문장 이내.

                ---- 실시간 헤드라인 ----
                %s
                """.formatted(question, context == null ? "" : context, dossier(top));

        String fallback = localAnswer(question, top);
        return claude.ask(PERSONA, prompt, 2000, fallback);
    }

    public void chatStream(String question, Consumer<String> onDelta, Runnable onDone) {
        List<Article> top = news.all().stream().limit(35).toList();
        String prompt = """
                사용자 질문: %s

                아래 실시간 헤드라인을 참고해서 한국어로 답하세요. 관련 없으면 무시해도 됩니다.

                ---- 실시간 헤드라인 ----
                %s
                """.formatted(question, dossier(top));
        claude.stream(PERSONA, prompt, 2400, localAnswer(question, top), onDelta, onDone);
    }

    public void briefingStream(Consumer<String> onDelta, Runnable onDone) {
        List<Article> arts = news.all();
        String prompt = """
                아래 실시간 헤드라인으로 '오늘의 글로벌 브리핑'을 쓰세요.
                구성: ## 한 줄 요약 / ## 오늘의 핵심 3가지 / ## 권역별 온도 / ## 한국에 미치는 영향 / ## 지켜볼 것

                ---- 헤드라인 ----
                %s
                """.formatted(dossier(arts.stream().limit(70).toList()));
        claude.stream(PERSONA, prompt, 4000, localGlobalBriefing(arts), onDelta, onDone);
    }

    // ------------------------------------------------------------ helpers

    public String dossier(List<Article> arts) {
        StringBuilder sb = new StringBuilder();
        Map<String, List<Article>> byRegion = arts.stream()
                .collect(Collectors.groupingBy(Article::regionLabel, LinkedHashMap::new, Collectors.toList()));
        for (var e : byRegion.entrySet()) {
            sb.append("\n[").append(e.getKey()).append("]\n");
            for (Article a : e.getValue()) {
                sb.append("- (").append(a.source()).append(", ").append(relative(a.publishedAt())).append(") ")
                        .append(a.title());
                if (!a.snippet().isBlank()) sb.append(" :: ").append(TextKit.clip(a.snippet(), 120));
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public Map<String, Object> card(Article a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id());
        m.put("title", a.title());
        m.put("link", a.link());
        m.put("source", a.source());
        m.put("region", a.region());
        m.put("regionLabel", a.regionLabel());
        m.put("flag", a.flag());
        m.put("category", a.category());
        m.put("snippet", a.snippet());
        m.put("image", a.image());
        m.put("publishedAt", a.publishedAt());
        m.put("relative", relative(a.publishedAt()));
        m.put("heat", a.heat());
        m.put("sentiment", a.sentiment());
        m.put("keywords", a.keywords());
        return m;
    }

    public static String relative(Instant t) {
        if (t == null) return "";
        long min = Duration.between(t, Instant.now()).toMinutes();
        if (min < 1) return "방금";
        if (min < 60) return min + "분 전";
        long h = min / 60;
        if (h < 24) return h + "시간 전";
        return (h / 24) + "일 전";
    }

    private Map<String, Object> cached(String key, boolean force, Duration ttl,
                                       java.util.function.Supplier<Map<String, Object>> supplier) {
        Cached c = cache.get(key);
        if (!force && c != null && Duration.between(c.at(), Instant.now()).compareTo(ttl) < 0) {
            Map<String, Object> copy = new LinkedHashMap<>(c.body());
            copy.put("cached", true);
            return copy;
        }
        Map<String, Object> body = supplier.get();
        body.put("cached", false);
        cache.put(key, new Cached(Instant.now(), body));
        return body;
    }

    public void clearCache() {
        cache.clear();
    }

    // ---------- local fallbacks (no API key required) ----------

    private String localGlobalBriefing(List<Article> arts) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 한 줄 요약\n");
        List<Map<String, Object>> issues = news.globalIssues(3);
        if (issues.isEmpty()) {
            sb.append("여러 권역에 동시에 걸친 단일 이슈는 아직 관측되지 않았습니다.\n\n");
        } else {
            sb.append("지금 지구가 동시에 보고 있는 키워드는 **")
                    .append(issues.stream().map(i -> String.valueOf(i.get("keyword")))
                            .collect(Collectors.joining(", ")))
                    .append("** 입니다.\n\n");
        }
        sb.append("## 오늘의 핵심 3가지\n");
        arts.stream().limit(3).forEach(a -> sb.append("- **").append(a.title()).append("**  \n  ")
                .append(a.flag()).append(" ").append(a.source()).append(" · ")
                .append(relative(a.publishedAt())).append(" · 화제도 ").append(Math.round(a.heat())).append("\n"));
        sb.append("\n## 권역별 온도\n");
        for (Map<String, Object> p : news.pulse()) {
            double s = ((Number) p.get("sentiment")).doubleValue();
            String mood = s > 0.12 ? "긍정 우세" : s < -0.12 ? "부정 우세" : "중립";
            sb.append("- ").append(p.get("flag")).append(" **").append(p.get("label")).append("** — 기사 ")
                    .append(p.get("count")).append("건, ").append(mood).append("\n");
        }
        sb.append("\n## 한국에 미치는 영향\n")
                .append("- 상위 키워드가 3개 권역 이상에서 동시 관측되면 국내 자산시장 변동성으로 이어지는 경우가 많습니다.\n")
                .append("- 미국/중국 권역 감성 지표가 동시에 음(-)일 때 수출주 압력이 커집니다.\n")
                .append("- 상세 분석은 ANTHROPIC_API_KEY를 설정하면 Claude가 직접 작성합니다.\n");
        sb.append("\n## 지켜볼 것\n")
                .append("- 위 키워드가 24시간 뒤에도 상위권에 남아 있는지\n")
                .append("- 권역별 감성 지표의 부호가 뒤집히는지\n");
        sb.append("\n> _로컬 추출 엔진으로 생성했습니다. ANTHROPIC_API_KEY를 넣으면 Claude가 직접 씁니다._\n");
        return sb.toString();
    }

    private String localRegionDigest(String label, List<Article> arts) {
        StringBuilder sb = new StringBuilder("## ").append(label).append(" 지금 상황\n");
        double senti = arts.stream().mapToDouble(Article::sentiment).average().orElse(0);
        sb.append("총 ").append(arts.size()).append("건의 기사를 수집했습니다. 평균 감성 지표는 ")
                .append(String.format("%.2f", senti)).append(" 입니다.\n\n## 주요 이슈\n");
        arts.stream().limit(4).forEach(a -> sb.append("- **").append(a.title()).append("** — ")
                .append(a.source()).append(", ").append(relative(a.publishedAt())).append("\n"));
        sb.append("\n## 한국 관점\n- 키워드: ")
                .append(arts.stream().flatMap(a -> a.keywords().stream()).distinct().limit(8)
                        .collect(Collectors.joining(", ")))
                .append("\n- 심층 분석은 ANTHROPIC_API_KEY 설정 후 이용 가능합니다.\n");
        return sb.toString();
    }

    private String localPrism(String keyword, Map<String, List<Article>> byRegion) {
        StringBuilder sb = new StringBuilder("## 사안 정리\n");
        sb.append("\"").append(keyword).append("\" 키워드로 ").append(byRegion.size())
                .append("개 권역에서 보도를 확인했습니다.\n\n## 프레이밍 비교\n");
        byRegion.forEach((r, list) -> {
            double s = list.stream().mapToDouble(Article::sentiment).average().orElse(0);
            sb.append("- **").append(RegionCatalog.label(r)).append("** (").append(list.size())
                    .append("건, 감성 ").append(String.format("%.2f", s)).append(") — 대표 헤드라인: ")
                    .append(list.get(0).title()).append("\n");
        });
        sb.append("\n## 왜 다르게 쓰는가\n- 권역별 감성 지표 차이가 곧 이해관계 차이입니다.\n")
                .append("- Claude 분석을 켜면 구조적 원인까지 서술됩니다.\n");
        return sb.toString();
    }

    private String localAnswer(String q, List<Article> top) {
        List<Article> hits = news.search(q, 5);
        StringBuilder sb = new StringBuilder();
        if (hits.isEmpty()) {
            sb.append("지금 수집된 헤드라인에서는 \"").append(q).append("\" 관련 기사를 찾지 못했습니다. ")
                    .append("ANTHROPIC_API_KEY를 설정하면 Claude가 직접 답변합니다.");
        } else {
            sb.append("\"").append(q).append("\" 관련으로 지금 잡힌 기사는 다음과 같습니다.\n\n");
            hits.forEach(a -> sb.append("- ").append(a.flag()).append(" **").append(a.title())
                    .append("** (").append(a.source()).append(", ").append(relative(a.publishedAt())).append(")\n"));
            sb.append("\n_로컬 검색 엔진 응답입니다._");
        }
        return sb.toString();
    }
}
