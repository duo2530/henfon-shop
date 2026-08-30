package com.henfon.shop.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.identity.entity.MemberUserTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员标签关联数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Mapper
public interface MemberUserTagMapper extends BaseMapper<MemberUserTag> {
}
