package com.henfon.shop.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.henfon.shop.catalog.entity.CatalogProduct;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 客服消息里的一张商品卡片。
 *
 * 买家问「帮我找键盘」时，只回一段商品名与价格的文字，买家还得自己去搜一遍；把封面、价格、
 * 名称一起给出来，并带上可直接打开的商品页地址，才是他要的结果。所以商品信息在消息里是
 * 结构化的一段，不是一个字符串——前端要渲染成可点的卡片。
 *
 * 卡片由服务端组装而不是让模型输出 JSON：模型只提供关键词，价格、库存、封面地址一律由后端
 * 按商品主键查库回填。让模型复述金额与图片地址，等于把「买家看到的数字是否真实」交给一个
 * 会算错加法、会拼错 URL 的组件。
 *
 * price 与 marketPrice 刻意下发成两位小数的字符串：{@code BigDecimal} 直接序列化后前端拿到的
 * 是数字，{@code 899.00} 与前端的本地演示商品（数字）拼在一起会出现两种口径，展示时又得各自
 * 补小数点。统一成字符串，前端只管原样显示。
 *
 * @param productId 商品主键
 * @param title 商品名称
 * @param imageUrl 封面图访问地址，无图时为空
 * @param price 售价文本，两位小数
 * @param marketPrice 市场价文本，两位小数，无划线价时为空
 * @param url 商品详情页地址，门户站内路由，买家点击直接打开
 * @param note 推荐说明，由模型或客服给出的一句话，可为空
 * @author Henfon
 * @date 2026-09-22
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiProductCard(Long productId,
                            String title,
                            String imageUrl,
                            String price,
                            String marketPrice,
                            String url,
                            String note) {

    /**
     * 由商品实体组装卡片。
     *
     * 只在商品已上架时才有卡片：下架商品出现在客服对话里，点开就是一个「商品不存在」的页面。
     * 调用方的查询已经带了上架条件，这里再兜一次，是为了挡住"查到之后被下架"的窗口。
     *
     * @param product 商品实体，封面地址须已换成可访问地址
     * @param note 推荐说明，可为空
     * @return 商品卡片，商品为空或未上架时返回 null
     * @author Henfon
     * @date 2026-09-22
     */
    public static AiProductCard from(CatalogProduct product, String note) {
        if (product == null || product.getId() == null) {
            return null;
        }
        if (product.getStatus() != null && product.getStatus() != 1) {
            return null;
        }
        return new AiProductCard(product.getId(),
                product.getProductName(),
                blankToNull(product.getMainImageUrl()),
                amount(product.getPrice()),
                amount(product.getMarketPrice()),
                productUrl(product.getId()),
                blankToNull(note));
    }

    /**
     * 批量组装，跳过无法成卡的条目。
     *
     * @param products 商品列表
     * @param note 统一的推荐说明，可为空
     * @return 卡片列表，没有可用商品时返回空列表
     * @author Henfon
     * @date 2026-09-22
     */
    public static List<AiProductCard> from(List<CatalogProduct> products, String note) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        return products.stream()
                .map(product -> from(product, note))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 生成商品详情页地址。
     *
     * 门户没有路由库，整页视图靠 {@code location.hash} 标识，商品页的写法是
     * {@code #/product/prod-<id>}。地址由服务端拼好下发，而不是把 ID 交给前端各自拼一遍：
     * 路由格式改一次就会有漏改的地方，而下发完整地址只改这一处。
     *
     * @param productId 商品主键
     * @return 门户站内地址
     * @author Henfon
     * @date 2026-09-22
     */
    public static String productUrl(Long productId) {
        return productId == null ? null : "#/product/prod-" + productId;
    }

    /**
     * 金额转两位小数的字符串。
     *
     * @param value 金额
     * @return 金额文本，为空时返回 null
     * @author Henfon
     * @date 2026-09-22
     */
    private static String amount(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
