package com.henfon.shop.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.payment.entity.PaymentInvoice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单发票申请数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Mapper
public interface PaymentInvoiceMapper extends BaseMapper<PaymentInvoice> {
}
