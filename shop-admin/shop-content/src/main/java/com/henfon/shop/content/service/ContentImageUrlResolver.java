package com.henfon.shop.content.service;

import com.henfon.shop.integration.storage.ImageReferenceResolver;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内容模块图片地址解析器，统一处理对象键归一化和访问地址重签。
 *
 * <p>具体规则见 {@link ImageReferenceResolver}。本类保留内容模块的语义化入口，
 * 便于内容服务注入时明确依赖用途，并与交易、支付模块共用同一套解析实现。</p>
 *
 * @author Henfon
 * @date 2026-09-17
 */
@Component
public class ContentImageUrlResolver {

    private final ImageReferenceResolver referenceResolver;

    /**
     * 创建内容图片地址解析器。
     *
     * @param referenceResolver 通用存储引用解析器
     * @author Henfon
     * @date 2026-09-17
     */
    public ContentImageUrlResolver(ImageReferenceResolver referenceResolver) {
        this.referenceResolver = referenceResolver;
    }

    /**
     * 将单个图片引用转换为当前有效的访问地址。
     *
     * @param reference 对象键或历史访问地址
     * @return 当前有效的访问地址，外链原样返回
     * @author Henfon
     * @date 2026-09-17
     */
    public String accessUrl(String reference) {
        return referenceResolver.accessUrl(reference);
    }

    /**
     * 将图片引用归一化为稳定对象键，便于持久化。
     *
     * @param reference 对象键或访问地址
     * @return 稳定存储引用
     * @author Henfon
     * @date 2026-09-17
     */
    public String normalizeReference(String reference) {
        return referenceResolver.normalizeReference(reference);
    }

    /**
     * 将存储中的图片地址 JSON 数组重签为当前有效的访问地址。
     *
     * @param jsonArray 图片地址 JSON 数组
     * @return 重签后的 JSON 数组，无法解析时原样返回
     * @author Henfon
     * @date 2026-09-17
     */
    public String resignJsonArray(String jsonArray) {
        return referenceResolver.resignJsonArray(jsonArray);
    }

    /**
     * 将待写入的图片地址列表归一化为对象键并序列化。
     *
     * @param urls 图片地址列表
     * @return JSON 数组字符串，无有效地址时返回 null
     * @author Henfon
     * @date 2026-09-17
     */
    public String normalizeJsonArray(List<String> urls) {
        return referenceResolver.normalizeJsonArray(urls);
    }
}
