package com.henfon.shop.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.content.dto.ContentReviewReplyRequest;
import com.henfon.shop.content.entity.ContentReview;
import com.henfon.shop.content.mapper.ContentReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 商品评价后台审核应用服务，负责审核、隐藏和商家回复。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class ContentReviewAdminService {

    private static final int HIDDEN = 0;
    private static final int VISIBLE = 1;

    private final ContentReviewMapper reviewMapper;

    /**
     * 创建评价后台审核服务。
     *
     * @param reviewMapper 评价数据访问对象
     * @author Henfon
     * @date 2026-08-30
     */
    public ContentReviewAdminService(ContentReviewMapper reviewMapper) {
        this.reviewMapper = reviewMapper;
    }

    /**
     * 分页查询后台评价。
     *
     * @param productId 商品ID，可选
     * @param status 展示状态，可选
     * @param keyword 评价内容或会员名称关键字
     * @param current 当前页
     * @param size 页大小
     * @return 评价分页数据
     * @author Henfon
     * @date 2026-08-30
     */
    public IPage<ContentReview> page(Long productId, Integer status, String keyword, long current, long size) {
        // 统一限制分页参数，避免后台审核页面一次性加载过多评价内容。
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        LambdaQueryWrapper<ContentReview> wrapper = new LambdaQueryWrapper<ContentReview>()
                .eq(productId != null, ContentReview::getProductId, productId)
                .eq(status != null, ContentReview::getStatus, status)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(ContentReview::getMemberName, keyword)
                        .or().like(ContentReview::getReviewContent, keyword))
                .orderByDesc(ContentReview::getCreatedAt);
        return reviewMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
    }

    /**
     * 审核或隐藏评价。
     *
     * @param id 评价ID
     * @param status 目标状态，1展示、0隐藏
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != VISIBLE && status != HIDDEN)) {
            throw new BusinessException("CONTENT_REVIEW_STATUS_INVALID", "评价状态必须为0或1");
        }
        ContentReview review = getRequired(id);
        review.setStatus(status);
        if (status == VISIBLE) {
            // 审核通过时记录审核时间，门户只展示状态为1的评价。
            review.setReviewedAt(LocalDateTime.now());
        }
        if (reviewMapper.updateById(review) == 0) {
            throw new BusinessException("CONTENT_REVIEW_CONCURRENT_UPDATE", "评价已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 保存商家回复。
     *
     * @param id 评价ID
     * @param request 回复请求
     * @param repliedBy 回复管理员名称
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void reply(Long id, ContentReviewReplyRequest request, String repliedBy) {
        ContentReview review = getRequired(id);
        String reply = request.replyContent().trim();
        if (!StringUtils.hasText(reply)) {
            throw new BusinessException("CONTENT_REVIEW_REPLY_INVALID", "回复内容不能为空");
        }
        review.setReplyContent(reply);
        review.setRepliedAt(LocalDateTime.now());
        review.setRepliedBy(StringUtils.hasText(repliedBy) ? repliedBy.trim() : "管理员");
        if (reviewMapper.updateById(review) == 0) {
            throw new BusinessException("CONTENT_REVIEW_CONCURRENT_UPDATE", "评价已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 查询指定评价，不存在时抛出统一业务异常。
     *
     * @param id 评价ID
     * @return 评价实体
     * @author Henfon
     * @date 2026-08-30
     */
    private ContentReview getRequired(Long id) {
        if (id == null) {
            throw new BusinessException("CONTENT_REVIEW_NOT_FOUND", "评价不存在");
        }
        ContentReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException("CONTENT_REVIEW_NOT_FOUND", "评价不存在");
        }
        return review;
    }
}
