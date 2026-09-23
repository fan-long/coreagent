/**
 * 导航项（含置灰与悬停提示），见 §3.5.4 与 UI-M01-04。
 *
 * 可用性由后端判定并下发 enabled + disabledReason + configPath，前端只渲染不判断（DP-M01-03）；
 * 置灰项保持可悬停但阻止点击（CAP-M01-05 异常预期）。
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

function buildTooltip(item) {
  const tooltip = element('div', 'nav-tooltip');
  tooltip.setAttribute('role', 'tooltip');
  tooltip.appendChild(element('p', 'nav-tooltip-reason', item.disabledReason || '当前入口不可用'));
  if (item.configPath) {
    tooltip.appendChild(element('p', 'nav-tooltip-path', `配置位置：${item.configPath}`));
  }
  return tooltip;
}

function buildButton(item, activeKey, onSelect) {
  const enabled = item.enabled !== false;
  const tagName = enabled ? 'button' : 'div';
  const node = element(tagName, 'nav-item');
  if (enabled) {
    node.type = 'button';
  } else {
    node.classList.add('is-disabled');
    node.setAttribute('aria-disabled', 'true');
    node.tabIndex = 0;
    node.title = item.configPath
      ? `${item.disabledReason || '当前入口不可用'}（配置位置：${item.configPath}）`
      : (item.disabledReason || '当前入口不可用');
  }
  if (item.key === activeKey) {
    node.classList.add('is-active');
    node.setAttribute('aria-current', 'page');
  }

  node.appendChild(element('span', 'nav-item-name', item.name));
  if (item.badge !== null && item.badge !== undefined) {
    node.appendChild(element('span', 'nav-badge', String(item.badge)));
  }

  if (enabled && typeof onSelect === 'function') {
    node.addEventListener('click', () => onSelect(item));
  }
  return { node, enabled };
}

/**
 * 创建导航项节点。
 *
 * @param {object} item 后端下发的 NavItemVO
 * @param {object} options { activeKey, onSelect }
 * @returns {HTMLElement} li 元素（含可选子菜单与提示气泡）
 */
export function createNavItem(item, options = {}) {
  const { activeKey = null, onSelect = null } = options;
  const wrapper = element('li', 'nav-item-wrap');
  const { node, enabled } = buildButton(item, activeKey, onSelect);
  wrapper.appendChild(node);

  if (!enabled) {
    wrapper.appendChild(buildTooltip(item));
  }

  if (Array.isArray(item.children) && item.children.length > 0) {
    const children = element('ul', 'nav-children');
    item.children.forEach((child) => {
      children.appendChild(createNavItem(child, options));
    });

    const toggle = element('button', 'nav-item nav-parent');
    toggle.type = 'button';
    toggle.setAttribute('aria-expanded', 'true');
    toggle.appendChild(element('span', 'nav-item-name', item.name));
    toggle.appendChild(element('span', 'nav-caret', '▾'));
    toggle.addEventListener('click', () => {
      const collapsed = children.classList.toggle('is-collapsed');
      const caret = toggle.querySelector('.nav-caret');
      toggle.setAttribute('aria-expanded', String(!collapsed));
      if (caret) {
        caret.textContent = collapsed ? '▸' : '▾';
      }
    });

    // 父项本身不可点击（route 为 null），仅用于折叠；是否处于选中态由子项体现
    const childActive = item.children.some((child) => child.key === activeKey);
    if (childActive) {
      toggle.classList.add('is-open');
    }
    wrapper.replaceChild(toggle, node);
    wrapper.appendChild(children);
  }
  return wrapper;
}
