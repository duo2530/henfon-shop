package com.henfon.shop.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.content.entity.ContentBanner;
import com.henfon.shop.content.entity.ContentReview;
import com.henfon.shop.content.mapper.ContentBannerMapper;
import com.henfon.shop.content.mapper.ContentReviewMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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

    /**
     * 创建门户内容服务。
     *
     * @param bannerMapper Banner 数据访问对象
     * @param reviewMapper 评价数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public ContentPortalService(ContentBannerMapper bannerMapper, ContentReviewMapper reviewMapper) {
        this.bannerMapper = bannerMapper;
        this.reviewMapper = reviewMapper;
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
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return reviewMapper.selectList(new LambdaQueryWrapper<ContentReview>()
                .eq(ContentReview::getProductId, productId)
                .eq(ContentReview::getStatus, 1)
                .orderByDesc(ContentReview::getReviewedAt)
                .last("LIMIT " + safeLimit));
    }
}
