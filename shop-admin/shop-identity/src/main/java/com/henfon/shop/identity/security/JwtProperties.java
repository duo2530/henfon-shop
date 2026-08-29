package com.henfon.shop.identity.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Data
@ConfigurationProperties(prefix = "shop.security.jwt")
public class JwtProperties {

    /** JWT 签名密钥，生产环境必须通过环境变量覆盖。 */
    private String secret = "henfon-shop-jwt-secret-key-change-in-production-2026";

    /** 令牌有效期，默认八小时。 */
    private long expirationSeconds = 8 * 60 * 60;

    /** 令牌签发方。 */
    private String issuer = "henfon-shop";
}
