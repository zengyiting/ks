package com.example.recommend.service;

import com.example.recommend.config.CacheService;
import com.example.recommend.model.User;
import com.example.recommend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{6,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final String EMAIL_CODE_PREFIX = "email:code:";
    private static final String ACCESS_TOKEN_PREFIX = "token:access:";
    private static final String REFRESH_TOKEN_PREFIX = "token:refresh:";
    private static final String USER_ID_PREFIX = "token:user:";

    private static final int SMS_CODE_EXPIRE_SECONDS = 300;
    private static final int EMAIL_CODE_EXPIRE_SECONDS = 300;
    private static final int ACCESS_TOKEN_EXPIRE_SECONDS = 1800;
    private static final int REFRESH_TOKEN_EXPIRE_SECONDS = 86400 * 7;

    private final UserRepository userRepository;
    private final CacheService cacheService;
    private final EmailService emailService;
    private final SecureRandom secureRandom;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, CacheService cacheService, EmailService emailService) {
        this.userRepository = userRepository;
        this.cacheService = cacheService;
        this.emailService = emailService;
        this.secureRandom = new SecureRandom();
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public void sendSmsCode(String phone) {
        String safePhone = normalizePhone(phone);
        String code = generateCode();
        String key = SMS_CODE_PREFIX + safePhone;

        System.out.println("【模拟发送短信验证码】手机号: " + safePhone + ", 验证码: " + code);

        cacheService.set(key, code, SMS_CODE_EXPIRE_SECONDS);
    }

    public void sendEmailCode(String email) {
        String safeEmail = normalizeEmail(email);
        String code = generateCode();
        String key = EMAIL_CODE_PREFIX + safeEmail;

        emailService.sendVerificationCode(safeEmail, code);

        cacheService.set(key, code, EMAIL_CODE_EXPIRE_SECONDS);
    }

    @Transactional
    public TokenResponse loginBySms(String phone, String code) {
        String safePhone = normalizePhone(phone);
        String key = SMS_CODE_PREFIX + safePhone;

        String storedCode = cacheService.get(key);
        if (storedCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码已过期，请重新获取");
        }
        if (!storedCode.equals(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码错误");
        }

        cacheService.delete(key);

        User user = findOrCreateUserByPhone(safePhone);
        return generateTokens(user);
    }

    @Transactional
    public TokenResponse loginByPhone(String phone, String password) {
        String safePhone = normalizePhone(phone);
        String safePassword = normalizePassword(password);

        Optional<User> existing = userRepository.findByPhone(safePhone);
        if (existing.isEmpty()) {
            User user = new User(generateUsername());
            user.setPhone(safePhone);
            user.setPasswordHash(passwordEncoder.encode(safePassword));
            User saved = userRepository.save(user);
            return generateTokens(saved);
        }

        User user = existing.get();
        if (user.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已禁用");
        }

        String stored = user.getPasswordHash();
        if (stored == null || stored.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(safePassword));
            userRepository.save(user);
            return generateTokens(user);
        }

        if (!passwordEncoder.matches(safePassword, stored)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号或密码错误");
        }

        return generateTokens(user);
    }

    @Transactional
    public TokenResponse loginByEmail(String email, String password) {
        String safeEmail = normalizeEmail(email);
        String safePassword = normalizePassword(password);

        Optional<User> existing = userRepository.findByEmail(safeEmail);
        if (existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该邮箱未注册，请先注册");
        }

        User user = existing.get();
        if (user.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已禁用");
        }

        String stored = user.getPasswordHash();
        if (stored == null || stored.isBlank() || !passwordEncoder.matches(safePassword, stored)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱或密码错误");
        }

        return generateTokens(user);
    }

    @Transactional
    public TokenResponse loginByUsername(String username, String password) {
        String safeUsername = username == null ? "" : username.trim();
        String safePassword = normalizePassword(password);

        if (safeUsername.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }

        Optional<User> existing = userRepository.findByUsername(safeUsername);
        if (existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该用户名未注册，请先注册");
        }

        User user = existing.get();
        if (user.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已禁用");
        }

        String stored = user.getPasswordHash();
        if (stored == null || stored.isBlank() || !passwordEncoder.matches(safePassword, stored)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名或密码错误");
        }

        return generateTokens(user);
    }

    public TokenResponse refreshToken(String refreshToken) {
        String userIdStr = cacheService.get(REFRESH_TOKEN_PREFIX + refreshToken);
        if (userIdStr == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效的refresh token");
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效的用户ID");
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在");
        }

        cacheService.delete(REFRESH_TOKEN_PREFIX + refreshToken);

        return generateTokens(userOpt.get());
    }

    public void logout(String accessToken) {
        String userIdStr = cacheService.get(ACCESS_TOKEN_PREFIX + accessToken);
        if (userIdStr != null) {
            cacheService.delete(ACCESS_TOKEN_PREFIX + accessToken);
            String refreshToken = cacheService.get(USER_ID_PREFIX + userIdStr);
            if (refreshToken != null) {
                cacheService.delete(REFRESH_TOKEN_PREFIX + refreshToken);
                cacheService.delete(USER_ID_PREFIX + userIdStr);
            }
        }
    }

    @Transactional
    public TokenResponse registerByEmail(String email, String code, String username, String password) {
        String safeEmail = normalizeEmail(email);
        String safePassword = normalizePassword(password);

        String key = EMAIL_CODE_PREFIX + safeEmail;
        String storedCode = cacheService.get(key);
        if (storedCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码已过期，请重新获取");
        }
        if (!storedCode.equals(code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "验证码错误");
        }

        cacheService.delete(key);

        String safeUsername = username == null ? "" : username.trim();
        if (safeUsername.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名至少3个字符");
        }

        if (userRepository.findByEmail(safeEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该邮箱已被注册");
        }

        if (userRepository.findByUsername(safeUsername).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该用户名已被使用");
        }

        User user = new User(safeUsername);
        user.setEmail(safeEmail);
        user.setPasswordHash(passwordEncoder.encode(safePassword));
        User saved = userRepository.save(user);

        return generateTokens(saved);
    }

    @Transactional
    public TokenResponse registerByPhone(String phone, String username, String password) {
        String safePhone = normalizePhone(phone);
        String safePassword = normalizePassword(password);

        String safeUsername = username == null ? "" : username.trim();
        if (safeUsername.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名至少3个字符");
        }

        if (userRepository.findByPhone(safePhone).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该手机号已被注册");
        }

        if (userRepository.findByUsername(safeUsername).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该用户名已被使用");
        }

        User user = new User(safeUsername);
        user.setPhone(safePhone);
        user.setPasswordHash(passwordEncoder.encode(safePassword));
        User saved = userRepository.save(user);

        return generateTokens(saved);
    }

    public Optional<User> validateToken(String accessToken) {
        String userIdStr = cacheService.get(ACCESS_TOKEN_PREFIX + accessToken);
        if (userIdStr == null) {
            return Optional.empty();
        }

        try {
            Long userId = Long.parseLong(userIdStr);
            return userRepository.findById(userId);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private User findOrCreateUserByPhone(String phone) {
        Optional<User> existing = userRepository.findByPhone(phone);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.isDisabled()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已禁用");
            }
            return user;
        }

        User user = new User(generateUsername());
        user.setPhone(phone);
        return userRepository.save(user);
    }

    private TokenResponse generateTokens(User user) {
        String accessToken = generateToken();
        String refreshToken = generateToken();

        cacheService.set(ACCESS_TOKEN_PREFIX + accessToken,
                String.valueOf(user.getId()), ACCESS_TOKEN_EXPIRE_SECONDS);

        cacheService.set(REFRESH_TOKEN_PREFIX + refreshToken,
                String.valueOf(user.getId()), REFRESH_TOKEN_EXPIRE_SECONDS);

        cacheService.set(USER_ID_PREFIX + user.getId(),
                refreshToken, REFRESH_TOKEN_EXPIRE_SECONDS);

        return new TokenResponse(
                accessToken,
                refreshToken,
                ACCESS_TOKEN_EXPIRE_SECONDS,
                user.getId(),
                user.getUsername(),
                user.getPhone(),
                user.getEmail()
        );
    }

    private String generateCode() {
        int code = secureRandom.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    private String generateToken() {
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String generateUsername() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String normalizePhone(String phone) {
        String safe = phone == null ? "" : phone.trim();
        if (!PHONE_PATTERN.matcher(safe).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号格式不正确");
        }
        return safe;
    }

    private String normalizeEmail(String email) {
        String safe = email == null ? "" : email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(safe).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");
        }
        return safe;
    }

    private String normalizePassword(String password) {
        String safe = password == null ? "" : password.trim();
        if (safe.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码至少6位");
        }
        return safe;
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        if (user.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已禁用");
        }

        String safeOldPassword = normalizePassword(oldPassword);
        String safeNewPassword = normalizePassword(newPassword);

        String stored = user.getPasswordHash();
        if (stored == null || stored.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前账号未设置密码，请先设置密码");
        }

        if (!passwordEncoder.matches(safeOldPassword, stored)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "旧密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(safeNewPassword));
        userRepository.save(user);
    }

    @Transactional
    public void resetPasswordBySms(String phone, String code, String newPassword) {
        String safePhone = normalizePhone(phone);
        String safeNewPassword = normalizePassword(newPassword);

        String key = SMS_CODE_PREFIX + safePhone;
        String storedCode = cacheService.get(key);
        if (storedCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码已过期，请重新获取");
        }
        if (!storedCode.equals(code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "验证码错误");
        }

        cacheService.delete(key);

        User user = userRepository.findByPhone(safePhone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "该手机号未注册"));

        if (user.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已禁用");
        }

        user.setPasswordHash(passwordEncoder.encode(safeNewPassword));
        userRepository.save(user);
    }

    @Transactional
    public User updateUserInfo(Long userId, String username, String phone, String email) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        if (user.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已禁用");
        }

        if (username != null && !username.trim().isBlank()) {
            String safeUsername = username.trim();
            if (safeUsername.length() < 3) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名至少3个字符");
            }
            userRepository.findByUsername(safeUsername).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "该用户名已被使用");
                }
            });
            user.setUsername(safeUsername);
        }

        if (phone != null && !phone.trim().isBlank()) {
            String safePhone = normalizePhone(phone);
            userRepository.findByPhone(safePhone).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "该手机号已被注册");
                }
            });
            user.setPhone(safePhone);
        }

        if (email != null && !email.trim().isBlank()) {
            String safeEmail = normalizeEmail(email);
            userRepository.findByEmail(safeEmail).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "该邮箱已被注册");
                }
            });
            user.setEmail(safeEmail);
        }

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            Integer expiresIn,
            Long userId,
            String username,
            String phone,
            String email
    ) {}
}
