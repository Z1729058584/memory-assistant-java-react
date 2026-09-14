const PROVIDER_STORAGE_KEY = "memory-console-provider";
const MODEL_STORAGE_KEY = "memory-console-model";

const state = {
  conversations: [],
  memories: [],
  review: null,
  config: null,
};

const els = {
  userId: document.getElementById("user-id"),
  providerSelect: document.getElementById("provider-select"),
  modelSelect: document.getElementById("model-select"),
  aiStatus: document.getElementById("ai-status"),
  configStatus: document.getElementById("config-status"),
  refreshAll: document.getElementById("refresh-all"),
  clearChat: document.getElementById("clear-chat"),
  chatThread: document.getElementById("chat-thread"),
  chatForm: document.getElementById("chat-form"),
  chatInput: document.getElementById("chat-input"),
  chatStatus: document.getElementById("chat-status"),
  sendButton: document.getElementById("send-button"),
  memoryForm: document.getElementById("memory-form"),
  memoryCategory: document.getElementById("memory-category"),
  memoryKey: document.getElementById("memory-key"),
  memoryValue: document.getElementById("memory-value"),
  memoryConfidence: document.getElementById("memory-confidence"),
  memoryExpiry: document.getElementById("memory-expiry"),
  memoryPinned: document.getElementById("memory-pinned"),
  memoryStatus: document.getElementById("memory-status"),
  showInactive: document.getElementById("show-inactive"),
  memoryList: document.getElementById("memory-list"),
  statsGrid: document.getElementById("stats-grid"),
  categoryBars: document.getElementById("category-bars"),
  chatMessageTemplate: document.getElementById("chat-message-template"),
  memoryCardTemplate: document.getElementById("memory-card-template"),
};

bootstrap();

async function bootstrap() {
  const savedUserId = localStorage.getItem("memory-console-user-id");
  if (savedUserId) {
    els.userId.value = savedUserId;
  }

  bindEvents();
  await refreshAll();
}

function bindEvents() {
  els.userId.addEventListener("change", () => {
    localStorage.setItem("memory-console-user-id", currentUserId());
    refreshAll();
  });

  els.providerSelect.addEventListener("change", () => {
    localStorage.setItem(PROVIDER_STORAGE_KEY, els.providerSelect.value);
    syncModelOptions();
    refreshConfig();
  });

  els.modelSelect.addEventListener("change", () => {
    localStorage.setItem(MODEL_STORAGE_KEY, els.modelSelect.value);
    refreshConfig();
  });

  els.refreshAll.addEventListener("click", refreshAll);

  els.clearChat.addEventListener("click", () => {
    state.conversations = [];
    renderChat();
  });

  els.chatInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      els.chatForm.requestSubmit();
    }
  });

  els.chatForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const message = els.chatInput.value.trim();
    if (!message) {
      setStatus(els.chatStatus, "Message is empty.");
      return;
    }

    setSendingState(true);
    setStatus(els.chatStatus, "Requesting model...");
    appendLocalMessage("user", message);

    try {
      const payload = await request("/api/chat", {
        method: "POST",
        body: JSON.stringify({
          userId: currentUserId(),
          message,
        }),
      });

      els.chatInput.value = "";
      appendLocalMessage("assistant", payload.reply);
      setStatus(
        els.chatStatus,
        payload.mode === "ai"
          ? `AI reply from ${payload.provider}/${payload.model}`
          : `Fallback mode on ${payload.provider}/${currentModelId()}${payload.error ? ` | ${payload.error}` : ""}`
      );
      await refreshAll();
    } catch (error) {
      setStatus(els.chatStatus, error.message, true);
    } finally {
      setSendingState(false);
    }
  });

  els.memoryForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const payload = {
      category: els.memoryCategory.value,
      key: els.memoryKey.value.trim(),
      value: els.memoryValue.value.trim(),
      confidence: Number(els.memoryConfidence.value || "1"),
      pinned: els.memoryPinned.checked,
    };

    const expiryDays = Number(els.memoryExpiry.value || "0");
    if (expiryDays > 0) {
      payload.expiresInDays = expiryDays;
    }

    if (!payload.key || !payload.value) {
      setStatus(els.memoryStatus, "Key and value are required.", true);
      return;
    }

    setStatus(els.memoryStatus, "Saving...");
    try {
      await request(`/api/users/${encodeURIComponent(currentUserId())}/memories`, {
        method: "POST",
        body: JSON.stringify(payload),
      });
      els.memoryValue.value = "";
      els.memoryExpiry.value = "";
      els.memoryPinned.checked = false;
      setStatus(els.memoryStatus, "Memory saved.");
      await refreshAll();
    } catch (error) {
      setStatus(els.memoryStatus, error.message, true);
    }
  });

  els.showInactive.addEventListener("change", refreshMemories);

  els.memoryList.addEventListener("click", async (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) {
      return;
    }

    const card = button.closest("[data-memory-id]");
    if (!card) {
      return;
    }

    const memoryId = Number(card.dataset.memoryId);
    const action = button.dataset.action;
    const memory = state.memories.find((item) => item.id === memoryId);
    if (!memory) {
      return;
    }

    try {
      if (action === "delete") {
        await request(`/api/users/${encodeURIComponent(currentUserId())}/memories/${memoryId}`, {
          method: "DELETE",
        });
      } else if (action === "pin") {
        await request(`/api/users/${encodeURIComponent(currentUserId())}/memories/${memoryId}`, {
          method: "PATCH",
          body: JSON.stringify({
            pinned: !memory.pinned,
            status: "active",
          }),
        });
      } else if (action === "archive") {
        await request(`/api/users/${encodeURIComponent(currentUserId())}/memories/${memoryId}`, {
          method: "PATCH",
          body: JSON.stringify({
            status: memory.status === "archived" ? "active" : "archived",
          }),
        });
      }

      await refreshAll();
    } catch (error) {
      setStatus(els.memoryStatus, error.message, true);
    }
  });
}

function currentUserId() {
  return els.userId.value.trim() || "u123";
}

function currentProviderId() {
  return els.providerSelect.value || localStorage.getItem(PROVIDER_STORAGE_KEY) || "";
}

function currentModelId() {
  return els.modelSelect.value || "";
}

async function refreshAll() {
  await Promise.all([refreshConfig(), refreshConversations(), refreshMemories(), refreshReview()]);
}

async function refreshConfig() {
  try {
    state.config = await request("/api/app-config", { includeAIHeaders: false });
    syncProviderOptions();
    renderConfig();
  } catch (error) {
    setStatus(els.configStatus, error.message, true);
  }
}

async function refreshConversations() {
  try {
    state.conversations = await request(`/api/users/${encodeURIComponent(currentUserId())}/conversations`);
    renderChat();
  } catch (error) {
    setStatus(els.chatStatus, error.message, true);
  }
}

async function refreshMemories() {
  try {
    const params = new URLSearchParams();
    params.set("includeInactive", String(els.showInactive.checked));
    params.set("includeExpired", String(els.showInactive.checked));
    state.memories = await request(
      `/api/users/${encodeURIComponent(currentUserId())}/memories?${params.toString()}`
    );
    renderMemories();
  } catch (error) {
    setStatus(els.memoryStatus, error.message, true);
  }
}

async function refreshReview() {
  try {
    state.review = await request(`/api/users/${encodeURIComponent(currentUserId())}/memories/review`);
    renderReview();
  } catch (error) {
    setStatus(els.memoryStatus, error.message, true);
  }
}

function syncProviderOptions() {
  const providers = state.config?.providers || [];
  const savedProvider = localStorage.getItem(PROVIDER_STORAGE_KEY);
  const defaultProvider = state.config?.provider || savedProvider || "ollama";
  const currentValue = els.providerSelect.value;

  if (!providers.length) {
    return;
  }

  els.providerSelect.innerHTML = "";
  for (const provider of providers) {
    const option = document.createElement("option");
    option.value = provider.id;
    option.textContent = provider.label;
    els.providerSelect.appendChild(option);
  }

  const preferredProvider =
    currentValue ||
    state.config?.provider ||
    savedProvider ||
    (providers.find((provider) => provider.id === defaultProvider)?.id ?? providers[0].id);

  els.providerSelect.value = preferredProvider;

  syncModelOptions();
}

function syncModelOptions() {
  const providers = state.config?.providers || [];
  const provider = providers.find((item) => item.id === currentProviderId());
  const savedModel = localStorage.getItem(MODEL_STORAGE_KEY);
  const currentValue = els.modelSelect.value;

  els.modelSelect.innerHTML = "";
  if (!provider) {
    return;
  }

  for (const model of provider.models) {
    const option = document.createElement("option");
    option.value = model.id;
    option.textContent = model.freeTier ? `${model.label} [free]` : model.label;
    els.modelSelect.appendChild(option);
  }

  const candidate = [currentValue, savedModel, state.config?.model, provider.models[0]?.id].find(Boolean);
  if (candidate && provider.models.some((model) => model.id === candidate)) {
    els.modelSelect.value = candidate;
  } else if (provider.models[0]) {
    els.modelSelect.value = provider.models[0].id;
  }

  localStorage.setItem(PROVIDER_STORAGE_KEY, currentProviderId());
  localStorage.setItem(MODEL_STORAGE_KEY, currentModelId());
}

function renderConfig() {
  if (!state.config) {
    return;
  }

  const provider = state.config.providers.find((item) => item.id === currentProviderId());
  const model = provider?.models.find((item) => item.id === currentModelId());
  const aiAvailable = Boolean(provider?.available);
  const source = currentProviderId() === "ollama" ? "local" : "environment";
  const baseUrl = provider?.baseUrl || "";

  els.aiStatus.textContent =
    aiAvailable
      ? source === "local"
        ? "Ollama online"
        : `Key configured ${currentProviderId()}`
      : source === "local"
        ? "Ollama offline"
        : "AI offline";
  els.aiStatus.className = `status-pill ${aiAvailable ? "online" : "offline"}`;

  if (!provider) {
    els.configStatus.textContent = "No provider configuration loaded yet.";
    return;
  }

  const modelText = model
    ? source === "local"
      ? `Current local model: ${model.label}.`
      : model.freeTier
        ? `Current model: ${model.label}. Free tier available.`
        : `Current model: ${model.label}.`
    : "No model selected.";

  const sourceText =
    source === "local"
      ? aiAvailable
        ? `Ollama is reachable at ${baseUrl}.`
        : `Ollama is not reachable at ${baseUrl}. Start Ollama and pull a model first.`
      : aiAvailable
        ? "The server has a provider key configured. This does not guarantee the upstream API is reachable."
        : "The server does not have a provider key yet, so chat stays in fallback mode.";

  const hintText =
    source === "local" && !aiAvailable
      ? "Suggested setup: ollama serve, then ollama pull qwen2.5:7b-instruct."
      : model?.description || "";

  els.configStatus.textContent = `${modelText} ${sourceText} ${hintText}`.trim();
}

function renderChat() {
  els.chatThread.innerHTML = "";

  if (!state.conversations.length) {
    els.chatThread.appendChild(emptyState("No stored conversation for this user yet."));
    return;
  }

  for (const turn of state.conversations) {
    const node = els.chatMessageTemplate.content.firstElementChild.cloneNode(true);
    node.classList.add(turn.role === "assistant" ? "assistant" : "user");
    node.querySelector(".bubble-role").textContent = turn.role;
    node.querySelector(".bubble-content").textContent = turn.content;
    node.querySelector(".bubble-meta").textContent = formatTimestamp(turn.createdAt);
    els.chatThread.appendChild(node);
  }

  els.chatThread.scrollTop = els.chatThread.scrollHeight;
}

function appendLocalMessage(role, content) {
  state.conversations = [
    ...state.conversations,
    {
      id: `local-${Date.now()}`,
      role,
      content,
      createdAt: new Date().toISOString(),
    },
  ];
  renderChat();
}

function renderMemories() {
  els.memoryList.innerHTML = "";

  if (!state.memories.length) {
    els.memoryList.appendChild(emptyState("No long-term memory for this user."));
    return;
  }

  for (const memory of state.memories) {
    const node = els.memoryCardTemplate.content.firstElementChild.cloneNode(true);
    node.dataset.memoryId = String(memory.id);
    node.querySelector(".memory-category").textContent = memory.category;
    node.querySelector(".memory-key").textContent = memory.key;
    node.querySelector(".memory-value").textContent = memory.value;
    node.querySelector(".memory-meta").textContent =
      `confidence ${memory.confidence.toFixed(2)} | accesses ${memory.accessCount} | updated ${formatTimestamp(memory.updatedAt)}`;

    const tags = node.querySelector(".memory-tags");
    tags.appendChild(tag(memory.status, memory.status === "archived" ? "archived" : ""));
    if (memory.pinned) {
      tags.appendChild(tag("pinned", "pinned"));
    }
    if (memory.expiresAt) {
      const expiresSoon =
        new Date(memory.expiresAt).getTime() - Date.now() < 7 * 24 * 60 * 60 * 1000;
      tags.appendChild(tag(`expires ${shortDate(memory.expiresAt)}`, expiresSoon ? "expiring" : ""));
    }

    const pinButton = node.querySelector('[data-action="pin"]');
    pinButton.textContent = memory.pinned ? "Unpin" : "Pin";

    const archiveButton = node.querySelector('[data-action="archive"]');
    archiveButton.textContent = memory.status === "archived" ? "Restore" : "Archive";

    els.memoryList.appendChild(node);
  }
}

function renderReview() {
  els.statsGrid.innerHTML = "";
  els.categoryBars.innerHTML = "";

  if (!state.review) {
    return;
  }

  const stats = state.review.stats;
  const entries = [
    ["Active", stats.active],
    ["Pinned", stats.pinned],
    ["Archived", stats.archived],
    ["Expired", stats.expired],
    ["Stale", stats.stale],
    ["Soon", stats.expiringSoon],
  ];

  for (const [label, value] of entries) {
    const card = document.createElement("div");
    card.className = "stat-card";
    card.innerHTML = `<span class="microcopy">${label}</span><strong>${value}</strong>`;
    els.statsGrid.appendChild(card);
  }

  const byCategory = state.review.byCategory || {};
  const maxValue = Math.max(1, ...Object.values(byCategory));

  if (!Object.keys(byCategory).length) {
    els.categoryBars.appendChild(emptyState("Category distribution appears after memory is stored."));
    return;
  }

  for (const [category, count] of Object.entries(byCategory)) {
    const row = document.createElement("div");
    row.className = "bar-row";
    row.innerHTML = `
      <span class="microcopy">${category}</span>
      <div class="bar-track"><div class="bar-fill" style="width:${(count / maxValue) * 100}%"></div></div>
      <strong>${count}</strong>
    `;
    els.categoryBars.appendChild(row);
  }
}

function tag(text, extraClass = "") {
  const el = document.createElement("span");
  el.className = `tag ${extraClass}`.trim();
  el.textContent = text;
  return el;
}

function emptyState(text) {
  const el = document.createElement("div");
  el.className = "empty-state";
  el.textContent = text;
  return el;
}

function setStatus(el, text, isError = false) {
  el.textContent = text;
  el.style.color = isError ? "var(--danger)" : "";
}

function setSendingState(sending) {
  els.sendButton.disabled = sending;
  els.chatInput.disabled = sending;
  els.sendButton.textContent = sending ? "Sending..." : "Send";
}

async function request(path, options = {}) {
  const includeAIHeaders = options.includeAIHeaders !== false;
  const response = await fetch(path, {
    headers: {
      "Content-Type": "application/json",
      ...(includeAIHeaders && currentProviderId() ? { "x-ai-provider": currentProviderId() } : {}),
      ...(includeAIHeaders && currentModelId() ? { "x-ai-model": currentModelId() } : {}),
      ...(options.headers || {}),
    },
    ...Object.fromEntries(Object.entries(options).filter(([key]) => key !== "includeAIHeaders")),
  });

  if (!response.ok) {
    let message = `Request failed: ${response.status}`;
    try {
      const data = await response.json();
      message = data.message || data.detail || message;
    } catch (_error) {
      // ignore parse failure
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

function formatTimestamp(value) {
  try {
    return new Date(value).toLocaleString();
  } catch (_error) {
    return value;
  }
}

function shortDate(value) {
  try {
    return new Date(value).toLocaleDateString();
  } catch (_error) {
    return value;
  }
}

