package com.example.the_greatest_project.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class Auth {
    private Auth() {}

    public static Long uidOrNull(HttpServletRequest req) {
        Object v = req.getAttribute(JwtAuthFilter.ATTR_UID);
        return v instanceof Long l ? l : null;
    }

    public static Long require(HttpServletRequest req) {
        Long uid = uidOrNull(req);
        if (uid == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
        return uid;
    }
}
