package com.henfon.shop.content.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.content.entity.ContentReview;
import com.henfon.shop.content.dto.ContentReviewFollowupRequest;
import com.henfon.shop.content.dto.ContentReviewSubmitRequest;
import com.henfon.shop.content.mapper.ContentBannerMapper;
import com.henfon.shop.content.mapper.ContentReviewMapper;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.trade.service.TradeOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * 门户内容服务单元测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@ExtendWith(MockitoExtension.class)
class ContentPortalServiceTest {

    @Mock
    private ContentBannerMapper bannerMapper;

    @Mock
    private ContentReviewMapper reviewMapper;

    @Mock
    private TradeOrderService tradeOrderService;

    @Mock
    private ContentImageUrlResolver imageUrlResolver;

    @Mock
    private MemberUserMapper memberUserMapper;

    /**
     * 创建被测服务，统一注入图片地址解析器替身。
     *
     * @return 门户内容服务
     * @author Henfon
     * @date 2026-09-17
     */
    private ContentPortalService newService() {
        return new ContentPortalService(bannerMapper, reviewMapper, tradeOrderService, imageUrlResolver,
                memberUserMapper);
    }

    /**
     * 校验评价查询必须提供商品ID，避免空参数查询全量评价。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectReviewQueryWithoutProductId() {
        ContentPortalService service = newService();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reviews(null, 20));

        assertEquals("CONTENT_REVIEW_PRODUCT_REQUIRED", exception.getCode());
        verifyNoInteractions(reviewMapper);
    }

    /**
     * 校验评价数据访问层返回空值时门户统一返回空集合。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldNormalizeNullReviewResultToEmptyList() {
        ContentPortalService service = newService();
        when(reviewMapper.selectList(any())).thenReturn(null);

        var result = service.reviews(100L, 0);

        assertEquals(Collections.emptyList(), result);
    }

    /**
     * 验证会员可为本人已审核评价提交一次追评。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldSubmitReviewFollowupOnce() {
        ContentReview review = new ContentReview();
        review.setId(9L);
        review.setMemberId(7L);
        review.setStatus(1);
        when(reviewMapper.selectOne(any())).thenReturn(review);
        when(reviewMapper.updateById(any(ContentReview.class))).thenReturn(1);
        ContentPortalService service = newService();

        ContentReview result = service.followup(9L, 7L, new ContentReviewFollowupRequest("使用一周后体验很好"));

        assertEquals("使用一周后体验很好", result.getFollowupContent());
        verify(reviewMapper).updateById(review);
    }

    /**
     * 验证已存在追评时拒绝重复提交。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectDuplicateFollowup() {
        ContentReview review = new ContentReview();
        review.setId(9L);
        review.setMemberId(7L);
        review.setStatus(1);
        review.setFollowupContent("已有追评");
        when(reviewMapper.selectOne(any())).thenReturn(review);
        ContentPortalService service = newService();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.followup(9L, 7L, new ContentReviewFollowupRequest("再次提交")));

        assertEquals("CONTENT_REVIEW_FOLLOWUP_EXISTS", exception.getCode());
    }

    /**
     * 校验门户评价列表返回的晒单图片已重签为当前有效地址。
     *
     * @author Henfon
     * @date 2026-09-17
     */
    @Test
    void shouldResignReviewImagesWhenListing() {
        ContentReview review = new ContentReview();
        review.setId(5L);
        review.setImageUrls("[\"media/a.png\"]");
        when(reviewMapper.selectList(any())).thenReturn(List.of(review));
        when(imageUrlResolver.resignJsonArray("[\"media/a.png\"]")).thenReturn("[\"https://signed/a.png\"]");

        List<ContentReview> result = newService().reviews(100L, 20);

        assertEquals("[\"https://signed/a.png\"]", result.get(0).getImageUrls());
    }

    /**
     * 校验会员提交评价时把预签名地址归一化为对象键后入库。
     *
     * @author Henfon
     * @date 2026-09-17
     */
    @Test
    void shouldNormalizeReviewImagesWhenSubmitting() {
        String signedUrl = "https://minio.local/henfon-shop/media/a.png?X-Amz-Signature=abc";
        when(tradeOrderService.hasPurchasedProduct(7L, 100L)).thenReturn(true);
        when(imageUrlResolver.normalizeJsonArray(List.of(signedUrl))).thenReturn("[\"media/a.png\"]");
        when(reviewMapper.insert(any(ContentReview.class))).thenAnswer(invocation -> {
            ContentReview inserted = invocation.getArgument(0);
            inserted.setId(31L);
            return 1;
        });

        Long id = newService().submitReview(100L, 7L, "会员",
                new ContentReviewSubmitRequest(5, "很好用", null, List.of(signedUrl)));

        assertEquals(31L, id);
        ArgumentCaptor<ContentReview> captor = ArgumentCaptor.forClass(ContentReview.class);
        verify(reviewMapper).insert(captor.capture());
        assertEquals("[\"media/a.png\"]", captor.getValue().getImageUrls());
    }

    /**
     * 校验同一会员对同一商品重复提交评价时直接拒绝。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldRejectDuplicateReview() {
        when(tradeOrderService.hasPurchasedProduct(7L, 100L)).thenReturn(true);
        when(reviewMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> newService().submitReview(100L, 7L, "会员",
                        new ContentReviewSubmitRequest(5, "再来一条", null, null)));

        assertEquals("CONTENT_REVIEW_ALREADY_EXISTS", exception.getCode());
        verify(reviewMapper, org.mockito.Mockito.never()).insert(any(ContentReview.class));
    }

    /**
     * 校验提交评价时快照会员当前头像并归一化为稳定引用。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldSnapshotMemberAvatarWhenSubmitting() {
        MemberUser member = new MemberUser();
        member.setId(7L);
        member.setAvatarUrl("media/avatar/7.png");
        when(tradeOrderService.hasPurchasedProduct(7L, 100L)).thenReturn(true);
        when(memberUserMapper.selectById(7L)).thenReturn(member);
        when(imageUrlResolver.normalizeReference("media/avatar/7.png")).thenReturn("media/avatar/7.png");
        when(reviewMapper.insert(any(ContentReview.class))).thenAnswer(invocation -> {
            ContentReview inserted = invocation.getArgument(0);
            inserted.setId(32L);
            return 1;
        });

        newService().submitReview(100L, 7L, "会员",
                new ContentReviewSubmitRequest(5, "很好用", null, null));

        ArgumentCaptor<ContentReview> captor = ArgumentCaptor.forClass(ContentReview.class);
        verify(reviewMapper).insert(captor.capture());
        assertEquals("media/avatar/7.png", captor.getValue().getMemberAvatarUrl());
    }
}
