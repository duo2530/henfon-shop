package com.henfon.shop.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.content.dto.ContentReviewSubmitRequest;
import com.henfon.shop.content.dto.ContentReviewFollowupRequest;
import com.henfon.shop.content.dto.ContentReviewSummaryItem;
import com.henfon.shop.content.entity.ContentBanner;
import com.henfon.shop.content.entity.ContentReview;
import com.henfon.shop.content.mapper.ContentBannerMapper;
import com.henfon.shop.content.mapper.ContentReviewMapper;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.trade.service.TradeOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private final MemberUserMapper memberUserMapper;

    /** 单次评价统计允许的商品数量上限。 */
    private static final int MAX_SUMMARY_PRODUCT_IDS = 100;

    /**
     * 创建门户内容服务。
     *
     * @param bannerMapper Banner 数据访问对象
     * @param reviewMapper 评价数据访问对象
     * @param tradeOrderService 订单应用服务
     * @param imageUrlResolver 图片地址解析器
     * @param memberUserMapper 会员数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public ContentPortalService(ContentBannerMapper bannerMapper, ContentReviewMapper reviewMapper,
                                TradeOrderService tradeOrderService, ContentImageUrlResolver imageUrlResolver,
                                MemberUserMapper memberUserMapper) {
        this.bannerMapper = bannerMapper;
        this.reviewMapper = reviewMapper;
        this.tradeOrderService = tradeOrderService;
        this.imageUrlResolver = imageUrlResolver;
        this.memberUserMapper = memberUserMapper;
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
        fillMemberAvatars(reviews);
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
            fillMemberAvatars(result.getRecords());
        }
        return result;
    }

    /**
     * 按商品批量统计审核通过的评价条数与平均分。
     *
     * <p>门户商品列表一次展示多款商品，逐款查询评价会让列表接口出现 N+1 请求，
     * 这里一次聚合返回；只统计 status=1 的评价，与门户展示口径保持一致。</p>
     *
     * @param productIds 商品ID集合
     * @return 评价统计项，未命中商品不会出现在结果中
     * @author Henfon
     * @date 2026-09-18
     */
    public List<ContentReviewSummaryItem> reviewSummary(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 去重、剔除非法 ID 并限制批量大小，避免把整表聚合交给数据库。
        List<Long> safeIds = productIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(MAX_SUMMARY_PRODUCT_IDS)
                .toList();
        if (safeIds.isEmpty()) {
            return Collections.emptyList();
        }
        QueryWrapper<ContentReview> wrapper = new QueryWrapper<>();
        wrapper.select("product_id", "COUNT(*) AS review_count", "ROUND(AVG(rating), 1) AS avg_rating")
                .eq("status", 1)
                .in("product_id", safeIds)
                .groupBy("product_id");
        List<Map<String, Object>> rows = reviewMapper.selectMaps(wrapper);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<ContentReviewSummaryItem> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Long productId = readLongColumn(row, "product_id");
            if (productId == null) {
                continue;
            }
            Long count = readLongColumn(row, "review_count");
            items.add(new ContentReviewSummaryItem(productId, count == null ? 0L : count,
                    readDoubleColumn(row, "avg_rating")));
        }
        return items;
    }

    /**
     * 分页查询当前会员自己提交的评价。
     *
     * <p>与门户展示口径不同：这里包含待审核、已隐藏的评价，会员需要能看到自己提交内容的处理状态。</p>
     *
     * @param memberId 会员ID
     * @param current 当前页
     * @param size 页大小
     * @return 评价分页数据
     * @author Henfon
     * @date 2026-09-18
     */
    public IPage<ContentReview> myReviews(Long memberId, long current, long size) {
        if (memberId == null) {
            throw new BusinessException("CONTENT_REVIEW_MEMBER_REQUIRED", "会员信息不能为空");
        }
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        IPage<ContentReview> result = reviewMapper.selectPage(new Page<>(safeCurrent, safeSize),
                new LambdaQueryWrapper<ContentReview>()
                        .eq(ContentReview::getMemberId, memberId)
                        .orderByDesc(ContentReview::getCreatedAt)
                        .orderByDesc(ContentReview::getId));
        if (result != null && result.getRecords() != null) {
            result.getRecords().forEach(this::resignReviewImages);
            fillMemberAvatars(result.getRecords());
        }
        return result;
    }

    /**
     * 从聚合结果中按列名取值。
     *
     * <p>不同驱动返回的列名大小写、下划线风格并不一致，这里归一化后再匹配，避免线上取不到值。</p>
     *
     * @param row 聚合结果行
     * @param column 列名
     * @return 列值，缺失时返回 null
     * @author Henfon
     * @date 2026-09-18
     */
    private Object readColumn(Map<String, Object> row, String column) {
        if (row == null || column == null) {
            return null;
        }
        String expected = column.replace("_", "").toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.replace("_", "").toLowerCase(Locale.ROOT).equals(expected)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 读取聚合结果中的整型列。
     *
     * @param row 聚合结果行
     * @param column 列名
     * @return 列值，缺失或不可解析时返回 null
     * @author Henfon
     * @date 2026-09-18
     */
    private Long readLongColumn(Map<String, Object> row, String column) {
        Object value = readColumn(row, column);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 读取聚合结果中的浮点列。
     *
     * @param row 聚合结果行
     * @param column 列名
     * @return 列值，缺失或不可解析时返回 0
     * @author Henfon
     * @date 2026-09-18
     */
    private double readDoubleColumn(Map<String, Object> row, String column) {
        Object value = readColumn(row, column);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0d;
        }
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return 0d;
        }
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
     * 补齐并重签评价中的会员头像。
     *
     * <p>历史评价的 member_avatar_url 可能为空，这里按会员ID批量回查当前头像兜底，
     * 有值的引用统一重签成当前有效的访问地址，外链原样透传。</p>
     *
     * @param reviews 评价列表
     * @author Henfon
     * @date 2026-09-18
     */
    private void fillMemberAvatars(List<ContentReview> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return;
        }
        List<Long> missingMemberIds = reviews.stream()
                .filter(review -> review != null && review.getMemberId() != null
                        && !StringUtils.hasText(review.getMemberAvatarUrl()))
                .map(ContentReview::getMemberId)
                .distinct()
                .toList();
        Map<Long, String> avatarById = new HashMap<>();
        if (!missingMemberIds.isEmpty()) {
            List<MemberUser> members = memberUserMapper.selectBatchIds(missingMemberIds);
            if (members != null) {
                members.stream()
                        .filter(member -> member != null && StringUtils.hasText(member.getAvatarUrl()))
                        .forEach(member -> avatarById.put(member.getId(), member.getAvatarUrl()));
            }
        }
        for (ContentReview review : reviews) {
            if (review == null) {
                continue;
            }
            if (!StringUtils.hasText(review.getMemberAvatarUrl()) && review.getMemberId() != null) {
                review.setMemberAvatarUrl(avatarById.get(review.getMemberId()));
            }
            if (StringUtils.hasText(review.getMemberAvatarUrl())) {
                review.setMemberAvatarUrl(imageUrlResolver.accessUrl(review.getMemberAvatarUrl()));
            }
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
        // 同一会员对同一商品只允许一条评价，重复提交直接拒绝，想补充内容走追评。
        Long existing = reviewMapper.selectCount(new LambdaQueryWrapper<ContentReview>()
                .eq(ContentReview::getMemberId, memberId)
                .eq(ContentReview::getProductId, productId));
        if (existing != null && existing > 0) {
            throw new BusinessException("CONTENT_REVIEW_ALREADY_EXISTS", "您已评价过该商品，可在个人中心查看或追评");
        }
        ContentReview review = new ContentReview();
        review.setProductId(productId);
        review.setMemberId(memberId);
        review.setMemberName(StringUtils.hasText(memberName) ? memberName.trim() : "会员");
        // 快照会员当前头像，与会员名一样定格在评价时刻，避免之后换头像连带改动历史评价。
        MemberUser member = memberUserMapper.selectById(memberId);
        if (member != null && StringUtils.hasText(member.getAvatarUrl())) {
            review.setMemberAvatarUrl(imageUrlResolver.normalizeReference(member.getAvatarUrl()));
        }
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
