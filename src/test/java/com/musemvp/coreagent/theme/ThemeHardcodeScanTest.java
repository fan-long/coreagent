package com.musemvp.coreagent.theme;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 硬编码色值静态检查门禁（DESIGN-FT-002 §4 D-018，对照 AC-FT002-01（V-001）与 AC-FT002-30）。
 *
 * <p>扫描 {@code src/main/resources/static/**} 下全部 {@code .css}、{@code .html}、{@code .js}，
 * 出现 {@code #hex}、{@code rgb()}、{@code rgba()}、{@code hsl()} 字面量即记为违规。
 * 界面颜色必须 100% 由令牌承载（BR-M01-16），否则「改令牌即换肤」的承诺不成立。
 *
 * <p>白名单仅有两类，且都是令牌体系的必要组成：
 * <ol>
 *   <li>{@code css/tokens.css}、{@code css/tokens-light.css}、{@code css/print.css}——
 *       令牌取值本身就在这里定义，硬编码是它们的职责（D-018/C-02 白名单条款）；</li>
 *   <li>{@code js/core/theme.js} 中的**校验正则定义行**（形如 {@code const HEX = /.../;}）——
 *       这些正则存在的意义就是识别颜色字面量，其源码必然包含 {@code #}、{@code rgba(} 等片段。
 *       设计原文为「按行号白名单」，此处收紧为「按行形态白名单」：只豁免 {@code = /.../} 形式的
 *       常量定义行，theme.js 其余任意一行新增颜色字面量仍会失败，且不随文件行号漂移而失效。</li>
 * </ol>
 *
 * <p>失败策略：默认失败；{@code -Dtheme.scan.skip=true} 临时跳过并把违规清单打到构建日志，
 * 供「待适配清单」维护（D-018/C-04）。
 */
class ThemeHardcodeScanTest {

    /** 扫描根目录，相对模块根（Surefire 的工作目录即模块根）。 */
    private static final Path STATIC_ROOT = Path.of("src", "main", "resources", "static");

    /** 跳过开关：true 时只输出清单、不失败。 */
    private static final String SKIP_PROPERTY = "theme.scan.skip";

    /** 允许出现字面量的文件（令牌定义文件，见类注释）。 */
    private static final List<String> WHITELISTED_FILES = List.of(
            "css/tokens.css",
            "css/tokens-light.css",
            "css/print.css"
    );

    /** theme.js 中定义校验正则的行：`const XXX = /.../;`。 */
    private static final String VALIDATION_REGEX_FILE = "js/core/theme.js";
    private static final Pattern REGEX_DEFINITION_LINE = Pattern.compile("^\\s*const\\s+[A-Z0-9_]+\\s*=\\s*/.*/;\\s*$");

    /** `#rgb` / `#rgba` / `#rrggbb` / `#rrggbbaa`；前缀 `&` 的 HTML 实体（如 `&#123;`）不算。 */
    private static final Pattern HEX_LITERAL = Pattern.compile("(?<!&)#[0-9a-fA-F]{3,8}(?![0-9a-fA-F])");

    /** `rgb(` / `rgba(` / `hsl(` / `hsla(` 函数式颜色字面量。 */
    private static final Pattern FUNCTION_LITERAL = Pattern.compile("(?<![a-zA-Z-])(rgba?|hsla?)\\(");

    private static final List<String> SCANNED_SUFFIXES = List.of(".css", ".html", ".js");

    /** 扫描目录必须至少覆盖这些文件，否则说明路径解析有误、门禁形同虚设。 */
    private static final List<String> SANITY_FILES = List.of(
            "index.html",
            "css/tokens.css",
            "css/app.css",
            "css/layout.css",
            "css/print.css",
            "js/core/theme.js"
    );

    @Test
    void staticAssetsMustNotHardcodeColors() throws IOException {
        List<Path> targets = collectTargets();
        assertSanity(targets);

        List<Violation> violations = new ArrayList<>();
        for (Path file : targets) {
            String relative = STATIC_ROOT.relativize(file).toString().replace('\\', '/');
            if (WHITELISTED_FILES.contains(relative)) {
                continue;
            }
            scan(relative, file, violations);
        }

        if (violations.isEmpty()) {
            return;
        }

        String report = render(violations, targets.size());
        if (Boolean.parseBoolean(System.getProperty(SKIP_PROPERTY, "false"))) {
            // 跳过模式下不作为门禁，但必须留下完整清单，否则「临时跳过」会退化成「永久失明」
            System.out.println(report);
            return;
        }
        fail(report);
    }

    /** 收集扫描目标：静态目录下全部 .css/.html/.js。 */
    private List<Path> collectTargets() throws IOException {
        assertTrue(Files.isDirectory(STATIC_ROOT),
                "静态资源目录不存在，无法执行硬编码色值门禁：" + STATIC_ROOT.toAbsolutePath());
        try (Stream<Path> stream = Files.walk(STATIC_ROOT)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> SCANNED_SUFFIXES.stream()
                            .anyMatch(suffix -> path.getFileName().toString().endsWith(suffix)))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    /** 防呆：路径写错时 collectTargets 可能返回空集而「零违规通过」，此处显式挡住。 */
    private void assertSanity(List<Path> targets) {
        List<String> relativePaths = targets.stream()
                .map(path -> STATIC_ROOT.relativize(path).toString().replace('\\', '/'))
                .toList();
        for (String required : SANITY_FILES) {
            assertTrue(relativePaths.contains(required),
                    "门禁未扫描到预期文件 " + required + "，扫描范围可能失效，实际：" + relativePaths);
        }
    }

    private void scan(String relative, Path file, List<Violation> violations) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        boolean regexWhitelistFile = VALIDATION_REGEX_FILE.equals(relative);

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            // 白名单②：theme.js 的校验正则定义行，其源码本就含颜色字面量片段
            if (regexWhitelistFile && REGEX_DEFINITION_LINE.matcher(line).matches()) {
                continue;
            }
            List<String> found = literalsIn(line);
            if (found.isEmpty()) {
                continue;
            }
            // 同一行可能有多个字面量（如 `border: 1px solid #ccc; color: #333`），逐条列出便于改造
            String context = contextOf(lines, index, relative);
            for (String literal : found) {
                violations.add(new Violation(relative, index + 1, literal, context));
            }
        }
    }

    /** 该行命中的全部字面量原文，按出现位置排序。 */
    private List<String> literalsIn(String line) {
        List<int[]> positions = new ArrayList<>();
        List<String> values = new ArrayList<>();
        Matcher hex = HEX_LITERAL.matcher(line);
        while (hex.find()) {
            positions.add(new int[]{hex.start(), values.size()});
            values.add(hex.group());
        }
        Matcher function = FUNCTION_LITERAL.matcher(line);
        while (function.find()) {
            positions.add(new int[]{function.start(), values.size()});
            values.add(function.group() + ")");
        }
        positions.sort(Comparator.comparingInt(position -> position[0]));
        return positions.stream().map(position -> values.get(position[1])).toList();
    }

    /** 上下文：CSS 取最近的一条选择器行，其余取本行原文，便于直接定位改造点。 */
    private String contextOf(List<String> lines, int index, String relative) {
        String trimmed = lines.get(index).trim();
        if (!relative.endsWith(".css")) {
            return trimmed;
        }
        for (int cursor = index; cursor >= 0; cursor--) {
            String candidate = lines.get(cursor).trim();
            if (candidate.endsWith("{")) {
                return candidate;
            }
        }
        return trimmed;
    }

    private String render(List<Violation> violations, int scannedFiles) {
        StringBuilder builder = new StringBuilder();
        builder.append(System.lineSeparator())
                .append("硬编码色值门禁失败（DESIGN-FT-002 D-018/C-01 / BR-M01-16）：扫描 ")
                .append(scannedFiles).append(" 个静态资源，命中 ")
                .append(violations.size()).append(" 处。").append(System.lineSeparator())
                .append("请改用 var(--ca-*) 令牌；确需新增令牌值时，写入 css/tokens.css 与")
                .append(" src/main/resources/theme/*.json。").append(System.lineSeparator())
                .append("临时跳过：-D").append(SKIP_PROPERTY).append("=true").append(System.lineSeparator());
        for (Violation violation : violations) {
            builder.append(System.lineSeparator())
                    .append("  ").append(violation.file())
                    .append(':').append(violation.line())
                    .append("  值=").append(violation.value())
                    .append(System.lineSeparator())
                    .append("      上下文：").append(violation.context())
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    /** 违规项：文件、行号、原始色值、上下文。 */
    private record Violation(String file, int line, String value, String context) {

        private Violation {
            if (file == null || value == null || context == null) {
                throw new IllegalArgumentException("违规项字段不得为空");
            }
        }

        @Override
        public String toString() {
            return String.format("%s:%d [%s] %s", file, line, value, context);
        }
    }
}
