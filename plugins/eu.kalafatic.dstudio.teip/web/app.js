/* DStudio Web tab — inference over DStudio's own engines, sessions, projects and
 * harnesses. Visual language mirrors the DCode web UI; the objects are DStudio's. */
(function () {
  "use strict";

  const { $, detect, providers, models, allModels, projects, harnesses, json, sse } =
      window.Inference;

  const STORE_KEY = "dstudio.conversations.v1";
  const THEME_KEY = "dstudio.theme.v1";

  const state = {
    providerId: "",
    modelId: "",
    projectId: "",
    harnessId: "",
    allProviders: [],
    allModels: [],
    allProjects: [],
    allHarnesses: [],
    conversations: [],
    currentId: null,
    busy: false,
    stream: null
  };

  // ---- persistence ----

  function loadStore() {
    try {
      const raw = localStorage.getItem(STORE_KEY);
      const parsed = raw ? JSON.parse(raw) : null;
      state.conversations = Array.isArray(parsed) ? parsed : [];
    } catch (e) {
      state.conversations = [];
    }
    if (!state.conversations.length) {
      state.conversations.push(newConversation());
    }
    state.currentId = state.conversations[0].id;
    const first = state.conversations[0];
    if (first && first.providerId) { state.providerId = first.providerId; }
    if (first && first.modelId) { state.modelId = first.modelId; }
  }

  function persist() {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(state.conversations)); }
    catch (e) { /* storage unavailable */ }
  }

  function newConversation() {
    return {
      id: "c-" + Date.now().toString(36) + "-" + Math.floor(Math.random() * 1e6).toString(36),
      title: "New conversation",
      messages: [],
      providerId: state.providerId || "",
      modelId: state.modelId || "",
      studioSessionId: null,
      createdAt: new Date().toISOString()
    };
  }

  function current() {
    return state.conversations.find((c) => c.id === state.currentId) || null;
  }

  // ---- theme ----

  function applyTheme() {
    let theme = "dark";
    try { theme = localStorage.getItem(THEME_KEY) || "dark"; } catch (e) { /* ignore */ }
    document.documentElement.dataset.theme = theme;
    $("themeBtn").onclick = () => {
      const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
      document.documentElement.dataset.theme = next;
      try { localStorage.setItem(THEME_KEY, next); } catch (e) { /* ignore */ }
    };
  }

  // ---- toast / connection ----

  let toastTimer = null;
  function toast(text, isError) {
    const el = $("toast");
    el.textContent = text;
    el.className = "toast" + (isError ? " error" : "");
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 4200);
  }

  function setConnection(kind, text) {
    const el = $("connStatus");
    el.className = "conn " + kind;
    el.textContent = "\u25CF " + text;
  }

  // ---- selects ----

  function fillSelect(select, items, selectedId, emptyLabel) {
    select.innerHTML = "";
    if (!items.length) {
      const option = document.createElement("option");
      option.value = "";
      option.textContent = emptyLabel || "(none)";
      select.appendChild(option);
      return;
    }
    for (const item of items) {
      const option = document.createElement("option");
      option.value = item.id;
      option.textContent = item.label;
      select.appendChild(option);
    }
    if (selectedId && items.some((item) => item.id === selectedId)) {
      select.value = selectedId;
    }
  }

  function providerLabel(provider) {
    return provider.name + (provider.isDefault ? " \u2605" : "")
        + (provider.status ? " · " + provider.status : "");
  }

  async function refreshProviders() {
    state.allProviders = (await providers()).filter((p) => p.enabled !== false);
    const keep = state.providerId
        || (state.allProviders.find((p) => p.isDefault) || {}).id || "";
    const select = $("providerSelect");
    fillSelect(select, state.allProviders.map((p) => ({ id: p.id, label: providerLabel(p) })),
        keep, "no provider");
    state.providerId = select.value || "";
    await refreshModels();
  }

  async function refreshModels(preferredModelId) {
    const providerId = $("providerSelect").value;
    state.providerId = providerId;
    const select = $("modelSelect");
    if (!providerId) {
      state.allModels = [];
      fillSelect(select, [], "", "no model");
      updateModelMeta();
      return;
    }
    select.innerHTML = "<option value=\"\">loading…</option>";
    try {
      state.allModels = await models(providerId);
    } catch (e) {
      state.allModels = [];
      $("sendStatus").textContent = "Models unavailable: " + e.message;
    }
    if (!state.allModels.length) {
      // The selected engine may be offline; still load every LLM that is reachable
      // from the other engines so the combo is never empty when models exist.
      try {
        const aggregate = await allModels(state.allProviders);
        state.allModels = aggregate.models;
      } catch (e) { /* keep empty */ }
    }
    const keep = preferredModelId || state.modelId;
    fillSelect(select, state.allModels.map((m) => ({
      id: m.id,
      label: (m.providerName ? m.providerName + " :: " : "") + m.name
    })), keep, "no model");
    state.modelId = select.value || "";
    updateModelMeta();
  }

  function updateModelMeta() {
    const provider = state.allProviders.find((p) => p.id === state.providerId);
    const model = state.allModels.find((m) => m.id === state.modelId);
    $("modelMeta").textContent = [
      provider ? provider.name : "",
      model ? model.name : "",
      model && model.sizeBytes ? formatBytes(model.sizeBytes) : ""
    ].filter(Boolean).join(" · ");
  }

  function formatBytes(n) {
    if (!n || n <= 0) { return ""; }
    const units = ["B", "KB", "MB", "GB", "TB"];
    let value = n, index = 0;
    while (value >= 1024 && index < units.length - 1) { value /= 1024; index++; }
    return value.toFixed(value >= 100 || index === 0 ? 0 : 1) + " " + units[index];
  }

  async function refreshProjects() {
    try { state.allProjects = await projects(); }
    catch (e) { state.allProjects = []; }
    const select = $("projectSelect");
    fillSelect(select, state.allProjects.map((p) => ({ id: p.id,
      label: p.name + (p.rootPath ? " — " + p.rootPath : "") })), state.projectId, "(none)");
    state.projectId = select.value || "";
  }

  async function refreshHarnesses() {
    try { state.allHarnesses = await harnesses(); }
    catch (e) { state.allHarnesses = []; }
    const field = $("harnessField");
    const select = $("harnessSelect");
    if (!state.allHarnesses.length) {
      field.hidden = true;
      state.harnessId = "";
      return;
    }
    field.hidden = false;
    fillSelect(select, state.allHarnesses.map((h) => ({ id: h.id,
      label: h.name + (h.status ? " · " + h.status : "") })), state.harnessId, "(none)");
    state.harnessId = select.value || "";
  }

  // ---- conversations ----

  function renderConversations() {
    const list = $("conversationList");
    list.innerHTML = "";
    const ordered = state.conversations.slice().sort((a, b) =>
        (b.updatedAt || b.createdAt || "").localeCompare(a.updatedAt || a.createdAt || ""));
    for (const conversation of ordered) {
      const row = document.createElement("div");
      row.className = "conv" + (conversation.id === state.currentId ? " active" : "");
      const label = document.createElement("button");
      label.className = "conv-label";
      label.textContent = conversation.title || "New conversation";
      label.title = conversation.title || "";
      label.onclick = () => selectConversation(conversation.id);
      const remove = document.createElement("button");
      remove.className = "conv-remove";
      remove.textContent = "\u00D7";
      remove.title = "Delete conversation";
      remove.onclick = (event) => {
        event.stopPropagation();
        deleteConversation(conversation.id);
      };
      row.appendChild(label);
      row.appendChild(remove);
      list.appendChild(row);
    }
  }

  function selectConversation(id) {
    state.currentId = id;
    const conversation = current();
    if (conversation) {
      if (conversation.providerId) {
        state.providerId = conversation.providerId;
        $("providerSelect").value = conversation.providerId;
        refreshModels(conversation.modelId).catch(() => {});
      }
      renderChat();
      renderConversations();
    }
  }

  function deleteConversation(id) {
    state.conversations = state.conversations.filter((c) => c.id !== id);
    if (!state.conversations.length) { state.conversations.push(newConversation()); }
    if (state.currentId === id) { state.currentId = state.conversations[0].id; }
    persist();
    renderConversations();
    renderChat();
  }

  function startNewConversation() {
    const conversation = newConversation();
    conversation.providerId = state.providerId;
    conversation.modelId = state.modelId;
    state.conversations.unshift(conversation);
    state.currentId = conversation.id;
    persist();
    renderConversations();
    renderChat();
    $("promptInput").focus();
  }

  function touchConversation() {
    const conversation = current();
    if (!conversation) { return; }
    conversation.providerId = state.providerId;
    conversation.modelId = state.modelId;
    conversation.updatedAt = new Date().toISOString();
    if (!conversation.messages.length) { return; }
    const firstUser = conversation.messages.find((m) => m.role === "user");
    if (firstUser) {
      const text = firstUser.content.replace(/\s+/g, " ").trim();
      conversation.title = text.length > 48 ? text.slice(0, 48) + "…" : text;
    }
    persist();
  }

  // ---- chat rendering ----

  function renderChat() {
    const log = $("chatLog");
    log.innerHTML = "";
    const conversation = current();
    const messages = conversation ? conversation.messages : [];
    if (!messages.length) {
      log.appendChild(emptyState());
      return;
    }
    for (const message of messages) { log.appendChild(messageNode(message)); }
    log.scrollTop = log.scrollHeight;
  }

  function emptyState() {
    const wrapper = document.createElement("div");
    wrapper.className = "empty";
    const title = document.createElement("div");
    title.className = "empty-title";
    title.textContent = "Start a conversation.";
    const text = document.createElement("div");
    text.className = "empty-text";
    text.textContent = "Choose an inference engine and model above, then send a prompt. "
        + "Engines, models, projects and harnesses are configured in the Settings tab.";
    wrapper.appendChild(title);
    wrapper.appendChild(text);
    return wrapper;
  }

  function messageNode(message) {
    const node = document.createElement("div");
    node.className = "msg " + message.role;
    const who = document.createElement("div");
    who.className = "who";
    who.textContent = message.role;
    const text = document.createElement("div");
    text.className = "text";
    text.textContent = message.content;
    node.appendChild(who);
    node.appendChild(text);
    return node;
  }

  function appendToken(token) {
    const conversation = current();
    if (!conversation) { return; }
    const message = conversation.messages[conversation.messages.length - 1];
    if (!message || message.role !== "assistant") { return; }
    message.content += token;
    const nodes = $("chatLog").querySelectorAll(".msg.assistant .text");
    const last = nodes[nodes.length - 1];
    if (last) { last.textContent = message.content; }
    const log = $("chatLog");
    log.scrollTop = log.scrollHeight;
  }

  // ---- send / stream ----

  function setBusy(busy) {
    state.busy = busy;
    $("sendBtn").disabled = busy;
    $("cancelBtn").hidden = !busy;
    $("sendStatus").textContent = busy ? "Streaming…" : "";
  }

  async function ensureSession(conversation) {
    if (conversation.studioSessionId) { return conversation.studioSessionId; }
    const created = await json("/api/inference/sessions", {
      method: "POST",
      body: {
        engine: state.providerId,
        model: state.modelId,
        systemPrompt: "",
        title: conversation.title
      }
    });
    conversation.studioSessionId = created.id;
    persist();
    return created.id;
  }

  async function send() {
    if (state.busy) { return; }
    const input = $("promptInput");
    const text = input.value.trim();
    if (!text) { return; }
    if (!state.providerId || !state.modelId) {
      toast("Select an engine and model first (Settings scans engines and browses models).",
          true);
      return;
    }
    if (!current()) { startNewConversation(); }
    const active = current();
    if (active.studioSessionId && (active.providerId !== state.providerId
        || active.modelId !== state.modelId)) {
      active.studioSessionId = null;
    }
    active.providerId = state.providerId;
    active.modelId = state.modelId;
    const userMessage = { role: "user", content: text };
    const assistantMessage = { role: "assistant", content: "" };
    active.messages.push(userMessage);
    active.messages.push(assistantMessage);
    input.value = "";
    renderChat();
    touchConversation();
    renderConversations();

    const controller = new AbortController();
    state.stream = { controller: controller, cancelled: false };
    setBusy(true);
    let streamError = null;

    try {
      const sessionId = await ensureSession(active);
      await sse("/api/inference/sessions/" + encodeURIComponent(sessionId)
          + "/messages/stream", {
        method: "POST", body: { content: text }, signal: controller.signal
      }, (type, data) => {
        if (type === "chunk") {
          if (data && data.text != null) { appendToken(data.text); }
        } else if (type === "done") {
          return false;
        } else if (type === "error") {
          streamError = new Error(data.message || "Inference failed");
          return false;
        }
      });
    } catch (e) {
      if (!state.stream.cancelled && e.name !== "AbortError") { streamError = e; }
    }

    const wasCancelled = state.stream.cancelled;
    state.stream = null;
    setBusy(false);
    if (streamError) {
      if (!assistantMessage.content) {
        const index = active.messages.indexOf(assistantMessage);
        if (index >= 0) { active.messages.splice(index, 1); }
      }
      toast(streamError.message, true);
      $("sendStatus").textContent = "Failed: " + streamError.message;
      renderChat();
    } else if (wasCancelled) {
      if (!assistantMessage.content) { assistantMessage.content = "[cancelled]"; }
      active.studioSessionId = null;
      renderChat();
    }
    touchConversation();
    renderConversations();
  }

  async function cancel() {
    if (!state.stream) { return; }
    state.stream.cancelled = true;
    try {
      const conversation = current();
      if (conversation && conversation.studioSessionId) {
        await json("/api/inference/sessions/"
            + encodeURIComponent(conversation.studioSessionId) + "/cancel",
            { method: "POST", body: {} });
      }
    } catch (e) { /* best effort */ }
    try { state.stream.controller.abort(); } catch (e) { /* ignore */ }
    $("sendStatus").textContent = "Cancelling…";
  }

  // ---- wiring ----

  function wire() {
    $("newChatBtn").onclick = startNewConversation;
    $("sendBtn").onclick = send;
    $("cancelBtn").onclick = cancel;
    $("refreshBtn").onclick = () => bootData(true);
    $("providerSelect").onchange = () => {
      state.modelId = "";
      refreshModels("").catch((e) => toast(e.message, true));
    };
    $("modelSelect").onchange = () => {
      state.modelId = $("modelSelect").value;
      const model = state.allModels.find((m) => m.id === state.modelId);
      if (model && model.providerId && model.providerId !== state.providerId) {
        state.providerId = model.providerId;
        $("providerSelect").value = model.providerId;
      }
      updateModelMeta();
    };
    $("projectSelect").onchange = () => { state.projectId = $("projectSelect").value; };
    $("harnessSelect").onchange = () => { state.harnessId = $("harnessSelect").value; };
    $("promptInput").addEventListener("keydown", (event) => {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        send();
      }
    });
  }

  async function bootData(showToast) {
    setConnection("offline", "connecting");
    try {
      await detect();
    } catch (e) {
      setConnection("offline", "offline");
      $("sendStatus").textContent = e.message;
      toast(e.message, true);
      return;
    }
    document.title = "DStudio — Inference";
    setConnection("online", "DStudio connected");
    try {
      await refreshProviders();
      await refreshProjects();
      await refreshHarnesses();
      if (showToast) { toast("Engines, models, projects and harnesses reloaded."); }
    } catch (e) {
      setConnection("offline", "error");
      $("sendStatus").textContent = e.message;
      toast(e.message, true);
    }
  }

  async function boot() {
    applyTheme();
    loadStore();
    wire();
    renderConversations();
    renderChat();
    await bootData(false);
  }

  boot();
})();
