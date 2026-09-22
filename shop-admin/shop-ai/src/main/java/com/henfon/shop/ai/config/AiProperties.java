package com.henfon.shop.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 智能客服业务配置。
 *
 * 百炼与 Qdrant 的连接参数由 Spring AI 两个 starter 的官方属性承载
 * （spring.ai.dashscope.*、spring.ai.vectorstore.qdrant.*），这里只放业务侧开关、
 * 模型名与检索参数，避免同一份配置在两处维护。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Data
@ConfigurationProperties(prefix = "shop.ai")
public class AiProperties {

    /** 总开关。关闭时对话接口直接返回"客服未启用"，不发任何外部请求。 */
    private boolean enabled = false;

    /** 门户知识库 collection 名。 */
    private String portalCollection = "shop_knowledge_portal";

    /** 管理端知识库 collection 名。与门户物理分开，避免过滤条件写漏导致数据越权。 */
    private String adminCollection = "shop_knowledge_admin";

    private Qdrant qdrant = new Qdrant();

    private Retrieval retrieval = new Retrieval();

    private Chat chat = new Chat();

    private Vectorize vectorize = new Vectorize();

    private Limit limit = new Limit();

    private Agent agent = new Agent();

    /**
     * Qdrant 连接参数。
     *
     * 没有复用 Spring AI 的 spring.ai.vectorstore.qdrant.*：那套只支持一个 collection 名，
     * 而门户与管理端必须落在两个物理分开的 collection 上，所以客户端与两个 VectorStore
     * 都由本模块自行装配。
     */
    @Data
    public static class Qdrant {
        /** 集群主机名，只填主机名，不带 https:// 与端口。 */
        private String host = "";
        /** gRPC 端口，Qdrant Cloud 为 6334。 */
        private int port = 6334;
        /** database 级 API Key。 */
        private String apiKey = "";
        /** 云端集群必须显式开启 TLS，同时必须传入 API Key。 */
        private boolean useTls = true;
        /** 单次请求超时秒数。 */
        private long timeoutSeconds = 10;

        /**
         * 判断连接参数是否齐全。
         *
         * 与服务端的"看起来像配置了"不同，这里要求 host 与 apiKey 都非空白，
         * 否则启动自检会给出明确日志而不是等到第一次检索才暴露。
         *
         * @return 参数齐全返回 true
         * @author Henfon
         * @date 2026-09-21
         */
        public boolean isConfigured() {
            return host != null && !host.isBlank() && apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * 检索参数。
     */
    @Data
    public static class Retrieval {
        /** 向量召回条数，召回后交给精排。 */
        private int topK = 20;
        /** 精排后保留条数，最终拼进上下文。 */
        private int rerankTopN = 5;
        /** 相似度下限，低于该值视为未命中，不拼进上下文。 */
        private double scoreThreshold = 0.5;
    }

    /**
     * 对话参数。
     */
    @Data
    public static class Chat {
        /** 对话模型。 */
        private String chatModel = "qwen-plus";
        /** 向量模型。 */
        private String embeddingModel = "text-embedding-v4";
        /** 精排模型。 */
        private String rerankModel = "qwen3-rerank";
        /** 单次回答最大输出 token，同时是成本上限的一道闸。 */
        private int maxTokens = 1024;
        private double temperature = 0.3;
        /** 送进模型的历史消息条数上限，超出丢弃最早的。 */
        private int maxHistoryMessages = 12;
        /** 首字节超时毫秒数，超时按兜底话术收尾而不是让连接悬着。 */
        private long firstTokenTimeoutMs = 15000;
        /** 单轮对话最长等待毫秒数。 */
        private long streamTimeoutMs = 60000;
    }

    /**
     * 向量化管道参数。
     */
    @Data
    public static class Vectorize {
        /** 单次请求提交的向量化条数。百炼 text-embedding-v4 单次上限 10 条。 */
        private int batchSize = 10;
        /** 单批失败后的重试次数。 */
        private int maxRetries = 3;
        /** 批与批之间的间隔毫秒数，避免触发服务端限流。 */
        private long throttleMs = 200;
        /** 单次全量任务最多处理的商品数，0 表示不限制。 */
        private int maxItems = 0;
    }

    /**
     * 人工接待的空闲回收。
     *
     * 人工会话的状态原本只由客服点「结束服务」推进，客服去忙别的、下班忘了点、或者浏览器直接
     * 关掉，会话就永久停在 HUMAN：买家那边一直显示"人工客服正在接待"，长连接挂着，连提问都被
     * 拦住（HUMAN 状态下走的是人工链路，智能客服不可用）。所以必须有一道时间闸把它收回来。
     *
     * 两道闸的分工：催办是"提醒"，只推一条提示给客服，不动状态，避免客服正看着买家打字时
     * 会话被系统抢走；回收是"兜底"，到点仍无消息才真的结束，把买家交回智能客服。
     */
    @Data
    public static class Agent {
        /** 空闲多久给接待客服推一条催办提示，0 表示不催办。 */
        private long idleRemindMinutes = 15;
        /** 空闲多久自动结束人工接待、退回智能客服，0 表示不回收。 */
        private long idleCloseMinutes = 30;
        /** 单轮扫描处理的会话条数上限，防止积压时一次读进太多。 */
        private int scanLimit = 200;
    }

    /**
     * 门户对话配额。
     *
     * 门户对话接口对访客开放，每次提问都要调用按量计费的模型，所以按调用方维度设配额。
     * 匿名访客按来源 IP 计数，同一出口的多个访客共享额度；登录会员按会员 ID 计数，换 IP
     * 也绕不过去。两种维度分属不同键空间，访客伪造不出会员的额度。
     *
     * 分钟与日两道闸的分工：分钟闸挡脚本的瞬时冲击，日闸挡的是把请求摊薄到长时间里的
     * 慢速占用——后者单看分钟配额永远合规，累计账单却很难看。
     */
    @Data
    public static class Limit {
        /** 是否启用配额，关闭后对话接口不限次。 */
        private boolean enabled = true;
        /** 单个会员每分钟提问上限。 */
        private int memberPerMinute = 30;
        /** 单个会员每日提问上限。 */
        private int memberPerDay = 500;
        /** 单个来源 IP 每分钟提问上限，同一出口的未登录访客会互相占用。 */
        private int anonymousPerMinute = 10;
        /** 单个来源 IP 每日提问上限。 */
        private int anonymousPerDay = 100;
    }
}
