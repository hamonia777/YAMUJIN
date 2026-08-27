package com.example.the_greatest_project.web;

import com.example.the_greatest_project.service.Article;
import com.example.the_greatest_project.service.BriefingService;
import com.example.the_greatest_project.service.FunService;
import com.example.the_greatest_project.service.NewsService;
import com.example.the_greatest_project.service.RegionCatalog;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService news;
    private final BriefingService briefing;
    private final FunService fun;

    public NewsController(NewsService news, BriefingService briefing, FunService fun) {
        this.news = news;
        this.briefing = briefing;
        this.fun = fun;
    }

    @GetMapping
    public Map<String, Object> feed(@RequestParam(defaultValue = "ALL") String region,
                                    @RequestParam(defaultValue = "60") int limit,
                                    @RequestParam(required = false) String category) {
        List<Article> arts = news.byRegion(region);
        if (category != null && !category.isBlank() && !"전체".equals(category)) {
            arts = arts.stream().filter(a -> a.category().equals(category)).toList();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("region", region.toUpperCase());
        m.put("total", arts.size());
        m.put("items", arts.stream().limit(limit).map(briefing::card).toList());
        m.put("categories", arts.stream().map(Article::category).distinct().sorted().toList());
        return m;
    }

    @GetMapping("/regions")
    public List<Map<String, String>> regions() {
        return RegionCatalog.REGION_LABEL.entrySet().stream().map(e -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", e.getKey());
            m.put("label", e.getValue());
            m.put("flag", RegionCatalog.flag(e.getKey()));
            return m;
        }).toList();
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String q, @RequestParam(defaultValue = "40") int limit) {
        List<Article> hits = news.search(q, limit);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("query", q);
        m.put("total", hits.size());
        m.put("items", hits.stream().map(briefing::card).toList());
        return m;
    }

    @GetMapping("/related/{id}")
    public Map<String, Object> related(@PathVariable String id) {
        Article seed = news.byId(id);
        Map<String, Object> m = new LinkedHashMap<>();
        if (seed == null) {
            m.put("error", "기사를 찾을 수 없습니다");
            return m;
        }
        m.put("seed", briefing.card(seed));
        m.put("items", news.related(seed, 12).stream().map(briefing::card).toList());
        return m;
    }

    @GetMapping("/trends")
    public List<Map<String, Object>> trends(@RequestParam(defaultValue = "40") int limit) {
        return news.trends(limit);
    }

    @GetMapping("/global-issues")
    public List<Map<String, Object>> globalIssues(@RequestParam(defaultValue = "10") int limit) {
        return news.globalIssues(limit);
    }

    @GetMapping("/pulse")
    public List<Map<String, Object>> pulse() {
        return news.pulse();
    }

    @GetMapping("/graph")
    public Map<String, Object> graph(@RequestParam(defaultValue = "48") int nodes) {
        return fun.knowledgeGraph(nodes);
    }

    @GetMapping("/timeline")
    public Map<String, Object> timeline(@RequestParam String q) {
        return briefing.timeline(q);
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        news.invalidate();
        briefing.clearCache();
        int n = news.all().size();
        return Map.of("ok", true, "crawled", n);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return news.health();
    }
}
