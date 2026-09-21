package com.henfon.shop.ai.config;

import io.qdrant.client.QdrantClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AI 客服启动自检。
 *
 * 连不上向量库这件事必须开机就说清楚：否则要等到买家提问、检索失败，才从一次
 * 上下文缺失的回答里倒推原因。自检只记录日志，不中断启动——向量库临时不可用时，
 * 应用其余功能仍应可用。
 *
 * 百炼 API Key 单独检查：它为空不会让任何 Bean 装配失败（客户端是懒建的），
 * 症状要等到第一次提问或第一次同步向量才出现，且报出来的是模型侧的鉴权错误，
 * 很容易被当成模型服务故障。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Component
@Order(20)
@ConditionalOnProperty(prefix = "shop.ai", name = "enabled", havingValue = "true")
public class AiStartupChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiStartupChecker.class);

    private final AiProperties properties;

    private final QdrantClient qdrantClient;

    private final String dashscopeApiKey;

    /**
     * 创建启动自检。
     *
     * @param properties AI 配置
     * @param qdrantClient Qdrant 客户端
     * @param dashscopeApiKey 百炼 API Key，取自 Spring AI 官方属性
     * @author Henfon
     * @date 2026-09-21
     */
    public AiStartupChecker(AiProperties properties,
                            QdrantClient qdrantClient,
                            @Value("${spring.ai.dashscope.api-key:}") String dashscopeApiKey) {
        this.properties = properties;
        this.qdrantClient = qdrantClient;
        this.dashscopeApiKey = dashscopeApiKey;
    }

    /**
     * 启动后核对连接参数并探测向量库。
     *
     * @param args 启动参数
     * @author Henfon
     * @date 2026-09-21
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(dashscopeApiKey)) {
            log.warn("AI 客服已启用，但百炼 API Key 为空（SHOP_DASHSCOPE_API_KEY），对话与知识库向量化要等到首次调用才会失败");
        }
        AiProperties.Qdrant config = properties.getQdrant();
        if (!config.isConfigured()) {
            log.warn("AI 客服已启用，但 Qdrant 连接参数不完整（host 或 api-key 为空），知识库检索不可用");
            return;
        }
        long timeoutSeconds = config.getTimeoutSeconds();
        try {
            List<String> collections = qdrantClient.listCollectionsAsync()
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            log.info("AI 客服已启用，Qdrant 连接正常（{}:{}），现有 collection：{}",
                    config.getHost(), config.getPort(), collections);
            for (String expected : List.of(properties.getPortalCollection(), properties.getAdminCollection())) {
                if (!collections.contains(expected)) {
                    log.error("Qdrant 缺少预建 collection：{}。维度与距离无法在运行期修正，请先在控制台建好再启用", expected);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("AI 客服启动自检被中断");
        } catch (Exception exception) {
            // 出网被拦、TLS 未开、API Key 写错都会落到这里，日志里带上地址便于直接定位。
            log.error("AI 客服已启用但无法访问 Qdrant（{}:{}）：{}",
                    config.getHost(), config.getPort(), exception.getMessage());
        }
    }
}
