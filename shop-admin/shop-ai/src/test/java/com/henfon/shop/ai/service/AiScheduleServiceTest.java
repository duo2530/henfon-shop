package com.henfon.shop.ai.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.henfon.shop.ai.dto.AiScheduleBoard;
import com.henfon.shop.ai.dto.AiSchedulePlan;
import com.henfon.shop.ai.dto.AiSchedulePlanRequest;
import com.henfon.shop.ai.entity.AiSchedule;
import com.henfon.shop.ai.mapper.AiScheduleMapper;
import com.henfon.shop.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 一键排班的测试。
 *
 * 这里守的是四条不能松的约束：一人一天只能一段班（撞了唯一键落库直接失败）、班次在人之间
 * 均摊（不均摊就会变成"总是同一个人顶周末"）、默认只补不删（默认覆盖会把主管手工调过的班
 * 次悄悄改掉）、排不出来要报错而不是悄悄少排几个人。
 *
 * @author Henfon
 * @date 2026-09-22
 */
@ExtendWith(MockitoExtension.class)
class AiScheduleServiceTest {

    @Mock
    private AiScheduleMapper scheduleMapper;

    @Mock
    private AiAgentDirectory directory;

    @InjectMocks
    private AiScheduleService service;

    /** 两个客服，与线上当前的规模一致。 */
    private List<AiScheduleBoard.AiScheduleAgent> twoAgents() {
        return List.of(
                new AiScheduleBoard.AiScheduleAgent(2L, "service01", "客服一号", "OFFLINE"),
                new AiScheduleBoard.AiScheduleAgent(3L, "service02", "客服二号", "OFFLINE"));
    }

    private AiSchedule row(long id, long agentId, String agentName, int weekday, String start, String end) {
        AiSchedule schedule = new AiSchedule();
        schedule.setId(id);
        schedule.setAgentId(agentId);
        schedule.setAgentName(agentName);
        schedule.setWeekday(weekday);
        schedule.setStartTime(LocalTime.parse(start));
        schedule.setEndTime(LocalTime.parse(end));
        schedule.setEnabled(1);
        return schedule;
    }

    private AiSchedulePlanRequest request(LocalTime start, LocalTime end, int perDay, List<Integer> weekdays) {
        return new AiSchedulePlanRequest(start, end, perDay, weekdays, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private void givenExisting(List<AiSchedule> rows) {
        when(scheduleMapper.selectList(any(Wrapper.class))).thenReturn(rows);
    }

    @Test
    void spreadsShiftsEvenlyAcrossAgents() {
        when(directory.agents()).thenReturn(twoAgents());
        givenExisting(List.of());

        AiSchedulePlan plan = service.plan(request(LocalTime.of(9, 0), LocalTime.of(18, 0), 1, List.of(1, 2, 3, 4, 5, 6, 7)));

        assertEquals(7, plan.items().size(), "整周每天一人，共七个班次");
        assertEquals(List.of("客服一号 4 班", "客服二号 3 班"), plan.shiftCounts(), "班次要在两人之间均摊");
        Set<String> perDay = new HashSet<>();
        plan.items().forEach(item -> assertTrue(perDay.add(item.agentId() + "-" + item.weekday()),
                "一个客服一天只能一段班"));
        assertTrue(plan.warnings().isEmpty(), "整周都有人时不该有提醒");
    }

    @Test
    void keepsExistingShiftsUnlessAskedToClear() {
        when(directory.agents()).thenReturn(twoAgents());
        givenExisting(List.of(row(11L, 3L, "客服二号", 1, "09:00", "12:00")));

        AiSchedulePlan plan = service.plan(request(LocalTime.of(9, 0), LocalTime.of(18, 0), 1, List.of(1, 2, 3, 4, 5, 6, 7)));

        assertFalse(plan.items().stream().anyMatch(item -> item.change() == AiSchedulePlan.Item.Change.REMOVE),
                "默认只补不删，主管手工调过的班次不能被覆盖掉");
        // 周一原本是客服二号的 09:00-12:00，这次排给客服一号，属于新增而不是改掉人家那一条
        assertTrue(plan.items().stream().allMatch(item -> item.change() == AiSchedulePlan.Item.Change.ADD));
    }

    @Test
    void removesUncoveredShiftWhenAsked() {
        when(directory.agents()).thenReturn(twoAgents());
        givenExisting(List.of(row(11L, 3L, "客服二号", 1, "09:00", "12:00")));

        AiSchedulePlan plan = service.plan(new AiSchedulePlanRequest(LocalTime.of(9, 0), LocalTime.of(18, 0),
                1, List.of(1, 2, 3, 4, 5, 6, 7), null, null, true));

        assertEquals(1, plan.items().stream()
                .filter(item -> item.change() == AiSchedulePlan.Item.Change.REMOVE)
                .count(), "勾选清掉未被覆盖的旧班次后，范围内没排到的那一条要列出来");
    }

    @Test
    void marksExistingShiftAsUpdateWithPreviousTime() {
        when(directory.agents()).thenReturn(twoAgents());
        givenExisting(List.of(row(11L, 2L, "客服一号", 1, "09:00", "12:00")));

        AiSchedulePlan plan = service.plan(request(LocalTime.of(9, 0), LocalTime.of(18, 0), 1, List.of(1)));

        AiSchedulePlan.Item item = plan.items().stream()
                .filter(candidate -> candidate.change() == AiSchedulePlan.Item.Change.UPDATE)
                .findFirst()
                .orElseThrow();
        assertEquals(LocalTime.of(9, 0), item.previousStart());
        assertEquals(LocalTime.of(12, 0), item.previousEnd(), "预览要带原来的时间，主管才知道改了什么");
        assertEquals(LocalTime.of(18, 0), item.endTime());
    }

    @Test
    void warnsAboutDaysWithoutCoverage() {
        when(directory.agents()).thenReturn(twoAgents());
        givenExisting(List.of());

        AiSchedulePlan plan = service.plan(request(LocalTime.of(9, 0), LocalTime.of(18, 0), 1, List.of(1, 2, 3, 4, 5)));

        assertTrue(plan.warnings().contains("周六全天无人排班"));
        assertTrue(plan.warnings().contains("周日全天无人排班"));
    }

    @Test
    void splitsLongDayIntoTwoSegments() {
        when(directory.agents()).thenReturn(twoAgents());
        givenExisting(List.of());

        AiSchedulePlan plan = service.plan(new AiSchedulePlanRequest(LocalTime.of(9, 0), LocalTime.of(21, 0),
                1, List.of(1, 2), 8, null, null));

        assertEquals(4, plan.items().size(), "12 小时按 8 小时上限拆成两段，两天共四个班次");
        assertEquals(2, plan.items().stream().filter(item -> item.weekday() == 1).count());
        assertTrue(plan.items().stream().anyMatch(item -> item.startTime().equals(LocalTime.of(9, 0))
                && item.endTime().equals(LocalTime.of(15, 0))), "第一段 09:00-15:00");
        assertTrue(plan.items().stream().anyMatch(item -> item.startTime().equals(LocalTime.of(15, 0))
                && item.endTime().equals(LocalTime.of(21, 0))), "第二段 15:00-21:00");
        Set<String> perDay = new HashSet<>();
        plan.items().forEach(item -> assertTrue(perDay.add(item.agentId() + "-" + item.weekday()),
                "拆段靠的是不同的人，同一人不能占两段"));
    }

    @Test
    void rejectsRequestsThatCannotBeStaffed() {
        when(directory.agents()).thenReturn(twoAgents());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.plan(request(LocalTime.of(9, 0), LocalTime.of(18, 0), 3, List.of(1))));

        assertEquals("AI_SCHEDULE_CAPACITY", exception.getCode(),
                "排不出来要报错，不能悄悄少排几个人让人以为排满了");
    }

    @Test
    void rejectsInvalidTimeRange() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.plan(request(LocalTime.of(18, 0), LocalTime.of(9, 0), 1, List.of(1))));

        assertEquals("AI_SCHEDULE_TIME_INVALID", exception.getCode());
    }
}
