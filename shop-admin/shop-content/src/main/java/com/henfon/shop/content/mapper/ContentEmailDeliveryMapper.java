package com.henfon.shop.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.content.entity.ContentEmailDelivery;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 会员业务邮件投递记录数据访问接口。
 *
 * @author Henfon
 * @date 2026-09-03
 */
@Mapper
public interface ContentEmailDeliveryMapper extends BaseMapper<ContentEmailDelivery> {

    /**
     * 幂等插入邮件投递记录，已存在时保持原记录不变。
     *
     * @param delivery 投递记录
     * @return 影响行数
     * @author Henfon
     * @date 2026-09-03
     */
    @Insert("INSERT IGNORE INTO content_email_delivery "
            + "(member_id,dedupe_key,recipient,event_type,subject,status,created_at,updated_at) "
            + "VALUES (#{memberId},#{dedupeKey},#{recipient},#{eventType},#{subject},0,NOW(3),NOW(3))")
    int insertIgnore(ContentEmailDelivery delivery);

    /**
     * 原子抢占待发送记录，允许超时的发送中记录重新尝试。
     *
     * @param memberId 会员ID
     * @param dedupeKey 事件幂等键
     * @param sendingToken 本次发送占用令牌
     * @return 抢占成功返回1
     * @author Henfon
     * @date 2026-09-03
     */
    @Update("UPDATE content_email_delivery SET status=2,sending_token=#{sendingToken},"
            + "sending_at=NOW(3),last_error=NULL,updated_at=NOW(3) "
            + "WHERE member_id=#{memberId} AND dedupe_key=#{dedupeKey} "
            + "AND (status=0 OR (status=2 AND sending_at < DATE_SUB(NOW(3), INTERVAL 10 MINUTE)))")
    int claimSending(Long memberId, String dedupeKey, String sendingToken);

    /**
     * 查询邮件投递记录当前状态。
     *
     * @param memberId 会员ID
     * @param dedupeKey 事件幂等键
     * @return 投递记录
     * @author Henfon
     * @date 2026-09-03
     */
    @Select("SELECT id,member_id,dedupe_key,recipient,event_type,subject,status,sending_token,"
            + "sending_at,sent_at,last_error,created_at,updated_at FROM content_email_delivery "
            + "WHERE member_id=#{memberId} AND dedupe_key=#{dedupeKey} LIMIT 1")
    ContentEmailDelivery selectByDedupeKey(Long memberId, String dedupeKey);

    /**
     * 标记邮件发送成功，仅允许当前占用者更新。
     *
     * @param memberId 会员ID
     * @param dedupeKey 事件幂等键
     * @param sendingToken 本次发送占用令牌
     * @return 更新行数
     * @author Henfon
     * @date 2026-09-03
     */
    @Update("UPDATE content_email_delivery SET status=1,sent_at=NOW(3),sending_token=NULL,"
            + "updated_at=NOW(3) WHERE member_id=#{memberId} AND dedupe_key=#{dedupeKey} "
            + "AND status=2 AND sending_token=#{sendingToken}")
    int markSent(Long memberId, String dedupeKey, String sendingToken);

    /**
     * 记录发送失败并释放占用，允许下一轮重新发送。
     *
     * @param memberId 会员ID
     * @param dedupeKey 事件幂等键
     * @param sendingToken 本次发送占用令牌
     * @param lastError 错误信息
     * @return 更新行数
     * @author Henfon
     * @date 2026-09-03
     */
    @Update("UPDATE content_email_delivery SET status=0,sending_token=NULL,sending_at=NULL,"
            + "last_error=#{lastError},updated_at=NOW(3) WHERE member_id=#{memberId} "
            + "AND dedupe_key=#{dedupeKey} AND status=2 AND sending_token=#{sendingToken}")
    int markFailed(Long memberId, String dedupeKey, String sendingToken, String lastError);
}
