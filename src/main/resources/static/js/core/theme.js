/**
 * 视觉主题令牌装载器，见详细设计 §4.2、§4.3。
 *
 * 职责：拉取令牌 → 校验 → 原子注入 CSS 自定义属性 → 失败时静默降级到 css/tokens.css 静态基线。
 *
 * 关键点：
 * - 装载**不阻塞**首屏渲染，也不参与 mountView 的路由流程——令牌是全局视觉配置，与视图生命周期无关；
 * - 注入方式为「生成／整体替换单个 <style id="ca-theme-runtime">」，一次 DOM 操作原子生效，
 *   既不产生逐个 setProperty 的中间态闪烁，也便于失败时整体移除回退到基线；
 * - 取值在服务端已校验一次，此处**再校验一次**：令牌值最终会写入 <style> 元素，任何一环失守
 *   都可能形成 CSS 注入面（§6.4 纵深防御）。
 */
import { http } from './http.js';
import { showToast } from './notice.js';

const TOKEN_PATH = '/api/theme/tokens';
const STYLE_ID = 'ca-theme-runtime';
const DEFAULT_THEME = 'dark-gold';
const FAILURE_MESSAGE = '主题令牌加载失败，已使用默认黑金主题';

/** 分组白名单：与后端 TokenGroup 枚举一致（§3.4.2）。 */
const GROUPS = ['color', 'font', 'space', 'radius', 'border', 'shadow', 'motion'];

/** 令牌名称：kebab-case，与后端 TokenValidator.NAME 同规则。 */
const NAME_PATTERN = /^[a-z][a-z0-9]*(-[a-z0-9]+)*$/;

const HEX = /^#[0-9a-fA-F]{6}$/;
const RGBA = /^rgba\(\d{1,3},\d{1,3},\d{1,3},(0|1|0?\.\d+)\)$/;
const GRADIENT_ANGLE = /^\d{1,3}deg$/;
const PERCENT = /^\d{1,3}%$/;
const PX = /^\d+(\.\d+)?px$/;
const REM = /^\d*(\.\d+)?rem$/;
const WEIGHT = /^\d{3}$/;
const UNITLESS = /^\d+(\.\d+)?$/;
const MS = /^\d+ms$/;
const EASE = /^ease(-in|-out|-in-out)?$/;
const CUBIC_BEZIER = /^cubic-bezier\(([01]|0?\.\d+),([01]|0?\.\d+),([01]|0?\.\d+),([01]|0?\.\d+)\)$/;
const VAR_REF = /^var\(--ca-[a-z0-9-]+\)$/;
const BORDER_SHORTHAND = /^\d+px (solid|dashed) [^;{}<>]+$/;
const SHADOW_LENGTH = /^(0|-?\d+(\.\d+)?px)$/;
const FONT_FAMILY = /^[A-Za-z0-9 ,\-_. "']+$/;
const FONT_FEATURE = /^"[a-z]{4}"\s+(0|1|on|off)(,\s*"[a-z]{4}"\s+(0|1|on|off))*$/;

/** 注入的令牌值一律不得含分号、花括号、尖括号（§6.4）。 */
const DANGEROUS = /[;{}<>]/;

let inflight = null;
let loadedVersion = null;
let lastResult = null;
let activeTheme = null;
const listeners = new Set();

/**
 * 装载主题令牌。
 *
 * 幂等：重复调用共享同一次请求；同一 version 重复装载直接返回缓存结果。
 * 任何失败都不抛出——令牌装载失败不影响任何业务功能（CAP-M01-10 异常预期）。
 *
 * @returns {Promise<{theme: string, version: string|null, applied: number, warnings: Array}>}
 */
export async function loadTheme() {
  if (inflight) {
    return inflight;
  }
  inflight = doLoad().finally(() => {
    inflight = null;
  });
  return inflight;
}

/** 当前生效主题标识；未装载时为 null。 */
export function getActiveTheme() {
  return activeTheme;
}

/**
 * 订阅主题变更（供后续图表等需要读取令牌值的场景）。
 *
 * @param {Function} listener 回调，入参为 loadTheme 的返回值
 * @returns {Function} 取消订阅
 */
export function subscribeTheme(listener) {
  if (typeof listener !== 'function') {
    return () => {};
  }
  listeners.add(listener);
  return () => listeners.delete(listener);
}

async function doLoad() {
  let payload;
  try {
    payload = await http.get(TOKEN_PATH);
  } catch (error) {
    // 503 M01-E007、超时与网络异常同一处理：保留静态基线渲染，提示失败，不白屏（§4.9）
    console.warn(`[theme] 令牌接口不可用：${error && error.message}`);
    return degrade();
  }

  if (!payload || !Array.isArray(payload.tokens)) {
    console.warn('[theme] 令牌响应结构非法，已保留静态基线');
    return degrade();
  }

  if (loadedVersion !== null && payload.version === loadedVersion) {
    return lastResult;
  }

  const { css, applied, warnings } = buildDeclarations(payload);
  if (applied === 0) {
    console.warn('[theme] 没有任何合法令牌可用，已保留静态基线');
    return degrade();
  }

  warnings.forEach((warning) => {
    console.warn(`[theme] 令牌已回退：group=${warning.group} name=${warning.name} reason=${warning.reason}`);
  });

  injectRuntimeStyle(css);
  activeTheme = payload.theme || DEFAULT_THEME;
  document.documentElement.dataset.theme = activeTheme;
  loadedVersion = payload.version || null;
  lastResult = {
    theme: activeTheme,
    version: payload.version || null,
    applied,
    warnings
  };
  listeners.forEach((listener) => listener(lastResult));
  return lastResult;
}

/** 整包失败：移除运行时样式（回到 tokens.css 基线）+ 用户可见提示，不静默（§6.5）。 */
function degrade() {
  removeRuntimeStyle();
  activeTheme = DEFAULT_THEME;
  document.documentElement.dataset.theme = DEFAULT_THEME;
  showToast(FAILURE_MESSAGE, 'warn');
  lastResult = { theme: DEFAULT_THEME, version: null, applied: 0, warnings: [] };
  return lastResult;
}

/**
 * 校验令牌并按组展开为 CSS 自定义属性声明。
 *
 * 非法令牌**跳过该项**（沿用基线值）、计入 warnings，其余令牌正常生效——单个令牌非法不构成整包失败。
 */
function buildDeclarations(payload) {
  const declarations = [];
  const warnings = [];
  const seen = new Set();

  payload.tokens.forEach((token) => {
    const group = token ? token.group : null;
    const name = token ? token.name : null;
    if (!GROUPS.includes(group)) {
      warnings.push(warning(group, name, 'TOKEN_GROUP_INVALID'));
      return;
    }
    if (typeof name !== 'string' || !NAME_PATTERN.test(name) || seen.has(`${group}:${name}`)) {
      warnings.push(warning(group, name, 'TOKEN_NAME_INVALID'));
      return;
    }
    seen.add(`${group}:${name}`);
    // 全局令牌不得被模块覆盖：本期只接受 GLOBAL（§2.5、§4.3 分组白名单）
    if (token.scope !== 'GLOBAL') {
      warnings.push(warning(group, name, 'TOKEN_SCOPE_INVALID'));
      return;
    }
    if (typeof token.value !== 'string' || !isValidValue(group, name, token.value)) {
      warnings.push(warning(group, name, 'TOKEN_VALUE_INVALID'));
      return;
    }
    declarations.push(`--ca-${name}:${token.value}`);
  });

  const css = declarations.length > 0 ? `:root{${declarations.join(';')}}` : '';
  return { css, applied: declarations.length, warnings };
}

function warning(group, name, reason) {
  return { group: group || null, name: name || null, reason };
}

/** 原子注入：整体替换单个 <style> 元素，追加在 <head> 末尾以高于 tokens.css 基线。 */
function injectRuntimeStyle(css) {
  let style = document.getElementById(STYLE_ID);
  if (!style) {
    style = document.createElement('style');
    style.id = STYLE_ID;
    document.head.appendChild(style);
  }
  style.textContent = css;
}

function removeRuntimeStyle() {
  const style = document.getElementById(STYLE_ID);
  if (style) {
    style.remove();
  }
}

/** 组内取值白名单校验，规则与后端 TokenValidator 一一对应（§3.4.2）。 */
function isValidValue(group, name, value) {
  if (DANGEROUS.test(value)) {
    return false;
  }
  switch (group) {
    case 'color':
      return isColor(value) || isLinearGradient(value);
    case 'font':
      return isFontValue(name, value);
    case 'space':
      return isSpaceValue(value);
    case 'radius':
      return PX.test(value);
    case 'border':
      return isBorderValue(name, value);
    case 'shadow':
      return isShadowValue(value);
    case 'motion':
      return isMotionValue(name, value);
    default:
      return false;
  }
}

function isColor(value) {
  return HEX.test(value) || RGBA.test(value);
}

/** 金色渐变：角度段固定为 {n}deg，色标段固定为「合法色值 + 百分比」，不含自由文本。 */
function isLinearGradient(value) {
  if (!value.startsWith('linear-gradient(') || !value.endsWith(')')) {
    return false;
  }
  const parts = splitTopLevel(value.slice('linear-gradient('.length, -1));
  if (parts.length < 3 || !GRADIENT_ANGLE.test(parts[0].trim())) {
    return false;
  }
  return parts.slice(1).every((stop) => {
    const pair = stop.trim().split(/\s+/);
    return pair.length === 2 && isColor(pair[0]) && PERCENT.test(pair[1]);
  });
}

function isFontValue(name, value) {
  if (name.includes('font-family')) {
    return FONT_FAMILY.test(value) && /[A-Za-z]/.test(value);
  }
  if (name.includes('font-size')) {
    return REM.test(value);
  }
  if (name.includes('font-weight')) {
    return WEIGHT.test(value) && Number(value) >= 100 && Number(value) <= 900;
  }
  if (name.includes('font-line-height')) {
    return UNITLESS.test(value);
  }
  if (name.includes('font-numeric-feature')) {
    return FONT_FEATURE.test(value);
  }
  return false;
}

/** 间距须为 px 且为 4 的正整数倍。 */
function isSpaceValue(value) {
  if (!PX.test(value)) {
    return false;
  }
  const px = Number(value.slice(0, -2));
  return px > 0 && px % 4 === 0;
}

function isBorderValue(name, value) {
  if (name.startsWith('border-width-')) {
    return PX.test(value);
  }
  if (!BORDER_SHORTHAND.test(value)) {
    return false;
  }
  const color = value.slice(value.lastIndexOf(' ') + 1);
  return VAR_REF.test(color) || isColor(color);
}

function isShadowValue(value) {
  if (value === 'none') {
    return true;
  }
  return splitTopLevel(value).every((layer) => {
    const parts = layer.trim().split(/\s+/);
    if (parts.length < 3 || parts.length > 5) {
      return false;
    }
    const lengths = parts.slice(0, -1).every((part) => SHADOW_LENGTH.test(part));
    return lengths && isColor(parts[parts.length - 1]);
  });
}

function isMotionValue(name, value) {
  if (name.includes('duration')) {
    return MS.test(value);
  }
  if (name.includes('ease')) {
    return EASE.test(value) || CUBIC_BEZIER.test(value);
  }
  return false;
}

/** 按括号深度切分顶层逗号：颜色函数与 var 引用内部的逗号不能当作分隔符。 */
function splitTopLevel(value) {
  const parts = [];
  let depth = 0;
  let start = 0;
  for (let index = 0; index < value.length; index += 1) {
    const current = value.charAt(index);
    if (current === '(') {
      depth += 1;
    } else if (current === ')') {
      depth -= 1;
    } else if (current === ',' && depth === 0) {
      parts.push(value.slice(start, index));
      start = index + 1;
    }
  }
  parts.push(value.slice(start));
  return parts;
}
