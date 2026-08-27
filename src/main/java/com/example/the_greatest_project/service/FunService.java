package com.example.the_greatest_project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 정색하고 만든 실없는 것들.
 *
 * <p>전부 사용자가 명시적으로 눌러야만 실행됩니다. 메인 대시보드 흐름에는 절대 끼어들지 않고,
 * 랜덤 팝업도 없습니다. 오직 /fun 탭 안에서만 삽니다.
 */
@Service
public class FunService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final NewsService news;
    private final ClaudeService claude;
    private final ObjectMapper json = new ObjectMapper();

    public FunService(NewsService news, ClaudeService claude) {
        this.news = news;
        this.claude = claude;
    }

    // ---------------------------------------------------------------- 사관체 번역

    /** 오늘의 헤드라인을 조선왕조실록 사관 문체로 옮깁니다. 진지한 얼굴로. */
    public Map<String, Object> joseon() {
        List<Article> top = news.all().stream().limit(8).toList();
        String heads = top.stream().map(a -> "- " + a.title()).collect(Collectors.joining("\n"));

        String fallback = top.stream().limit(5)
                .map(a -> "○ 어느 날, 아래와 같은 일이 있었다 하니: " + a.title()
                        + "\n  사신(史臣)은 논한다. 세상의 일이란 본디 이러하니, 무엇을 새삼 놀라겠는가.")
                .collect(Collectors.joining("\n\n"));

        String text = claude.ask("""
                당신은 조선왕조실록을 기록하는 사관(史官)입니다.
                현대의 사건을 실록 문체로 옮겨 적습니다. 국역 실록투 한국어를 사용합니다.
                각 항목은 '○'로 시작하고, 끝에 '사신은 논한다(史臣曰)'로 시작하는 한 줄 논평을 답니다.
                논평은 점잖되 은근히 촌철살인이어야 합니다. 실제 왕이나 실존 인물을 특정해 비방하지 않습니다.
                """, """
                아래 오늘의 헤드라인을 실록 문체로 옮기세요. 6개 항목.

                %s
                """.formatted(heads), 2200, fallback);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", text);
        m.put("engine", claude.engineName());
        m.put("live", claude.live());
        m.put("sourceHeadlines", top.stream().map(Article::title).toList());
        return m;
    }

    // ---------------------------------------------------------------- 톤 변환

    public Map<String, Object> tone(String text, String tone) {
        String style = switch (tone) {
            case "조폭" -> "1990년대 한국 조폭영화 대사체. 형님 말투, 위협적이지만 실제 폭력 묘사나 욕설은 금지.";
            case "할머니" -> "손주에게 말하는 다정한 시골 할머니 말투. 사투리 약간, 밥 먹었냐로 시작.";
            case "아나운서" -> "9시 뉴스 앵커의 격식 있는 표준어 낭독체.";
            case "중2병" -> "중2병 판타지 나레이션체. 각성, 봉인, 운명 같은 단어를 남발.";
            case "MZ" -> "요즘 인터넷 밈이 섞인 짧은 문장체. 과하지 않게.";
            case "면접관" -> "압박면접 면접관 말투. 계속 되묻는 형식.";
            default -> "담백한 표준 한국어.";
        };
        String out = claude.ask("당신은 문체 변환기입니다. 내용의 사실관계는 절대 바꾸지 않고 말투만 바꿉니다.",
                "다음 텍스트를 이 스타일로 바꾸세요: %s\n\n---- 원문 ----\n%s".formatted(style, text),
                2500, text + "\n\n(문체 변환은 ANTHROPIC_API_KEY 설정 시 동작합니다)");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tone", tone);
        m.put("text", out);
        m.put("live", claude.live());
        return m;
    }

    // ---------------------------------------------------------------- 뉴스 가챠

    /** 오늘의 뉴스 뽑기. 등급은 그 기사의 실제 화제도로 정해집니다. 나름 근거가 있습니다. */
    public Map<String, Object> gacha(String seed) {
        List<Article> pool = news.all();
        if (pool.isEmpty()) return Map.of("error", "아직 수집된 기사가 없습니다");
        Random r = new Random(java.util.Objects.hash(seed, System.nanoTime()));
        Article a = pool.get(r.nextInt(Math.min(pool.size(), 120)));

        String rarity;
        String color;
        double h = a.heat();
        if (h >= 85) { rarity = "UR"; color = "#ff2d95"; }
        else if (h >= 70) { rarity = "SSR"; color = "#ffb300"; }
        else if (h >= 55) { rarity = "SR"; color = "#a855f7"; }
        else if (h >= 40) { rarity = "R"; color = "#3b82f6"; }
        else { rarity = "N"; color = "#94a3b8"; }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rarity", rarity);
        m.put("color", color);
        m.put("heat", h);
        m.put("title", a.title());
        m.put("link", a.link());
        m.put("source", a.source());
        m.put("flag", a.flag());
        m.put("region", a.regionLabel());
        m.put("attack", (int) Math.round(a.heat()));
        m.put("defense", (int) Math.round(50 + a.sentiment() * 45));
        m.put("speed", (int) Math.round(100 - Math.min(99, java.time.Duration.between(
                a.publishedAt(), java.time.Instant.now()).toMinutes() / 5.0)));
        return m;
    }

    // ---------------------------------------------------------------- 뉴스 운세

    /**
     * 오늘의 운세. 같은 사람 + 같은 날짜면 항상 같은 결과가 나옵니다.
     * 새로고침해서 좋은 결과 나올 때까지 돌리는 걸 막기 위한 것으로, 이 사이트에서 제일
     * 정성 들인 무의미한 기능입니다.
     */
    public Map<String, Object> fortune(String who) {
        String day = LocalDate.now(KST).toString();
        long seed = (who + "|" + day).hashCode();
        Random r = new Random(seed);

        List<Article> pool = news.all();
        String omen = pool.isEmpty() ? "고요함" : pool.get(r.nextInt(Math.min(pool.size(), 60))).title();

        String[] grades = {"대길", "길", "소길", "평", "소흉", "흉"};
        int gi = r.nextInt(grades.length);
        int luck = 100 - gi * 15 - r.nextInt(10);

        String[] colors = {"먹색", "청록", "선홍", "미색", "감청", "은회색", "치자색"};
        String[] dirs = {"동", "서", "남", "북", "동남", "북서"};
        String[] items = {"텀블러", "우산", "이어폰", "볼펜", "USB 케이블", "립밤", "동전 하나"};
        String[] avoid = {"장바구니 결제", "단체 카톡방 첫 발언", "야식 주문", "충동적인 구독 신청",
                "읽지 않은 메일 일괄 삭제", "새 탭 40개 열기"};

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", day);
        m.put("grade", grades[gi]);
        m.put("luck", Math.max(5, luck));
        m.put("color", colors[r.nextInt(colors.length)]);
        m.put("direction", dirs[r.nextInt(dirs.length)]);
        m.put("item", items[r.nextInt(items.length)]);
        m.put("avoid", avoid[r.nextInt(avoid.length)]);
        m.put("omen", omen);
        m.put("reading",
                "오늘의 기운은 '" + grades[gi] + "'. 당신의 운세를 결정한 헤드라인은 다음과 같습니다 — \""
                        + TextKit.clip(omen, 60) + "\". "
                        + "이 기사와 당신의 오늘 사이에는 아무런 인과관계가 없습니다. 그래도 기분은 반영됩니다.");
        m.put("disclaimer", "이 기능은 100% 농담입니다. 의사결정에 쓰지 마세요.");
        return m;
    }

    // ---------------------------------------------------------------- 변명 생성기

    public Map<String, Object> excuse(String situation, String level) {
        String fallback = "죄송합니다. " + situation + " 관련해서 제 쪽 사정으로 지연되었습니다. "
                + "지금 바로 처리해서 오늘 안에 공유드리겠습니다.";
        String text = claude.ask("""
                당신은 '변명 생성기'입니다. 다만 거짓말을 지어내지는 않습니다.
                책임을 회피하는 문장 대신, 사실을 밝히면서도 관계를 해치지 않는 실제로 쓸 수 있는 문장을 씁니다.
                한국어. 3가지 버전을 제시합니다.
                """, """
                상황: %s
                수위: %s

                형식:
                ## 정공법
                (사실 그대로 + 복구 계획. 실제로 이걸 쓰세요)

                ## 무난한 버전
                (관계 관리용, 과장 없음)

                ## 웃긴 버전
                (절대 실제로 보내면 안 되는 버전. 마지막에 '보내지 마세요' 표기)
                """.formatted(situation, level), 1200, fallback);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", text);
        m.put("live", claude.live());
        return m;
    }

    // ---------------------------------------------------------------- 종말시계

    /** 뉴스 감성으로 계산한 완전히 근거 없는 시계. 근거 없음을 응답에 명시합니다. */
    public Map<String, Object> doomsday() {
        List<Article> arts = news.all();
        double senti = arts.stream().mapToDouble(Article::sentiment).average().orElse(0);
        long negatives = arts.stream().filter(a -> a.sentiment() < -0.2).count();
        double ratio = arts.isEmpty() ? 0 : (double) negatives / arts.size();

        int secondsToMidnight = (int) Math.max(30, Math.round(600 - ratio * 480 + senti * 120));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("secondsToMidnight", secondsToMidnight);
        m.put("display", "자정까지 " + (secondsToMidnight / 60) + "분 " + (secondsToMidnight % 60) + "초");
        m.put("negativeRatio", Math.round(ratio * 1000) / 10.0);
        m.put("sampled", arts.size());
        m.put("verdict", ratio > 0.45 ? "오늘 뉴스는 유난히 사납습니다. 그래도 저녁은 드세요."
                : ratio > 0.3 ? "평소와 비슷한 수준의 소란입니다."
                : "의외로 잔잔한 날입니다. 이런 날도 있습니다.");
        m.put("disclaimer", "실제 Doomsday Clock과 무관하며, 아무런 과학적 근거가 없습니다.");
        return m;
    }

    // ---------------------------------------------------------------- 미니게임: 진짜 vs 가짜

    /**
     * 진짜 헤드라인 3개 + Claude가 지어낸 가짜 1개.
     * 정답 인덱스는 클라이언트로 안 보내고 서버 세션 없이 검증하려고 해시를 같이 보냅니다.
     */
    public Map<String, Object> realOrFake() {
        List<Article> pool = news.all();
        if (pool.size() < 6) return Map.of("error", "기사 수집 중입니다. 잠시 후 다시 시도하세요.");

        Random r = new Random();
        List<Article> picks = new ArrayList<>();
        List<Article> copy = new ArrayList<>(pool.subList(0, Math.min(80, pool.size())));
        Collections.shuffle(copy, r);
        for (Article a : copy) {
            if (picks.size() >= 3) break;
            picks.add(a);
        }

        String realList = picks.stream().map(a -> "- " + a.title()).collect(Collectors.joining("\n"));
        String fakeFallback = "정부, 전국 모든 편의점에 '오후 3시 낮잠 의무화' 시범 도입 검토";
        String fake = claude.ask("""
                당신은 뉴스 헤드라인 게임의 출제자입니다.
                진짜 같지만 완전히 허구인 헤드라인을 1개 만듭니다.
                실존 인물의 실명이나 특정 기업의 실제 사건을 암시하지 않습니다.
                결과는 헤드라인 한 줄만 출력합니다. 따옴표, 설명, 접두사 금지.
                """, """
                아래는 오늘의 진짜 헤드라인들입니다. 이것들과 톤과 길이가 비슷하지만
                완전히 지어낸 가짜 헤드라인 1개를 만드세요.

                %s
                """.formatted(realList), 300, fakeFallback).trim().replaceAll("^[\"'\\-\\s]+|[\"']+$", "");

        List<Map<String, Object>> options = new ArrayList<>();
        for (Article a : picks) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("text", a.title());
            o.put("source", a.source());
            o.put("link", a.link());
            o.put("fake", false);
            options.add(o);
        }
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("text", fake);
        f.put("source", claude.live() ? "AI 생성" : "로컬 생성");
        f.put("link", "");
        f.put("fake", true);
        options.add(f);
        Collections.shuffle(options, r);

        int answer = 0;
        for (int i = 0; i < options.size(); i++) {
            if (Boolean.TRUE.equals(options.get(i).get("fake"))) answer = i;
        }
        // strip the tell before shipping to the client
        List<Map<String, Object>> shipped = options.stream().map(o -> {
            Map<String, Object> c = new LinkedHashMap<>(o);
            c.remove("fake");
            c.remove("source");
            c.remove("link");
            return c;
        }).toList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("options", shipped);
        m.put("answer", answer);
        m.put("reveal", options);
        m.put("live", claude.live());
        return m;
    }

    // ---------------------------------------------------------------- 미니게임: 화제도 맞히기

    public Map<String, Object> heatGuess() {
        List<Article> pool = news.all();
        if (pool.size() < 4) return Map.of("error", "기사 수집 중입니다.");
        Random r = new Random();
        List<Article> copy = new ArrayList<>(pool.subList(0, Math.min(60, pool.size())));
        Collections.shuffle(copy, r);
        Article a = copy.get(0);
        Article b = copy.stream().filter(x -> Math.abs(x.heat() - a.heat()) > 4).findFirst().orElse(copy.get(1));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("left", Map.of("title", a.title(), "flag", a.flag(), "source", a.source(), "heat", a.heat()));
        m.put("right", Map.of("title", b.title(), "flag", b.flag(), "source", b.source(), "heat", b.heat()));
        m.put("answer", a.heat() >= b.heat() ? "left" : "right");
        return m;
    }

    // ---------------------------------------------------------------- 지식 그래프 (진지한 쪽)

    /** 키워드 동시 출현 네트워크. 프론트에서 force-directed 캔버스로 그립니다. */
    public Map<String, Object> knowledgeGraph(int maxNodes) {
        List<Article> arts = news.all().stream().limit(180).toList();
        Map<String, Integer> freq = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> edges = new LinkedHashMap<>();
        Map<String, String> nodeRegion = new LinkedHashMap<>();

        for (Article a : arts) {
            List<String> ks = a.keywords().stream().limit(5).toList();
            for (String k : ks) {
                freq.merge(k, 1, Integer::sum);
                nodeRegion.putIfAbsent(k, a.region());
            }
            for (int i = 0; i < ks.size(); i++) {
                for (int j = i + 1; j < ks.size(); j++) {
                    edges.computeIfAbsent(ks.get(i), x -> new LinkedHashMap<>()).merge(ks.get(j), 1, Integer::sum);
                }
            }
        }

        List<String> top = freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(maxNodes).map(Map.Entry::getKey).toList();

        List<Map<String, Object>> nodes = top.stream().map(k -> {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", k);
            n.put("weight", freq.get(k));
            n.put("region", nodeRegion.getOrDefault(k, "WORLD"));
            return n;
        }).toList();

        List<Map<String, Object>> links = new ArrayList<>();
        for (String a : top) {
            Map<String, Integer> row = edges.get(a);
            if (row == null) continue;
            for (var e : row.entrySet()) {
                if (!top.contains(e.getKey())) continue;
                if (e.getValue() < 2) continue;
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("source", a);
                l.put("target", e.getKey());
                l.put("weight", e.getValue());
                links.add(l);
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodes", nodes);
        m.put("links", links);
        m.put("sampled", arts.size());
        return m;
    }
}
