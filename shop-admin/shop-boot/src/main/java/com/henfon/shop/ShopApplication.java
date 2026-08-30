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
@MapperScan("com.henfon.shop")
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
