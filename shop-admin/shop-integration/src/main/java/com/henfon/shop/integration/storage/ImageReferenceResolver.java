package com.henfon.shop.integration.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 存储引用解析器，统一处理对象键归一化和访问地址重签。
 *
 * <p>MinIO 桶是私有的，上传接口返回的是 24 小时过期的预签名地址。直接把它存进
 * 业务表，第二天图片或附件就会 403，因此写入时统一归一化成对象键、读取时按当前
 * 配置重新签名。非 MinIO 的外部图床地址两边都原样透传。</p>
 *
 * @author Henfon
 * @date 2026-09-17
 */
@Component
public class ImageReferenceResolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MinioStorageService storageService;

    /**
     * 创建存储引用解析器。
     *
     * @param storageService MinIO 文件服务
     * @author Henfon
     * @date 2026-09-17
     */
    public ImageReferenceResolver(MinioStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 将单个引用转换为当前有效的访问地址。
     *
     * @param reference 对象键或历史访问地址
     * @return 当前有效的访问地址，外链原样返回
     * @author Henfon
     * @date 2026-09-17
     */
    public String accessUrl(String reference) {
        if (!StringUtils.hasText(reference)) {
            return reference;
        }
        return storageService.resolveAccessUrl(reference.trim());
    }

    /**
     * 将引用归一化为稳定对象键，便于持久化。
     *
     * @param reference 对象键或访问地址
     * @return 稳定存储引用
     * @author Henfon
     * @date 2026-09-17
     */
    public String normalizeReference(String reference) {
        if (!StringUtils.hasText(reference)) {
            return reference;
        }
        return storageService.normalizeReference(reference.trim());
    }

    /**
     * 将存储中的引用 JSON 数组重签为当前有效的访问地址。
     *
     * @param jsonArray 引用地址 JSON 数组
     * @return 重签后的 JSON 数组，无法解析时原样返回
     * @author Henfon
     * @date 2026-09-17
     */
    public String resignJsonArray(String jsonArray) {
        List<String> urls = parse(jsonArray);
        if (urls.isEmpty()) {
            return jsonArray;
        }
        List<String> resigned = new ArrayList<>(urls.size());
        for (String url : urls) {
            resigned.add(accessUrl(url));
        }
        return serialize(resigned);
    }

    /**
     * 将待写入的引用地址列表归一化为对象键并序列化。
     *
     * @param urls 引用地址列表
     * @return JSON 数组字符串，无有效地址时返回 null
     * @author Henfon
     * @date 2026-09-17
     */
    public String normalizeJsonArray(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        List<String> normalized = new ArrayList<>(urls.size());
        for (String url : urls) {
            if (StringUtils.hasText(url)) {
                normalized.add(normalizeReference(url));
            }
        }
        return normalized.isEmpty() ? null : serialize(normalized);
    }

    /**
     * 解析存储中的引用地址 JSON 数组。
     *
     * @param jsonArray JSON 数组字符串
     * @return 地址列表，非法内容返回空列表
     * @author Henfon
     * @date 2026-09-17
     */
    private List<String> parse(String jsonArray) {
        if (!StringUtils.hasText(jsonArray)) {
            return Collections.emptyList();
        }
        try {
            List<String> parsed = MAPPER.readValue(jsonArray.trim(), new TypeReference<List<String>>() { });
            return parsed == null ? Collections.emptyList() : parsed;
        } catch (Exception exception) {
            // 历史脏数据不阻塞整页查询，交由调用方原样返回。
            return Collections.emptyList();
        }
    }

    /**
     * 将引用地址列表序列化为 JSON 数组字符串。
     *
     * @param urls 引用地址列表
     * @return JSON 数组字符串
     * @author Henfon
     * @date 2026-09-17
     */
    private String serialize(List<String> urls) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < urls.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"')
                    .append(urls.get(i).replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"');
        }
        return builder.append(']').toString();
    }
}
