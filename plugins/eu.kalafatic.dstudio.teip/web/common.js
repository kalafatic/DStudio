/* DStudio inference web client — DStudio's own objects over its own API.
 *
 * The visual language is shared with the DCode web UI, but these are DStudio's
 * objects: inference engines, sessions, projects and harnesses from the DStudio
 * backend (/api/status, /api/inference, /api/workspace, /api/diagnostics) plus the
 * desktop bridge for settings that only the RCP application can persist.
 */
window.Inference = (function () {
  "use strict";

  const TOKEN_KEY = "dstudio.web.token";
  const THEME_KEY = "dstudio.theme.v1";
  const state = { backend: "dstudio", meta: {} };

  function $(id) { return document.getElementById(id); }

  // ---- shared UI helpers (used by index.html and settings.html) ----

  function esc(value) {
    return String(value == null ? "" : value)
        .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function formatBytes(n) {
    if (!n || n <= 0) { return ""; }
    const units = ["B", "KB", "MB", "GB", "TB"];
    let value = n, index = 0;
    while (value >= 1024 && index < units.length - 1) { value /= 1024; index++; }
    return value.toFixed(value >= 100 || index === 0 ? 0 : 1) + " " + units[index];
  }

  function fillSelect(select, items, selectedId, emptyLabel) {
    select.innerHTML = "";
    if (!items.length) {
      const option = document.createElement("option");
      option.value = "";
      option.textContent = emptyLabel || "—";
      select.appendChild(option);
      return;
    }
    for (const item of items) {
      const option = document.createElement("option");
      option.value = item.id;
      option.textContent = item.label;
      if (item.providerId) { option.dataset.providerId = item.providerId; }
      select.appendChild(option);
    }
    if (selectedId && items.some((item) => item.id === selectedId)) {
      select.value = selectedId;
    }
  }

  let toastTimer = null;
  function toast(text, isError) {
    const el = $("toast");
    if (!el) { return; }
    el.textContent = text;
    el.className = "toast" + (isError ? " error" : "");
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 4200);
  }

  /** Applies the persisted theme; when buttonId is given the button becomes the toggle. */
  function applyTheme(buttonId) {
    let theme = "dark";
    try { theme = localStorage.getItem(THEME_KEY) || "dark"; } catch (e) { /* ignore */ }
    document.documentElement.dataset.theme = theme;
    const button = buttonId ? $(buttonId) : null;
    if (!button) { return; }
    button.onclick = () => {
      const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
      document.documentElement.dataset.theme = next;
      try { localStorage.setItem(THEME_KEY, next); } catch (e) { /* ignore */ }
    };
  }

  /**
   * Minimal, XSS-safe Markdown renderer for chat output: fenced code blocks,
   * inline code, bold, italic, links, ordered/unordered lists and paragraphs.
   * No external dependencies; everything is escaped before formatting.
   */
  function renderMarkdown(text) {
    if (text == null) { return ""; }
    let source = String(text);
    const blocks = [];
    source = source.replace(/```([\w+-]*)\n?([\s\S]*?)(```|$)/g, (match, lang, code) => {
      blocks.push("<pre><code class=\"md-code\">" + esc(code.replace(/\n$/, ""))
          + "</code></pre>");
      return "\u0000B" + (blocks.length - 1) + "\u0000";
    });
    source = esc(source);
    source = source.replace(/`([^`\n]+)`/g, "<code class=\"md-inline\">$1</code>");
    source = source.replace(/\*\*([^*\n]+)\*\*/g, "<strong>$1</strong>");
    source = source.replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, "$1<em>$2</em>");
    source = source.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
        "<a href=\"$2\" target=\"_blank\" rel=\"noopener\">$1</a>");
    const lines = source.split("\n");
    const html = [];
    let list = null;
    const closeList = () => {
      if (list) { html.push(list === "ul" ? "</ul>" : "</ol>"); list = null; }
    };
    for (const line of lines) {
      const block = /^\u0000B(\d+)\u0000$/.exec(line);
      if (block) { closeList(); html.push(blocks[+block[1]]); continue; }
      const ordered = /^(\d+)[.)]\s+(.+)$/.exec(line);
      const bullet = /^[-*+]\s+(.+)$/.exec(line);
      if (ordered) {
        if (list !== "ol") { closeList(); html.push("<ol>"); list = "ol"; }
        html.push("<li>" + ordered[2] + "</li>");
        continue;
      }
      if (bullet) {
        if (list !== "ul") { closeList(); html.push("<ul>"); list = "ul"; }
        html.push("<li>" + bullet[1] + "</li>");
        continue;
      }
      closeList();
      if (!line.trim()) { html.push(""); continue; }
      html.push("<p>" + line + "</p>");
    }
    closeList();
    return html.join("\n");
  }

  // ---- token handling ----

  function token() {
    // localStorage so the Web (inference) and Settings browser tabs share it.
    try { return localStorage.getItem(TOKEN_KEY) || ""; } catch (e) { return ""; }
  }
  function setToken(value) {
    try {
      if (value) { localStorage.setItem(TOKEN_KEY, value); }
      else { localStorage.removeItem(TOKEN_KEY); }
    } catch (e) { /* storage unavailable */ }
  }

  function headers(extra) {
    const result = Object.assign({ "Accept": "application/json" }, extra || {});
    const bearer = token();
    if (bearer) { result["Authorization"] = "Bearer " + bearer; }
    return result;
  }

  async function parse(response) {
    const text = await response.text();
    let body = null;
    if (text) {
      try { body = JSON.parse(text); } catch (e) { body = { raw: text }; }
    }
    if (!response.ok) {
      const message = (body && (body.message || body.error))
          || ("HTTP " + response.status);
      const error = new Error(message);
      error.status = response.status;
      error.body = body;
      throw error;
    }
    return body;
  }

  async function json(path, options) {
    const opts = Object.assign({}, options || {});
    opts.headers = headers(Object.assign({ "Content-Type": "application/json" },
        opts.headers || {}));
    if (opts.body !== undefined && opts.body !== null && typeof opts.body !== "string") {
      opts.body = JSON.stringify(opts.body);
    }
    return parse(await fetch(path, opts));
  }

  /** Minimal SSE reader: onEvent(type, data) may return false to stop. */
  async function sse(path, options, onEvent) {
    const opts = Object.assign({}, options || {});
    opts.headers = headers(Object.assign({ "Content-Type": "application/json" },
        opts.headers || {}));
    if (opts.body !== undefined && opts.body !== null && typeof opts.body !== "string") {
      opts.body = JSON.stringify(opts.body);
    }
    const response = await fetch(path, opts);
    if (!response.ok) {
      const text = await response.text();
      let message = "HTTP " + response.status;
      try { message = JSON.parse(text).message || message; } catch (e) { /* text */ }
      throw new Error(message);
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    for (;;) {
      const chunk = await reader.read();
      if (chunk.done) { break; }
      buffer += decoder.decode(chunk.value, { stream: true });
      let index;
      while ((index = buffer.indexOf("\n\n")) >= 0
          || (index = buffer.indexOf("\r\n\r\n")) >= 0) {
        const separator = buffer[index] === "\r" ? 4 : 2;
        const frame = buffer.slice(0, index);
        buffer = buffer.slice(index + separator);
        let event = "message";
        const dataLines = [];
        for (const line of frame.split(/\r?\n/)) {
          if (line.startsWith("event:")) { event = line.slice(6).trim(); }
          else if (line.startsWith("data:")) { dataLines.push(line.slice(5).replace(/^ /, "")); }
        }
        if (!dataLines.length) { continue; }
        let data;
        try { data = JSON.parse(dataLines.join("\n")); }
        catch (e) { data = { raw: dataLines.join("\n") }; }
        const keep = onEvent(event, data);
        if (keep === false) {
          try { await reader.cancel(); } catch (e) { /* already closed */ }
          return;
        }
      }
    }
  }

  function arrayOf(value, key) {
    if (Array.isArray(value)) { return value; }
    if (value && key && Array.isArray(value[key])) { return value[key]; }
    return [];
  }

  function statusText(raw) {
    if (raw == null) { return ""; }
    if (typeof raw === "string") { return raw; }
    if (typeof raw === "object") {
      if (raw.reachable === false) { return "DISCONNECTED"; }
      if (raw.healthy === false) { return "DEGRADED"; }
      return raw.message || (raw.reachable ? "CONNECTED" : "");
    }
    return String(raw);
  }

  async function detect() {
    if (state.meta && state.meta.application === "DSTUDIO") { return "dstudio"; }
    try {
      const status = await json("/api/status");
      state.meta = status || {};
    } catch (e) {
      // Older/partial servers: the inference surface is enough to confirm DStudio.
      await json("/api/inference/engines");
      state.meta = {};
    }
    return "dstudio";
  }

  function productName() { return "DStudio"; }

  async function providers() {
    await detect();
    const body = await json("/api/inference/engines");
    return arrayOf(body, "engines").map((e) => ({
      id: String(e.id),
      name: e.name || e.id,
      type: e.type || "",
      endpoint: e.endpoint || e.baseUrl || "",
      enabled: e.enabled !== false,
      isDefault: e.default === true,
      status: statusText(e.status),
      modelCount: e.modelCount,
      capabilities: e.capabilities || {}
    }));
  }

  async function models(providerId) {
    const body = await json("/api/inference/models?engine="
        + encodeURIComponent(providerId));
    return arrayOf(body, "models").map((m) => ({
      id: String(m.id),
      name: m.name || m.id,
      providerId: providerId,
      sizeBytes: m.sizeBytes,
      available: m.available !== false
    }));
  }

  async function allModels(providerList) {
    const list = providerList || await providers();
    const merged = [];
    const failures = [];
    for (const provider of list) {
      if (provider.enabled === false) { continue; }
      try {
        const found = await models(provider.id);
        for (const model of found) {
          model.providerName = provider.name;
          merged.push(model);
        }
      } catch (e) {
        failures.push(provider.id + ": " + e.message);
      }
    }
    return { models: merged, failures };
  }

  async function projects() {
    const body = await json("/api/workspace/projects");
    return arrayOf(body, "projects").map((p) => ({ id: String(p.id),
      name: p.name || p.id, rootPath: p.rootPath || "" }));
  }

  async function openProject(path) {
    const value = String(path || "").trim();
    if (!value) { throw new Error("A project path is required."); }
    return json("/api/workspace/projects", { method: "POST", body: { path: value } });
  }

  async function harnesses() {
    const body = await json("/api/diagnostics");
    return arrayOf(body, "harnesses").map((h) => ({
      id: String(h.id), name: h.id, type: h.type || "harness",
      enabled: h.enabled !== false, isDefault: h.default === true,
      status: h.status || ""
    }));
  }

  async function scanProviders() {
    // DStudio engines are registered in the core registry; a scan re-reads it.
    return providers();
  }

  async function testProvider(providerId) {
    if (!bridgeAvailable()) {
      throw new Error("Provider tests require the DStudio desktop bridge.");
    }
    return bridgeCall("testEngine", { engineId: providerId });
  }

  async function applyDefaults(selection) {
    if (!bridgeAvailable()) {
      throw new Error("Applying defaults for DStudio requires the desktop bridge.");
    }
    if (selection.harnessId) {
      await bridgeCall("setDefaultHarness", { harnessId: selection.harnessId });
    }
    if (selection.providerId) {
      await bridgeCall("setDefaultEngine", { engineId: selection.providerId });
    }
    if (selection.agentProfileId) {
      await bridgeCall("setDefaultAgent", { profileId: selection.agentProfileId });
    }
    if (selection.modelId || selection.providerId) {
      await bridgeCall("saveGlobalConfig", { harnessId: selection.harnessId || "",
        providerId: selection.providerId || "", modelId: selection.modelId || "",
        agentProfileId: selection.agentProfileId || "" });
    }
  }

  /** Small token modal (WebKit-safe; window.prompt is not reliable in SWT Browser). */
  function askToken(current) {
    return new Promise((resolve) => {
      const overlay = document.createElement("div");
      overlay.className = "modal-overlay";
      const modal = document.createElement("div");
      modal.className = "modal";
      const title = document.createElement("div");
      title.className = "modal-title";
      title.textContent = "Server bearer token";
      const input = document.createElement("input");
      input.type = "password";
      input.placeholder = "leave empty to clear";
      input.value = current || "";
      const actions = document.createElement("div");
      actions.className = "modal-actions";
      const cancel = document.createElement("button");
      cancel.textContent = "Cancel";
      const apply = document.createElement("button");
      apply.className = "primary";
      apply.textContent = "Apply";
      const close = (value) => { overlay.remove(); resolve(value); };
      cancel.onclick = () => close(null);
      apply.onclick = () => close(input.value.trim());
      overlay.onclick = (event) => { if (event.target === overlay) { close(null); } };
      input.addEventListener("keydown", (event) => {
        if (event.key === "Enter") { apply.click(); }
        else if (event.key === "Escape") { cancel.click(); }
      });
      actions.appendChild(cancel);
      actions.appendChild(apply);
      modal.appendChild(title);
      modal.appendChild(input);
      modal.appendChild(actions);
      overlay.appendChild(modal);
      document.body.appendChild(overlay);
      input.focus();
    });
  }

  // ---- desktop bridge (DStudio settings tab) ----
  let bridgeSeq = 0;
  const bridgePending = {};
  window.dstudioResult = function (id, payload) {
    const pending = bridgePending[id];
    if (pending) { delete bridgePending[id]; pending.resolve(payload); }
  };
  window.dstudioError = function (id, payload) {
    const pending = bridgePending[id];
    if (pending) {
      delete bridgePending[id];
      pending.reject(new Error(payload && payload.error ? payload.error : "bridge error"));
    }
  };
  window.dstudioEvent = function () { /* no-op */ };

  function bridgeCall(method, args) {
    return new Promise((resolve, reject) => {
      if (!(window.dstudio && typeof window.dstudio === "function")) {
        reject(new Error("Desktop bridge unavailable."));
        return;
      }
      const id = ++bridgeSeq;
      const timer = setTimeout(() => {
        if (bridgePending[id]) {
          delete bridgePending[id];
          reject(new Error("Desktop bridge timed out: " + method));
        }
      }, 8000);
      bridgePending[id] = {
        resolve: (value) => { clearTimeout(timer); resolve(value); },
        reject: (error) => { clearTimeout(timer); reject(error); }
      };
      try { window.dstudio.call(method, JSON.stringify(args || {}), id); }
      catch (e) { clearTimeout(timer); delete bridgePending[id]; reject(e); }
    });
  }
  function bridgeAvailable() {
    return !!(window.dstudio && typeof window.dstudio === "function");
  }

  return {
    $, state, detect, productName, providers, models, allModels, projects,
    openProject, harnesses, scanProviders, testProvider, applyDefaults,
    json, sse, arrayOf, statusText, token, setToken, askToken, headers,
    bridgeAvailable, bridgeCall,
    THEME_KEY, esc, formatBytes, fillSelect, toast, applyTheme, renderMarkdown
  };
})();
