package com.henfon.shop.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用一句话说清怎么排班。
 *
 * @param text 排班要求，例如「工作日两人，周末一人，早九到晚六」
 * @author Henfon
 * @date 2026-09-22
 */
public record AiScheduleParseRequest(@NotBlank(message = "请先写一句排班要求")
                                     @Size(max = 200, message = "排班要求不要太长") String text) {
}
