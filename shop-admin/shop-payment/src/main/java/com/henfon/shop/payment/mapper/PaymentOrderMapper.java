package com.henfon.shop.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.payment.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付单数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {
}
