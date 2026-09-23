package com.musemvp.coreagent.support;

/**
 * 接口层共用的请求头约定与解析工具。
 *
 * <p>当前项目为前端会话态（见详细设计 §2.4），通过 {@link #PROJECT_ID} 显式传递，后端不隐式持有用户状态
 * （DP-M01-05）。
 */
public final class ApiHeaders {

    /** 当前项目上下文请求头。 */
    public static final String PROJECT_ID = "X-Project-Id";

    private ApiHeaders() {
    }

    /**
     * 解析可选的 {@code X-Project-Id}。
     *
     * <p>缺失、非数字或非正数一律返回 {@code null}，交由 {@code ProjectService#resolveCurrent} 回退默认项目，
     * 保证直接访问 URL、清除 localStorage 等场景不白屏（§2.4 后端兜底策略）。
     */
    public static Long parseProjectId(String rawHeaderValue) {
        if (rawHeaderValue == null) {
            return null;
        }
        try {
            long value = Long.parseLong(rawHeaderValue.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
