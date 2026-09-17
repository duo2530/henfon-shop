package com.henfon.shop.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.content.dto.ContentReviewSubmitRequest;
import com.henfon.shop.content.dto.ContentReviewFollowupRequest;
import com.henfon.shop.content.entity.ContentBanner;
import com.henfon.shop.content.entity.ContentReview;
import com.henfon.shop.content.mapper.ContentBannerMapper;
import com.henfon.shop.content.mapper.ContentReviewMapper;
import com.henfon.shop.trade.service.TradeOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

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
    private final ContentImageUrlResolver imageUrlResolver;

    /**
     * 创建门户内容服务。
     *
     * @param bannerMapper Banner 数据访问对象
     * @param reviewMapper 评价数据访问对象
     * @param tradeOrderService 订单应用服务
     * @param imageUrlResolver 图片地址解析器
     * @author Henfon
     * @date 2026-08-29
     */
    public ContentPortalService(ContentBannerMapper bannerMapper, ContentReviewMapper reviewMapper,
                                TradeOrderService tradeOrderService, ContentImageUrlResolver imageUrlResolver) {
        this.bannerMapper = bannerMapper;
        this.reviewMapper = reviewMapper;
        this.tradeOrderService = tradeOrderService;
        this.imageUrlResolver = imageUrlResolver;
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
        List<ContentBanner> banners = bannerMapper.selectList(new LambdaQueryWrapper<ContentBanner>()
                .eq(ContentBanner::getStatus, 1)
                .and(q -> q.isNull(ContentBanner::getStartAt).or().le(ContentBanner::getStartAt, now))
                .and(q -> q.isNull(ContentBanner::getEndAt).or().ge(ContentBanner::getEndAt, now))
                .orderByAsc(ContentBanner::getSortNo));
        if (banners == null) {
            return Collections.emptyList();
        }
        // 门户直接拿 imageUrl 渲染，这里把持久化的对象键换成当前有效的访问地址。
        banners.forEach(banner -> {
            if (banner != null) {
                banner.setImageUrl(imageUrlResolver.accessUrl(banner.getImageUrl()));
            }
        });
        return banners;
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
        if (reviews == null) {
            return Collections.emptyList();
        }
        reviews.forEach(this::resignReviewImages);
        return reviews;
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
        IPage<ContentReview> result = reviewMapper.selectPage(new Page<>(safeCurrent, safeSize),
                new LambdaQueryWrapper<ContentReview>()
                        .eq(ContentReview::getProductId, productId)
                        .eq(ContentReview::getStatus, 1)
                        .orderByDesc(ContentReview::getReviewedAt)
                        .orderByDesc(ContentReview::getCreatedAt));
        if (result != null && result.getRecords() != null) {
            result.getRecords().forEach(this::resignReviewImages);
        }
        return result;
    }

    /**
     * 将评价晒单图片重签为当前有效的访问地址。
     *
     * <p>评价是长期留档内容，存储里放的是对象键，展示时必须按当前配置重新签名，
     * 否则 24 小时后晒单图全部失效。</p>
     *
     * @param review 评价实体
     * @author Henfon
     * @date 2026-09-17
     */
    private void resignReviewImages(ContentReview review) {
        if (review != null && StringUtils.hasText(review.getImageUrls())) {
            review.setImageUrls(imageUrlResolver.resignJsonArray(review.getImageUrls()));
        }
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
        // 会员上传拿到的是预签名地址，入库前归一化为对象键，展示时再重签。
        review.setImageUrls(imageUrlResolver.normalizeJsonArray(request.imageUrls()));
        review.setHelpfulCount(0);
        review.setStatus(0);
        reviewMapper.insert(review);
        return review.getId();
    }

    /**
     * 提交已审核评价的会员追评。
     *
     * @param reviewId 评价ID
     * @param memberId 当前会员ID
     * @param request 追评请求
     * @return 追评后的评价
     * @author Henfon
     * @date 2026-09-04
     */
    @Transactional
    public ContentReview followup(Long reviewId, Long memberId, ContentReviewFollowupRequest request) {
        if (reviewId == null || memberId == null || request == null || !StringUtils.hasText(request.content())) {
            throw new BusinessException("CONTENT_REVIEW_FOLLOWUP_INVALID", "追评信息不能为空");
        }
        ContentReview review = reviewMapper.selectOne(new LambdaQueryWrapper<ContentReview>()
                .eq(ContentReview::getId, reviewId)
                .eq(ContentReview::getMemberId, memberId)
                .last("LIMIT 1 FOR UPDATE"));
        if (review == null) {
            throw new BusinessException("CONTENT_REVIEW_NOT_FOUND", "评价不存在或无权操作");
        }
        if (!Integer.valueOf(1).equals(review.getStatus())) {
            throw new BusinessException("CONTENT_REVIEW_FOLLOWUP_FORBIDDEN", "仅审核通过的评价可追评");
        }
        if (StringUtils.hasText(review.getFollowupContent())) {
            throw new BusinessException("CONTENT_REVIEW_FOLLOWUP_EXISTS", "该评价已提交过追评");
        }
        review.setFollowupContent(request.content().trim());
        review.setFollowupAt(LocalDateTime.now());
        if (reviewMapper.updateById(review) == 0) {
            throw new BusinessException("CONTENT_REVIEW_CONCURRENT_UPDATE", "评价已被其他操作修改，请刷新后重试");
        }
        return review;
    }

}
