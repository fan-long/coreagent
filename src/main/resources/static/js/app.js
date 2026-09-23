(() => {
  'use strict';

  const elements = {
    headerStatus: document.querySelector('#header-status'),
    summaryMessage: document.querySelector('#summary-message'),
    applicationName: document.querySelector('#application-name'),
    applicationStatus: document.querySelector('#application-status'),
    serverTime: document.querySelector('#server-time'),
    responseTime: document.querySelector('#response-time'),
    requestState: document.querySelector('#request-state'),
    responseOutput: document.querySelector('#response-output'),
    refreshButton: document.querySelector('#refresh-button')
  };

  const setBadge = (text, state) => {
    elements.headerStatus.textContent = text;
    elements.headerStatus.className = `status-badge is-${state}`;
  };

  const formatTime = (timestamp) => {
    const date = new Date(timestamp);
    return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN', { hour12: false });
  };

  async function loadStatus() {
    const startedAt = performance.now();
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 5000);

    elements.refreshButton.disabled = true;
    elements.requestState.textContent = '请求中';
    elements.summaryMessage.textContent = '正在调用本应用 API...';
    setBadge('连接中', 'loading');

    try {
      const response = await fetch('./api/status', {
        headers: { Accept: 'application/json' },
        cache: 'no-store',
        signal: controller.signal
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const data = await response.json();
      const elapsed = Math.max(1, Math.round(performance.now() - startedAt));
      elements.applicationName.textContent = data.application || '-';
      elements.applicationStatus.textContent = data.status || '-';
      elements.serverTime.textContent = formatTime(data.timestamp);
      elements.responseTime.textContent = `${elapsed} ms`;
      elements.responseOutput.textContent = JSON.stringify(data, null, 2);
      elements.summaryMessage.textContent = data.message || 'API 调用成功';
      elements.requestState.textContent = '请求成功';
      setBadge(data.status === 'UP' ? '运行正常' : data.status, data.status === 'UP' ? 'online' : 'warning');
    } catch (error) {
      const message = error.name === 'AbortError' ? 'API 请求超时' : `API 调用失败：${error.message}`;
      elements.applicationName.textContent = '-';
      elements.applicationStatus.textContent = '不可用';
      elements.serverTime.textContent = '-';
      elements.responseTime.textContent = '-';
      elements.responseOutput.textContent = JSON.stringify({ error: message }, null, 2);
      elements.summaryMessage.textContent = message;
      elements.requestState.textContent = '请求失败';
      setBadge('连接失败', 'offline');
    } finally {
      window.clearTimeout(timeout);
      elements.refreshButton.disabled = false;
    }
  }

  elements.refreshButton.addEventListener('click', loadStatus);
  loadStatus();
})();
