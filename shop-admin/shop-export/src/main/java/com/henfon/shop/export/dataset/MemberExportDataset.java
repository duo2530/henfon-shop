package com.henfon.shop.export.dataset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.export.dto.ExportQuery;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.excel.ExcelColumn;
import com.henfon.shop.identity.entity.MemberConsumptionStat;
import com.henfon.shop.identity.entity.MemberTag;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.entity.MemberUserTag;
import com.henfon.shop.identity.mapper.MemberConsumptionStatMapper;
import com.henfon.shop.identity.mapper.MemberTagMapper;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.identity.mapper.MemberUserTagMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 会员名单导出数据集。
 *
 * <p>消费统计与标签采用整批补充的方式读取，避免逐条会员查询造成 N+1。
 * 前端 CSV 里的「成长值」恒为 0，后端并无该字段，导出不再保留这一列以免误导。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Component
public class MemberExportDataset extends AbstractExportDataset<MemberExportDataset.Row> {

    /** 会员等级文案，与前端 tier 展示保持一致。 */
    private static final Map<String, String> LEVEL_LABELS = Map.of(
            "REGULAR", "普通会员", "SILVER", "银卡会员", "GOLD", "金卡会员", "PLATINUM", "白金VIP");

    private final MemberUserMapper memberUserMapper;

    private final MemberUserTagMapper memberUserTagMapper;

    private final MemberTagMapper memberTagMapper;

    private final MemberConsumptionStatMapper memberConsumptionStatMapper;

    /**
     * 创建会员导出数据集。
     *
     * @param memberUserMapper 会员数据访问对象
     * @param memberUserTagMapper 会员标签关联数据访问对象
     * @param memberTagMapper 标签数据访问对象
     * @param memberConsumptionStatMapper 消费统计数据访问对象
     * @author Henfon
     * @date 2026-09-16
     */
    public MemberExportDataset(MemberUserMapper memberUserMapper,
                               MemberUserTagMapper memberUserTagMapper,
                               MemberTagMapper memberTagMapper,
                               MemberConsumptionStatMapper memberConsumptionStatMapper) {
        this.memberUserMapper = memberUserMapper;
        this.memberUserTagMapper = memberUserTagMapper;
        this.memberTagMapper = memberTagMapper;
        this.memberConsumptionStatMapper = memberConsumptionStatMapper;
    }

    /**
     * 会员导出行。
     *
     * @param memberNo 用户编号
     * @param name 姓名
     * @param phone 手机号
     * @param email 邮箱
     * @param levelLabel 会员等级
     * @param balance 可用余额
     * @param points 积分
     * @param totalSpent 累计消费
     * @param orderCount 订单数
     * @param statusLabel 状态
     * @param tags 用户标签
     * @author Henfon
     * @date 2026-09-16
     */
    public record Row(String memberNo, String name, String phone, String email, String levelLabel,
                      BigDecimal balance, Long points, BigDecimal totalSpent, Long orderCount,
                      String statusLabel, String tags) {
    }

    @Override
    public ExportType type() {
        return ExportType.MEMBER;
    }

    @Override
    public List<ExcelColumn<Row>> columns() {
        return List.of(
                ExcelColumn.text("用户编号", Row::memberNo, 20),
                ExcelColumn.text("姓名", Row::name, 16),
                ExcelColumn.text("手机号", Row::phone, 15),
                ExcelColumn.text("邮箱", Row::email, 26),
                ExcelColumn.text("会员等级", Row::levelLabel, 12),
                ExcelColumn.money("可用余额(¥)", Row::balance, 14),
                ExcelColumn.integer("积分", Row::points, 10),
                ExcelColumn.money("累计消费(¥)", Row::totalSpent, 14),
                ExcelColumn.integer("订单数", Row::orderCount, 10),
                ExcelColumn.text("状态", Row::statusLabel, 10),
                ExcelColumn.text("用户标签", Row::tags, 22));
    }

    @Override
    public void stream(Consumer<Row> consumer, ExportQuery query) {
        String level = StringUtils.hasText(query.memberLevel())
                ? query.memberLevel().trim().toUpperCase(Locale.ROOT) : null;
        LambdaQueryWrapper<MemberUser> wrapper = new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getTenantId, 0L)
                .eq(level != null, MemberUser::getMemberLevel, level)
                .eq(query.status() != null, MemberUser::getStatus, query.status())
                .and(StringUtils.hasText(query.keyword()), condition -> condition
                        .like(MemberUser::getMemberNo, query.keyword())
                        .or().like(MemberUser::getUsername, query.keyword())
                        .or().like(MemberUser::getNickname, query.keyword())
                        .or().like(MemberUser::getPhone, query.keyword())
                        .or().like(MemberUser::getEmail, query.keyword()))
                .orderByDesc(MemberUser::getId);

        streamPages(consumer, (pageNo, pageSize) -> {
            Page<MemberUser> page = memberUserMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
            List<MemberUser> members = page.getRecords();
            if (members.isEmpty()) {
                return List.of();
            }
            Map<Long, MemberConsumptionStat> stats = loadStats(members);
            Map<Long, String> tagNames = loadTagNames(members);
            return members.stream().map(member -> toRow(member, stats, tagNames)).toList();
        });
    }

    /**
     * 整批加载消费统计。
     *
     * @param members 本页会员
     * @return 会员ID到消费统计的映射
     * @author Henfon
     * @date 2026-09-16
     */
    private Map<Long, MemberConsumptionStat> loadStats(List<MemberUser> members) {
        List<Long> memberIds = members.stream().map(MemberUser::getId).toList();
        List<MemberConsumptionStat> stats = memberConsumptionStatMapper.selectList(
                new LambdaQueryWrapper<MemberConsumptionStat>().in(MemberConsumptionStat::getMemberId, memberIds));
        Map<Long, MemberConsumptionStat> result = new HashMap<>();
        for (MemberConsumptionStat stat : stats) {
            result.put(stat.getMemberId(), stat);
        }
        return result;
    }

    /**
     * 整批加载会员标签名称。
     *
     * @param members 本页会员
     * @return 会员ID到标签名称串的映射
     * @author Henfon
     * @date 2026-09-16
     */
    private Map<Long, String> loadTagNames(List<MemberUser> members) {
        List<Long> memberIds = members.stream().map(MemberUser::getId).toList();
        List<MemberUserTag> relations = memberUserTagMapper.selectList(
                new LambdaQueryWrapper<MemberUserTag>().in(MemberUserTag::getMemberId, memberIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        List<Long> tagIds = relations.stream().map(MemberUserTag::getTagId).distinct().toList();
        List<MemberTag> tags = memberTagMapper.selectList(new LambdaQueryWrapper<MemberTag>()
                .in(MemberTag::getId, tagIds).eq(MemberTag::getTenantId, 0L));
        Map<Long, String> tagNameById = tags.stream()
                .collect(Collectors.toMap(MemberTag::getId, MemberTag::getTagName, (left, right) -> left));
        Map<Long, List<String>> namesByMember = new HashMap<>();
        for (MemberUserTag relation : relations) {
            String tagName = tagNameById.get(relation.getTagId());
            if (tagName != null) {
                namesByMember.computeIfAbsent(relation.getMemberId(), key -> new ArrayList<>()).add(tagName);
            }
        }
        Map<Long, String> result = new HashMap<>();
        namesByMember.forEach((memberId, names) -> result.put(memberId, String.join(",", names)));
        return result;
    }

    /**
     * 组装导出行。
     *
     * @param member 会员实体
     * @param stats 消费统计映射
     * @param tagNames 标签映射
     * @return 导出行
     * @author Henfon
     * @date 2026-09-16
     */
    private static Row toRow(MemberUser member, Map<Long, MemberConsumptionStat> stats,
                            Map<Long, String> tagNames) {
        MemberConsumptionStat stat = stats.get(member.getId());
        return new Row(member.getMemberNo(),
                StringUtils.hasText(member.getNickname()) ? member.getNickname() : member.getUsername(),
                member.getPhone(), member.getEmail(),
                LEVEL_LABELS.getOrDefault(member.getMemberLevel(), member.getMemberLevel()),
                member.getBalance(), member.getPoints(),
                stat == null || stat.getPaidAmount() == null ? BigDecimal.ZERO : stat.getPaidAmount(),
                stat == null || stat.getPaidOrderCount() == null ? 0L : stat.getPaidOrderCount(),
                member.getStatus() != null && member.getStatus() == 1 ? "正常" : "已冻结",
                tagNames.getOrDefault(member.getId(), ""));
    }
}
