package com.henfon.shop.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.content.dto.ContentReviewSubmitRequest;
import com.henfon.shop.content.entity.ContentBanner;
import com.henfon.shop.content.entity.ContentReview;
import com.henfon.shop.content.mapper.ContentBannerMapper;
import com.henfon.shop.content.mapper.ContentReviewMapper;
import com.henfon.shop.trade.service.TradeOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 门户内容查询服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class ContentPortalService {
    private final ContentBannerMapper bannerMapper;
    private final ContentReviewMapper reviewMapper;
    private final TradeOrderService tradeOrderService;

    /**
     * 创建门户内容服务。
     *
     * @param bannerMapper Banner 数据访问对象
     * @param reviewMapper 评价数据访问对象
     * @param tradeOrderService 订单应用服务
     * @author Henfon
     * @date 2026-08-29
     */
    public ContentPortalService(ContentBannerMapper bannerMapper, ContentReviewMapper reviewMapper,
                                TradeOrderService tradeOrderService) {
        this.bannerMapper = bannerMapper;
        this.reviewMapper = reviewMapper;
        this.tradeOrderService = tradeOrderService;
    }

    /**
     * 查询当前有效 Banner。
     *
     * @return Banner 列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<ContentBanner> banners() {
        LocalDateTime now = LocalDateTime.now();
        return bannerMapper.selectList(new LambdaQueryWrapper<ContentBanner>()
                .eq(ContentBanner::getStatus, 1)
                .and(q -> q.isNull(ContentBanner::getStartAt).or().le(ContentBanner::getStartAt, now))
                .and(q -> q.isNull(ContentBanner::getEndAt).or().ge(ContentBanner::getEndAt, now))
                .orderByAsc(ContentBanner::getSortNo));
    }

    /**
     * 查询商品展示评价。
     *
     * @param productId 商品ID
     * @param limit 返回条数上限
     * @return 评价列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<ContentReview> reviews(Long productId, int limit) {
        if (productId == null) {
            throw new BusinessException("CONTENT_REVIEW_PRODUCT_REQUIRED", "商品ID不能为空");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        // 门户评价必须绑定具体商品，避免空商品参数被 ORM 忽略后误查全量评价。
        List<ContentReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<ContentReview>()
                .eq(ContentReview::getProductId, productId)
                .eq(ContentReview::getStatus, 1)
                .orderByDesc(ContentReview::getReviewedAt)
                .last("LIMIT " + safeLimit));
        // 统一将数据访问层的 null 结果转换为空列表，简化控制器和调用方处理。
        return reviews == null ? java.util.Collections.emptyList() : reviews;
    }

    /**
     * 分页查询门户可展示评价。
     *
     * @param productId 商品ID
     * @param current 当前页
     * @param size 页大小
     * @return 评价分页数据
     * @author Henfon
     * @date 2026-08-30
     */
    public IPage<ContentReview> reviewPage(Long productId, long current, long size) {
        if (productId == null) {
            throw new BusinessException("CONTENT_REVIEW_PRODUCT_REQUIRED", "商品ID不能为空");
        }
        // 门户只返回审核通过的评价，并限制单页大小避免评价内容接口被滥用。
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        return reviewMapper.selectPage(new Page<>(safeCurrent, safeSize), new LambdaQueryWrapper<ContentReview>()
                .eq(ContentReview::getProductId, productId)
                .eq(ContentReview::getStatus, 1)
                .orderByDesc(ContentReview::getReviewedAt)
                .orderByDesc(ContentReview::getCreatedAt));
    }

    /**
     * 保存门户会员评价，初始状态为待审核。
     *
     * @param productId 商品ID
     * @param memberId 会员ID
     * @param memberName 会员名称快照
     * @param request 评价请求
     * @return 新评价ID
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public Long submitReview(Long productId, Long memberId, String memberName, ContentReviewSubmitRequest request) {
        if (productId == null || memberId == null) {
            throw new BusinessException("CONTENT_REVIEW_ARGUMENT_INVALID", "商品和会员信息不能为空");
        }
        if (!tradeOrderService.hasPurchasedProduct(memberId, productId)) {
            throw new BusinessException("CONTENT_REVIEW_PURCHASE_REQUIRED", "购买过该商品后才能评价");
        }
        ContentReview review = new ContentReview();
        review.setProductId(productId);
        review.setMemberId(memberId);
        review.setMemberName(StringUtils.hasText(memberName) ? memberName.trim() : "会员");
        review.setRating(request.rating());
        review.setReviewContent(request.reviewContent().trim());
        review.setVariantSummary(StringUtils.hasText(request.variantSummary()) ? request.variantSummary().trim() : null);
        review.setImageUrls(serializeImageUrls(request.imageUrls()));
        review.setHelpfulCount(0);
        review.setStatus(0);
        reviewMapper.insert(review);
        return review.getId();
    }

    /**
     * 将评价图片地址安全序列化为 JSON 数组字符串。
     *
     * @param imageUrls 图片地址列表
     * @return JSON 数组字符串
     * @author Henfon
     * @date 2026-09-01
     */
    private String serializeImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return null;
        }
        return imageUrls.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(url -> "\"" + url.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
