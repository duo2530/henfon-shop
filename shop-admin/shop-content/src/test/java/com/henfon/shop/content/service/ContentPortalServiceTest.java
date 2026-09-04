package com.henfon.shop.content.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.content.entity.ContentReview;
import com.henfon.shop.content.mapper.ContentBannerMapper;
import com.henfon.shop.content.mapper.ContentReviewMapper;
import com.henfon.shop.trade.service.TradeOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    /**
     * 校验评价查询必须提供商品ID，避免空参数查询全量评价。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectReviewQueryWithoutProductId() {
        ContentPortalService service = new ContentPortalService(bannerMapper, reviewMapper, tradeOrderService);

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
        ContentPortalService service = new ContentPortalService(bannerMapper, reviewMapper, tradeOrderService);
        when(reviewMapper.selectList(any())).thenReturn(null);

        var result = service.reviews(100L, 0);

        assertEquals(Collections.emptyList(), result);
    }
}
