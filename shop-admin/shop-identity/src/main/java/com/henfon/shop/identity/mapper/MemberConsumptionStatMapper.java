package com.henfon.shop.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.identity.entity.MemberConsumptionStat;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员消费统计数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Mapper
public interface MemberConsumptionStatMapper extends BaseMapper<MemberConsumptionStat> {
}
