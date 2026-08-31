package com.henfon.shop.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.identity.entity.SysOperLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统操作日志数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {
}
