/**
 * 轻提示与失败块（§3.7 前端异常与降级）。
 *
 * 设计文档未单列该文件，但「加载失败保留页面结构 + 重试」「组件不存在提示后返回工作台」
 * 「工程仓库非法回退并提示」三处降级均需要统一的提示组件，故集中于此，避免各页面各写一份。
 * 所有文案以 textContent 写入，不拼接 HTML。
 */

function element(tagName, className, text) {
  const node = document.createElement(tagName);
  if (className) {
    node.className = className;
  }
  if (text !== undefined && text !== null) {
    node.textContent = text;
  }
  return node;
}

let toastRoot = null;

function ensureToastRoot() {
  if (!toastRoot) {
    toastRoot = element('div', 'toast-root');
    toastRoot.setAttribute('role', 'status');
    toastRoot.setAttribute('aria-live', 'polite');
    document.body.appendChild(toastRoot);
  }
  return toastRoot;
}

/**
 * 右下角轻提示。
 *
 * @param {string} message 提示文案
 * @param {'info'|'warn'|'error'} type 提示级别
 */
export function showToast(message, type = 'info', timeout = 3200) {
  const root = ensureToastRoot();
  const toast = element('div', `toast is-${type}`, message);
  root.appendChild(toast);
  window.setTimeout(() => {
    toast.classList.add('is-leaving');
    window.setTimeout(() => toast.remove(), 240);
  }, timeout);
}

/**
 * 渲染加载中占位（骨架屏）。
 *
 * @param {HTMLElement} container 承载容器
 * @param {string} [message] 辅助文案
 * @param {number} [skeletonCount] 骨架块数量，0 表示不渲染骨架
 */
export function renderLoading(container, message = '正在加载…', skeletonCount = 0) {
  container.textContent = '';
  if (skeletonCount > 0) {
    const grid = element('div', 'skeleton-grid');
    for (let index = 0; index < skeletonCount; index += 1) {
      grid.appendChild(element('div', 'skeleton-card'));
    }
    container.appendChild(grid);
  }
  const hint = element('p', 'loading-hint', message);
  container.appendChild(hint);
  return container;
}

/**
 * 渲染失败块：保留外层结构，仅替换内容区，并提供重试入口（M01-E006）。
 *
 * @param {HTMLElement} container 承载容器
 * @param {string} message 失败原因
 * @param {Function} [onRetry] 重试回调；缺省时不渲染重试按钮
 */
export function renderFailure(container, message, onRetry) {
  container.textContent = '';
  const block = element('div', 'failure-block');
  block.appendChild(element('p', 'failure-message', message || '加载失败，请稍后重试'));
  if (typeof onRetry === 'function') {
    const retry = element('button', 'button button-secondary', '重试');
    retry.type = 'button';
    retry.addEventListener('click', onRetry);
    block.appendChild(retry);
  }
  container.appendChild(block);
  return container;
}
