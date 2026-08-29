package com.henfon.shop.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.content.entity.ContentReview;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品评价数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Mapper
public interface ContentReviewMapper extends BaseMapper<ContentReview> {
}
