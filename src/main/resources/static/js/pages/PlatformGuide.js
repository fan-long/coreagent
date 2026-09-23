/**
 * 平台说明页，见 §3.5.5 与 UI-M01-05 / CAP-M01-07。
 *
 * 左侧提纲由 sections[].title 渲染，点击滚动到 anchor 对应章节；右侧渲染 sections[].content。
 * sections 为空数组时渲染空内容区，不报错（CAP-M01-07 异常预期）。
 */
import { http } from '../core/http.js';
import { renderFailure, renderLoading } from '../core/notice.js';

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

/** 章节锚点：优先使用后端 anchor，缺省时按序号兜底，保证提纲可点击。 */
function anchorOf(section, index) {
  return section.anchor || `section-${index + 1}`;
}

/**
 * 挂载平台说明页。
 *
 * @param {HTMLElement} container #main-view
 * @returns {{destroy: Function}}
 */
export function mountPlatformGuide(container) {
  container.textContent = '';
  let destroyed = false;
  let loadToken = 0;

  const page = element('section', 'page');
  const header = element('header', 'page-header');
  const titleArea = element('div', 'page-title-area');
  const title = element('h1', 'page-title', '平台说明');
  titleArea.appendChild(title);
  header.appendChild(titleArea);
  page.appendChild(header);

  const layout = element('div', 'guide-layout');
  const outline = element('aside', 'guide-outline');
  outline.setAttribute('aria-label', '说明提纲');
  const content = element('div', 'guide-content');
  layout.appendChild(outline);
  layout.appendChild(content);
  page.appendChild(layout);
  container.appendChild(page);

  function renderGuide(guide) {
    const sections = (guide && guide.sections) || [];
    title.textContent = (guide && guide.title) || '平台说明';
    outline.textContent = '';
    content.textContent = '';

    if (sections.length === 0) {
      // 说明文档缺失：渲染空内容区，不报错
      content.appendChild(element('p', 'empty-state', '平台说明内容暂未提供'));
      return;
    }

    const list = element('ul', 'guide-outline-list');
    sections.forEach((section, index) => {
      const anchor = anchorOf(section, index);
      const item = element('li', 'guide-outline-item');
      const link = element('button', 'guide-outline-link', section.title || `第 ${index + 1} 节`);
      link.type = 'button';
      link.addEventListener('click', () => {
        const target = document.getElementById(anchor);
        if (target) {
          target.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      });
      item.appendChild(link);
      list.appendChild(item);

      const article = element('article', 'guide-section');
      article.id = anchor;
      article.appendChild(element('h2', 'guide-section-title', section.title || ''));
      // 说明文档为平台受控内容，按段落拆分后逐段以 textContent 渲染
      String(section.content || '')
        .split(/\n{2,}/)
        .filter((paragraph) => paragraph.trim() !== '')
        .forEach((paragraph) => {
          article.appendChild(element('p', 'guide-paragraph', paragraph.trim()));
        });
      content.appendChild(article);
    });
    outline.appendChild(list);
  }

  async function load() {
    const token = ++loadToken;
    renderLoading(content, '正在加载平台说明…');
    outline.textContent = '';
    try {
      const guide = await http.get('/api/platform-guide');
      if (destroyed || token !== loadToken) {
        return;
      }
      renderGuide(guide);
    } catch (error) {
      if (destroyed || token !== loadToken) {
        return;
      }
      // 文档缺失不阻塞页面结构，仅内容区提示并可重试
      renderFailure(content, error.message || '平台说明加载失败', load);
    }
  }

  load();

  return {
    destroy() {
      destroyed = true;
      loadToken += 1;
      container.textContent = '';
    }
  };
}
