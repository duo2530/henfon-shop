package com.henfon.shop.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * 商品检索工具的包装：调用照原样透传，只把返回里出现的商品编号记下来。
 *
 * 记编号是为了补上卡片的一路来源。检索命中时，卡片来自编排层的 PRODUCT 引用；但模型也可能
 * 自己判断"没查到、再搜一次"而主动调工具，那一批商品不在引用里，按原实现就出不了卡片。
 * 工具的结果本来就是模型作答的依据，把它记下来与引用合并，卡片与回答才对得上。
 *
 * 不用 ThreadLocal 记，也不是工具签名里带上下文：前者在线程复用时串到别的买家身上，后者会被
 * Spring AI 判成"必须提供非空 ToolContext"而未登录买家没有身份可给。这里的收集器由编排层
 * 按轮创建后传进来，一轮一个，天然隔离。
 *
 * 解析失败一律静默：卡片是附件，编号读不出来只是这一轮少几张卡片，不该让整轮回答失败。
 *
 * @author Henfon
 * @date 2026-09-22
 */
public final class AiProductToolRecorder implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(AiProductToolRecorder.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolCallback delegate;

    private final Consumer<Collection<Long>> sink;

    /**
     * 包装一个工具回调。
     *
     * @param delegate 被包装的回调
     * @param sink 商品编号收集器，由编排层按轮传入
     * @author Henfon
     * @date 2026-09-22
     */
    public AiProductToolRecorder(ToolCallback delegate, Consumer<Collection<Long>> sink) {
        this.delegate = delegate;
        this.sink = sink;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        String result = delegate.call(toolInput);
        collect(result);
        return result;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String result = delegate.call(toolInput, toolContext);
        collect(result);
        return result;
    }

    /**
     * 从工具返回里读出商品编号。
     *
     * 只认 products 数组里的 productId：它是 {@link AiProductTools} 自己写的返回结构，
     * 与模型看到的是同一份，不需要模型再复述一遍。
     *
     * @param result 工具返回的 JSON
     * @author Henfon
     * @date 2026-09-22
     */
    private void collect(String result) {
        if (!StringUtils.hasText(result)) {
            return;
        }
        try {
            JsonNode root = MAPPER.readTree(result);
            JsonNode products = root == null ? null : root.get("products");
            if (products == null || !products.isArray()) {
                return;
            }
            List<Long> ids = new ArrayList<>();
            for (JsonNode item : products) {
                JsonNode id = item.get("productId");
                if (id == null || id.isNull()) {
                    continue;
                }
                ids.add(id.isNumber() ? id.asLong() : Long.parseLong(id.asText().trim()));
            }
            if (!ids.isEmpty()) {
                sink.accept(ids);
            }
        } catch (Exception exception) {
            // 返回结构变了、编号不是数字、JSON 不合法，都只影响这一轮的卡片补充
            log.debug("商品检索结果里没有可识别的商品编号，跳过：{}", exception.getMessage());
        }
    }
}
