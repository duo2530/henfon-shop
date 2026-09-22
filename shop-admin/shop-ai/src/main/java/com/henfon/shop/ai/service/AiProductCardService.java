package com.henfon.shop.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.ai.dto.AiProductCard;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.service.CatalogPortalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品卡片装配。
 *
 * 客服消息里的商品卡片有两个来源：智能客服检索到的候选，和人工客服在工作台挑的商品。
 * 两条链路都要「按主键取出商品、刷新封面地址、组装成卡片」，逻辑一样，所以收在这里，
 * 而不是各写一份——两边都改的时候总有一边漏掉封面刷新，买家看到的就是一张裂图。
 *
 * 封面地址必须走 {@link CatalogPortalService} 的查询结果，不能直接读商品表的 mainImageUrl：
 * 库里存的是对象键或过期的预签名地址，门户拿到相对路径会解析成本站地址而 404。这里通过
 * 门户列表查询按主键取回，顺带拿到已换发的可访问地址。
 *
 * 这个 Bean 不随 shop.ai.enabled 开关上下线：人工客服链路本来就不依赖模型，而它只查商品表，
 * 没有 AI 依赖。挂上开关会让「AI 未启用」的环境里人工客服也起不来——那是最不该出现的退化。
 *
 * @author Henfon
 * @date 2026-09-22
 */
@Service
public class AiProductCardService {

    private static final Logger log = LoggerFactory.getLogger(AiProductCardService.class);

    /** 一次最多装配的卡片数。 */
    private static final int MAX_CARDS = 3;

    /** 一次能按主键查回的商品数上限，超过部分直接丢弃而不是分批查。 */
    private static final int LOOKUP_LIMIT = 20;

    private final CatalogPortalService catalogPortalService;

    /**
     * 创建卡片装配服务。
     *
     * @param catalogPortalService 门户商品查询服务
     * @author Henfon
     * @date 2026-09-22
     */
    public AiProductCardService(CatalogPortalService catalogPortalService) {
        this.catalogPortalService = catalogPortalService;
    }

    /**
     * 按商品主键装配卡片，顺序与传入一致。
     *
     * 查不到、已下架的商品静默跳过：买家那边少一张卡片，比给一张点开报错的卡片好。
     *
     * @param productIds 商品主键列表
     * @param note 统一的推荐说明，可为空
     * @return 卡片列表，无可用商品时返回空列表
     * @author Henfon
     * @date 2026-09-22
     */
    public List<AiProductCard> cards(List<Long> productIds, String note) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<Long> wanted = productIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(MAX_CARDS)
                .toList();
        if (wanted.isEmpty()) {
            return List.of();
        }
        Map<Long, CatalogProduct> found = load(wanted);
        if (found.isEmpty()) {
            return List.of();
        }
        List<AiProductCard> cards = new ArrayList<>();
        for (Long id : wanted) {
            AiProductCard card = AiProductCard.from(found.get(id), note);
            if (card != null) {
                cards.add(card);
            }
        }
        return cards;
    }

    /**
     * 按关键词装配卡片，供人工客服在工作台挑商品后直接下发。
     *
     * @param keyword 关键词
     * @param limit 需要几张卡片
     * @return 卡片列表，搜不到时返回空列表
     * @author Henfon
     * @date 2026-09-22
     */
    public List<AiProductCard> search(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        int size = Math.min(Math.max(limit, 1), MAX_CARDS);
        IPage<CatalogProduct> page = catalogPortalService.page(keyword.trim(), null, null, null,
                "featured", 1, size);
        if (page.getRecords() == null || page.getRecords().isEmpty()) {
            return List.of();
        }
        return page.getRecords().stream()
                .map(product -> AiProductCard.from(product, null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 按主键回查商品。
     *
     * @param productIds 商品主键
     * @return 主键到商品的映射，只含查到且已上架的
     * @author Henfon
     * @date 2026-09-22
     */
    private Map<Long, CatalogProduct> load(List<Long> productIds) {
        Map<Long, CatalogProduct> found = new LinkedHashMap<>();
        List<Long> ids = productIds.size() > LOOKUP_LIMIT ? productIds.subList(0, LOOKUP_LIMIT) : productIds;
        try {
            List<CatalogProduct> records = catalogPortalService.byIds(ids);
            if (records != null) {
                records.stream()
                        .filter(product -> product.getId() != null)
                        .forEach(product -> found.put(product.getId(), product));
            }
        } catch (Exception exception) {
            // 查不到不该让整条消息发不出去，卡片本来就只是附件。
            log.warn("装配商品卡片时批量读取商品 {} 失败：{}", ids, exception.getMessage());
        }
        return found;
    }

    /**
     * 金额转文本，供调用方拼推荐说明。保留两位小数与卡片口径一致。
     *
     * @param value 金额
     * @return 金额文本，为空时返回空串
     * @author Henfon
     * @date 2026-09-22
     */
    public String amountText(BigDecimal value) {
        return value == null ? "" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
