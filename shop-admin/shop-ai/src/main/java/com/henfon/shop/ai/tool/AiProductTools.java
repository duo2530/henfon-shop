package com.henfon.shop.ai.tool;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.ai.dto.AiProductCard;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.service.CatalogPortalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
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
 * 卡片在服务端组装（{@link AiProductCard}），模型只提供关键词与候选数量。检索到的商品主键
 * 存在本轮结果状态里，回答结束时统一取卡片下发；模型自己输出一遍价格与图片地址是不可接受的，
 * 它会算错、会拼错地址，而卡片上的数字买家会直接拿去对账。
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

    private static final int MAX_LIMIT = 8;

    /** 卡片上能给买家看的候选数量上限。再多就把聊天窗口铺满了。 */
    private static final int MAX_CARDS = 3;

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
     * @param keyword 商品关键词，由买家提供
     * @param sortBy 排序方式，可为空
     * @param context 工具上下文，本轮检索状态由此拿取
     * @return 候选商品摘要
     * @author Henfon
     * @date 2026-09-22
     */
    @Tool(description = "按关键词搜索商城在售商品。买家问「有没有 XX」「帮我找 XX」「推荐几款 XX」时调用。"
            + "keyword 用买家说的商品词（如「机械键盘」「蓝牙耳机」），不要自己展开成长句，也不要编造商品名。"
            + "sortBy 可选，只能填：销量、价格升序、价格降序、最新，不填按综合推荐。"
            + "返回的每个候选都带商品编号，回答时请结合编号与名称介绍，商品价格与库存以下面返回的为准，不要自己估算。")
    public Map<String, Object> searchProducts(String keyword, String sortBy, ToolContext context) {
        ProductSearchState state = ProductSearchState.current();
        Map<String, Object> result = new LinkedHashMap<>();
        String text = normalizeKeyword(keyword);
        if (text == null) {
            result.put("note", "没有拿到有效的商品关键词，请向买家确认他想找什么商品。");
            return result;
        }
        int limit = state == null ? DEFAULT_LIMIT : state.limit();
        IPage<CatalogProduct> page = catalogPortalService.page(text, null, null, null,
                resolveSort(sortBy), 1, Math.min(limit, MAX_LIMIT));
        List<CatalogProduct> products = page.getRecords() == null ? List.of() : page.getRecords();
        result.put("products", products.stream().map(this::toBrief).toList());
        if (products.isEmpty()) {
            // 明确告诉模型"没找到"，它才会如实说没找到；留空它倾向于自行编排一个不存在的商品。
            result.put("note", "商城里没有搜到匹配的在售商品。请如实告诉买家没找到，可以提示他换个更常见的说法，"
                    + "不要推荐搜索结果以外的商品，也不要编造商品名或价格。");
            return result;
        }
        if (state != null) {
            state.collect(products.stream().map(CatalogProduct::getId).toList());
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

    /**
     * 本轮检索结果，挂在工具上下文里供编排层取用。
     *
     * 卡片必须来自真实检索结果，所以把命中的商品主键按出现顺序记下来，回答结束后由编排层
     * 回查并组装。只记主键不记整条商品：商品可能在这一轮对话期间被改价或下架，结束前回查
     * 拿到的才是买家点开时真正会看到的那一份。
     *
     * 用 ThreadLocal 而不是往 ToolContext 里塞可变对象：工具上下文是模型可见参数的载体，
     * 往里放状态容易被框架序列化或复制，而检索与收尾在同一个请求线程上，ThreadLocal 更直接。
     * 用完必须 {@link #finish()}，否则线程复用时会把上一个买家的候选带进下一个会话。
     *
     * @author Henfon
     * @date 2026-09-22
     */
    public static final class ProductSearchState {

        private static final ThreadLocal<ProductSearchState> CURRENT = new ThreadLocal<>();

        /** 命中的商品主键，按检索顺序去重。 */
        private final List<Long> productIds = new ArrayList<>();

        private final int limit;

        private ProductSearchState(int limit) {
            this.limit = limit;
        }

        /**
         * 开始一轮商品检索状态。
         *
         * @param limit 本次检索的候选数量上限
         * @author Henfon
         * @date 2026-09-22
         */
        public static void begin(int limit) {
            CURRENT.set(new ProductSearchState(limit));
        }

        /**
         * 收尾并清空。
         *
         * @return 命中的商品主键，无检索时返回空列表
         * @author Henfon
         * @date 2026-09-22
         */
        public static List<Long> finish() {
            ProductSearchState state = CURRENT.get();
            CURRENT.remove();
            if (state == null || state.productIds.isEmpty()) {
                return List.of();
            }
            // 卡片按最靠前的几个商品出，顺序保持检索结果的优劣次序。
            List<Long> ids = state.productIds.stream().distinct().toList();
            return ids.size() <= MAX_CARDS ? ids : ids.subList(0, MAX_CARDS);
        }

        /**
         * 丢弃本轮状态，不清也算得到候选。
         *
         * 异常收尾时用：这一轮没有下发给买家任何卡片，留着键只会污染同线程的下一次请求。
         *
         * @author Henfon
         * @date 2026-09-22
         */
        public static void discard() {
            CURRENT.remove();
        }

        /**
         * 当前状态。
         *
         * @return 状态，未开启一轮检索时返回 null
         * @author Henfon
         * @date 2026-09-22
         */
        private static ProductSearchState current() {
            return CURRENT.get();
        }

        private int limit() {
            return limit > 0 ? limit : DEFAULT_LIMIT;
        }

        private void collect(List<Long> ids) {
            if (ids == null) {
                return;
            }
            ids.stream().filter(java.util.Objects::nonNull).forEach(productIds::add);
        }
    }
}
