package com.example.the_greatest_project.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Dependency-free text analytics: tokenizing, stopwords, keywording, TF-IDF cosine similarity
 * and a lexicon sentiment score. Used everywhere we need signal without calling an LLM.
 */
public final class TextKit {

    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{Nd}가-힣ぁ-んァ-ヶ一-龥]+");

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            // korean
            "그리고", "하지만", "그러나", "이번", "지난", "오늘", "내일", "어제", "관련", "대한", "위해", "통해", "따라",
            "라며", "이라고", "밝혔다", "말했다", "전했다", "했다", "한다", "된다", "있다", "없다", "이다", "에서", "으로",
            "까지", "부터", "보다", "만큼", "역시", "또한", "우리", "그것", "지난해", "올해", "기자", "속보", "단독", "종합",
            "뉴스", "사진", "영상", "오전", "오후", "일보", "신문", "취재", "포토",
            // korean outlet names - they are metadata, not topics
            "연합뉴스", "동아일보", "매일경제", "한겨레", "경향신문", "조선일보", "중앙일보", "머니투데이",
            "이데일리", "서울신문", "국민일보", "한국일보", "뉴시스", "뉴스1", "헤럴드경제", "아시아경제",
            // english
            "the", "and", "for", "with", "from", "that", "this", "have", "has", "was", "were", "are", "you",
            "your", "but", "not", "all", "can", "will", "his", "her", "its", "they", "them", "their", "into",
            "over", "after", "before", "says", "said", "new", "news", "amid", "how", "why", "who", "what",
            "more", "than", "about", "would", "could", "should", "been", "being", "also", "may", "one", "two",
            "says", "say", "get", "got", "make", "made", "take", "took", "see", "look", "back", "out", "off",
            "down", "still", "just", "now", "then", "there", "here", "when", "where", "which", "while",
            "first", "last", "next", "year", "years", "day", "days", "week", "month", "time", "times",
            "people", "world", "report", "reports", "live", "updates", "video", "photos", "opinion",
            "reuters", "bloomberg", "guardian", "times", "post", "press", "agency", "com", "www", "https",
            // ja / zh common
            "する", "した", "こと", "これ", "それ", "ため", "など", "から", "まで", "ます", "です", "ない",
            "记者", "报道", "表示", "我们", "他们", "这个", "以及", "关于", "已经"
    ));

    private static final Map<String, Double> SENTIMENT = new HashMap<>();

    static {
        String[] neg = {
                "위기", "폭락", "붕괴", "전쟁", "사망", "테러", "충돌", "제재", "갈등", "논란", "비판", "파업", "적자",
                "침체", "실패", "규탄", "우려", "공포", "패배", "부진", "해고", "감축", "경고", "리스크", "부도", "손실",
                "crisis", "crash", "war", "dead", "kill", "attack", "sanction", "conflict", "protest", "slump",
                "recession", "fear", "risk", "collapse", "fraud", "layoff", "plunge", "loss", "threat", "strike",
                "危机", "战争", "抗议", "下跌", "危険", "戦争", "不安", "批判"
        };
        String[] pos = {
                "성장", "급등", "회복", "합의", "타결", "돌파", "최고", "흑자", "호조", "확대", "수출", "혁신", "성공",
                "협력", "지원", "개선", "기대", "반등", "신기록", "수상", "돌파구", "안정",
                "growth", "surge", "record", "deal", "agreement", "rally", "boost", "win", "recovery", "profit",
                "breakthrough", "upgrade", "soar", "gain", "innovation", "partnership", "milestone",
                "增长", "上涨", "合作", "成功", "回復", "成長", "合意"
        };
        for (String w : neg) SENTIMENT.put(w, -1.0);
        for (String w : pos) SENTIMENT.put(w, 1.0);
    }

    public static String stripHtml(String s) {
        if (s == null) return "";
        String out = TAG.matcher(s).replaceAll(" ");
        out = out.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&apos;", "'");
        return out.replaceAll("\\s+", " ").trim();
    }

    public static String clip(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    public static List<String> tokens(String text) {
        if (text == null || text.isBlank()) return List.of();
        String norm = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String raw : SPLIT.split(norm)) {
            if (raw.length() < 2 || raw.length() > 24) continue;
            if (STOP.contains(raw)) continue;
            if (raw.chars().allMatch(Character::isDigit)) continue;
            // short latin tokens are almost always function words ("to", "in", "of").
            // CJK is different: two characters there is a real word.
            if (raw.length() <= 3 && isLatin(raw) && !SHORT_KEEP.contains(raw)) continue;
            out.add(raw);
        }
        return out;
    }

    private static final Set<String> SHORT_KEEP = new HashSet<>(Arrays.asList(
            "ai", "eu", "un", "us", "uk", "xi", "5g", "6g", "ev", "gdp", "cpi", "oil", "gas", "war",
            "tax", "fed", "ecb", "imf", "nato", "opec", "gpu", "cpu", "api", "app", "iot", "vr", "ar"));

    private static boolean isLatin(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 0x24F) return false;
        }
        return true;
    }

    /** Top-N keywords by frequency, longer terms winning ties. */
    public static List<String> keywords(String text, int n) {
        Map<String, Integer> freq = new HashMap<>();
        for (String t : tokens(text)) freq.merge(t, 1, Integer::sum);
        return freq.entrySet().stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(b.getValue(), a.getValue());
                    return c != 0 ? c : Integer.compare(b.getKey().length(), a.getKey().length());
                })
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** -1 .. 1 lexicon sentiment. Crude but fast and language-agnostic enough. */
    public static double sentiment(String text) {
        if (text == null || text.isBlank()) return 0;
        String low = text.toLowerCase(Locale.ROOT);
        double score = 0;
        int hits = 0;
        for (Map.Entry<String, Double> e : SENTIMENT.entrySet()) {
            if (low.contains(e.getKey())) {
                score += e.getValue();
                hits++;
            }
        }
        if (hits == 0) return 0;
        return Math.max(-1, Math.min(1, score / Math.sqrt(hits + 1)));
    }

    public static Map<String, Double> termVector(String text) {
        Map<String, Double> v = new LinkedHashMap<>();
        for (String t : tokens(text)) v.merge(t, 1.0, Double::sum);
        double norm = Math.sqrt(v.values().stream().mapToDouble(d -> d * d).sum());
        if (norm > 0) v.replaceAll((k, val) -> val / norm);
        return v;
    }

    public static double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Map<String, Double> small = a.size() <= b.size() ? a : b;
        Map<String, Double> big = small == a ? b : a;
        double dot = 0;
        for (Map.Entry<String, Double> e : small.entrySet()) {
            Double o = big.get(e.getKey());
            if (o != null) dot += e.getValue() * o;
        }
        return dot;
    }

    /** Cheap extractive summary: pick the sentences that carry the most frequent terms. */
    public static String extractiveSummary(String text, int sentences) {
        if (text == null || text.isBlank()) return "";
        String[] parts = text.split("(?<=[.!?。！？\\n])\\s+");
        if (parts.length <= sentences) return text.trim();
        Map<String, Integer> freq = new HashMap<>();
        for (String t : tokens(text)) freq.merge(t, 1, Integer::sum);
        record Scored(int idx, String s, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            double sc = 0;
            List<String> tk = tokens(parts[i]);
            for (String t : tk) sc += freq.getOrDefault(t, 0);
            if (!tk.isEmpty()) sc /= Math.sqrt(tk.size());
            scored.add(new Scored(i, parts[i], sc));
        }
        return scored.stream()
                .sorted((x, y) -> Double.compare(y.score(), x.score()))
                .limit(sentences)
                .sorted((x, y) -> Integer.compare(x.idx(), y.idx()))
                .map(s -> s.s().trim())
                .reduce((x, y) -> x + " " + y)
                .orElse("");
    }

    public static String hash(String s) {
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        return Long.toHexString(h);
    }

    private TextKit() {
    }
}
