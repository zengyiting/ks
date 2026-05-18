package com.example.recommend.service;

import com.example.recommend.config.CacheService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RateLimiterService {
    private static final String RATE_LIMIT_PREFIX = "rate:";

    private final CacheService cacheService;

    public RateLimiterService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    public void checkRateLimit(String key, int maxRequests, int windowSeconds) {
        String rateKey = RATE_LIMIT_PREFIX + key;
        String countStr = cacheService.get(rateKey);
        int count = countStr == null ? 0 : Integer.parseInt(countStr);

        if (count >= maxRequests) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }

        cacheService.set(rateKey, String.valueOf(count + 1), windowSeconds);
    }

    public void checkSmsRateLimit(String phone) {
        checkRateLimit("sms:" + phone, 5, 300);
    }

    public void checkEmailRateLimit(String email) {
        checkRateLimit("email:" + email, 5, 300);
    }

    public void checkLoginRateLimit(String identifier) {
        checkRateLimit("login:" + identifier, 10, 300);
    }

    public void checkRegisterRateLimit(String identifier) {
        checkRateLimit("register:" + identifier, 5, 300);
    }

    public void checkGlobalIpRateLimit(String ip) {
        checkRateLimit("ip:" + ip, 60, 60);
    }
}
