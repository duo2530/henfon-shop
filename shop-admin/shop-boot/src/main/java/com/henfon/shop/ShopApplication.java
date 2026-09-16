package com.henfon.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

/**
 * Henfon 商城后端应用入口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@SpringBootApplication
// 仅扫描各业务模块的 Mapper 包，避免把支付客户端等普通接口误注册为 MyBatis Mapper。
@MapperScan({
        "com.henfon.shop.catalog.mapper",
        "com.henfon.shop.content.mapper",
        "com.henfon.shop.export.mapper",
        "com.henfon.shop.identity.mapper",
        "com.henfon.shop.inventory.mapper",
        "com.henfon.shop.marketing.mapper",
        "com.henfon.shop.payment.mapper",
        "com.henfon.shop.reporting.mapper",
        "com.henfon.shop.trade.mapper"
})
@EnableScheduling
public class ShopApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 启动参数
     * @author Henfon
     * @date 2026-08-29
     */
    public static void main(String[] args) {
        // 所有业务模块以依赖方式装配到同一个可部署应用中。
        SpringApplication.run(ShopApplication.class, args);
    }
}
