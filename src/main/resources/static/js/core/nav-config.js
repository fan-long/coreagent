/**
 * 一级导航与环境管理二级导航静态常量（§3.2）。
 *
 * 环境管理的分组与顺序固定（BR-M01-03），不随项目／组件变化，因此以常量声明、不请求服务端；
 * 组件信息二级导航为动态数据，由后端下发（见 SideNav.js 动态分支）。
 */

/** 一级导航：工作台／环境管理（CAP-M01-03）。 */
export const PRIMARY_NAV = [
  { key: 'workbench', name: '工作台', route: '#/workbench' },
  { key: 'env', name: '环境管理', route: '#/env/project' }
];

/** 环境管理二级导航：4 个分组、11 个导航项，顺序固定（BR-M01-03）。 */
export const ENV_NAV_GROUPS = [
  {
    key: 'project-config',
    name: '项目配置',
    items: [
      { key: 'project', name: '项目信息', route: '#/env/project' },
      { key: 'component', name: '应用组件', route: '#/env/component' },
      { key: 'framework', name: '技术框架', route: '#/env/framework' }
    ]
  },
  {
    key: 'knowledge',
    name: '知识库管理',
    items: [
      { key: 'feature', name: '系统特性', route: '#/env/feature' }
    ]
  },
  {
    key: 'system',
    name: '系统配置',
    items: [
      { key: 'model', name: '模型配置', route: '#/env/model' },
      { key: 'agent-integration', name: '通用Agent集成', route: '#/env/agent-integration' },
      { key: 'skill', name: 'Skill配置', route: '#/env/skill' },
      { key: 'agent', name: 'Agent配置', route: '#/env/agent' },
      { key: 'workflow', name: '工作流配置', route: '#/env/workflow' }
    ]
  },
  {
    key: 'template',
    name: '模板管理',
    items: [
      { key: 'app-type', name: '应用类型', route: '#/env/app-type' },
      { key: 'spec-template', name: 'Spec模板', route: '#/env/spec-template' }
    ]
  }
];

/** 角色清单兜底；接口 /api/context 未返回时使用，保证顶栏不空（BR-M01-10 仅展示）。 */
export const ROLE_OPTIONS = [
  { code: 'architect', name: '项目架构师' },
  { code: 'analyst', name: '业务分析人员' },
  { code: 'reviewer', name: '评审人员' },
  { code: 'auditor', name: '审计人员' }
];

/** 按 pageKey 定位环境管理导航项，用于占位页标题。 */
export function findEnvItem(pageKey) {
  for (const group of ENV_NAV_GROUPS) {
    const item = group.items.find((candidate) => candidate.key === pageKey);
    if (item) {
      return { ...item, groupName: group.name };
    }
  }
  return null;
}

/** 在组件信息二级导航响应中按 menuKey 查找菜单项，用于占位页标题与选中态。 */
export function findNavItem(componentNav, menuKey) {
  if (!componentNav || !Array.isArray(componentNav.groups)) {
    return null;
  }
  for (const group of componentNav.groups) {
    for (const item of group.items || []) {
      if (item.key === menuKey) {
        return item;
      }
      const child = (item.children || []).find((candidate) => candidate.key === menuKey);
      if (child) {
        return child;
      }
    }
  }
  return null;
}
