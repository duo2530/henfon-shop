package com.henfon.shop.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.payment.entity.PaymentRefundOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付退款单数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Mapper
public interface PaymentRefundOrderMapper extends BaseMapper<PaymentRefundOrder> {
}
