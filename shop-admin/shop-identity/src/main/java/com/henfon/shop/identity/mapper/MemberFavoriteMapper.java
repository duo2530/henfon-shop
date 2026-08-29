package com.henfon.shop.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.identity.entity.MemberFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员收藏数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Mapper
public interface MemberFavoriteMapper extends BaseMapper<MemberFavorite> {
}
