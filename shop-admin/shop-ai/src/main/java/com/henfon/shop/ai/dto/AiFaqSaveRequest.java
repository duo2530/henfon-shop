package com.henfon.shop.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库问答保存请求。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Data
public class AiFaqSaveRequest {

    /** 为空表示新增，否则为修改。 */
    private Long id;

    @NotBlank(message = "标准问法不能为空")
    @Size(max = 255, message = "标准问法不能超过 255 字")
    private String question;

    @NotBlank(message = "标准答案不能为空")
    @Size(max = 4000, message = "标准答案不能超过 4000 字")
    private String answer;

    @Size(max = 32, message = "分类标识不能超过 32 字")
    private String category;

    @Size(max = 255, message = "关键词不能超过 255 字")
    private String keywords;

    private Integer sortNo;

    /** 是否启用：1 启用，0 停用。停用后不参与向量化。 */
    private Integer enabled;
}
