package com.example.the_greatest_project.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * The crawler core. Sweeps every regional feed in parallel (virtual threads),
 * normalizes into {@link Article}, dedupes near-identical headlines, then scores
 * heat + sentiment. Everything downstream (AI, politics, search, games) eats from here.
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final Http http;
    private final Duration cacheTtl;

    private final Map<String, List<Article>> cache = new ConcurrentHashMap<>();
    private final Map<String, Instant> cacheAt = new ConcurrentHashMap<>();
    private final Map<String, String> feedStatus = new ConcurrentHashMap<>();
    private final AtomicLong sweepCount = new AtomicLong();
    private volatile Instant lastSweep;

    public NewsService(Http http, @Value("${yamujin.news.cache-minutes:8}") int cacheMinutes) {
        this.http = http;
        this.cacheTtl = Duration.ofMinutes(cacheMinutes);
    }

    // ------------------------------------------------------------------ public API

    /** All regions merged, freshest and hottest first. */
    public List<Article> all() {
        return sweep(RegionCatalog.FEEDS, "ALL");
    }

    public List<Article> byRegion(String region) {
        if (region == null || region.isBlank() || "ALL".equalsIgnoreCase(region)) return all();
        String r = region.toUpperCase(Locale.ROOT);
        List<RegionCatalog.Feed> feeds = RegionCatalog.FEEDS.stream()
                .filter(f -> f.region().equals(r)).toList();
        if (feeds.isEmpty()) return List.of();
        return sweep(feeds, "R:" + r);
    }

    public List<Article> politics() {
        return sweep(RegionCatalog.POLITICS_FEEDS, "POLITICS");
    }

    public List<Article> search(String query, int limit) {
        if (query == null || query.isBlank()) return all().stream().limit(limit).toList();
        Map<String, Double> q = TextKit.termVector(query);
        String lower = query.toLowerCase(Locale.ROOT);
        return all().stream()
                .map(a -> {
                    double sim = TextKit.cosine(q, TextKit.termVector(a.title() + " " + a.snippet()));
                    if (a.title().toLowerCase(Locale.ROOT).contains(lower)) sim += 0.6;
                    if (a.snippet().toLowerCase(Locale.ROOT).contains(lower)) sim += 0.2;
                    return Map.entry(a, sim);
                })
                .filter(e -> e.getValue() > 0.04)
                .sorted(Map.Entry.<Article, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Articles most similar to a seed article - powers "이 이슈 더 보기" and the timeline. */
    public List<Article> related(Article seed, int limit) {
        Map<String, Double> v = TextKit.termVector(seed.title() + " " + seed.snippet());
        return all().stream()
                .filter(a -> !a.id().equals(seed.id()))
                .map(a -> Map.entry(a, TextKit.cosine(v, TextKit.termVector(a.title() + " " + a.snippet()))))
                .filter(e -> e.getValue() > 0.12)
                .sorted(Map.Entry.<Article, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    public Article byId(String id) {
        return all().stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
    }

    /** Trending keywords across everything, with per-region breakdown. */
    public List<Map<String, Object>> trends(int limit) {
        List<Article> arts = all();
        Map<String, Integer> freq = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> perRegion = new LinkedHashMap<>();
        Map<String, Double> sentiSum = new LinkedHashMap<>();
        for (Article a : arts) {
            for (String k : a.keywords()) {
                freq.merge(k, 1, Integer::sum);
                perRegion.computeIfAbsent(k, x -> new LinkedHashMap<>()).merge(a.region(), 1, Integer::sum);
                sentiSum.merge(k, a.sentiment(), Double::sum);
            }
        }
        return freq.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("keyword", e.getKey());
                    m.put("count", e.getValue());
                    m.put("regions", perRegion.get(e.getKey()));
                    m.put("sentiment", round(sentiSum.get(e.getKey()) / e.getValue()));
                    m.put("global", perRegion.get(e.getKey()).size());
                    return m;
                })
                .toList();
    }

    /**
     * Cross-country prism: keywords that show up in 3+ regions at once.
     * These are the stories the whole planet is chewing on simultaneously.
     */
    public List<Map<String, Object>> globalIssues(int limit) {
        return trends(120).stream()
                .filter(t -> ((Integer) t.get("global")) >= 3)
                .sorted(Comparator.comparingInt((Map<String, Object> t) -> (Integer) t.get("global")).reversed()
                        .thenComparing(t -> -(Integer) t.get("count")))
                .limit(limit)
                .toList();
    }

    /** Region-level pulse: volume, average sentiment, heat. Feeds the world map. */
    public List<Map<String, Object>> pulse() {
        List<Article> arts = all();
        Map<String, List<Article>> grouped = arts.stream().collect(Collectors.groupingBy(Article::region));
        List<Map<String, Object>> out = new ArrayList<>();
        for (String region : RegionCatalog.REGION_LABEL.keySet()) {
            List<Article> list = grouped.getOrDefault(region, List.of());
            double senti = list.stream().mapToDouble(Article::sentiment).average().orElse(0);
            double heat = list.stream().mapToDouble(Article::heat).average().orElse(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("region", region);
            m.put("label", RegionCatalog.label(region));
            m.put("flag", RegionCatalog.flag(region));
            m.put("count", list.size());
            m.put("sentiment", round(senti));
            m.put("heat", round(heat));
            m.put("top", list.stream().limit(3).map(Article::title).toList());
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("feeds", RegionCatalog.FEEDS.size() + RegionCatalog.POLITICS_FEEDS.size());
        m.put("cachedArticles", cache.values().stream().mapToInt(List::size).sum());
        m.put("lastSweep", lastSweep);
        m.put("sweeps", sweepCount.get());
        m.put("feedStatus", feedStatus);
        return m;
    }

    public void invalidate() {
        cache.clear();
        cacheAt.clear();
    }

    /** Keep the front page warm so the first visit of the day is not a cold start. */
    @Scheduled(initialDelay = 4000, fixedDelayString = "PT7M")
    public void warm() {
        try {
            all();
            politics();
        } catch (Exception e) {
            log.debug("warm failed: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------ internals

    private List<Article> sweep(List<RegionCatalog.Feed> feeds, String cacheKey) {
        Instant at = cacheAt.get(cacheKey);
        if (at != null && Duration.between(at, Instant.now()).compareTo(cacheTtl) < 0) {
            List<Article> hit = cache.get(cacheKey);
            if (hit != null && !hit.isEmpty()) return hit;
        }
        synchronized (cacheKey.intern()) {
            Instant again = cacheAt.get(cacheKey);
            if (again != null && Duration.between(again, Instant.now()).compareTo(cacheTtl) < 0) {
                List<Article> hit = cache.get(cacheKey);
                if (hit != null && !hit.isEmpty()) return hit;
            }
            List<Article> merged = fetchAll(feeds);
            if (!merged.isEmpty()) {
                cache.put(cacheKey, merged);
                cacheAt.put(cacheKey, Instant.now());
                lastSweep = Instant.now();
                sweepCount.incrementAndGet();
            }
            return merged.isEmpty() ? cache.getOrDefault(cacheKey, List.of()) : merged;
        }
    }

    private List<Article> fetchAll(List<RegionCatalog.Feed> feeds) {
        List<Article> merged = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<List<Article>>> futures = feeds.stream()
                    .map(f -> pool.submit(() -> parseFeed(f)))
                    .toList();
            for (var fut : futures) {
                try {
                    merged.addAll(fut.get(20, TimeUnit.SECONDS));
                } catch (Exception ignored) {
                    // a dead feed must never take the page down
                }
            }
        }
        return rank(dedupe(merged));
    }

    private List<Article> parseFeed(RegionCatalog.Feed feed) {
        String xml = http.get(feed.url());
        if (xml == null || xml.isBlank()) {
            feedStatus.put(feed.source() + " " + feed.category(), "DOWN");
            return List.of();
        }
        try {
            Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
            var items = doc.select("item");
            if (items.isEmpty()) items = doc.select("entry");   // Atom
            List<Article> out = new ArrayList<>();
            for (Element item : items) {
                Article a = toArticle(item, feed);
                if (a != null) out.add(a);
            }
            feedStatus.put(feed.source() + " " + feed.category(), out.isEmpty() ? "EMPTY" : "OK:" + out.size());
            return out;
        } catch (Exception e) {
            feedStatus.put(feed.source() + " " + feed.category(), "ERR");
            return List.of();
        }
    }

    private Article toArticle(Element item, RegionCatalog.Feed feed) {
        String title = TextKit.stripHtml(text(item, "title"));
        if (title.isBlank()) return null;

        String link = text(item, "link");
        if (link.isBlank()) {
            Element l = item.selectFirst("link[href]");
            if (l != null) link = l.attr("href");
        }
        if (link.isBlank()) link = text(item, "guid");
        if (link.isBlank()) return null;

        String desc = TextKit.stripHtml(firstNonBlank(
                text(item, "description"), text(item, "summary"), text(item, "content"),
                text(item, "content|encoded")));

        String source = firstNonBlank(text(item, "source"), feed.source());
        if (source.isBlank()) source = feed.source();

        String image = "";
        Element media = item.selectFirst("media|content[url], media|thumbnail[url], enclosure[url]");
        if (media != null) image = media.attr("url");

        Instant published = parseDate(firstNonBlank(
                text(item, "pubDate"), text(item, "published"), text(item, "updated"), text(item, "dc|date")));

        String blob = title + " " + desc;
        double senti = TextKit.sentiment(blob);
        List<String> kws = TextKit.keywords(blob, 6);

        return new Article(
                TextKit.hash(normalizeTitle(title)),
                title,
                link,
                source,
                feed.region(),
                RegionCatalog.label(feed.region()),
                RegionCatalog.flag(feed.region()),
                feed.category(),
                TextKit.clip(desc, 260),
                image,
                published,
                0,
                round(senti),
                kws
        );
    }

    private static String text(Element item, String tag) {
        Element e = item.selectFirst(tag);
        return e == null ? "" : e.text().trim();
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return "";
    }

    private static String normalizeTitle(String t) {
        return t.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]", "");
    }

    private static Instant parseDate(String raw) {
        if (raw == null || raw.isBlank()) return Instant.now();
        DateTimeFormatter[] fmts = {
                DateTimeFormatter.RFC_1123_DATE_TIME,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                DateTimeFormatter.ISO_ZONED_DATE_TIME,
                DateTimeFormatter.ISO_INSTANT
        };
        for (DateTimeFormatter f : fmts) {
            try {
                return ZonedDateTime.parse(raw.trim(), f).toInstant();
            } catch (Exception ignored) {
            }
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception ignored) {
        }
        return Instant.now();
    }

    /** Drop near-duplicate headlines that every outlet reprints verbatim. */
    private List<Article> dedupe(List<Article> in) {
        Map<String, Article> byId = new LinkedHashMap<>();
        for (Article a : in) byId.putIfAbsent(a.id(), a);
        return new ArrayList<>(byId.values());
    }

    /**
     * Heat = recency decay x cross-source repetition x sentiment intensity.
     * A story 20 outlets ran 30 minutes ago outranks a lone piece from yesterday.
     */
    private List<Article> rank(List<Article> in) {
        Map<String, Integer> kwFreq = new LinkedHashMap<>();
        for (Article a : in) for (String k : a.keywords()) kwFreq.merge(k, 1, Integer::sum);

        Instant now = Instant.now();
        List<Article> scored = new ArrayList<>(in.size());
        for (Article a : in) {
            long ageMin = Math.max(1, Duration.between(a.publishedAt(), now).toMinutes());
            double recency = 100.0 / (1 + Math.log1p(ageMin / 45.0) * 3.2);
            double echo = a.keywords().stream().mapToInt(k -> kwFreq.getOrDefault(k, 1)).average().orElse(1);
            double intensity = 1 + Math.abs(a.sentiment()) * 0.55;
            double heat = Math.min(100, recency * 0.55 + Math.min(45, echo * 4.5) * intensity * 0.55);
            scored.add(new Article(a.id(), a.title(), a.link(), a.source(), a.region(), a.regionLabel(),
                    a.flag(), a.category(), a.snippet(), a.image(), a.publishedAt(),
                    round(heat), a.sentiment(), a.keywords()));
        }
        scored.sort(Comparator.comparingDouble(Article::heat).reversed());
        return scored;
    }

    static double round(double d) {
        return Math.round(d * 100.0) / 100.0;
    }
}
