package com.henfon.shop.ai.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 客服基础装配。
 *
 * 与 Spring AI 自带自动配置的分工：百炼的对话与向量模型交给
 * spring-ai-alibaba-starter-dashscope 自动配置（由 spring.ai.dashscope.enabled 控制），
 * Qdrant 侧自行装配——因为门户与管理端要用两个物理分开的 collection，而官方的
 * spring.ai.vectorstore.qdrant.* 只能配一个 collection 名。
 *
 * 整个类受 shop.ai.enabled 控制：关闭时不会创建任何指向外部服务的客户端，
 * 本地不配 key 也能正常启动。配置属性对象的注册在 {@link AiPropertiesConfiguration}，
 * 那里不带条件——挂在当前这个类上会让关闭时 AiProperties 一并消失。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Configuration
@ConditionalOnProperty(prefix = "shop.ai", name = "enabled", havingValue = "true")
public class AiConfiguration {

    /**
     * 创建 Qdrant gRPC 客户端。
     *
     * @param properties AI 配置
     * @return Qdrant 客户端，随应用关闭释放连接
     * @author Henfon
     * @date 2026-09-21
     */
    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient(AiProperties properties) {
        AiProperties.Qdrant config = properties.getQdrant();
        // 云端集群必须开 TLS 且必须带 API Key，两者缺一都会在第一次请求时失败。
        QdrantGrpcClient grpcClient = QdrantGrpcClient
                .newBuilder(config.getHost(), config.getPort(), config.isUseTls())
                .withApiKey(config.getApiKey())
                .withTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
        return new QdrantClient(grpcClient);
    }

    /**
     * 门户公开知识的向量库实例。
     *
     * @param qdrantClient Qdrant 客户端
     * @param embeddingModel 百炼向量模型
     * @param properties AI 配置
     * @return 门户 collection 的向量库
     * @author Henfon
     * @date 2026-09-21
     */
    @Bean("portalVectorStore")
    public VectorStore portalVectorStore(QdrantClient qdrantClient,
                                         EmbeddingModel embeddingModel,
                                         AiProperties properties) {
        return buildVectorStore(qdrantClient, embeddingModel, properties.getPortalCollection());
    }

    /**
     * 管理端内部知识的向量库实例。
     *
     * @param qdrantClient Qdrant 客户端
     * @param embeddingModel 百炼向量模型
     * @param properties AI 配置
     * @return 管理端 collection 的向量库
     * @author Henfon
     * @date 2026-09-21
     */
    @Bean("adminVectorStore")
    public VectorStore adminVectorStore(QdrantClient qdrantClient,
                                        EmbeddingModel embeddingModel,
                                        AiProperties properties) {
        return buildVectorStore(qdrantClient, embeddingModel, properties.getAdminCollection());
    }

    /**
     * 构造指向指定 collection 的向量库。
     *
     * collection 已在云端手工预建（1024 维、Cosine、匿名向量），维度一旦定死不可改，
     * 所以不让框架按模型维度自动创建，避免它悄悄建出维度不对或距离不对的表。
     *
     * @param qdrantClient Qdrant 客户端
     * @param embeddingModel 向量模型
     * @param collectionName collection 名
     * @return 向量库实例
     * @author Henfon
     * @date 2026-09-21
     */
    private VectorStore buildVectorStore(QdrantClient qdrantClient,
                                         EmbeddingModel embeddingModel,
                                         String collectionName) {
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(false)
                .build();
    }
}
