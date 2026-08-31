package com.henfon.shop.identity.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理员登录 IP 固定窗口限流器。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 判断当前 IP 是否允许继续登录。
     *
     * @param clientIp 客户端 IP
     * @return 是否允许尝试
     * @author Henfon
     * @date 2026-08-31
     */
    public boolean allow(String clientIp) {
        String key = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt() >= WINDOW_MILLIS) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt(), current.attempts() + 1);
        });
        // 周期性清理过期窗口，避免异常来源 IP 持续增长内存占用。
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_MILLIS);
        }
        return window.attempts() <= MAX_ATTEMPTS;
    }

    /**
     * 清除登录成功后的限流窗口。
     *
     * @param clientIp 客户端 IP
     * @author Henfon
     * @date 2026-08-31
     */
    public void reset(String clientIp) {
        if (clientIp != null) {
            windows.remove(clientIp);
        }
    }

    private record Window(long startedAt, int attempts) {
    }
}
