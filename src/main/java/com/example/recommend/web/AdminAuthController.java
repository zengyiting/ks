package com.example.recommend.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "123456";
    private static final String SESSION_KEY = "adminLoggedIn";

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> request,
            HttpSession session
    ) {
        String username = request.getOrDefault("username", "").trim();
        String password = request.getOrDefault("password", "").trim();

        if (ADMIN_USERNAME.equals(username) && ADMIN_PASSWORD.equals(password)) {
            session.setAttribute(SESSION_KEY, true);
            session.setMaxInactiveInterval(3600);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "登录成功"
            ));
        }

        return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "message", "用户名或密码错误"
        ));
    }

    @GetMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        session.invalidate();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "已退出登录"
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(HttpSession session) {
        Boolean loggedIn = (Boolean) session.getAttribute(SESSION_KEY);
        if (Boolean.TRUE.equals(loggedIn)) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", true,
                    "username", ADMIN_USERNAME
            ));
        }
        return ResponseEntity.ok(Map.of(
                "authenticated", false
        ));
    }
}
