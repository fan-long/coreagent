/**
 * 工作台组件卡片，见 §3.5.2 与 UI-M01-02。
 *
 * 渲染规则：
 * - enabled === false 时加 .is-disabled 置灰，但保留点击（BR-M01-02）；
 * - tasks 不可用时仅展示数值占位，不渲染口径名称；rules 不可用时展示 0 并附悬停提示；
 * - specGroupName / defaultRepository 为 null 时对应展示项整体不渲染；
 * - 全部字段以 textContent 渲染，不使用 innerHTML 拼接接口数据（防 XSS）。
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

function buildMetric(value, label, options = {}) {
  const metric = element('div', 'metric');
  if (!options.available) {
    metric.classList.add('is-unavailable');
  }
  if (options.reason) {
    metric.title = options.reason;
  }
  metric.appendChild(element('span', 'metric-value', value));
  if (label) {
    metric.appendChild(element('span', 'metric-label', label));
  }
  return metric;
}

function buildMetrics(card) {
  const metrics = element('div', 'card-metrics');
  const source = card.metrics || {};
  const tasks = source.tasks || {};
  const rules = source.rules || {};

  // 阻塞事项数：默认工程缺失时口径不可计算，展示 0
  metrics.appendChild(buildMetric(String(source.blockingIssues ?? 0), '阻塞事项', { available: true }));

  // 任务数量：未设置主任务类型或默认工程不可用时仅展示占位，不展示口径名称
  metrics.appendChild(buildMetric(
    tasks.available ? String(tasks.value ?? 0) : '—',
    tasks.available ? tasks.label : '任务数量',
    { available: Boolean(tasks.available), reason: tasks.unavailableReason }
  ));

  // 规则数量：口径未落地时展示 0 并附悬停说明（PQ-01）
  metrics.appendChild(buildMetric(
    String(rules.value ?? 0),
    rules.label || '规则库',
    { available: Boolean(rules.available), reason: rules.unavailableReason }
  ));

  return metrics;
}

function buildFooter(card) {
  const footer = element('div', 'card-footer');
  if (card.specGroupName) {
    footer.appendChild(element('span', 'card-tag', card.specGroupName));
  }
  footer.appendChild(element('span', 'card-tag', `工程数量 ${card.repositoryCount ?? 0}`));
  if (card.defaultRepository && card.defaultRepository.version) {
    footer.appendChild(element('span', 'card-tag', `默认工程 ${card.defaultRepository.version}`));
  }
  return footer;
}

/**
 * @param {object} card 后端下发的 ComponentCardVO
 * @param {object} options { onOpen(card) }
 * @returns {HTMLElement}
 */
export function createComponentCard(card, options = {}) {
  const button = element('button', 'component-card');
  button.type = 'button';
  button.dataset.componentId = card.id;
  if (!card.enabled) {
    // 置灰但保留点击（BR-M01-02）
    button.classList.add('is-disabled');
  }

  const header = element('div', 'card-head');
  const icon = element('span', 'card-icon',
    card.name ? card.name.slice(0, 1) : '组');
  if (card.icon) {
    icon.dataset.icon = card.icon;
  }
  header.appendChild(icon);

  const titleArea = element('div', 'card-title-area');
  titleArea.appendChild(element('span', 'card-name', card.name));

  const status = element('span', `card-status${card.enabled ? '' : ' is-off'}`,
    card.status || (card.enabled ? '启用' : '未启用'));
  header.appendChild(titleArea);
  header.appendChild(status);
  button.appendChild(header);

  button.appendChild(element('p', 'card-scope', card.scope || ''));
  button.appendChild(buildMetrics(card));
  button.appendChild(buildFooter(card));

  if (typeof options.onOpen === 'function') {
    button.addEventListener('click', () => options.onOpen(card));
  }
  return button;
}
