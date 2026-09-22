package com.henfon.shop.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.henfon.shop.ai.dto.AiScheduleBoard;
import com.henfon.shop.ai.dto.AiScheduleSaveRequest;
import com.henfon.shop.ai.dto.AiScheduleView;
import com.henfon.shop.ai.entity.AiSchedule;
import com.henfon.shop.ai.mapper.AiScheduleMapper;
import com.henfon.shop.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * 客服排班。
 *
 * 排班不参与"现在有没有人在线"的判断——那是坐席上线状态与心跳的职责，两者不一致时以事实
 * 为准。排班只用于服务时段：当前时段有排班却没有人上线，说明客服临时离开了；当前时段根本
 * 没有排班，说明本来就不是服务时间。这两句话对买家的意思完全不同，直接决定要不要引导他去留言。
 *
 * 排班表按 (坐席, 周几) 一条记录维护：重复保存同一天是覆盖而不是追加，否则同一天会出现多条
 * 互相矛盾的班次，服务时段算出来就没有意义了。也正因如此，本表不做多时段支持（早班 + 晚班
 * 需要放开这一约束，同时管理界面要改成多段编辑）。
 *
 * 服务时段把全部启用记录一起算（最早开始到最晚结束）：客服各自的班次之间通常首尾相接，
 * 对买家而言"有人值班"的区间才是他关心的口径，逐人展示只会让他自己去做取并集。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Service
public class AiScheduleService {

    private static final Logger log = LoggerFactory.getLogger(AiScheduleService.class);

    private final AiScheduleMapper scheduleMapper;

    private final AiAgentDirectory directory;

    /**
     * 创建排班服务。
     *
     * @param scheduleMapper 排班数据访问对象
     * @param directory 客服名册
     * @author Henfon
     * @date 2026-09-21
     */
    public AiScheduleService(AiScheduleMapper scheduleMapper, AiAgentDirectory directory) {
        this.scheduleMapper = scheduleMapper;
        this.directory = directory;
    }

    /**
     * 排班页所需的整块数据。
     *
     * 同时带上已停用账号的历史排班：账号被停用后权限没了，名册里查不到人，但排班记录还在，
     * 不显示出来主管就没法把它删掉，而它仍然在影响服务时段。
     *
     * @return 名册与排班
     * @author Henfon
     * @date 2026-09-21
     */
    public AiScheduleBoard board() {
        List<AiSchedule> rows = allSchedules();
        return new AiScheduleBoard(directory.agents(), rows.stream().map(AiScheduleView::from).toList());
    }

    /**
     * 保存一条排班，同日覆盖。
     *
     * @param request 排班内容
     * @return 保存后的排班
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiScheduleView save(AiScheduleSaveRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessException("AI_SCHEDULE_TIME_INVALID", "结束时间要晚于开始时间");
        }
        String agentName = directory.displayName(request.agentId());
        int enabled = request.enabled() == null || request.enabled() ? 1 : 0;
        AiSchedule existing = scheduleMapper.selectOne(Wrappers.lambdaQuery(AiSchedule.class)
                .eq(AiSchedule::getAgentId, request.agentId())
                .eq(AiSchedule::getWeekday, request.weekday()));
        if (existing == null) {
            AiSchedule schedule = new AiSchedule();
            schedule.setAgentId(request.agentId());
            schedule.setAgentName(agentName);
            schedule.setWeekday(request.weekday());
            schedule.setStartTime(request.startTime());
            schedule.setEndTime(request.endTime());
            schedule.setEnabled(enabled);
            scheduleMapper.insert(schedule);
            log.info("新增排班：客服 {} 周 {} {} - {}",
                    agentName, request.weekday(), request.startTime(), request.endTime());
            return AiScheduleView.from(schedule);
        }
        existing.setAgentName(agentName);
        existing.setStartTime(request.startTime());
        existing.setEndTime(request.endTime());
        existing.setEnabled(enabled);
        scheduleMapper.updateById(existing);
        log.info("更新排班：客服 {} 周 {} {} - {}",
                agentName, request.weekday(), request.startTime(), request.endTime());
        return AiScheduleView.from(existing);
    }

    /**
     * 删除一条排班。
     *
     * @param id 排班记录 ID
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public void delete(Long id) {
        if (id == null || scheduleMapper.selectById(id) == null) {
            throw new BusinessException("AI_SCHEDULE_NOT_FOUND", "排班记录不存在");
        }
        scheduleMapper.deleteById(id);
    }

    /**
     * 某位客服一周的班次。
     *
     * @param agentId 坐席账号 ID
     * @return 排班列表，按星期升序
     * @author Henfon
     * @date 2026-09-21
     */
    public List<AiScheduleView> weeklySchedule(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        return scheduleMapper.selectList(Wrappers.lambdaQuery(AiSchedule.class)
                        .eq(AiSchedule::getAgentId, agentId)
                        .orderByAsc(AiSchedule::getWeekday)).stream()
                .map(AiScheduleView::from)
                .toList();
    }

    /**
     * 某位客服今天的班次。
     *
     * 停用的班次也返回，工作台要能显示"今天的班次已停用"，而不是当作没排班。
     *
     * @param agentId 坐席账号 ID
     * @return 班次，未排班时返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    public AiScheduleView todayShift(Long agentId) {
        if (agentId == null) {
            return null;
        }
        AiSchedule row = scheduleMapper.selectOne(Wrappers.lambdaQuery(AiSchedule.class)
                .eq(AiSchedule::getAgentId, agentId)
                .eq(AiSchedule::getWeekday, LocalDate.now().getDayOfWeek().getValue())
                .last("LIMIT 1"));
        return row == null ? null : AiScheduleView.from(row);
    }

    /**
     * 今天的服务时段。
     *
     * @return 服务时段，当天没有任何启用排班时返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    public ServiceWindow todayWindow() {
        int weekday = LocalDate.now().getDayOfWeek().getValue();
        List<AiSchedule> rows = scheduleMapper.selectList(Wrappers.lambdaQuery(AiSchedule.class)
                .eq(AiSchedule::getWeekday, weekday)
                .eq(AiSchedule::getEnabled, 1));
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        LocalTime start = rows.stream().map(AiSchedule::getStartTime).min(Comparator.naturalOrder()).orElse(null);
        LocalTime end = rows.stream().map(AiSchedule::getEndTime).max(Comparator.naturalOrder()).orElse(null);
        if (start == null || end == null) {
            return null;
        }
        int agents = (int) rows.stream().map(AiSchedule::getAgentId).distinct().count();
        return new ServiceWindow(start, end, agents);
    }

    /**
     * 读取全部排班。
     *
     * @return 排班列表，按星期与客服排序
     * @author Henfon
     * @date 2026-09-21
     */
    private List<AiSchedule> allSchedules() {
        List<AiSchedule> rows = scheduleMapper.selectList(Wrappers.lambdaQuery(AiSchedule.class)
                .orderByAsc(AiSchedule::getWeekday)
                .orderByAsc(AiSchedule::getAgentId));
        return rows == null ? List.of() : rows;
    }

    /**
     * 当天的服务时段。
     *
     * @param start 最早开始时间
     * @param end 最晚结束时间
     * @param agentCount 参与排班的客服数
     * @author Henfon
     * @date 2026-09-21
     */
    public record ServiceWindow(LocalTime start, LocalTime end, int agentCount) {

        /**
         * 判断某个时刻是否落在服务时段内，边界时刻算在内。
         *
         * @param time 时刻
         * @return 在时段内返回 true
         * @author Henfon
         * @date 2026-09-21
         */
        public boolean covers(LocalTime time) {
            return time != null && !time.isBefore(start) && !time.isAfter(end);
        }
    }
}
