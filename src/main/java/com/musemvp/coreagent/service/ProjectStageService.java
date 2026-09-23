package com.musemvp.coreagent.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musemvp.coreagent.support.BizException;
import com.musemvp.coreagent.support.ErrorCode;
import com.musemvp.coreagent.support.StageCatalog;

/**
 * 工程阶段规则构件，见详细设计 D-M02-03（M02）。
 *
 * <p>本构件是工程阶段的<b>唯一</b>解析、规范化、校验、序列化与回落入口：读取路径调用
 * {@link #parse} + {@link #fallback}，保存路径调用 {@link #normalize} + {@link #validateLocked}
 * + {@link #serialize}，保证「读到的」与「要保存的」按同一口径解释（D-M02-12/P-07 的跨接口一致性）。
 *
 * <p>纯规则构件：无数据访问、不写库、不管事务；唯一副作用是解析异常时的 {@code warn} 日志。
 */
@Service
public class ProjectStageService {

    private static final Logger log = LoggerFactory.getLogger(ProjectStageService.class);

    private final ObjectMapper objectMapper;

    public ProjectStageService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析阶段文本（D-M02-03/C-01）。
     *
     * <p>文本以 {@code [} 起始时按 JSON 字符串数组解析，否则按 {@code [,;，；\n]} 拆分；
     * 逐项裁剪首尾空白、忽略空白项、保序去重。{@code null} 或空白文本返回空集合。
     *
     * <p>解析异常返回空集合且记录 {@code warn}，<b>不抛异常</b>（沿用既有容忍策略，避免读取路径
     * 因存量脏数据整体失败）；回落由调用方决定（{@link #fallback}）。
     */
    public List<String> parse(String rawStages) {
        if (!StringUtils.hasText(rawStages)) {
            return List.of();
        }
        String trimmed = rawStages.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<String> parsed = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {
                });
                return normalize(parsed);
            } catch (Exception ex) {
                // 只记录长度，不记录原文，避免脏数据刷日志（D-M02-03/4.5）
                log.warn("project_info.stages 不是合法 JSON 数组，按分隔符解析：length={}", trimmed.length());
            }
        }
        return normalize(Arrays.asList(trimmed.split(StageCatalog.STAGE_SPLIT_REGEX)));
    }

    /**
     * 规范化阶段集合（D-M02-03/C-02）。
     *
     * <p>逐项裁剪首尾空白 → 忽略裁剪后为空的项 → 按输入顺序去重（保留首个出现的项）。
     * 比较口径为区分大小写与全半角的精确字符串比较（Q-M02-09 未裁决，见 DQ-M02-09）；
     * 输入顺序不被改变。
     */
    public List<String> normalize(List<String> stages) {
        if (stages == null || stages.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String stage : stages) {
            if (stage == null) {
                continue;
            }
            String trimmed = stage.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            normalized.add(trimmed);
        }
        return List.copyOf(normalized);
    }

    /**
     * 锁定阶段校验（D-M02-03/C-03，RC-M02-07）。
     *
     * <p>DEF-M02-06 的四项锁定阶段必须全部存在；缺失时抛出 {@link ErrorCode#M02_E003}，
     * 消息列出<b>全部</b>缺失项，顺序按 DEF-M02-06 的固定顺序。
     *
     * <p>本条优先于阶段改名判定：调用方必须先校验锁定阶段，再进入改名迁移（D-M02-04/C-05）。
     */
    public void validateLocked(List<String> stages) {
        List<String> missing = missingLocked(stages);
        if (missing.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder();
        for (String name : missing) {
            if (message.length() > 0) {
                // 多缺失项的拼接形态需求未规定（DQ-M02-20），本设计按固定顺序以「；」分隔逐项拼接
                message.append('；');
            }
            message.append("工程阶段「").append(name).append("」为锁定阶段，不可删除或改名");
        }
        throw new BizException(ErrorCode.M02_E003, message.toString());
    }

    /** 计算缺失的锁定阶段，顺序按 {@link StageCatalog#LOCKED_STAGES}。 */
    public List<String> missingLocked(List<String> stages) {
        List<String> present = stages == null ? List.of() : stages;
        List<String> missing = new ArrayList<>();
        for (String locked : StageCatalog.LOCKED_STAGES) {
            if (!present.contains(locked)) {
                missing.add(locked);
            }
        }
        return missing;
    }

    /** 序列化为英文逗号分隔文本（D-M02-03/C-04，RC-M02-04）。 */
    public String serialize(List<String> stages) {
        return String.join(StageCatalog.STAGE_SEPARATOR, stages == null ? List.<String>of() : stages);
    }

    /**
     * 读取回落到默认阶段序列（D-M02-03/C-05，RC-M02-05）。
     *
     * <p>仅用于读取路径，<b>不回写存储</b>（DD-M02-04）；非空集合原样返回，不得报错。
     * 保存路径不得调用本方法——新增项目的预设阶段由调用方显式取
     * {@link StageCatalog#DEFAULT_STAGES}（RC-M02-19 与 RC-M02-05 分别适用）。
     */
    public List<String> fallback(List<String> stages) {
        return stages == null || stages.isEmpty() ? StageCatalog.DEFAULT_STAGES : stages;
    }
}
