/* DStudio Web — coding-task pane. Executes real harness workflows through the
 * TEIP 1.1 provider surface of the same backend (POST /teip/v1/executions,
 * status/events/result polling). No frontend-only state: every status, event,
 * changed file and diff comes from the backend execution services. */
window.DStudioTasks = (function () {
  "use strict";

  const STORE_KEY = "dstudio.tasks.v1";
  const POLL_MS = 1500;

  let deps = null;
  let container = null;
  let right = null;
  let active = false;
  const state = {
    tasks: [],
    current: null,
    timer: null,
    busy: false
  };

  function tasksOf() {
    const list = [];
    for (const id of Object.keys(state.tasks)) {
      if (state.tasks[id]) { list.push(state.tasks[id]); }
    }
    return list.sort((a, b) =>
        String(b.createdAt || "").localeCompare(String(a.createdAt || "")));
  }

  function persistIds() {
    try {
      const ids = tasksOf().slice(0, 50).map((t) => t.id);
      sessionStorage.setItem(STORE_KEY, JSON.stringify(ids));
    } catch (e) { /* storage unavailable */ }
  }

  async function refreshTask(id) {
    const task = state.tasks[id];
    if (!task) { return; }
    try {
      const status = await deps.json("/teip/v1/executions/"
          + encodeURIComponent(id));
      task.status = status.status || task.status;
      task.phase = status.phase || "";
      task.progress = typeof status.progress === "number" ? status.progress : null;
      task.statusMessage = status.statusMessage || "";
      if ((status.lastSequence || 0) > (task.lastSequence || 0)) {
        try {
          const events = await deps.json("/teip/v1/executions/"
              + encodeURIComponent(id) + "/events?afterSequence="
              + (task.lastSequence || 0) + "&limit=100");
          task.lastSequence = events.nextSequence || task.lastSequence;
          for (const event of (events.events || [])) {
            task.events.push({ type: event.type, data: event.data || {},
                at: event.timestamp || "" });
            if (task.events.length > 500) { task.events.shift(); }
          }
        } catch (e) { /* events optional */ }
      }
      if (["COMPLETED", "FAILED", "CANCELLED"].includes(task.status)
          && !task.result) {
        try {
          task.result = await deps.json("/teip/v1/executions/"
              + encodeURIComponent(id) + "/result");
        } catch (e) { /* result optional */ }
      }
    } catch (e) {
      task.statusMessage = e.message;
    }
    return task;
  }

  function anyActive() {
    return Object.values(state.tasks).some((t) =>
        t && ["QUEUED", "RUNNING"].includes(t.status));
  }

  function schedulePoll() {
    clearInterval(state.timer);
    state.timer = setInterval(async () => {
      if (!active) { return; }
      for (const task of tasksOf()) {
        if (["QUEUED", "RUNNING"].includes(task.status)
            || (!task.result && ["COMPLETED", "FAILED", "CANCELLED"]
                .includes(task.status))) {
          await refreshTask(task.id);
        }
      }
      if (state.current && state.tasks[state.current]) {
        renderDetail();
      }
      renderList();
      if (!anyActive()) {
        clearInterval(state.timer);
        state.timer = null;
      }
    }, POLL_MS);
  }

  async function run(instruction, harnessId, projectRoot) {
    if (!instruction.trim()) { return; }
    if (!harnessId) { deps.toast("Select a harness first.", true); return; }
    if (!projectRoot) { deps.toast("Select a project first (Settings → Projects).", true); return; }
    if (state.busy) { return; }
    state.busy = true;
    try {
      const accepted = await deps.json("/teip/v1/executions", {
        method: "POST",
        body: {
          protocol: "TEIP",
          version: "1.1",
          instruction: instruction,
          mode: "CODING",
          model: "",
          caller: { system: "DSTUDIO_WEB" },
          parameters: { harness: harnessId, workingDirectory: projectRoot }
        }
      });
      const task = {
        id: accepted.executionId,
        harnessId: harnessId,
        project: projectRoot,
        instruction: instruction,
        status: accepted.status || "QUEUED",
        phase: "", progress: null, statusMessage: "",
        lastSequence: 0, events: [], result: null,
        createdAt: accepted.createdAt || new Date().toISOString()
      };
      state.tasks[task.id] = task;
      state.current = task.id;
      persistIds();
      deps.log("task", "execution " + task.id.slice(0, 12) + " submitted via "
          + harnessId);
      renderList();
      renderDetail();
      schedulePoll();
      await refreshTask(task.id);
      renderList();
      renderDetail();
    } catch (e) {
      deps.toast("Task submission failed: " + e.message, true);
      deps.log("error", "task submission failed: " + e.message);
    } finally {
      state.busy = false;
      renderList();
    }
  }

  async function cancel(id) {
    try {
      await deps.json("/teip/v1/executions/" + encodeURIComponent(id) + "/cancel",
          { method: "POST" });
      deps.log("warning", "cancel requested for " + id.slice(0, 12));
      await refreshTask(id);
    } catch (e) {
      deps.toast("Cancel failed: " + e.message, true);
    }
    renderList();
    renderDetail();
  }

  function escapeHtml(value) {
    return deps.esc(value);
  }

  function badge(status) {
    const value = String(status || "—").toUpperCase();
    const cls = /COMPLETED/.test(value) ? " ok"
        : /FAILED|CANCELLED/.test(value) ? " err"
        : /RUNNING|QUEUED/.test(value) ? " info" : "";
    return "<span class=\"badge" + cls + "\">" + escapeHtml(value) + "</span>";
  }

  function renderList() {
    if (!container) { return; }
    const composer = container.querySelector("#task-composer");
    container.innerHTML = "";
    if (composer) { container.appendChild(composer); }
    const head = document.createElement("div");
    head.className = "section-head";
    const label = document.createElement("span");
    label.textContent = "Coding tasks";
    const refresh = document.createElement("button");
    refresh.className = "btn btn-small";
    refresh.textContent = "Refresh";
    refresh.onclick = async () => {
      for (const task of tasksOf()) { await refreshTask(task.id); }
      renderList();
      renderDetail();
    };
    head.appendChild(label);
    head.appendChild(refresh);
    container.appendChild(head);

    const list = document.createElement("div");
    list.className = "item-list";
    container.appendChild(list);
    if (!tasksOf().length) {
      const empty = document.createElement("div");
      empty.className = "empty-note";
      empty.style.padding = "10px";
      empty.textContent = "No tasks yet. Pick a harness, a project and a prompt, "
          + "then Run — the task executes through the real backend harness.";
      list.appendChild(empty);
    }
    for (const task of tasksOf()) {
      const row = document.createElement("div");
      row.className = "item";
      if (task.id === state.current) { row.classList.add("active"); }
      const body = document.createElement("div");
      body.className = "item-body";
      const title = document.createElement("div");
      title.className = "item-title";
      title.textContent = (task.instruction || "").replace(/\s+/g, " ").slice(0, 48)
          || task.id.slice(0, 12);
      body.appendChild(title);
      const sub = document.createElement("div");
      sub.className = "item-sub";
      sub.innerHTML = badge(task.status) + " " + escapeHtml(task.harnessId)
          + " · " + escapeHtml((task.instruction || "").length) + " chars";
      body.appendChild(sub);
      row.appendChild(body);
      row.onclick = () => { state.current = task.id; renderDetail(); renderList(); };
      list.appendChild(row);
    }
  }

  function renderDetail() {
    if (!right) { return; }
    const task = state.current ? state.tasks[state.current] : null;
    if (!task) {
      right.innerHTML = "<div class=\"empty-note\">Select a task for details.</div>";
      return;
    }
    const rows = [];
    const add = (k, v) => rows.push("<div class=\"ctx-kv\"><span class=\"k\">"
        + escapeHtml(k) + "</span><span class=\"v\">" + v + "</span></div>");
    add("Execution", task.id);
    add("Status", badge(task.status) + (task.phase ? " · " + escapeHtml(task.phase) : ""));
    if (task.progress != null) {
      add("Progress", Math.round(task.progress * 100) + "%");
    }
    add("Harness", task.harnessId);
    add("Project", task.project);
    if (task.statusMessage) { add("Message", escapeHtml(task.statusMessage)); }
    let html = "<div class=\"ctx-title\">Task " + task.id.slice(0, 12)
        + "</div>" + rows.join("");

    if (task.events.length) {
      const recent = task.events.slice(-6).map((event) =>
          "<div class=\"ev-line\"><span class=\"ts\">"
          + escapeHtml(String(event.at || "").slice(11, 19))
          + "</span> " + escapeHtml(event.type) + " "
          + escapeHtml(JSON.stringify(event.data).slice(0, 140))
          + "</div>").join("");
      html += "<div class=\"ctx-sub\">Events</div>"
          + "<div class=\"task-events\">" + recent + "</div>";
    }

    if (task.result) {
      const files = task.result.modifiedFiles || [];
      html += "<div class=\"ctx-sub\">Changed files (" + files.length + ")</div>";
      html += files.map((f) => "<div class=\"ev-line\">"
          + escapeHtml(f) + "</div>").join("") || "<div class=\"empty-note\">none</div>";
      if (task.result.patch) {
        html += "<div class=\"ctx-sub\">Diff</div><pre class=\"diff-view\">"
            + escapeHtml(task.result.patch) + "</pre>";
      }
      if (task.result.summary) {
        html += "<div class=\"ctx-sub\">Summary</div><div class=\"ev-line\">"
            + escapeHtml(task.result.summary) + "</div>";
      }
    }

    html += "<div class=\"task-actions\"><button class=\"btn btn-small\" id=\"task-cancel\""
        + (["COMPLETED", "FAILED", "CANCELLED"].includes(task.status) ? " disabled" : "")
        + ">cancel</button></div>";
    right.innerHTML = html;
    const cancelBtn = right.querySelector("#task-cancel");
    if (cancelBtn) { cancelBtn.onclick = () => cancel(task.id); }
  }

  function renderComposer() {
    if (!container) { return; }
    const composer = document.createElement("div");
    composer.className = "panel";
    composer.id = "task-composer";
    const title = document.createElement("div");
    title.className = "panel-title";
    title.textContent = "Run a coding task";
    composer.appendChild(title);
    const harnessSelect = document.createElement("select");
    harnessSelect.id = "task-harness";
    const projectSelect = document.createElement("select");
    projectSelect.id = "task-project";
    const input = document.createElement("textarea");
    input.id = "task-prompt";
    input.rows = 3;
    input.placeholder = "Describe the change… (runs through the selected harness)";
    const run = document.createElement("button");
    run.className = "btn btn-primary";
    run.id = "task-run";
    run.textContent = "Run";
    composer.appendChild(harnessSelect);
    composer.appendChild(projectSelect);
    composer.appendChild(input);
    composer.appendChild(run);
    container.insertBefore(composer, container.firstChild);

    Promise.all([deps.harnesses(), deps.projects()]).then(([harnesses, projects]) => {
      const selectedHarness = (harnesses.find((h) => h.isDefault) || harnesses[0]);
      for (const h of harnesses) {
        const option = document.createElement("option");
        option.value = h.id;
        option.textContent = h.name + (h.isDefault ? " ★" : "")
            + (h.enabled === false ? " (disabled)" : "");
        harnessSelect.appendChild(option);
      }
      if (selectedHarness) { harnessSelect.value = selectedHarness.id; }
      for (const p of projects) {
        const option = document.createElement("option");
        option.value = p.rootPath;
        option.textContent = p.name;
        projectSelect.appendChild(option);
      }
      if (!projects.length) {
        const option = document.createElement("option");
        option.value = "";
        option.textContent = "no project — open one in Settings";
        projectSelect.appendChild(option);
      }
    }).catch((e) => {
      const option = document.createElement("option");
      option.value = "";
      option.textContent = "backend unreachable: " + e.message;
      harnessSelect.appendChild(option);
    });

    run.onclick = () => run(input.value, harnessSelect.value, projectSelect.value);
  }

  async function activate(leftContent, rightContent) {
    active = true;
    container = leftContent;
    right = rightContent;
    container.innerHTML = "";
    renderComposer();
    renderList();
    renderDetail();
    // Adopt executions the backend reports as active (cross-client honesty).
    try {
      const diagnostics = await deps.json("/api/diagnostics");
      for (const execution of (diagnostics.executions || [])) {
        if (!state.tasks[execution.id]) {
          state.tasks[execution.id] = {
            id: execution.id, harnessId: execution.harnessId || "",
            project: "", instruction: "(backend execution)",
            status: execution.status, phase: "", progress: null,
            statusMessage: "", lastSequence: 0, events: [], result: null,
            createdAt: new Date().toISOString()
          };
        }
      }
      renderList();
    } catch (e) { /* diagnostics optional */ }
    schedulePoll();
  }

  function deactivate() {
    active = false;
    container = null;
    right = null;
    clearInterval(state.timer);
    state.timer = null;
  }

  async function restore() {
    let ids = [];
    try {
      ids = JSON.parse(sessionStorage.getItem(STORE_KEY) || "[]");
    } catch (e) { ids = []; }
    for (const id of ids) {
      if (!state.tasks[id]) {
        state.tasks[id] = {
          id: id, harnessId: "", project: "", instruction: "(restored)",
          status: "QUEUED", phase: "", progress: null, statusMessage: "",
          lastSequence: 0, events: [], result: null, createdAt: ""
        };
      }
    }
    for (const task of tasksOf()) { await refreshTask(task.id); }
    if (active) { renderList(); renderDetail(); }
  }

  function boot(depsMap) {
    deps = depsMap;
    restore();
  }

  return { boot, activate, deactivate };
})();
