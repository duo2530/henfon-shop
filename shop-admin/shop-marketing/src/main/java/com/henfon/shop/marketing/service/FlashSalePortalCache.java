package com.henfon.shop.marketing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.marketing.dto.MarketingFlashSalePortalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * 门户秒杀活动列表缓存。
 *
 * <p>门户首页每 30 秒全量轮询一次列表，而列表查询要打 4 张表并对每个商品重签图片地址，
 * 用 10 秒 TTL 就能挡掉绝大部分重复查询。除配置变更外不做主动失效：预占与释放太频繁，
 * 每次删缓存等于没有缓存，10 秒的最坏滞后仍短于前端轮询周期。</p>
 *
 * @author Henfon
 * @date 2026-09-17
 */
@Component
public class FlashSalePortalCache {

    private static final Logger log = LoggerFactory.getLogger(FlashSalePortalCache.class);

    /** 门户秒杀列表缓存键，沿用本项目 shop: 前缀。 */
    static final String PORTAL_CACHE_KEY = "shop:marketing:flash-sale:portal";

    /** 缓存有效期，短于门户 30 秒轮询周期即可显著削峰。 */
    private static final Duration PORTAL_CACHE_TTL = Duration.ofSeconds(10);

    private static final TypeReference<List<MarketingFlashSalePortalResponse>> CACHE_TYPE =
            new TypeReference<>() {
            };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 创建门户秒杀列表缓存。
     *
     * @param redisTemplate Redis 字符串模板
     * @param objectMapper JSON 序列化器
     * @author Henfon
     * @date 2026-09-17
     */
    public FlashSalePortalCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 读取门户列表缓存。
     *
     * @return 缓存的活动列表，未命中或读取失败时返回 null 由调用方回源
     * @author Henfon
     * @date 2026-09-17
     */
    public List<MarketingFlashSalePortalResponse> get() {
        try {
            String cached = redisTemplate.opsForValue().get(PORTAL_CACHE_KEY);
            if (!StringUtils.hasText(cached)) {
                return null;
            }
            return objectMapper.readValue(cached, CACHE_TYPE);
        } catch (Exception exception) {
            // 缓存不可用不能影响门户展示，降级为直接查库。
            log.warn("秒杀门户列表缓存读取失败，回源数据库", exception);
            return null;
        }
    }

    /**
     * 写入门户列表缓存，TTL 到期自动失效。
     *
     * @param activities 门户秒杀活动列表
     * @author Henfon
     * @date 2026-09-17
     */
    public void put(List<MarketingFlashSalePortalResponse> activities) {
        try {
            redisTemplate.opsForValue().set(PORTAL_CACHE_KEY,
                    objectMapper.writeValueAsString(activities), PORTAL_CACHE_TTL);
        } catch (Exception exception) {
            log.warn("秒杀门户列表缓存写入失败，跳过缓存", exception);
        }
    }

    /**
     * 活动配置变更后清除缓存，让运营调整立即生效。
     *
     * @author Henfon
     * @date 2026-09-17
     */
    public void evict() {
        try {
            redisTemplate.delete(PORTAL_CACHE_KEY);
        } catch (Exception exception) {
            // 清理失败最多让调整晚 10 秒生效，不值得打断主流程。
            log.warn("秒杀门户列表缓存清理失败", exception);
        }
    }
}
