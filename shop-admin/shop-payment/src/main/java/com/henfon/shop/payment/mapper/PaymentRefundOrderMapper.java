package com.henfon.shop.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.payment.entity.PaymentRefundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 支付退款单数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Mapper
public interface PaymentRefundOrderMapper extends BaseMapper<PaymentRefundOrder> {

    /**
     * 查出存在超额退款的支付单号。
     *
     * <p>对账视图要判断同一支付单的多次成功退款有没有超出实付金额。这里把聚合放到库侧算，
     * 结果集规模等于被退过款的支付单数，导出时就不必先把全部退款单读进内存再分组求和。</p>
     *
     * <p>口径与内存聚合保持一致：只统计成功状态（status=2）且已关联支付单号的退款；
     * 关联到的支付单必须处于对账视图收录的状态（0/1/2），否则该笔支付单本来就不在视图里，
     * 也就谈不上超额。金额为空的退款按 0 计入，与 SUM 忽略 NULL 的行为一致。</p>
     *
     * @return 超额退款的支付单号
     * @author Henfon
     * @date 2026-09-16
     */
    @Select("SELECT r.payment_no FROM ("
            + "SELECT payment_no, SUM(amount) AS refund_total FROM payment_refund_order "
            + "WHERE status = 2 AND payment_no IS NOT NULL AND is_deleted = 0 "
            + "GROUP BY payment_no) r "
            + "JOIN payment_order p ON p.payment_no = r.payment_no "
            + "WHERE p.status IN (0, 1, 2) AND p.is_deleted = 0 "
            + "AND r.refund_total > COALESCE(p.amount, 0)")
    List<String> selectOverRefundPaymentNos();
}
