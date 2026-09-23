/**
 * 功能页面占位，见 §3.1 与 §8.3。
 *
 * M01 只负责导航骨架：环境管理 11 个页面与组件信息各功能页由对应模块（M02 及以后）实现。
 * 本模块保留「页面标题 + 上下文说明」结构，使路由跳转后主区域始终有明确落点（BR-M01-11、DP-M01-06）。
 */
import { get as getState } from '../core/store.js';
import { findEnvItem, findNavItem } from '../core/nav-config.js';

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

function buildPage(titleText, subtitleText, rows) {
  const page = element('section', 'page');
  const header = element('header', 'page-header');
  const titleArea = element('div', 'page-title-area');
  titleArea.appendChild(element('h1', 'page-title', titleText));
  if (subtitleText) {
    titleArea.appendChild(element('p', 'page-subtitle', subtitleText));
  }
  header.appendChild(titleArea);
  page.appendChild(header);

  const body = element('div', 'placeholder-body');
  body.appendChild(element('p', 'placeholder-hint', '该页面由后续模块实现，当前仅提供导航与上下文。'));
  if (rows.length > 0) {
    const list = element('dl', 'context-list');
    rows.forEach(([label, value]) => {
      if (value === null || value === undefined || value === '') {
        return;
      }
      list.appendChild(element('dt', 'context-label', label));
      list.appendChild(element('dd', 'context-value', value));
    });
    body.appendChild(list);
  }
  page.appendChild(body);
  return page;
}

/**
 * 挂载占位页面。
 *
 * @param {HTMLElement} container #main-view
 * @param {object} route 当前路由对象
 * @returns {{destroy: Function}}
 */
export function mountPlaceholder(container, route) {
  container.textContent = '';
  const state = getState();

  if (route.name === 'env') {
    const item = findEnvItem(route.params.pageKey);
    const title = item
      ? `${item.groupName} / ${item.name}`
      : '环境管理';
    const page = buildPage(title, '环境管理', [
      ['当前项目', state.projectName],
      ['路由', route.hash]
    ]);
    container.appendChild(page);
  } else {
    const componentNav = state.componentNav || {};
    const component = componentNav.component || {};
    const item = findNavItem(componentNav, route.params.menuKey);
    const title = item ? item.name : (route.params.menuKey || '组件功能页');
    const repositories = componentNav.repositories || [];
    const current = repositories.find(
      (repository) => String(repository.id) === String(state.repositoryId)
    );
    const page = buildPage(title, component.name || '组件信息', [
      ['当前项目', state.projectName],
      ['当前组件', component.name],
      ['当前工程仓库', current ? (current.version ? `${current.name}（${current.version}）` : current.name) : null],
      ['路由', route.hash]
    ]);
    container.appendChild(page);
  }

  return {
    destroy() {
      container.textContent = '';
    }
  };
}
