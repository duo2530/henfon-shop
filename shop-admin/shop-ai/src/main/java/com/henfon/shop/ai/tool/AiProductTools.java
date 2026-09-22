package com.henfon.shop.ai.tool;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.ai.dto.AiProductCard;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.service.CatalogPortalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按关键词检索在售商品，供智能客服回答「帮我找 XX」。
 *
 * 与订单类工具的分工：订单、物流、售后、优惠券是「状态」，答案唯一且在变；商品是「目录」，
 * 答案是一批候选。两者都用工具而不是向量检索，理由一样——检索只是相似度匹配，买家要的是
 * 能点进去看的确定商品，不是语义相近的一段文案。商品本身在门户商品表里是唯一事实来源，
 * 检索出来的名称、价格、库存都以这里为准。
 *
 * 身份不是必需：检索在售商品对外是公开信息（门户首页与搜索框查的就是同一批数据），
 * 未登录也会挂上这个工具。工具签名里没有 memberId，也就没有可被模型改写的越权面。
 *
 * 卡片不由这个工具产生。卡片在服务端组装（{@link AiProductCard}），主键来自编排层检索到的
 * PRODUCT 引用（见 AiChatService），因为它才是回答所依据的那批商品；工具回调只能算模型
 * 「额外查了一次」，而实测模型拿到现成的检索上下文后通常不会再调它。模型自己输出一遍价格与
 * 图片地址是不可接受的，它会算错、会拼错地址，而卡片上的数字买家会直接拿去对账。
 *
 * @author Henfon
 * @date 2026-09-22
 */
@Component
@ConditionalOnProperty(prefix = "shop.ai", name = "enabled", havingValue = "true")
public class AiProductTools {

    private static final Logger log = LoggerFactory.getLogger(AiProductTools.class);

    /** 单次回给模型的候选数量上限。调大只会挤占上下文，也不会让推荐更准。 */
    private static final int DEFAULT_LIMIT = 5;

    /** 关键词长度上限，挡住异常长的输入。 */
    private static final int MAX_KEYWORD_LENGTH = 50;

    /** 模型给出的排序词与门户搜索的映射，改动要与 CatalogPortalService 的白名单同步。 */
    private static final Map<String, String> SORT_ALIASES = Map.of(
            "销量", "sales",
            "价格升序", "price-asc",
            "价格降序", "price-desc",
            "最新", "newest");

    private final CatalogPortalService catalogPortalService;

    /**
     * 创建商品检索工具。
     *
     * @param catalogPortalService 门户商品查询服务
     * @author Henfon
     * @date 2026-09-22
     */
    public AiProductTools(CatalogPortalService catalogPortalService) {
        this.catalogPortalService = catalogPortalService;
    }

    /**
     * 按关键词检索在售商品。
     *
     * <p>签名里刻意不带 ToolContext。带上它 Spring AI 会强制要求调用方提供非空的工具上下文
     * （`MethodToolCallback#validateToolContextSupport` 把空 Map 也判为不合格），而未登录买家
     * 本来就没有身份可放，只能编一个假 key 去凑，那是拿框架契约当摆设。商品检索也确实不需要
     * 上下文：在售商品是公开信息，卡片另由编排层从检索引用派生。</p>
     *
     * @param keyword 商品关键词，由买家提供
     * @param sortBy 排序方式，可为空
     * @return 候选商品摘要
     * @author Henfon
     * @date 2026-09-22
     */
    @Tool(description = "按关键词搜索商城在售商品。买家问「有没有 XX」「帮我找 XX」「推荐几款 XX」时调用。"
            + "keyword 用买家说的商品词（如「机械键盘」「蓝牙耳机」），不要自己展开成长句，也不要编造商品名。"
            + "sortBy 可选，只能填：销量、价格升序、价格降序、最新，不填按综合推荐。"
            + "返回的每个候选都带商品编号，回答时请结合编号与名称介绍，商品价格与库存以下面返回的为准，不要自己估算。")
    public Map<String, Object> searchProducts(String keyword, String sortBy) {
        Map<String, Object> result = new LinkedHashMap<>();
        String text = normalizeKeyword(keyword);
        if (text == null) {
            result.put("note", "没有拿到有效的商品关键词，请向买家确认他想找什么商品。");
            return result;
        }
        IPage<CatalogProduct> page = catalogPortalService.page(text, null, null, null,
                resolveSort(sortBy), 1, DEFAULT_LIMIT);
        List<CatalogProduct> products = page.getRecords() == null ? List.of() : page.getRecords();
        result.put("products", products.stream().map(this::toBrief).toList());
        if (products.isEmpty()) {
            // 明确告诉模型"没找到"，它才会如实说没找到；留空它倾向于自行编排一个不存在的商品。
            result.put("note", "商城里没有搜到匹配的在售商品。请如实告诉买家没找到，可以提示他换个更常见的说法，"
                    + "不要推荐搜索结果以外的商品，也不要编造商品名或价格。");
            return result;
        }
        result.put("note", "以上是搜索结果，只介绍这几款，不要补充没有出现在结果里的商品。");
        return result;
    }

    /**
     * 组装候选商品摘要。
     *
     * 编号一并给出，是为了让模型在回答里带上它，买家问「第二款呢」时双方指的还是同一件。
     *
     * @param product 商品
     * @return 摘要
     * @author Henfon
     * @date 2026-09-22
     */
    private Map<String, Object> toBrief(CatalogProduct product) {
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("productId", product.getId());
        putIfPresent(brief, "name", product.getProductName());
        putIfPresent(brief, "brand", product.getBrandName());
        putIfPresent(brief, "price", amount(product.getPrice()));
        putIfPresent(brief, "marketPrice", amount(product.getMarketPrice()));
        putIfPresent(brief, "summary", product.getShortDescription());
        if (product.getCurrentStock() != null) {
            brief.put("stock", product.getCurrentStock());
        }
        if (product.getSalesCount() != null) {
            brief.put("salesCount", product.getSalesCount());
        }
        return brief;
    }

    /**
     * 规整关键词。
     *
     * @param keyword 原始关键词
     * @return 规整后的关键词，无效时返回 null
     * @author Henfon
     * @date 2026-09-22
     */
    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String text = keyword.trim().replaceAll("\\s+", " ");
        return text.length() > MAX_KEYWORD_LENGTH ? text.substring(0, MAX_KEYWORD_LENGTH) : text;
    }

    /**
     * 把模型给的排序词映射成查询白名单里的取值。
     *
     * 认不出来就不排序，而不是抛错：模型把「便宜点」译成「价格升序」是常态，译偏了也不该
     * 让整轮回答失败，按综合推荐给一批候选，模型自己会挑。
     *
     * @param sortBy 排序词
     * @return 排序取值，无法识别时返回 null
     * @author Henfon
     * @date 2026-09-22
     */
    private String resolveSort(String sortBy) {
        if (!StringUtils.hasText(sortBy)) {
            return null;
        }
        String text = sortBy.trim();
        if (SORT_ALIASES.containsKey(text)) {
            return SORT_ALIASES.get(text);
        }
        return switch (text) {
            case "featured", "sales", "price-asc", "price-desc", "newest" -> text;
            default -> null;
        };
    }

    private String amount(BigDecimal value) {
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }
}
