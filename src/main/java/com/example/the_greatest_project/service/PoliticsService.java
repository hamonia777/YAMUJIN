package com.example.the_greatest_project.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 대한민국 정세 트랙.
 *
 * <p>KOREA PULSE 는 정치 트랙 헤드라인에서 계산한 합성 지표입니다. 여론조사가 아니라
 * 보도량과 어휘 기반의 파생 지표이고, UI에도 그렇게 표시합니다.
 */
@Service
public class PoliticsService {

    private static final Map<String, String[]> AXES = new LinkedHashMap<>() {{
        put("갈등", new String[]{"충돌", "대립", "공방", "규탄", "고발", "탄핵", "특검", "파행", "보이콧", "농성", "삭발", "단식", "폭로"});
        put("협치", new String[]{"합의", "타결", "처리", "통과", "협의", "회동", "조율", "여야정", "초당적", "협력"});
        put("외교긴장", new String[]{"제재", "도발", "미사일", "훈련", "규탄", "영유권", "관세", "무역분쟁", "핵", "군사"});
        put("경제불안", new String[]{"환율", "고환율", "물가", "금리", "적자", "부채", "침체", "가계부채", "위기", "폭락", "인상"});
        put("민생", new String[]{"지원금", "복지", "일자리", "감세", "돌봄", "주거", "의료", "연금", "교육", "임금"});
    }};

    private final NewsService news;
    private final ClaudeService claude;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private volatile Instant cachedAt = Instant.EPOCH;

    public PoliticsService(NewsService news, ClaudeService claude) {
        this.news = news;
        this.claude = claude;
    }

    public Map<String, Object> snapshot(boolean force) {
        if (!force && !cache.isEmpty() && Duration.between(cachedAt, Instant.now()).toMinutes() < 15) {
            Map<String, Object> copy = new LinkedHashMap<>(cache);
            copy.put("cached", true);
            return copy;
        }

        List<Article> arts = news.politics();
        Map<String, Object> out = new LinkedHashMap<>();

        // ---- axis scoring ----
        Map<String, Integer> raw = new LinkedHashMap<>();
        Map<String, List<String>> evidence = new LinkedHashMap<>();
        for (var axis : AXES.entrySet()) {
            int score = 0;
            List<String> ev = new ArrayList<>();
            for (Article a : arts) {
                String blob = (a.title() + " " + a.snippet());
                for (String kw : axis.getValue()) {
                    if (blob.contains(kw)) {
                        score++;
                        if (ev.size() < 4) ev.add(a.title());
                        break;
                    }
                }
            }
            raw.put(axis.getKey(), score);
            evidence.put(axis.getKey(), ev);
        }
        int maxRaw = Math.max(1, raw.values().stream().mapToInt(Integer::intValue).max().orElse(1));
        Map<String, Integer> axes = new LinkedHashMap<>();
        raw.forEach((k, v) -> axes.put(k, (int) Math.round(v * 100.0 / maxRaw)));

        // ---- composite index ----
        int conflict = axes.getOrDefault("갈등", 0);
        int cooperation = axes.getOrDefault("협치", 0);
        int diplomatic = axes.getOrDefault("외교긴장", 0);
        int economic = axes.getOrDefault("경제불안", 0);
        double senti = arts.stream().mapToDouble(Article::sentiment).average().orElse(0);

        // 100 = 안정, 0 = 대혼란
        double stability = 100
                - conflict * 0.30
                - diplomatic * 0.22
                - economic * 0.26
                + cooperation * 0.18
                + senti * 12;
        int pulse = (int) Math.max(0, Math.min(100, Math.round(stability)));

        String grade;
        String gradeDesc;
        if (pulse >= 78) { grade = "안정"; gradeDesc = "특별한 충격 없이 굴러가는 중"; }
        else if (pulse >= 60) { grade = "주의"; gradeDesc = "잡음은 있지만 통제 범위"; }
        else if (pulse >= 42) { grade = "경계"; gradeDesc = "갈등 축이 확실히 살아 있음"; }
        else if (pulse >= 25) { grade = "위험"; gradeDesc = "다중 축이 동시에 과열"; }
        else { grade = "격동"; gradeDesc = "정치·외교·경제가 한꺼번에 흔들리는 국면"; }

        // ---- clusters by track ----
        Map<String, List<Map<String, Object>>> tracks = new LinkedHashMap<>();
        for (Article a : arts) {
            tracks.computeIfAbsent(a.category(), k -> new ArrayList<>());
            if (tracks.get(a.category()).size() < 8) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", a.title());
                m.put("link", a.link());
                m.put("source", a.source());
                m.put("relative", BriefingService.relative(a.publishedAt()));
                m.put("sentiment", a.sentiment());
                tracks.get(a.category()).add(m);
            }
        }

        // ---- keywords ----
        Map<String, Integer> kw = new LinkedHashMap<>();
        for (Article a : arts) for (String k : a.keywords()) kw.merge(k, 1, Integer::sum);
        List<Map<String, Object>> topKeywords = kw.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(24)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("word", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                }).toList();

        // ---- AI read ----
        String analysis = claude.ask("""
                당신은 대한민국 정치·경제 정세를 읽는 애널리스트입니다.
                특정 정당이나 진영을 편들지 않습니다. 사실과 구조만 씁니다.
                제공된 헤드라인에 없는 사실은 만들지 않습니다. 한국어, 마크다운(h2 이하)만 사용.
                """, """
                아래는 지금 이 시각 국내 정치·외교·경제정책 트랙에서 수집한 헤드라인입니다.
                계산된 지표: 정세지수 %d/100 (%s), 갈등 %d, 협치 %d, 외교긴장 %d, 경제불안 %d, 민생 %d

                형식:
                ## 지금 국면
                3문장.

                ## 축별 진단
                갈등 / 외교 / 경제 각 한 줄.

                ## 앞으로 2주
                일어날 가능성이 높은 것 3불릿. 각 불릿 끝에 (확률: 높음/중간/낮음).

                ## 개인이 체크할 것
                생활·자산 관점 2불릿.

                ---- 헤드라인 ----
                %s
                """.formatted(pulse, grade, conflict, cooperation, diplomatic, economic,
                axes.getOrDefault("민생", 0), digest(arts)),
                2800,
                localAnalysis(pulse, grade, axes, arts));

        out.put("pulse", pulse);
        out.put("grade", grade);
        out.put("gradeDesc", gradeDesc);
        out.put("axes", axes);
        out.put("axesRaw", raw);
        out.put("evidence", evidence);
        out.put("sentiment", Math.round(senti * 100) / 100.0);
        out.put("tracks", tracks);
        out.put("keywords", topKeywords);
        out.put("analysis", analysis);
        out.put("engine", claude.engineName());
        out.put("live", claude.live());
        out.put("sampleSize", arts.size());
        out.put("generatedAt", Instant.now());
        out.put("disclaimer", "정세지수는 여론조사가 아니라 보도량·어휘 기반 파생 지표입니다.");

        cache.clear();
        cache.putAll(out);
        cachedAt = Instant.now();
        Map<String, Object> copy = new LinkedHashMap<>(out);
        copy.put("cached", false);
        return copy;
    }

    private String digest(List<Article> arts) {
        return arts.stream().limit(60)
                .map(a -> "- [" + a.category() + "/" + a.source() + "] " + a.title())
                .collect(Collectors.joining("\n"));
    }

    private String localAnalysis(int pulse, String grade, Map<String, Integer> axes, List<Article> arts) {
        StringBuilder sb = new StringBuilder("## 지금 국면\n");
        sb.append("정세지수 ").append(pulse).append("점, 등급 '").append(grade).append("'. ")
                .append("수집 기사 ").append(arts.size()).append("건 기준입니다. ")
                .append("가장 강한 축은 ")
                .append(axes.entrySet().stream().max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey).orElse("없음"))
                .append(" 입니다.\n\n## 축별 진단\n");
        axes.forEach((k, v) -> sb.append("- **").append(k).append("**: ").append(v).append("/100\n"));
        sb.append("\n## 앞으로 2주\n- 상위 키워드 지속 여부가 관건입니다. (확률: 중간)\n")
                .append("- 외교·경제 축 동반 상승 시 변동성 확대. (확률: 중간)\n")
                .append("- 심층 전망은 ANTHROPIC_API_KEY 설정 후. (확률: 높음)\n")
                .append("\n## 개인이 체크할 것\n- 환율·금리 관련 헤드라인 빈도\n- 상위 키워드의 생활물가 연관성\n");
        return sb.toString();
    }

    /** Powers the sparkline: an approximate 14-day reconstruction from article timestamps. */
    public List<Map<String, Object>> history() {
        List<Article> arts = news.politics();
        Map<String, List<Article>> byDay = arts.stream().collect(Collectors.groupingBy(
                a -> a.publishedAt().toString().substring(0, 10), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> out = new ArrayList<>();
        byDay.forEach((day, list) -> {
            double s = list.stream().mapToDouble(Article::sentiment).average().orElse(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", day);
            m.put("volume", list.size());
            m.put("sentiment", Math.round(s * 100) / 100.0);
            m.put("index", (int) Math.max(0, Math.min(100, Math.round(60 + s * 35))));
            out.add(m);
        });
        out.sort((a, b) -> String.valueOf(a.get("day")).compareTo(String.valueOf(b.get("day"))));
        return out;
    }

    public List<String> hotIssues(int n) {
        return news.politics().stream()
                .sorted((a, b) -> Double.compare(b.heat(), a.heat()))
                .map(Article::title)
                .distinct()
                .limit(n)
                .map(t -> t.toLowerCase(Locale.ROOT).isBlank() ? "-" : t)
                .toList();
    }
}
