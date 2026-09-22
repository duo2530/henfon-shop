package com.henfon.shop.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.henfon.shop.ai.dto.AiScheduleBoard;
import com.henfon.shop.ai.dto.AiSchedulePlan;
import com.henfon.shop.ai.dto.AiSchedulePlanRequest;
import com.henfon.shop.ai.dto.AiScheduleSaveRequest;
import com.henfon.shop.ai.dto.AiScheduleView;
import com.henfon.shop.ai.entity.AiSchedule;
import com.henfon.shop.ai.mapper.AiScheduleMapper;
import com.henfon.shop.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    /** 单人单班超过这个小时数会在体检里提醒一句，不是硬拦——有时就是要一个人顶一整天。 */
    private static final int LONG_SHIFT_HOURS = 10;

    /** 星期中文名，下标 0 空着，直接按 1-7 取。 */
    private static final List<String> WEEKDAY_NAMES =
            List.of("", "周一", "周二", "周三", "周四", "周五", "周六", "周日");

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
     * 按参数生成一份排班计划，不写库。
     *
     * <p>选人是确定的：按天顺序遍历，每天按段遍历，每段挑"本周已排到班次最少、其次账号 ID
     * 最小"的人。于是同一个人在一周里不会连着被排、周末也不会总是同一个人顶。这里刻意做得
     * 可解释——排班是要拿去执行的，主管问"周三为什么是他"必须答得出来，交给人人都会算的
     * 规则比交给模型稳。</p>
     *
     * @param request 生成参数
     * @return 计划：将要发生的变动、体检提醒、每人班次
     * @author Henfon
     * @date 2026-09-22
     */
    public AiSchedulePlan plan(AiSchedulePlanRequest request) {
        Draft draft = draft(request);
        return new AiSchedulePlan(draft.items(), draft.warnings(), draft.shiftCounts());
    }

    /**
     * 按参数生成并写入排班。
     *
     * <p>落库前重新算一遍而不是让前端把它拿到的那批条目传回来：参数相同则结果相同，这样
     * "预览看到的"和"真正写入的"必然是同一份，也不用去校验客户端拼出来的排班内容。代价是
     * 多算一次，这点开销远小于信任客户端数据的风险。</p>
     *
     * @param request 生成参数
     * @return 实际写入的计划
     * @author Henfon
     * @date 2026-09-22
     */
    @Transactional
    public AiSchedulePlan apply(AiSchedulePlanRequest request) {
        Draft draft = draft(request);
        for (Draft.Action action : draft.actions()) {
            switch (action.change()) {
                case ADD -> {
                    AiSchedule schedule = new AiSchedule();
                    schedule.setAgentId(action.agentId());
                    schedule.setAgentName(action.agentName());
                    schedule.setWeekday(action.weekday());
                    schedule.setStartTime(action.start());
                    schedule.setEndTime(action.end());
                    schedule.setEnabled(1);
                    scheduleMapper.insert(schedule);
                }
                case UPDATE -> {
                    AiSchedule row = action.existing();
                    row.setAgentName(action.agentName());
                    row.setStartTime(action.start());
                    row.setEndTime(action.end());
                    row.setEnabled(1);
                    scheduleMapper.updateById(row);
                }
                case REMOVE -> scheduleMapper.deleteById(action.existing().getId());
            }
        }
        log.info("一键排班落库：新增或改动 {} 条，删除 {} 条，班次 {}",
                draft.actions().size() - draft.removeCount(), draft.removeCount(), draft.shiftCounts());
        return new AiSchedulePlan(draft.items(), draft.warnings(), draft.shiftCounts());
    }

    /**
     * 生成计划的全过程。
     *
     * @param request 生成参数
     * @return 计划草稿，含可直接执行的动作
     * @author Henfon
     * @date 2026-09-22
     */
    private Draft draft(AiSchedulePlanRequest request) {
        Setting setting = setting(request);
        List<AiScheduleBoard.AiScheduleAgent> agents = directory.agents();
        if (agents.isEmpty()) {
            throw new BusinessException("AI_SCHEDULE_NO_AGENT", "没有可排班的客服：请先给账号授予客服工作台权限");
        }
        int slotsPerDay = setting.segments().size() * setting.perDay();
        if (slotsPerDay > agents.size()) {
            throw new BusinessException("AI_SCHEDULE_CAPACITY", String.format(
                    "每天需要 %d 人次，可用客服只有 %d 人：请减少同时在岗人数，或放宽单班时长上限让一个人顶更久",
                    slotsPerDay, agents.size()));
        }
        List<AiSchedule> existingRows = allSchedules();
        Map<String, AiSchedule> existing = new HashMap<>();
        for (AiSchedule row : existingRows) {
            existing.put(key(row.getAgentId(), row.getWeekday()), row);
        }

        Map<Long, Integer> counts = new LinkedHashMap<>();
        agents.forEach(agent -> counts.put(agent.agentId(), 0));
        Map<String, Assignment> planned = new LinkedHashMap<>();
        Map<Integer, Integer> filledByDay = new LinkedHashMap<>();
        for (Integer weekday : setting.weekdays()) {
            Set<Long> usedToday = new HashSet<>();
            int filled = 0;
            for (Segment segment : setting.segments()) {
                for (int i = 0; i < setting.perDay(); i++) {
                    Long picked = pick(agents, counts, usedToday, setting.maxShifts());
                    if (picked == null) {
                        continue;
                    }
                    usedToday.add(picked);
                    counts.merge(picked, 1, Integer::sum);
                    planned.put(key(picked, weekday), new Assignment(picked, weekday, segment));
                    filled++;
                }
            }
            filledByDay.put(weekday, filled);
        }

        Map<Long, String> names = new HashMap<>();
        agents.forEach(agent -> names.put(agent.agentId(), agent.agentName()));
        List<Draft.Action> actions = new ArrayList<>();
        for (Assignment assignment : planned.values()) {
            AiSchedule row = existing.get(key(assignment.agentId(), assignment.weekday()));
            String name = names.get(assignment.agentId());
            if (row == null) {
                actions.add(new Draft.Action(AiSchedulePlan.Item.Change.ADD, assignment.agentId(), name,
                        assignment.weekday(), assignment.segment().start(), assignment.segment().end(), null));
            } else if (!assignment.segment().start().equals(row.getStartTime())
                    || !assignment.segment().end().equals(row.getEndTime())
                    || !Integer.valueOf(1).equals(row.getEnabled())) {
                actions.add(new Draft.Action(AiSchedulePlan.Item.Change.UPDATE, assignment.agentId(), name,
                        assignment.weekday(), assignment.segment().start(), assignment.segment().end(), row));
            }
        }
        if (setting.clearUncovered()) {
            for (AiSchedule row : existingRows) {
                if (!setting.weekdays().contains(row.getWeekday())
                        || planned.containsKey(key(row.getAgentId(), row.getWeekday()))) {
                    continue;
                }
                actions.add(new Draft.Action(AiSchedulePlan.Item.Change.REMOVE, row.getAgentId(),
                        names.getOrDefault(row.getAgentId(), row.getAgentName()),
                        row.getWeekday(), null, null, row));
            }
        }
        actions.sort(Comparator.comparingInt(Draft.Action::weekday)
                .thenComparingInt(action -> action.change() == AiSchedulePlan.Item.Change.REMOVE ? 1 : 0)
                .thenComparing(Draft.Action::agentId));

        List<AiSchedulePlan.Item> items = actions.stream()
                .map(action -> new AiSchedulePlan.Item(action.change(), action.agentId(), action.agentName(),
                        action.weekday(), action.start(), action.end(),
                        action.existing() == null ? null : action.existing().getStartTime(),
                        action.existing() == null ? null : action.existing().getEndTime()))
                .toList();
        List<String> warnings = warnings(setting, planned, filledByDay, slotsPerDay, existingRows, counts, names);
        List<String> shiftCounts = new ArrayList<>();
        counts.forEach((agentId, count) -> shiftCounts.add(names.get(agentId) + " " + count + " 班"));
        return new Draft(actions, items, warnings, shiftCounts,
                (int) actions.stream().filter(a -> a.change() == AiSchedulePlan.Item.Change.REMOVE).count());
    }

    /**
     * 挑一个人顶一个班次。
     *
     * 排序口径是"本周已排到的班次最少者优先，同数按账号 ID"，所以工作日排满之后周末自然
     * 轮到下一个人，不会每次都落在名单第一个。
     *
     * @param agents 可用客服，按账号 ID 升序
     * @param counts 每人本周已排到的班次数
     * @param usedToday 当天已经排过的人，一人一天只能一段班
     * @param maxShifts 每人每周班次上限，为空表示不限
     * @return 选中的账号 ID，没人可选时返回 null
     * @author Henfon
     * @date 2026-09-22
     */
    private Long pick(List<AiScheduleBoard.AiScheduleAgent> agents,
                      Map<Long, Integer> counts,
                      Set<Long> usedToday,
                      Integer maxShifts) {
        Long best = null;
        int bestCount = Integer.MAX_VALUE;
        for (AiScheduleBoard.AiScheduleAgent agent : agents) {
            if (usedToday.contains(agent.agentId())) {
                continue;
            }
            int count = counts.getOrDefault(agent.agentId(), 0);
            if (maxShifts != null && count >= maxShifts) {
                continue;
            }
            if (count < bestCount) {
                best = agent.agentId();
                bestCount = count;
            }
        }
        return best;
    }

    /**
     * 体检提醒。
     *
     * 都是"排得出来但值得看一眼"的事：哪天人手没排够、哪天全天没人、谁的单班过长、班次
     * 分布歪了。排不出来的情况在更早的地方就直接报错了，不进提醒。
     *
     * @param setting 生成参数
     * @param planned 本次排到的班次
     * @param filledByDay 每天实际排到的人次
     * @param slotsPerDay 每天需要的人次
     * @param existingRows 库中已有排班
     * @param counts 每人本周排到的班次数
     * @param names 账号 ID 到展示名的映射
     * @return 提醒列表
     * @author Henfon
     * @date 2026-09-22
     */
    private List<String> warnings(Setting setting,
                                  Map<String, Assignment> planned,
                                  Map<Integer, Integer> filledByDay,
                                  int slotsPerDay,
                                  List<AiSchedule> existingRows,
                                  Map<Long, Integer> counts,
                                  Map<Long, String> names) {
        List<String> warnings = new ArrayList<>();
        filledByDay.forEach((weekday, filled) -> {
            if (filled < slotsPerDay) {
                warnings.add(String.format("%s只排到 %d 人次，需要 %d 人次：每人每周班次上限把人卡住了",
                        WEEKDAY_NAMES.get(weekday), filled, slotsPerDay));
            }
        });
        Set<Integer> covered = new HashSet<>();
        planned.values().forEach(assignment -> covered.add(assignment.weekday()));
        for (AiSchedule row : existingRows) {
            boolean removed = setting.clearUncovered() && setting.weekdays().contains(row.getWeekday())
                    && !planned.containsKey(key(row.getAgentId(), row.getWeekday()));
            if (!removed && Integer.valueOf(1).equals(row.getEnabled())) {
                covered.add(row.getWeekday());
            }
        }
        for (int weekday = 1; weekday <= 7; weekday++) {
            if (!covered.contains(weekday)) {
                warnings.add(WEEKDAY_NAMES.get(weekday) + "全天无人排班");
            }
        }
        for (Assignment assignment : planned.values()) {
            long hours = Duration.between(assignment.segment().start(), assignment.segment().end()).toHours();
            if (hours >= LONG_SHIFT_HOURS) {
                warnings.add(String.format("%s有人单班 %d 小时：单人单班过长，建议拆成两段或加人",
                        WEEKDAY_NAMES.get(assignment.weekday()), hours));
                break;
            }
        }
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = counts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        if (max - min >= 2) {
            List<String> distribution = new ArrayList<>();
            names.forEach((agentId, name) -> distribution.add(name + " " + counts.get(agentId) + " 班"));
            warnings.add("班次分布不均：" + String.join("、", distribution));
        }
        return warnings;
    }

    /**
     * 把请求参数整理成可直接计算的形式，缺省值在这里补齐。
     *
     * @param request 生成参数
     * @return 整理后的参数
     * @author Henfon
     * @date 2026-09-22
     */
    private Setting setting(AiSchedulePlanRequest request) {
        LocalTime start = request.startTime();
        LocalTime end = request.endTime();
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BusinessException("AI_SCHEDULE_TIME_INVALID", "结束时间要晚于开始时间");
        }
        int perDay = request.perDay() == null || request.perDay() < 1 ? 1 : request.perDay();
        List<Integer> weekdays = (request.weekdays() == null ? List.<Integer>of() : request.weekdays()).stream()
                .filter(Objects::nonNull)
                .filter(weekday -> weekday >= 1 && weekday <= 7)
                .distinct()
                .sorted()
                .toList();
        if (weekdays.isEmpty()) {
            throw new BusinessException("AI_SCHEDULE_WEEKDAY_INVALID", "星期取值要在 1 到 7 之间");
        }
        Integer maxShifts = request.maxShiftsPerAgent() == null || request.maxShiftsPerAgent() < 1
                ? null
                : request.maxShiftsPerAgent();
        return new Setting(start, end, perDay, weekdays, segments(start, end, request.maxShiftHours()),
                maxShifts, Boolean.TRUE.equals(request.clearUncovered()));
    }

    /**
     * 把一天的覆盖时段切成首尾相接的几段。
     *
     * 一人一天只能一段班，所以拆段是拿人数换时长：切成两段就需要两批不同的人。最后一段以
     * 覆盖结束时间收尾，避免整除不尽时多出几分钟的空档。
     *
     * @param start 覆盖开始时间
     * @param end 覆盖结束时间
     * @param maxShiftHours 单人单班时长上限（小时），为空或覆盖时长不超过它时不拆
     * @return 班次段，按时间先后
     * @author Henfon
     * @date 2026-09-22
     */
    private List<Segment> segments(LocalTime start, LocalTime end, Integer maxShiftHours) {
        long minutes = Duration.between(start, end).toMinutes();
        if (maxShiftHours == null || maxShiftHours < 1 || minutes <= maxShiftHours * 60L) {
            return List.of(new Segment(start, end));
        }
        int count = (int) ((minutes + maxShiftHours * 60L - 1) / (maxShiftHours * 60L));
        long each = (long) Math.ceil((double) minutes / count);
        List<Segment> segments = new ArrayList<>();
        LocalTime cursor = start;
        for (int i = 0; i < count && cursor.isBefore(end); i++) {
            LocalTime stop = cursor.plusMinutes(each);
            if (i == count - 1 || !stop.isBefore(end)) {
                stop = end;
            }
            if (!stop.isAfter(cursor)) {
                break;
            }
            segments.add(new Segment(cursor, stop));
            cursor = stop;
        }
        return segments;
    }

    /**
     * 排班记录的唯一标识：一个客服一天一段班。
     *
     * @param agentId 客服账号 ID
     * @param weekday 周几
     * @return 标识
     * @author Henfon
     * @date 2026-09-22
     */
    private String key(Long agentId, Integer weekday) {
        return agentId + "-" + weekday;
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
     * 整理后的生成参数，缺省值已补齐。
     *
     * @param start 覆盖开始时间
     * @param end 覆盖结束时间
     * @param perDay 每段需要几个人
     * @param weekdays 生成哪几天，已排序去重
     * @param segments 切分后的班次段
     * @param maxShifts 每人每周班次上限，为空不限
     * @param clearUncovered 是否清掉范围内没被覆盖的旧班次
     * @author Henfon
     * @date 2026-09-22
     */
    private record Setting(LocalTime start,
                           LocalTime end,
                           int perDay,
                           List<Integer> weekdays,
                           List<Segment> segments,
                           Integer maxShifts,
                           boolean clearUncovered) {
    }

    /**
     * 一天里的一个班次段。
     *
     * @param start 段开始时间
     * @param end 段结束时间
     * @author Henfon
     * @date 2026-09-22
     */
    private record Segment(LocalTime start, LocalTime end) {
    }

    /**
     * 一个排到的班次。
     *
     * @param agentId 客服账号 ID
     * @param weekday 周几
     * @param segment 班次段
     * @author Henfon
     * @date 2026-09-22
     */
    private record Assignment(Long agentId, Integer weekday, Segment segment) {
    }

    /**
     * 计划草稿：给界面看的条目之外，还留着可直接执行的动作。
     *
     * @param actions 要执行的动作
     * @param items 给界面看的变动
     * @param warnings 体检提醒
     * @param shiftCounts 每人排到的班次数
     * @param removeCount 其中删除的条数，仅用于日志
     * @author Henfon
     * @date 2026-09-22
     */
    private record Draft(List<Action> actions,
                         List<AiSchedulePlan.Item> items,
                         List<String> warnings,
                         List<String> shiftCounts,
                         int removeCount) {

        /**
         * 一条要执行的动作。
         *
         * @param change 变动类型
         * @param agentId 客服账号 ID
         * @param agentName 客服展示名
         * @param weekday 周几
         * @param start 变动后的开始时间，删除时为空
         * @param end 变动后的结束时间，删除时为空
         * @param existing 库中已有记录，新增时为空
         * @author Henfon
         * @date 2026-09-22
         */
        private record Action(AiSchedulePlan.Item.Change change,
                              Long agentId,
                              String agentName,
                              Integer weekday,
                              LocalTime start,
                              LocalTime end,
                              AiSchedule existing) {
        }
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
