/* DStudio Web IDE — vanilla JS client over the shared backend (no frameworks). */
"use strict";

const $ = (id) => document.getElementById(id);
const state = { projects: [], project: null, session: null, sessions: [], openFiles: new Map() };

function api(path, options) {
  return fetch(path, options).then(async (response) => {
    const text = await response.text();
    let body;
    try { body = text ? JSON.parse(text) : {}; } catch (e) { body = { raw: text }; }
    if (!response.ok) {
      throw new Error(body.message || body.code || ("HTTP " + response.status));
    }
    return body;
  });
}

/* ---------- status ---------- */
async function refreshStatus() {
  try {
    const status = await api("/api/status");
    $("statusDot").className = "dot up";
    $("statusDot").title = "Backend " + status.version + " — UP";
  } catch (e) {
    $("statusDot").className = "dot down";
    $("statusDot").title = "Backend unreachable: " + e.message;
  }
}

/* ---------- projects & file tree ---------- */
async function refreshProjects() {
  const body = await api("/api/workspace/projects");
  state.projects = body.projects || [];
  const select = $("projectSelect");
  select.innerHTML = "";
  for (const project of state.projects) {
    const option = document.createElement("option");
    option.value = project.id;
    option.textContent = project.name + " (" + project.rootPath + ")";
    select.appendChild(option);
  }
  if (state.project) {
    const still = state.projects.find((p) => p.id === state.project.id);
    if (still) { select.value = still.id; } else { state.project = null; }
  }
  if (!state.project && state.projects.length > 0) {
    state.project = state.projects[0];
    select.value = state.project.id;
  }
  await refreshTree();
  await refreshHarnesses();
}

async function openProject() {
  const path = prompt("Path of the project folder or repository:");
  if (!path) { return; }
  try {
    const project = await api("/api/workspace/projects", {
      method: "POST", body: JSON.stringify({ path }),
    });
    await refreshProjects();
    const select = $("projectSelect");
    select.value = project.id;
    state.project = project;
    await refreshTree();
    log("Opened project " + project.rootPath);
  } catch (e) {
    alert("Cannot open project: " + e.message);
  }
}

async function refreshTree() {
  const tree = $("fileTree");
  tree.innerHTML = "";
  if (!state.project) { return; }
  await renderDirectory(tree, "");
}

async function renderDirectory(container, path) {
  const body = await api("/api/workspace/files?project="
      + encodeURIComponent(state.project.id) + "&path=" + encodeURIComponent(path));
  for (const child of (body.children || [])) {
    const node = document.createElement("div");
    node.className = child.directory ? "treeDir" : "treeFile";
    node.textContent = child.name;
    if (child.directory) {
      node.addEventListener("click", async (event) => {
        event.stopPropagation();
        if (node.classList.contains("open")) {
          node.classList.remove("open");
          node.nextSibling && node.nextSibling.classList.contains("children")
              && node.nextSibling.remove();
        } else {
          node.classList.add("open");
          const wrapper = document.createElement("div");
          wrapper.className = "children";
          wrapper.style.paddingLeft = "12px";
          node.after(wrapper);
          try { await renderDirectory(wrapper, child.path); }
          catch (e) { log("Tree error: " + e.message); }
        }
      });
    } else {
      node.addEventListener("click", () => openFile(child.path));
    }
    container.appendChild(node);
  }
}

/* ---------- editor ---------- */
async function openFile(path) {
  if (state.openFiles.has(path)) { activateTab(path); return; }
  const body = await api("/api/workspace/files?project="
      + encodeURIComponent(state.project.id) + "&path=" + encodeURIComponent(path));
  state.openFiles.set(path, body.content || "");
  renderTabs();
  activateTab(path);
}

function renderTabs() {
  const tabs = $("tabs");
  tabs.innerHTML = "";
  for (const path of state.openFiles.keys()) {
    const tab = document.createElement("div");
    tab.className = "tab" + (path === state.activePath ? " active" : "");
    tab.textContent = path.split("/").pop();
    tab.addEventListener("click", () => activateTab(path));
    tabs.appendChild(tab);
  }
}

function activateTab(path) {
  state.activePath = path;
  renderTabs();
  const host = $("editorHost");
  host.innerHTML = "";
  const bar = document.createElement("div");
  bar.className = "editorBar";
  const label = document.createElement("span");
  label.className = "path";
  label.textContent = path;
  const save = document.createElement("button");
  save.textContent = "Save";
  save.addEventListener("click", () => saveFile(path));
  bar.appendChild(label);
  bar.appendChild(save);
  const textarea = document.createElement("textarea");
  textarea.value = state.openFiles.get(path);
  textarea.spellcheck = false;
  textarea.addEventListener("input", () => state.openFiles.set(path, textarea.value));
  host.appendChild(bar);
  host.appendChild(textarea);
  textarea.focus();
}

async function saveFile(path) {
  try {
    await api("/api/workspace/files?project=" + encodeURIComponent(state.project.id)
        + "&path=" + encodeURIComponent(path), {
      method: "PUT", body: state.openFiles.get(path),
    });
    log("Saved " + path);
  } catch (e) {
    alert("Save failed: " + e.message);
  }
}

/* ---------- AI assistant ---------- */
async function refreshEngines() {
  const body = await api("/api/inference/engines");
  const select = $("engineSelect");
  select.innerHTML = "";
  for (const engine of (body.engines || [])) {
    if (!engine.enabled) { continue; }
    const option = document.createElement("option");
    option.value = engine.id;
    option.textContent = engine.id + (engine.default ? " ★" : "");
    select.appendChild(option);
  }
  await refreshModels();
  await refreshSessions();
}

async function refreshModels() {
  const engine = $("engineSelect").value;
  if (!engine) { return; }
  const select = $("modelSelect");
  select.innerHTML = "";
  try {
    const body = await api("/api/inference/models?engine=" + encodeURIComponent(engine));
    for (const model of (body.models || [])) {
      const option = document.createElement("option");
      option.value = model.id;
      option.textContent = model.id;
      select.appendChild(option);
    }
  } catch (e) {
    const option = document.createElement("option");
    option.textContent = "models unavailable (" + e.message + ")";
    select.appendChild(option);
  }
}

async function refreshSessions() {
  const body = await api("/api/inference/sessions");
  state.sessions = body.sessions || [];
  const select = $("sessionSelect");
  select.innerHTML = "";
  const none = document.createElement("option");
  none.value = "";
  none.textContent = "(sessions)";
  select.appendChild(none);
  for (const session of state.sessions) {
    const option = document.createElement("option");
    option.value = session.id;
    option.textContent = session.id + " — " + session.model + " [" + session.status + "]";
    select.appendChild(option);
  }
}

async function newSession() {
  try {
    const body = await api("/api/inference/sessions", {
      method: "POST",
      body: JSON.stringify({ engine: $("engineSelect").value,
          model: $("modelSelect").value, systemPrompt: "" }),
    });
    state.session = body;
    $("aiEngineTag").textContent = "(" + body.engineId + ")";
    $("chatLog").innerHTML = "";
    await refreshSessions();
    $("sessionSelect").value = body.id;
    log("Session " + body.id + " created");
  } catch (e) {
    alert("Cannot create session: " + e.message);
  }
}

async function openSession(id) {
  const body = await api("/api/inference/sessions/" + encodeURIComponent(id));
  state.session = body;
  $("aiEngineTag").textContent = "(" + body.engineId + ")";
  const chat = $("chatLog");
  chat.innerHTML = "";
  for (const message of (body.messages || [])) {
    appendChat(message.role, message.content);
  }
}

async function sendChat() {
  const input = $("chatInput");
  const content = input.value.trim();
  if (!content) { return; }
  if (!state.session) {
    try { await newSession(); } catch (e) { return; }
  }
  input.value = "";
  appendChat("user", content);
  const assistantNode = appendChat("assistant", "");
  try {
    const response = await fetch("/api/inference/sessions/"
        + encodeURIComponent(state.session.id) + "/messages/stream", {
      method: "POST",
      body: JSON.stringify({ content }),
    });
    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      assistantNode.querySelector(".text").textContent = "ERROR: "
          + (err.message || ("HTTP " + response.status));
      return;
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    for (;;) {
      const { done, value } = await reader.read();
      if (done) { break; }
      buffer += decoder.decode(value, { stream: true });
      let index;
      while ((index = buffer.indexOf("\n\n")) >= 0) {
        const frame = buffer.slice(0, index);
        buffer = buffer.slice(index + 2);
        let eventType = "";
        for (const line of frame.split("\n")) {
          if (line.startsWith("event: ")) { eventType = line.slice(7); }
          if (line.startsWith("data: ")) {
            const data = JSON.parse(line.slice(6));
            if (eventType === "chunk" && data.text) {
              assistantNode.querySelector(".text").textContent += data.text;
            } else if (eventType === "error") {
              assistantNode.querySelector(".text").textContent +=
                  "\n[ERROR: " + data.message + "]";
            }
          }
        }
      }
    }
  } catch (e) {
    assistantNode.querySelector(".text").textContent += "\n[STREAM FAILED: " + e.message + "]";
  }
  await refreshSessions();
}

function appendChat(role, content) {
  const chat = $("chatLog");
  const node = document.createElement("div");
  node.className = "msg " + role;
  const who = document.createElement("div");
  who.className = "who";
  who.textContent = role;
  const text = document.createElement("div");
  text.className = "text";
  text.textContent = content;
  node.appendChild(who);
  node.appendChild(text);
  chat.appendChild(node);
  chat.scrollTop = chat.scrollHeight;
  return node;
}

/* ---------- coding tasks ---------- */
async function refreshHarnesses() {
  const body = await api("/api/diagnostics");
  const select = $("harnessSelect");
  select.innerHTML = "";
  for (const harness of (body.harnesses || [])) {
    if (!harness.enabled) { continue; }
    const option = document.createElement("option");
    option.value = harness.id;
    option.textContent = harness.id + " (" + harness.type + ")";
    select.appendChild(option);
  }
}

async function runTask() {
  const instruction = $("taskInstruction").value.trim();
  if (!instruction) { return; }
  if (!state.project) { alert("Open a project first."); return; }
  $("taskStatus").textContent = "Submitting…";
  try {
    const idempotencyKey = "web-" + Date.now();
    const body = {
      protocol: "TEIP", version: "1.1", idempotencyKey,
      timestamp: new Date().toISOString(),
      caller: { system: "DSTUDIO_WEB" },
      capability: "CODE_IMPLEMENTATION", mode: "DEFAULT",
      instruction,
      parameters: { workingDirectory: state.project.rootPath,
          harness: $("harnessSelect").value },
    };
    const created = await api("/teip/v1/executions", {
      method: "POST", body: JSON.stringify(body),
    });
    $("taskStatus").textContent = "Running " + created.executionId + "…";
    pollExecution(created.executionId, 0);
  } catch (e) {
    $("taskStatus").textContent = "FAILED: " + e.message;
  }
}

async function pollExecution(id, afterSequence) {
  try {
    const events = await api("/teip/v1/executions/" + encodeURIComponent(id)
        + "/events?afterSequence=" + afterSequence + "&limit=100");
    for (const event of (events.events || [])) {
      log("[" + id.slice(0, 8) + "] " + event.type + ": "
          + (event.message || ""));
    }
    const status = await api("/teip/v1/executions/" + encodeURIComponent(id));
    if (status.status === "COMPLETED") {
      $("taskStatus").textContent = "Completed — " + id;
      log("Task " + id + " COMPLETED: " + (status.statusMessage || ""));
      await refreshTree();
      return;
    }
    if (["FAILED", "CANCELLED"].includes(status.status)) {
      $("taskStatus").textContent = status.status + " — " + id;
      log("Task " + id + " " + status.status);
      return;
    }
    setTimeout(() => pollExecution(id, events.nextSequence || afterSequence), 1500);
  } catch (e) {
    $("taskStatus").textContent = "Polling error: " + e.message;
  }
}

/* ---------- live events ---------- */
function connectEvents() {
  try {
    const source = new EventSource("/api/events");
    source.onopen = () => log("Live event stream connected");
    source.onmessage = (message) => {
      try {
        const data = JSON.parse(message.data);
        $("eventsLog").innerHTML += '<div class="ev">' + (message.lastEventId || "")
            + " " + (data.kind || data.type || "") + "</div>";
        $("eventsLog").scrollTop = $("eventsLog").scrollHeight;
      } catch (ignored) { }
    };
    source.onerror = () => {
      log("Live event stream disconnected (will retry on reload)");
      source.close();
    };
  } catch (e) {
    log("EventSource unavailable: " + e.message);
  }
}

function log(text) {
  $("eventsLog").innerHTML += '<div class="ev">' + String(text) + "</div>";
  $("eventsLog").scrollTop = $("eventsLog").scrollHeight;
}

/* ---------- wiring ---------- */
$("openProjectBtn").addEventListener("click", openProject);
$("refreshBtn").addEventListener("click", () => refreshProjects().catch((e) => log(e.message)));
$("projectSelect").addEventListener("change", async () => {
  state.project = state.projects.find((p) => p.id === $("projectSelect").value) || null;
  await refreshTree();
});
$("engineSelect").addEventListener("change", refreshModels);
$("modelSelect").addEventListener("change", () => { state.session = null; });
$("newSessionBtn").addEventListener("click", newSession);
$("sessionSelect").addEventListener("change", (e) => {
  if (e.target.value) { openSession(e.target.value); } else { state.session = null; }
});
$("chatInput").addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); sendChat(); }
});
$("runTaskBtn").addEventListener("click", runTask);

/* ---------- unified contextual actions (Darwin Family contract v1.0) ---------- */
/* The same action ids/descriptors as the RCP context menus, resolved and executed
   through the shared backend registry. Event delegation only — no inline handlers. */
let lastContextTrigger = null;

function markContextualTargets() {
  const targets = [
    { el: $("projectSelect"), type: "project" },
    { el: $("engineSelect"), type: "provider" },
    { el: $("modelSelect"), type: "model" },
    { el: $("harnessSelect"), type: "harness" },
    { el: $("sessionSelect"), type: "session" },
  ];
  for (const target of targets) {
    const el = target.el;
    if (!el || el.dataset.ctxBound) { continue; }
    el.dataset.ctxBound = "1";
    el.classList.add("ctx-target");
    el.addEventListener("contextmenu", (event) => {
      event.preventDefault();
      openContextMenuFor(event.clientX, event.clientY, target.type, el);
    });
    const overflow = document.createElement("button");
    overflow.className = "ctx-target";
    overflow.textContent = "⋯";
    overflow.title = "More actions";
    overflow.addEventListener("click", () => {
      const rect = overflow.getBoundingClientRect();
      openContextMenuFor(rect.left, rect.bottom + 2, target.type, overflow);
    });
    el.after(overflow);
  }
}

function selectionFrom(type) {
  const selection = {
    objectType: type, objectId: "", objectLabel: "", providerId: "",
    harnessId: "", modelId: "", projectId: "", sessionId: "", objectState: {},
  };
  if (type === "provider") { selection.objectId = $("engineSelect").value || ""; }
  else if (type === "model") {
    selection.objectId = $("modelSelect").value || "";
    selection.providerId = $("engineSelect").value || "";
  } else if (type === "harness") { selection.objectId = $("harnessSelect").value || ""; }
  else if (type === "project") { selection.objectId = $("projectSelect").value || ""; }
  else if (type === "session") { selection.objectId = $("sessionSelect").value || ""; }
  return selection;
}

async function openContextMenuFor(x, y, type, trigger) {
  const selection = selectionFrom(type);
  if (!selection.objectId) { return; }
  lastContextTrigger = trigger || null;
  try {
    const params = new URLSearchParams();
    params.set("type", selection.objectType);
    params.set("id", selection.objectId);
    params.set("providerId", selection.providerId || "");
    params.set("projectId", selection.projectId || "");
    params.set("sessionId", selection.sessionId || "");
    const body = await api("/api/contextual/actions?" + params.toString());
    renderContextMenu(x, y, body.actions || [], selection);
  } catch (e) {
    toast("Could not resolve actions: " + e.message, true);
  }
}

function renderContextMenu(x, y, actions, selection) {
  closeContextMenu();
  const menu = $("ctx-menu");
  menu.innerHTML = "";
  let currentGroup = null;
  for (const action of actions) {
    if (action.group !== currentGroup) {
      if (currentGroup !== null) {
        const sep = document.createElement("div");
        sep.className = "ctx-sep";
        menu.appendChild(sep);
      }
      currentGroup = action.group;
    }
    const item = document.createElement("button");
    item.type = "button";
    item.className = "ctx-item" + (action.dangerous ? " danger" : "");
    item.setAttribute("role", "menuitem");
    if (action.dangerous) { item.setAttribute("aria-label", action.label + " (dangerous)"); }
    if (!action.enabled) { item.setAttribute("aria-disabled", "true"); }
    item.textContent = action.label;
    item.disabled = !action.enabled;
    item.title = action.description
        + (action.enabled ? "" : " — " + (action.disabledReason || "unavailable"));
    if (action.enabled) {
      item.addEventListener("click", () => executeContextual(action, selection));
    }
    menu.appendChild(item);
  }
  if (!menu.childNodes.length) {
    const none = document.createElement("div");
    none.className = "ctx-item";
    none.setAttribute("role", "menuitem");
    none.setAttribute("aria-disabled", "true");
    none.textContent = "No actions available";
    none.disabled = true;
    menu.appendChild(none);
  }
  menu.hidden = false;
  const left = Math.min(x, window.innerWidth - 240);
  const top = Math.min(y, window.innerHeight - menu.offsetHeight - 8);
  menu.style.left = left + "px";
  menu.style.top = top + "px";
  // Keyboard accessibility: focus the first enabled item when the menu opens.
  const firstEnabled = menu.querySelector("button.ctx-item:not(:disabled)");
  if (firstEnabled) { firstEnabled.focus(); }
}

function enabledMenuItems() {
  const menu = $("ctx-menu");
  if (menu.hidden) { return []; }
  return [...menu.querySelectorAll("button.ctx-item")].filter((item) => !item.disabled);
}

function closeContextMenu(restoreFocus) {
  const menu = $("ctx-menu");
  menu.hidden = true;
  if (restoreFocus && lastContextTrigger) {
    lastContextTrigger.focus();
    lastContextTrigger = null;
  }
}

async function executeContextual(action, selection) {
  closeContextMenu();
  if (action.confirmation === "confirm" || action.confirmation === "dangerous") {
    const ok = confirm(action.label + " on "
        + (selection.objectId || selection.objectType) + "?");
    if (!ok) { return; }
  }
  try {
    const result = await api("/api/contextual/execute", {
      method: "POST",
      body: JSON.stringify(Object.assign({ actionId: action.actionId }, selection)),
    });
    toast(result.ok ? (result.message || action.label) : result.message, !result.ok);
    refreshAfterAction(selection.objectType);
  } catch (e) {
    toast(action.label + " failed: " + e.message, true);
  }
}

function refreshAfterAction(objectType) {
  if (objectType === "provider") {
    refreshEngines().catch(() => {});
  } else if (objectType === "project") {
    refreshProjects().catch(() => {});
  } else if (objectType === "session") {
    refreshSessions().catch(() => {});
  } else if (objectType === "model") {
    refreshModels().catch(() => {});
  } else if (objectType === "harness") {
    refreshHarnesses().catch(() => {});
  }
}

let toastTimer = null;
function toast(message, isError) {
  const el = $("ctx-toast");
  el.textContent = message;
  el.className = "ctx-toast" + (isError ? " error" : "");
  el.hidden = false;
  if (toastTimer) { clearTimeout(toastTimer); }
  toastTimer = setTimeout(() => { el.hidden = true; }, 4000);
}

document.addEventListener("contextmenu", (event) => {
  const target = event.target.closest("[data-ctx-bound]");
  if (!target) { closeContextMenu(); }
});
document.addEventListener("click", (event) => {
  if (!$("ctx-menu").contains(event.target)) { closeContextMenu(); }
});
document.addEventListener("keydown", (event) => {
  const menu = $("ctx-menu");
  if (menu.hidden) { return; }
  if (event.key === "Escape") {
    event.preventDefault();
    closeContextMenu(true);
    return;
  }
  const items = enabledMenuItems();
  if (items.length === 0) { return; }
  const currentIndex = items.indexOf(document.activeElement);
  if (event.key === "ArrowDown") {
    event.preventDefault();
    items[(currentIndex + 1) % items.length].focus();
  } else if (event.key === "ArrowUp") {
    event.preventDefault();
    items[(currentIndex - 1 + items.length) % items.length].focus();
  }
  // Enter activates the focused item through its native button click handling.
});

(async function boot() {
  await refreshStatus().catch(() => {});
  try {
    await refreshProjects();
    await refreshEngines();
    markContextualTargets();
  } catch (e) {
    log("Startup error: " + e.message + " — check the backend log.");
  }
  connectEvents();
  setInterval(refreshStatus, 10000);
})();

/* ---------- model manager (shared ENR model management service) ---------- */
(function () {
  "use strict";
  const POLL_MS = 1000;
  let pollTimer = null;

  function el(tag, attrs, text) {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (k === "style") { Object.assign(node.style, v); }
      else { node.setAttribute(k, v); }
    }
    if (text != null) { node.textContent = text; }
    return node;
  }

  function fmtBytes(n) {
    if (n == null || n <= 0) { return "—"; }
    const units = ["B", "KB", "MB", "GB", "TB"];
    let i = 0, v = n;
    while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
    return v.toFixed(v >= 100 || i === 0 ? 0 : 1) + " " + units[i];
  }

  async function refreshProviders() {
    const select = $("mmProvider");
    if (!select) { return; }
    try {
      const data = await api("/api/models/providers");
      const providers = (data.providers || []).filter((p) =>
        (p.capabilities || []).includes("MODEL_PULL"));
      select.innerHTML = "";
      for (const provider of providers) {
        const option = document.createElement("option");
        option.value = provider.id;
        option.textContent = provider.display_name + " (" + provider.id + ")";
        select.appendChild(option);
      }
      if (!providers.length) {
        $("mmStatus").textContent = "No model providers registered in ENR "
          + "(check enr.yaml model_providers).";
        return;
      }
      select.onchange = () => refreshInstalled();
      await refreshInstalled();
    } catch (e) {
      $("mmStatus").textContent = "Providers unavailable: " + e.message;
    }
  }

  async function refreshInstalled() {
    const box = $("mmInstalled");
    const provider = $("mmProvider").value;
    if (!box || !provider) { return; }
    box.innerHTML = "";
    box.appendChild(el("div", {}, "Installed — " + provider));
    try {
      const data = await api("/api/models/providers/"
        + encodeURIComponent(provider) + "/installed");
      const models = data.models || [];
      if (!models.length) {
        box.appendChild(el("div", { class: "muted" }, "No installed models."));
        return;
      }
      for (const model of models) {
        const row = el("div", { style: { marginTop: "4px" } });
        const label = el("span", {},
          model.id + (model.size_bytes > 0 ? " · " + fmtBytes(model.size_bytes) : ""));
        row.appendChild(label);
        const remove = el("button", {}, "Remove");
        remove.style.marginLeft = "6px";
        remove.onclick = async () => {
          if (!confirm("Remove " + model.id + " from " + provider + "?")) { return; }
          try {
            await api("/api/models/providers/" + encodeURIComponent(provider)
              + "/" + encodeURIComponent(model.id), { method: "DELETE" });
            $("mmStatus").textContent = "Removed " + model.id;
            refreshInstalled();
          } catch (e) {
            $("mmStatus").textContent = "Remove failed: " + e.message;
          }
        };
        row.appendChild(remove);
        box.appendChild(row);
      }
    } catch (e) {
      box.appendChild(el("div", { class: "muted" }, "Unavailable: " + e.message));
    }
  }

  async function refreshDownloads() {
    const box = $("mmDownloads");
    if (!box) { return; }
    try {
      const data = await api("/api/models/jobs");
      const active = (data.downloads || []).filter((job) =>
        !["COMPLETED", "FAILED", "CANCELLED"].includes(job.status));
      box.innerHTML = "";
      if (!active.length) { return; }
      box.appendChild(el("div", {}, "Active downloads"));
      for (const job of active) {
        const pct = job.total_bytes > 0
          ? Math.min(100, Math.round(job.completed_bytes * 100 / job.total_bytes)) : null;
        const row = el("div", { style: { marginTop: "6px" } });
        row.appendChild(el("div", {},
          job.provider + " / " + job.model + " — " + job.status
          + (job.operation ? " (" + job.operation + ")" : "")
          + (pct != null ? " " + pct + "% (" + fmtBytes(job.completed_bytes)
            + " / " + fmtBytes(job.total_bytes) + ")" : "")));
        const bar = el("div", { style: { height: "6px", background: "#2a2f3a",
          borderRadius: "3px", overflow: "hidden", marginTop: "2px" } });
        const fill = el("div", { style: { height: "100%", width: (pct || 0) + "%",
          background: "#4f8cff" } });
        bar.appendChild(fill);
        row.appendChild(bar);
        const cancel = el("button", {}, "Cancel");
        cancel.style.marginTop = "2px";
        cancel.onclick = async () => {
          try {
            await api("/api/models/jobs/" + encodeURIComponent(job.job_id),
              { method: "DELETE" });
            $("mmStatus").textContent = "Cancel requested for job " + job.job_id;
          } catch (e) {
            $("mmStatus").textContent = "Cancel failed: " + e.message;
          }
        };
        row.appendChild(cancel);
        if (job.error) { row.appendChild(el("div", { class: "muted" }, job.error)); }
        box.appendChild(row);
      }
    } catch (e) {
      /* backend unreachable: keep previous content */
    }
  }

  function startPolling() {
    if (pollTimer) { clearInterval(pollTimer); }
    pollTimer = setInterval(refreshDownloads, POLL_MS);
  }

  $("mmPullBtn").addEventListener("click", async () => {
    const provider = $("mmProvider").value;
    const model = $("mmModel").value.trim();
    if (!provider) { $("mmStatus").textContent = "No provider selected."; return; }
    if (!model) { $("mmStatus").textContent = "Enter a model identifier."; return; }
    $("mmStatus").textContent = "";
    $("mmPullBtn").disabled = true;
    try {
      const job = await api("/api/models/providers/" + encodeURIComponent(provider)
        + "/pull", { method: "POST", body: JSON.stringify({ model }) });
      $("mmStatus").textContent = "Pull queued: " + model + " (job " + job.jobId + ")";
      $("mmModel").value = "";
      await refreshDownloads();
    } catch (e) {
      $("mmStatus").textContent = "Pull failed: " + e.message;
    } finally {
      $("mmPullBtn").disabled = false;
    }
  });

  $("mmRefreshBtn").addEventListener("click", () => {
    $("mmStatus").textContent = "";
    refreshProviders();
    refreshDownloads();
  });

  refreshProviders();
  refreshDownloads();
  startPolling();
})();
