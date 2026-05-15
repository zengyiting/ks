package com.example.recommend.service;

import com.example.recommend.config.CacheService;
import com.example.recommend.model.User;
import com.example.recommend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
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

    private static final int SMS_CODE_EXPIRE_SECONDS = 300; // 5分钟
    private static final int EMAIL_CODE_EXPIRE_SECONDS = 300; // 5分钟
    private static final int ACCESS_TOKEN_EXPIRE_SECONDS = 1800; // 30分钟
    private static final int REFRESH_TOKEN_EXPIRE_SECONDS = 86400 * 7; // 7天

    private final UserRepository userRepository;
    private final CacheService cacheService;
    private final EmailService emailService;
    private final SecureRandom secureRandom;

    public AuthService(UserRepository userRepository, CacheService cacheService, EmailService emailService) {
        this.userRepository = userRepository;
        this.cacheService = cacheService;
        this.emailService = emailService;
        this.secureRandom = new SecureRandom();
    }

    /**
     * 发送手机验证码
     */
    public void sendSmsCode(String phone) {
        String safePhone = normalizePhone(phone);
        String code = generateCode();
        String key = SMS_CODE_PREFIX + safePhone;

        // 模拟发送验证码（实际项目中调用短信服务商）
        System.out.println("【模拟发送短信验证码】手机号: " + safePhone + ", 验证码: " + code);

        cacheService.set(key, code, SMS_CODE_EXPIRE_SECONDS);
    }

    /**
     * 发送邮箱验证码
     */
    public void sendEmailCode(String email) {
        String safeEmail = normalizeEmail(email);
        String code = generateCode();
        String key = EMAIL_CODE_PREFIX + safeEmail;

        // 使用邮件服务发送验证码（支持真实发送或模拟发送）
        emailService.sendVerificationCode(safeEmail, code);

        cacheService.set(key, code, EMAIL_CODE_EXPIRE_SECONDS);
    }

    /**
     * 手机验证码登录
     */
    @Transactional
    public TokenResponse loginBySms(String phone, String code) {
        String safePhone = normalizePhone(phone);
        String key = SMS_CODE_PREFIX + safePhone;

        String storedCode = cacheService.get(key);
        if (storedCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码已过期，请重新获取");
        }
        if (!storedCode.equals(code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "验证码错误");
        }

        // 删除已使用的验证码
        cacheService.delete(key);

        // 查找或创建用户
        User user = findOrCreateUserByPhone(safePhone);
        return generateTokens(user);
    }

    /**
     * 手机密码登录
     */
    @Transactional
    public TokenResponse loginByPhone(String phone, String password) {
        String safePhone = normalizePhone(phone);
        String safePassword = normalizePassword(password);
        String hashed = hashPassword(safePassword);

        Optional<User> existing = userRepository.findByPhone(safePhone);
        if (existing.isEmpty()) {
            // 如果用户不存在，自动创建（兼容旧逻辑）
            User user = new User(generateUsername());
            user.setPhone(safePhone);
            user.setPasswordHash(hashed);
            User saved = userRepository.save(user);
            return new TokenResponse(null, null, null, saved.getId(), saved.getUsername(), saved.getPhone(), saved.getEmail());
        }

        User user = existing.get();
        if (user.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已禁用");
        }

        String stored = user.getPasswordHash();
        if (stored == null || stored.isBlank()) {
            // 如果用户没有设置密码，设置密码
            user.setPasswordHash(hashed);
            userRepository.save(user);
            return generateTokens(user);
        }

        if (!stored.equals(hashed)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "手机号或密码错误");
        }

        return generateTokens(user);
    }

    /**
     * 邮箱密码登录
     */
    @Transactional
    public TokenResponse loginByEmail(String email, String password) {
        String safeEmail = normalizeEmail(email);
        String safePassword = normalizePassword(password);
        String hashed = hashPassword(safePassword);

        Optional<User> existing = userRepository.findByEmail(safeEmail);
        if (existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "邮箱或密码错误");
        }

        User user = existing.get();
        if (user.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已禁用");
        }

        String stored = user.getPasswordHash();
        if (stored == null || stored.isBlank() || !stored.equals(hashed)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "邮箱或密码错误");
        }

        return generateTokens(user);
    }

    /**
     * 用户名密码登录
     */
    @Transactional
    public TokenResponse loginByUsername(String username, String password) {
        String safeUsername = username == null ? "" : username.trim();
        String safePassword = normalizePassword(password);
        String hashed = hashPassword(safePassword);

        if (safeUsername.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }

        Optional<User> existing = userRepository.findByUsername(safeUsername);
        if (existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        User user = existing.get();
        if (user.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已禁用");
        }

        String stored = user.getPasswordHash();
        if (stored == null || stored.isBlank() || !stored.equals(hashed)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        return generateTokens(user);
    }

    /**
     * 刷新token
     */
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

        // 删除旧的refresh token
        cacheService.delete(REFRESH_TOKEN_PREFIX + refreshToken);

        return generateTokens(userOpt.get());
    }

    /**
     * 登出
     */
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

    /**
     * 邮箱注册（带验证码）
     */
    @Transactional
    public TokenResponse registerByEmail(String email, String code, String username, String password) {
        String safeEmail = normalizeEmail(email);
        String safePassword = normalizePassword(password);

        // 验证验证码
        String key = EMAIL_CODE_PREFIX + safeEmail;
        String storedCode = cacheService.get(key);
        if (storedCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码已过期，请重新获取");
        }
        if (!storedCode.equals(code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "验证码错误");
        }

        // 删除已使用的验证码
        cacheService.delete(key);

        // 验证用户名
        String safeUsername = username == null ? "" : username.trim();
        if (safeUsername.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名至少3个字符");
        }

        // 检查邮箱是否已注册
        if (userRepository.findByEmail(safeEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该邮箱已被注册");
        }

        // 检查用户名是否已存在
        if (userRepository.findByUsername(safeUsername).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该用户名已被使用");
        }

        User user = new User(safeUsername);
        user.setEmail(safeEmail);
        user.setPasswordHash(hashPassword(safePassword));
        User saved = userRepository.save(user);

        return generateTokens(saved);
    }

    /**
     * 手机注册
     */
    @Transactional
    public TokenResponse registerByPhone(String phone, String username, String password) {
        String safePhone = normalizePhone(phone);
        String safePassword = normalizePassword(password);

        // 验证用户名
        String safeUsername = username == null ? "" : username.trim();
        if (safeUsername.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名至少3个字符");
        }

        // 检查手机号是否已注册
        if (userRepository.findByPhone(safePhone).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该手机号已被注册");
        }

        // 检查用户名是否已存在
        if (userRepository.findByUsername(safeUsername).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该用户名已被使用");
        }

        User user = new User(safeUsername);
        user.setPhone(safePhone);
        user.setPasswordHash(hashPassword(safePassword));
        User saved = userRepository.save(user);

        return generateTokens(saved);
    }

    /**
     * 验证access token
     */
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

        // 创建新用户
        User user = new User(generateUsername());
        user.setPhone(phone);
        return userRepository.save(user);
    }

    private TokenResponse generateTokens(User user) {
        String accessToken = generateToken();
        String refreshToken = generateToken();

        // 存储access token
        cacheService.set(ACCESS_TOKEN_PREFIX + accessToken,
                String.valueOf(user.getId()), ACCESS_TOKEN_EXPIRE_SECONDS);

        // 存储refresh token
        cacheService.set(REFRESH_TOKEN_PREFIX + refreshToken,
                String.valueOf(user.getId()), REFRESH_TOKEN_EXPIRE_SECONDS);

        // 存储用户的refresh token（用于登出时清理）
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
        int code = secureRandom.nextInt(900000) + 100000; // 100000-999999
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

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * 修改密码（需要旧密码）
     */
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

        if (!stored.equals(hashPassword(safeOldPassword))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "旧密码错误");
        }

        user.setPasswordHash(hashPassword(safeNewPassword));
        userRepository.save(user);
    }

    /**
     * 通过验证码重置密码
     */
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

        user.setPasswordHash(hashPassword(safeNewPassword));
        userRepository.save(user);
    }

    /**
     * 更新用户信息
     */
    @Transactional
    public User updateUserInfo(Long userId, String username, String phone, String email) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        if (user.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已禁用");
        }

        // 更新用户名（如果提供）
        if (username != null && !username.trim().isBlank()) {
            String safeUsername = username.trim();
            if (safeUsername.length() < 3) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名至少3个字符");
            }
            // 检查用户名是否被其他用户使用
            userRepository.findByUsername(safeUsername).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "该用户名已被使用");
                }
            });
            user.setUsername(safeUsername);
        }

        // 更新手机号（如果提供）
        if (phone != null && !phone.trim().isBlank()) {
            String safePhone = normalizePhone(phone);
            // 检查手机号是否被其他用户使用
            userRepository.findByPhone(safePhone).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "该手机号已被注册");
                }
            });
            user.setPhone(safePhone);
        }

        // 更新邮箱（如果提供）
        if (email != null && !email.trim().isBlank()) {
            String safeEmail = normalizeEmail(email);
            // 检查邮箱是否被其他用户使用
            userRepository.findByEmail(safeEmail).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "该邮箱已被注册");
                }
            });
            user.setEmail(safeEmail);
        }

        return userRepository.save(user);
    }

    /**
     * 获取用户详情
     */
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
