package com.example.the_greatest_project.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Reads the Bearer token (or mujin_token cookie) and stashes the identity on the request. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_UID = "mujin.uid";
    public static final String ATTR_EMAIL = "mujin.email";
    public static final String ATTR_NICK = "mujin.nick";

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) { this.jwt = jwt; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String token = null;
        String header = req.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = header.substring(7).trim();
        }
        if (token == null && req.getCookies() != null) {
            for (var c : req.getCookies()) {
                if ("mujin_token".equals(c.getName())) { token = c.getValue(); break; }
            }
        }
        if (token != null && !token.isBlank() && !"null".equals(token)) {
            try {
                Claims c = jwt.parse(token);
                req.setAttribute(ATTR_UID, Long.valueOf(c.getSubject()));
                req.setAttribute(ATTR_EMAIL, c.get("email", String.class));
                req.setAttribute(ATTR_NICK, c.get("nick", String.class));
            } catch (Exception ignored) {
                // expired / tampered -> stay anonymous
            }
        }
        chain.doFilter(req, res);
    }
}
