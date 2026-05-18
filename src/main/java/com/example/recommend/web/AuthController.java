package com.example.recommend.web;

import com.example.recommend.model.User;
import com.example.recommend.service.AuthService;
import com.example.recommend.service.RateLimiterService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final RateLimiterService rateLimiter;

    public AuthController(AuthService authService, RateLimiterService rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/send-sms-code")
    public ResponseEntity<Map<String, String>> sendSmsCode(@RequestBody SmsCodeRequest request) {
        if (request == null || request.phone() == null || request.phone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号不能为空");
        }
        rateLimiter.checkSmsRateLimit(request.phone());
        authService.sendSmsCode(request.phone());
        return ResponseEntity.ok(Map.of("message", "验证码已发送"));
    }

    @PostMapping("/send-email-code")
    public ResponseEntity<Map<String, String>> sendEmailCode(@RequestBody EmailCodeRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
        rateLimiter.checkEmailRateLimit(request.email());
        authService.sendEmailCode(request.email());
        return ResponseEntity.ok(Map.of("success", "true", "message", "验证码已发送，请查收邮箱"));
    }

    @PostMapping("/login/sms")
    public AuthService.TokenResponse loginBySms(@RequestBody SmsLoginRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (request.phone() == null || request.phone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号不能为空");
        }
        if (request.code() == null || request.code().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码不能为空");
        }
        rateLimiter.checkLoginRateLimit(request.phone());
        return authService.loginBySms(request.phone(), request.code());
    }

    @PostMapping("/login")
    public AuthService.TokenResponse loginByPhone(@RequestBody PhonePasswordRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (request.phone() == null || request.phone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号不能为空");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        rateLimiter.checkLoginRateLimit(request.phone());
        return authService.loginByPhone(request.phone(), request.password());
    }

    @PostMapping("/login-email")
    public AuthService.TokenResponse loginByEmail(@RequestBody EmailPasswordRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        rateLimiter.checkLoginRateLimit(request.email());
        return authService.loginByEmail(request.email(), request.password());
    }

    @PostMapping("/login-username")
    public AuthService.TokenResponse loginByUsername(@RequestBody UsernamePasswordRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (request.username() == null || request.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        rateLimiter.checkLoginRateLimit(request.username());
        return authService.loginByUsername(request.username(), request.password());
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerByPhone(@RequestBody PhoneRegisterRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (request.username() == null || request.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        if (request.phone() == null || request.phone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号不能为空");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        rateLimiter.checkRegisterRateLimit(request.phone());
        AuthService.TokenResponse response = authService.registerByPhone(request.phone(), request.username(), request.password());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "注册成功",
                "userId", response.userId(),
                "username", response.username()
        ));
    }

    @PostMapping("/register-email")
    public ResponseEntity<Map<String, Object>> registerByEmail(@RequestBody EmailRegisterRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (request.username() == null || request.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
        if (request.code() == null || request.code().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码不能为空");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        rateLimiter.checkRegisterRateLimit(request.email());
        AuthService.TokenResponse response = authService.registerByEmail(request.email(), request.code(), request.username(), request.password());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "注册成功",
                "userId", response.userId(),
                "username", response.username()
        ));
    }

    @PostMapping("/refresh")
    public AuthService.TokenResponse refreshToken(@RequestBody RefreshTokenRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refresh token不能为空");
        }
        return authService.refreshToken(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader("Authorization") String authorization) {
        String accessToken = extractToken(authorization);
        authService.logout(accessToken);
        return ResponseEntity.ok(Map.of("message", "登出成功"));
    }

    @GetMapping("/validate")
    public ResponseEntity<UserInfoResponse> validateToken(@RequestHeader("Authorization") String authorization) {
        String accessToken = extractToken(authorization);
        Optional<User> userOpt = authService.validateToken(accessToken);
        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效的token");
        }
        User user = userOpt.get();
        return ResponseEntity.ok(new UserInfoResponse(user.getId(), user.getUsername(), user.getPhone(), user.getEmail(), user.isDisabled(), user.getCreatedAt()));
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getCurrentUser(@RequestHeader("Authorization") String authorization) {
        String accessToken = extractToken(authorization);
        Optional<User> userOpt = authService.validateToken(accessToken);
        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效的token");
        }
        User user = userOpt.get();
        return ResponseEntity.ok(new UserInfoResponse(user.getId(), user.getUsername(), user.getPhone(), user.getEmail(), user.isDisabled(), user.getCreatedAt()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserInfoResponse> updateUserInfo(
            @RequestHeader("Authorization") String authorization,
            @RequestBody UserUpdateRequest request) {
        String accessToken = extractToken(authorization);
        Optional<User> userOpt = authService.validateToken(accessToken);
        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效的token");
        }
        Long userId = userOpt.get().getId();
        User updated = authService.updateUserInfo(userId, request.username(), request.phone(), request.email());
        return ResponseEntity.ok(new UserInfoResponse(updated.getId(), updated.getUsername(), updated.getPhone(), updated.getEmail(), updated.isDisabled(), updated.getCreatedAt()));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ChangePasswordRequest request) {
        String accessToken = extractToken(authorization);
        Optional<User> userOpt = authService.validateToken(accessToken);
        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效的token");
        }
        if (request.oldPassword() == null || request.oldPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "旧密码不能为空");
        }
        if (request.newPassword() == null || request.newPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能为空");
        }
        authService.changePassword(userOpt.get().getId(), request.oldPassword(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "密码修改成功"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPasswordBySms(@RequestBody ResetPasswordRequest request) {
        if (request.phone() == null || request.phone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号不能为空");
        }
        if (request.code() == null || request.code().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码不能为空");
        }
        if (request.newPassword() == null || request.newPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能为空");
        }
        rateLimiter.checkSmsRateLimit(request.phone());
        authService.resetPasswordBySms(request.phone(), request.code(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "密码重置成功"));
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效的授权头");
        }
        return authorization.substring(7);
    }

    public record SmsCodeRequest(String phone) {}
    public record EmailCodeRequest(String email) {}
    public record SmsLoginRequest(String phone, String code) {}
    public record PhonePasswordRequest(String phone, String password) {}
    public record EmailPasswordRequest(String email, String password) {}
    public record UsernamePasswordRequest(String username, String password) {}
    public record PhoneRegisterRequest(String phone, String username, String password) {}
    public record EmailRegisterRequest(String email, String code, String username, String password) {}
    public record RefreshTokenRequest(String refreshToken) {}
    public record UserUpdateRequest(String username, String phone, String email) {}
    public record ChangePasswordRequest(String oldPassword, String newPassword) {}
    public record ResetPasswordRequest(String phone, String code, String newPassword) {}

    public record UserInfoResponse(Long id, String username, String phone, String email, Boolean disabled, Object createdAt) {}
}
