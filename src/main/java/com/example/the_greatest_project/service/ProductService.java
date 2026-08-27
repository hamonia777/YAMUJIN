package com.example.the_greatest_project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 최저가 헌터.
 *
 * <p>NAVER_CLIENT_ID / NAVER_CLIENT_SECRET 이 있으면 네이버 쇼핑 검색 API로 실제 가격을 가져옵니다.
 * 없으면 실제 가격을 지어내지 않고, 각 쇼핑몰의 검색 딥링크와 비교 체크리스트만 돌려줍니다.
 * (가짜 가격을 그럴듯하게 보여주는 게 제일 나쁜 UX라서요.)
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    public record Offer(String title, String mall, String link, long price, String image,
                        String brand, String category, String badge) {
    }

    private final Http http;
    private final ClaudeService claude;
    private final NewsService news;
    private final ObjectMapper json = new ObjectMapper();

    private final String naverId;
    private final String naverSecret;

    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
    private final Map<String, Instant> cachedAt = new ConcurrentHashMap<>();

    public ProductService(Http http, ClaudeService claude, NewsService news,
                          @Value("${yamujin.naver.client-id:}") String naverId,
                          @Value("${yamujin.naver.client-secret:}") String naverSecret) {
        this.http = http;
        this.claude = claude;
        this.news = news;
        this.naverId = naverId == null ? "" : naverId.trim();
        this.naverSecret = naverSecret == null ? "" : naverSecret.trim();
    }

    public boolean liveShopping() {
        return !naverId.isBlank() && !naverSecret.isBlank();
    }

    // ------------------------------------------------------------------ search

    public Map<String, Object> search(String query, int limit) {
        String key = query.toLowerCase() + "|" + limit;
        Instant at = cachedAt.get(key);
        if (at != null && Duration.between(at, Instant.now()).toMinutes() < 25) {
            Map<String, Object> hit = cache.get(key);
            if (hit != null) {
                Map<String, Object> copy = new LinkedHashMap<>(hit);
                copy.put("cached", true);
                return copy;
            }
        }

        List<Offer> offers = liveShopping() ? naverShopping(query, Math.max(limit, 20)) : List.of();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("live", !offers.isEmpty());
        out.put("source", offers.isEmpty() ? "deep-link" : "naver-open-api");
        out.put("links", deepLinks(query));
        out.put("checklist", checklist(query));

        if (!offers.isEmpty()) {
            List<Offer> sorted = offers.stream()
                    .sorted(Comparator.comparingLong(Offer::price))
                    .limit(limit).toList();
            long min = sorted.get(0).price();
            long max = sorted.stream().mapToLong(Offer::price).max().orElse(min);
            double avg = sorted.stream().mapToLong(Offer::price).average().orElse(min);

            out.put("offers", sorted.stream().map(o -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", o.title());
                m.put("mall", o.mall());
                m.put("link", o.link());
                m.put("price", o.price());
                m.put("image", o.image());
                m.put("brand", o.brand());
                m.put("category", o.category());
                m.put("badge", o.price() == min ? "최저가" : o.badge());
                m.put("vsMin", o.price() - min);
                m.put("vsAvgPct", Math.round((o.price() - avg) / avg * 1000) / 10.0);
                return m;
            }).toList());
            out.put("stats", Map.of(
                    "min", min, "max", max, "avg", Math.round(avg),
                    "spread", max - min,
                    "spreadPct", min == 0 ? 0 : Math.round((max - min) * 1000.0 / min) / 10.0,
                    "count", sorted.size()));
            out.put("verdict", verdict(query, sorted, min, max, avg));
        } else {
            out.put("offers", List.of());
            out.put("stats", Map.of());
            out.put("verdict", """
                    ## 실가격 연동 꺼져 있음
                    네이버 쇼핑 API 키가 없어서 **실제 가격은 표시하지 않습니다.** 지어낸 가격을 보여주느니
                    안 보여주는 게 낫다고 판단했습니다.

                    아래 **몰별 검색 딥링크**는 지금 바로 동작합니다. 그리고 아래 체크리스트는
                    이 카테고리에서 실제로 돈이 새는 지점들입니다.

                    실가격을 켜려면: `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` 환경변수 설정 후 재시작.
                    """);
        }
        out.put("cached", false);
        out.put("generatedAt", Instant.now());
        cache.put(key, out);
        cachedAt.put(key, Instant.now());
        return out;
    }

    private List<Offer> naverShopping(String query, int display) {
        String url = "https://openapi.naver.com/v1/search/shop.json?query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&display=" + Math.min(100, display) + "&sort=asc";
        String body = http.getWithHeaders(url,
                "X-Naver-Client-Id", naverId,
                "X-Naver-Client-Secret", naverSecret);
        if (body == null) return List.of();
        try {
            JsonNode items = json.readTree(body).path("items");
            List<Offer> out = new ArrayList<>();
            for (JsonNode it : items) {
                long price = it.path("lprice").asLong(0);
                if (price <= 0) continue;
                out.add(new Offer(
                        TextKit.stripHtml(it.path("title").asText("")),
                        it.path("mallName").asText("네이버쇼핑"),
                        it.path("link").asText(""),
                        price,
                        it.path("image").asText(""),
                        it.path("brand").asText(""),
                        it.path("category3").asText(it.path("category2").asText("")),
                        it.path("productType").asInt(0) <= 2 ? "정품몰" : ""
                ));
            }
            return out;
        } catch (Exception e) {
            log.warn("naver shop parse failed: {}", e.toString());
            return List.of();
        }
    }

    private String verdict(String query, List<Offer> sorted, long min, long max, double avg) {
        String table = sorted.stream().limit(12)
                .map(o -> "- " + o.mall() + " | " + String.format("%,d", o.price()) + "원 | " + o.title())
                .collect(Collectors.joining("\n"));
        return claude.ask("""
                        당신은 최저가 사냥꾼입니다. 소비자 편에 서서 냉정하게 판단합니다.
                        제공된 가격 데이터에 없는 숫자를 만들지 않습니다. 한국어, 마크다운(h2 이하).
                        """,
                """
                        검색어: "%s"
                        최저 %,d원 / 최고 %,d원 / 평균 %,.0f원 / 가격 편차 %,d원

                        아래는 실제 수집된 판매처별 가격입니다.

                        형식:
                        ## 결론
                        지금 사도 되는지 한 문장.

                        ## 이걸 사세요
                        추천 1개. 왜 이게 야무진지 2문장.

                        ## 피해야 할 것
                        1~2불릿. 가격 편차가 크면 그 이유를 추정.

                        ## 더 깎는 법
                        실제로 통하는 방법 2불릿 (카드할인, 쿠폰, 시즌 등).

                        ---- 판매처별 가격 ----
                        %s
                        """.formatted(query, min, max, avg, max - min, table),
                1800,
                "## 결론\n최저 %,d원, 최고 %,d원으로 편차가 %,d원입니다. 최저가 판매처의 신뢰도만 확인하면 지금 사도 무방합니다.\n"
                        .formatted(min, max, max - min));
    }

    // ------------------------------------------------------------------ deep links

    public List<Map<String, String>> deepLinks(String query) {
        String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
        List<Map<String, String>> out = new ArrayList<>();
        out.add(link("쿠팡", "https://www.coupang.com/np/search?q=" + q + "&sorter=scoreDesc", "#ff5a5f"));
        out.add(link("네이버쇼핑", "https://search.shopping.naver.com/search/all?query=" + q + "&sort=price_asc", "#03c75a"));
        out.add(link("다나와 최저가", "https://search.danawa.com/dsearch.php?k1=" + q + "&sort=priceASC", "#1a73e8"));
        out.add(link("11번가", "https://search.11st.co.kr/Search.tmall?kwd=" + q + "&sortCd=L", "#f43142"));
        out.add(link("G마켓", "https://browse.gmarket.co.kr/search?keyword=" + q + "&s=8", "#00a0e9"));
        out.add(link("에누리", "https://www.enuri.com/search.jsp?keyword=" + q, "#ffb400"));
        out.add(link("알리익스프레스", "https://ko.aliexpress.com/w/wholesale-" + q + ".html?SortType=price_asc", "#ff4747"));
        return out;
    }

    private static Map<String, String> link(String name, String url, String color) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("mall", name);
        m.put("url", url);
        m.put("color", color);
        return m;
    }

    private List<String> checklist(String query) {
        String q = query.toLowerCase();
        List<String> base = new ArrayList<>(List.of(
                "같은 모델명인데 판매처마다 구성품이 다른 경우가 많습니다. 박스 구성 먼저 확인하세요.",
                "무료배송처럼 보여도 도서산간 추가금이 붙는지 결제 직전 화면에서 확인하세요.",
                "카드사 청구할인 + 쇼핑몰 쿠폰 중복 여부에 따라 실구매가가 10% 넘게 갈립니다."
        ));
        if (q.matches(".*(노트북|맥북|laptop|그래픽|gpu|cpu|모니터).*")) {
            base.add("전자제품은 '리퍼/전시/미개봉 중고'가 최저가에 섞여 나옵니다. 상품명에 붙은 수식어를 보세요.");
            base.add("동일 모델이라도 램/SSD 옵션 차이로 가격이 벌어집니다. 스펙표를 대조하세요.");
        }
        if (q.matches(".*(의자|책상|가구|침대|매트).*")) {
            base.add("가구는 배송·설치비가 본체가의 10~20%입니다. 총액 기준으로 비교하세요.");
        }
        if (q.matches(".*(영양제|비타민|프로틴|건강).*")) {
            base.add("건강기능식품은 g당·정당 단가로 환산해야 진짜 최저가가 보입니다.");
        }
        return base;
    }

    // ------------------------------------------------------------------ 뉴스 기반 추천

    /**
     * 뉴스에서 지금 뜨는 키워드를 뽑아 쇼핑 키워드로 번역합니다.
     * "지금 세상이 이 얘기 중이니 이 물건을 보게 될 것" 이라는 연결.
     */
    public Map<String, Object> trendingPicks() {
        List<Map<String, Object>> trends = news.trends(40);
        String words = trends.stream().limit(25)
                .map(t -> String.valueOf(t.get("keyword")))
                .collect(Collectors.joining(", "));

        String fallback = """
                {"picks":[
                {"keyword":"보조배터리","reason":"이동·재난 관련 보도가 늘면 항상 같이 오르는 품목입니다.","category":"전자"},
                {"keyword":"공기청정기 필터","reason":"대기질 관련 헤드라인이 잡힐 때 소모품 수요가 먼저 움직입니다.","category":"생활"},
                {"keyword":"보온병","reason":"기후·에너지 이슈 구간에서 꾸준히 팔리는 저관여 상품입니다.","category":"생활"}
                ]}""";

        String raw = claude.ask("""
                        당신은 트렌드 분석가 겸 쇼핑 큐레이터입니다.
                        뉴스 키워드에서 실제 구매 수요로 이어질 상품 키워드를 뽑습니다.
                        반드시 JSON만 출력합니다. 설명 문장, 코드펜스 금지.
                        """,
                """
                        지금 전 세계 뉴스에서 자주 등장하는 키워드입니다:
                        %s

                        이 중 소비와 연결되는 흐름을 골라, 한국 쇼핑몰에서 실제로 검색될 법한
                        구체적 상품 키워드 6개를 뽑으세요.

                        출력 형식(JSON only):
                        {"picks":[{"keyword":"상품 검색어","reason":"왜 지금인지 한 문장","category":"카테고리"}]}
                        """.formatted(words), 1200, fallback);

        List<Map<String, Object>> picks = new ArrayList<>();
        try {
            String cleaned = raw.trim();
            int s = cleaned.indexOf('{');
            int e = cleaned.lastIndexOf('}');
            if (s >= 0 && e > s) cleaned = cleaned.substring(s, e + 1);
            JsonNode node = json.readTree(cleaned).path("picks");
            for (JsonNode p : node) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("keyword", p.path("keyword").asText());
                m.put("reason", p.path("reason").asText());
                m.put("category", p.path("category").asText(""));
                m.put("links", deepLinks(p.path("keyword").asText("")));
                picks.add(m);
            }
        } catch (Exception e) {
            log.debug("pick parse failed: {}", e.toString());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("picks", picks);
        out.put("basedOn", trends.stream().limit(12).toList());
        out.put("engine", claude.engineName());
        out.put("live", claude.live());
        out.put("generatedAt", Instant.now());
        return out;
    }
}
