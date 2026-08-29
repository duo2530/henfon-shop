package com.henfon.shop.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.identity.entity.MemberCompareHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品对比历史数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Mapper
public interface MemberCompareHistoryMapper extends BaseMapper<MemberCompareHistory> {
}
