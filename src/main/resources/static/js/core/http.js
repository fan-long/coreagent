/**
 * 统一请求封装（§3.7）：注入 X-Project-Id、10s 超时、错误映射。
 *
 * 后端统一错误响应体为 { code, message, timestamp, traceId }（§2.7），前端据 code 做差异化提示。
 */
import { get as getState } from './store.js';

const TIMEOUT_MS = 10000;

export class HttpError extends Error {
  constructor(message, { code = null, status = 0 } = {}) {
    super(message);
    this.name = 'HttpError';
    this.code = code;
    this.status = status;
  }
}

async function parseBody(response) {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch (error) {
    return null;
  }
}

export async function request(path, { method = 'GET', body } = {}) {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), TIMEOUT_MS);
  const headers = { Accept: 'application/json' };
  const { projectId } = getState();
  if (projectId !== null && projectId !== undefined) {
    headers['X-Project-Id'] = String(projectId);
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json; charset=UTF-8';
  }

  try {
    const response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: 'no-store',
      signal: controller.signal
    });
    const payload = await parseBody(response);
    if (!response.ok) {
      throw new HttpError((payload && payload.message) || `请求失败（HTTP ${response.status}）`, {
        code: payload && payload.code,
        status: response.status
      });
    }
    return payload;
  } catch (error) {
    if (error instanceof HttpError) {
      throw error;
    }
    if (error.name === 'AbortError') {
      throw new HttpError('请求超时，请稍后重试', { code: 'TIMEOUT' });
    }
    throw new HttpError('网络异常，请确认服务是否可用', { code: 'NETWORK' });
  } finally {
    window.clearTimeout(timer);
  }
}

export const http = {
  get: (path) => request(path),
  post: (path, body) => request(path, { method: 'POST', body }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  // 删除类请求：无请求体，故不传 body（D-M04-12/C-02）。既有 get/post/put 语义不变。
  del: (path) => request(path, { method: 'DELETE' })
};

/** 拼接查询参数，自动跳过空值。 */
export function withQuery(path, params) {
  const search = new URLSearchParams();
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') {
      search.set(key, value);
    }
  });
  const queryString = search.toString();
  return queryString ? `${path}?${queryString}` : path;
}
