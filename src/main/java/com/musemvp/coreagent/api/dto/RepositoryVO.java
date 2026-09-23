package com.musemvp.coreagent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 工程仓库。
 *
 * <p>工作台卡片的默认工程只暴露 {@code id / name / version}（§2.3.2），组件信息侧边栏的工程选择器额外暴露
 * {@code branch / default}（§2.3.3）。以 {@code NON_NULL} 省略未使用的字段，避免列表接口返回冗余数据。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepositoryVO(
        String id,
        String name,
        String version,
        String branch,
        @JsonProperty("default") Boolean isDefault) {

    /** 工作台卡片用的精简形态。 */
    public static RepositoryVO forCard(String id, String name, String version) {
        return new RepositoryVO(id, name, version, null, null);
    }
}
