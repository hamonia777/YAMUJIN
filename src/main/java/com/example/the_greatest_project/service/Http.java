package com.example.the_greatest_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Tiny shared HTTP client with browser-ish headers so feeds and shops do not slam the door. */
@Component
public class Http {

    private static final Logger log = LoggerFactory.getLogger(Http.class);
    public static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/125.0.0.0 Safari/537.36";

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public String get(String url) {
        return get(url, 12);
    }

    public String get(String url, int timeoutSeconds) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8,ja;q=0.7,zh;q=0.6")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                log.debug("GET {} -> {}", url, res.statusCode());
                return null;
            }
            return res.body();
        } catch (Exception e) {
            log.debug("GET {} failed: {}", url, e.toString());
            return null;
        }
    }

    public String getWithHeaders(String url, String... headerPairs) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", UA)
                    .timeout(Duration.ofSeconds(12)).GET();
            for (int i = 0; i + 1 < headerPairs.length; i += 2) b.header(headerPairs[i], headerPairs[i + 1]);
            HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return res.statusCode() >= 400 ? null : res.body();
        } catch (Exception e) {
            log.debug("GET(h) {} failed: {}", url, e.toString());
            return null;
        }
    }

    public HttpClient raw() {
        return client;
    }
}
