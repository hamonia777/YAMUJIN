package com.example.the_greatest_project.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Which feeds we sweep, per region. All plain RSS, no API keys required. */
public final class RegionCatalog {

    public record Feed(String url, String source, String region, String category) {
    }

    public static final Map<String, String> REGION_LABEL = new LinkedHashMap<>() {{
        put("KR", "대한민국");
        put("US", "미국");
        put("EU", "유럽");
        put("JP", "일본");
        put("CN", "중국");
        put("WORLD", "세계");
    }};

    public static final Map<String, String> REGION_FLAG = Map.of(
            "KR", "🇰🇷",
            "US", "🇺🇸",
            "EU", "🇪🇺",
            "JP", "🇯🇵",
            "CN", "🇨🇳",
            "WORLD", "🌍"
    );

    public static final List<Feed> FEEDS = List.of(
            // ---------- 대한민국 ----------
            new Feed("https://news.google.com/rss?hl=ko&gl=KR&ceid=KR:ko", "Google News KR", "KR", "종합"),
            new Feed("https://news.google.com/rss/headlines/section/topic/BUSINESS?hl=ko&gl=KR&ceid=KR:ko", "Google News KR", "KR", "경제"),
            new Feed("https://news.google.com/rss/headlines/section/topic/TECHNOLOGY?hl=ko&gl=KR&ceid=KR:ko", "Google News KR", "KR", "기술"),
            new Feed("https://www.yna.co.kr/rss/news.xml", "연합뉴스", "KR", "종합"),
            new Feed("https://www.hani.co.kr/rss/", "한겨레", "KR", "종합"),
            new Feed("https://rss.donga.com/total.xml", "동아일보", "KR", "종합"),
            new Feed("https://www.mk.co.kr/rss/30000001/", "매일경제", "KR", "경제"),
            new Feed("https://www.khan.co.kr/rss/rssdata/total_news.xml", "경향신문", "KR", "종합"),

            // ---------- 미국 ----------
            new Feed("https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en", "Google News US", "US", "종합"),
            new Feed("https://feeds.npr.org/1001/rss.xml", "NPR", "US", "종합"),
            new Feed("https://feeds.a.dj.com/rss/RSSWorldNews.xml", "WSJ", "US", "경제"),
            new Feed("https://www.cnbc.com/id/100003114/device/rss/rss.html", "CNBC", "US", "경제"),
            new Feed("https://techcrunch.com/feed/", "TechCrunch", "US", "기술"),
            new Feed("https://www.theverge.com/rss/index.xml", "The Verge", "US", "기술"),

            // ---------- 유럽 ----------
            new Feed("https://feeds.bbci.co.uk/news/world/europe/rss.xml", "BBC Europe", "EU", "종합"),
            new Feed("https://rss.dw.com/rdf/rss-en-all", "Deutsche Welle", "EU", "종합"),
            new Feed("https://www.euronews.com/rss?level=theme&name=news", "Euronews", "EU", "종합"),
            new Feed("https://www.france24.com/en/europe/rss", "France 24", "EU", "종합"),
            new Feed("https://www.theguardian.com/europe/rss", "The Guardian", "EU", "종합"),

            // ---------- 일본 ----------
            new Feed("https://news.google.com/rss?hl=ja&gl=JP&ceid=JP:ja", "Google News JP", "JP", "종합"),
            new Feed("https://www.nhk.or.jp/rss/news/cat0.xml", "NHK", "JP", "종합"),
            new Feed("https://www.japantimes.co.jp/feed/", "Japan Times", "JP", "종합"),
            new Feed("https://news.google.com/rss/headlines/section/topic/BUSINESS?hl=ja&gl=JP&ceid=JP:ja", "Google News JP", "JP", "경제"),

            // ---------- 중국 ----------
            new Feed("https://news.google.com/rss?hl=zh-CN&gl=CN&ceid=CN:zh-Hans", "Google News CN", "CN", "종합"),
            new Feed("https://www.scmp.com/rss/91/feed", "SCMP", "CN", "종합"),
            new Feed("https://www.globaltimes.cn/rss/outbrain.xml", "Global Times", "CN", "종합"),
            new Feed("https://news.google.com/rss/search?q=China+economy&hl=en-US&gl=US&ceid=US:en", "Google News", "CN", "경제"),

            // ---------- 세계 ----------
            new Feed("https://feeds.bbci.co.uk/news/world/rss.xml", "BBC World", "WORLD", "종합"),
            new Feed("https://www.aljazeera.com/xml/rss/all.xml", "Al Jazeera", "WORLD", "종합"),
            new Feed("https://rss.nytimes.com/services/xml/rss/nyt/World.xml", "NYTimes", "WORLD", "종합"),
            new Feed("https://moxie.foxnews.com/google-publisher/world.xml", "Fox News", "WORLD", "종합"),
            new Feed("https://feeds.skynews.com/feeds/rss/world.xml", "Sky News", "WORLD", "종합")
    );

    /** 대한민국 정세 전용 트랙 - 정치, 외교, 안보, 경제정책. */
    public static final List<Feed> POLITICS_FEEDS = List.of(
            new Feed("https://news.google.com/rss/search?q=%EA%B5%AD%ED%9A%8C+when:2d&hl=ko&gl=KR&ceid=KR:ko", "Google News", "KR", "국회"),
            new Feed("https://news.google.com/rss/search?q=%EB%8C%80%ED%86%B5%EB%A0%B9%EC%8B%A4+when:2d&hl=ko&gl=KR&ceid=KR:ko", "Google News", "KR", "대통령실"),
            new Feed("https://news.google.com/rss/search?q=%ED%95%9C%EB%AF%B8+%EC%99%B8%EA%B5%90+when:3d&hl=ko&gl=KR&ceid=KR:ko", "Google News", "KR", "외교"),
            new Feed("https://news.google.com/rss/search?q=%EB%B6%81%ED%95%9C+when:3d&hl=ko&gl=KR&ceid=KR:ko", "Google News", "KR", "남북"),
            new Feed("https://news.google.com/rss/search?q=%EA%B8%88%EB%A6%AC+%ED%99%98%EC%9C%A8+%EB%AC%BC%EA%B0%80+when:3d&hl=ko&gl=KR&ceid=KR:ko", "Google News", "KR", "경제정책"),
            new Feed("https://news.google.com/rss/headlines/section/topic/NATION?hl=ko&gl=KR&ceid=KR:ko", "Google News KR", "KR", "정치"),
            new Feed("https://www.yna.co.kr/rss/politics.xml", "연합뉴스", "KR", "정치")
    );

    public static String label(String region) {
        return REGION_LABEL.getOrDefault(region, region);
    }

    public static String flag(String region) {
        return REGION_FLAG.getOrDefault(region, "🌐");
    }

    private RegionCatalog() {
    }
}
