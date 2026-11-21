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
  const state = { backend: "dstudio", meta: {} };

  function $(id) { return document.getElementById(id); }

  function token() {
    try { return sessionStorage.getItem(TOKEN_KEY) || ""; } catch (e) { return ""; }
  }
  function setToken(value) {
    try {
      if (value) { sessionStorage.setItem(TOKEN_KEY, value); }
      else { sessionStorage.removeItem(TOKEN_KEY); }
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
      enabled: h.enabled !== false, isDefault: false, status: h.status || ""
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
    json, sse, arrayOf, statusText, token, setToken, headers,
    bridgeAvailable, bridgeCall
  };
})();
