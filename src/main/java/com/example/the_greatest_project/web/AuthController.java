package com.example.the_greatest_project.web;

import com.example.the_greatest_project.domain.AppUser;
import com.example.the_greatest_project.domain.Bookmark;
import com.example.the_greatest_project.repo.AppUserRepository;
import com.example.the_greatest_project.repo.BookmarkRepository;
import com.example.the_greatest_project.security.Auth;
import com.example.the_greatest_project.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterReq(
            @Email(message = "이메일 형식이 올바르지 않습니다") @NotBlank String email,
            @NotBlank(message = "닉네임을 입력하세요") @Size(min = 1, max = 30) String nickname,
            @NotBlank @Size(min = 6, max = 64, message = "비밀번호는 6자 이상이어야 합니다") String password) {
    }

    public record LoginReq(@NotBlank String email, @NotBlank String password) {
    }

    public record ProfileReq(String nickname, String interests, String theme) {
    }

    private final AppUserRepository users;
    private final BookmarkRepository bookmarks;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(AppUserRepository users, BookmarkRepository bookmarks,
                          PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.bookmarks = bookmarks;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterReq req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다");
        }
        AppUser u = new AppUser();
        u.setEmail(req.email().toLowerCase());
        u.setNickname(req.nickname());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setLastLoginAt(Instant.now());
        u.setVisitCount(1);
        users.save(u);
        return session(u, "가입 완료. 환영합니다.");
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginReq req) {
        AppUser u = users.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"));
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다");
        }
        u.setLastLoginAt(Instant.now());
        u.setVisitCount(u.getVisitCount() + 1);
        users.save(u);
        return session(u, "로그인되었습니다.");
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest req) {
        Long uid = Auth.uidOrNull(req);
        if (uid == null) return Map.of("authenticated", false);
        AppUser u = users.findById(uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "세션이 만료되었습니다"));
        Map<String, Object> m = new LinkedHashMap<>(profile(u));
        m.put("authenticated", true);
        m.put("bookmarkCount", bookmarks.countByUserId(uid));
        return m;
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(HttpServletRequest req, @RequestBody ProfileReq body) {
        Long uid = Auth.require(req);
        AppUser u = users.findById(uid).orElseThrow();
        if (body.nickname() != null && !body.nickname().isBlank()) u.setNickname(body.nickname().trim());
        if (body.interests() != null) u.setInterests(body.interests().trim());
        if (body.theme() != null && !body.theme().isBlank()) u.setTheme(body.theme().trim());
        users.save(u);
        return profile(u);
    }

    // ------------------------------------------------------------ bookmarks

    @GetMapping("/bookmarks")
    public Object listBookmarks(HttpServletRequest req) {
        Long uid = Auth.require(req);
        return bookmarks.findByUserIdOrderByCreatedAtDesc(uid);
    }

    @PostMapping("/bookmarks")
    public Object addBookmark(HttpServletRequest req, @RequestBody Bookmark body) {
        Long uid = Auth.require(req);
        if (body.getUrl() != null && bookmarks.existsByUserIdAndUrl(uid, body.getUrl())) {
            return Map.of("ok", true, "duplicate", true);
        }
        body.setId(null);
        body.setUserId(uid);
        body.setCreatedAt(Instant.now());
        bookmarks.save(body);
        return Map.of("ok", true, "id", body.getId());
    }

    @DeleteMapping("/bookmarks/{id}")
    public Map<String, Object> removeBookmark(HttpServletRequest req, @PathVariable Long id) {
        Long uid = Auth.require(req);
        bookmarks.findById(id).filter(b -> b.getUserId().equals(uid)).ifPresent(bookmarks::delete);
        return Map.of("ok", true);
    }

    // ------------------------------------------------------------ helpers

    private Map<String, Object> session(AppUser u, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", jwt.issue(u.getId(), u.getEmail(), u.getNickname(), u.getRole()));
        m.put("expiresIn", jwt.ttlSeconds());
        m.put("message", message);
        m.put("user", profile(u));
        return m;
    }

    private Map<String, Object> profile(AppUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("nickname", u.getNickname());
        m.put("interests", u.getInterests());
        m.put("theme", u.getTheme());
        m.put("createdAt", u.getCreatedAt());
        m.put("lastLoginAt", u.getLastLoginAt());
        m.put("visitCount", u.getVisitCount());
        return m;
    }
}
