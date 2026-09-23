package com.musemvp.coreagent.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@link TaskType} 的复合主键 {@code (spec_group_id, code)}（数据模型 v1 §三 3.3）。
 *
 * <p>任务类型编码仅在规格组内唯一，全平台表达任务类型必须携带组维度。
 */
public class TaskTypeId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long specGroupId;

    private String code;

    public TaskTypeId() {
    }

    public TaskTypeId(Long specGroupId, String code) {
        this.specGroupId = specGroupId;
        this.code = code;
    }

    public Long getSpecGroupId() {
        return specGroupId;
    }

    public void setSpecGroupId(Long specGroupId) {
        this.specGroupId = specGroupId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaskTypeId that)) {
            return false;
        }
        return Objects.equals(specGroupId, that.specGroupId) && Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(specGroupId, code);
    }
}
