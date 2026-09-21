package com.henfon.shop.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.ai.entity.AiFaq;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 客服知识库问答 Mapper。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Mapper
public interface AiFaqMapper extends BaseMapper<AiFaq> {
}
