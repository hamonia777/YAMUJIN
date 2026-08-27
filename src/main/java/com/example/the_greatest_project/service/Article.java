package com.example.the_greatest_project.service;

import java.time.Instant;
import java.util.List;

/** One normalized news item, wherever on the planet it came from. */
public record Article(
        String id,
        String title,
        String link,
        String source,
        String region,
        String regionLabel,
        String flag,
        String category,
        String snippet,
        String image,
        Instant publishedAt,
        double heat,
        double sentiment,
        List<String> keywords
) {
}
