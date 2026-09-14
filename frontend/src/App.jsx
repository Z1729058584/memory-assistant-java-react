import { useEffect, useMemo, useState } from "react";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");
const PROVIDER_STORAGE_KEY = "memory-console-provider";
const MODEL_STORAGE_KEY = "memory-console-model";
const USER_STORAGE_KEY = "memory-console-user-id";

async function api(path, options = {}, ai = {}) {
  const headers = {
    "Content-Type": "application/json",
    ...(ai.provider ? { "x-ai-provider": ai.provider } : {}),
    ...(ai.model ? { "x-ai-model": ai.model } : {}),
    ...(options.headers || {})
  };
  const res = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  if (!res.ok) {
    let msg = `Request failed: ${res.status}`;
    try {
      const data = await res.json();
      msg = data.message || data.detail || msg;
    } catch {
      // Keep the generic HTTP message when the response is not JSON.
    }
    throw new Error(msg);
  }
  if (res.status === 204) return null;
  return res.json();
}

function formatTimestamp(value) {
  if (!value) return "";
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

function shortDate(value) {
  if (!value) return "";
  try {
    return new Date(value).toLocaleDateString();
  } catch {
    return value;
  }
}

function EmptyState({ children }) {
  return <div className="empty-state">{children}</div>;
}

function StatusPill({ online, providerLabel = "AI" }) {
  return (
    <p className={`status-pill ${online ? "online" : "offline"}`}>
      {online ? `${providerLabel} online` : `${providerLabel} offline`}
    </p>
  );
}

function Tag({ children, tone = "" }) {
  return <span className={`tag ${tone}`.trim()}>{children}</span>;
}

function configMessage(cfg, selectedProvider, selectedModel) {
  const provider = cfg.providers?.find((item) => item.id === selectedProvider) || cfg.providers?.[0];
  const model = provider?.models?.find((item) => item.id === selectedModel) || provider?.models?.[0];
  const source = provider?.id === "ollama" ? "local" : "environment";
  const baseUrl = provider?.baseUrl || "";

  const modelText = model
    ? source === "local"
      ? `Current local model: ${model.label}.`
      : `Current model: ${model.label}.`
    : "No model selected.";

  const sourceText =
    source === "local"
      ? provider?.available
        ? `Ollama is reachable at ${baseUrl}.`
        : `Ollama is not reachable at ${baseUrl}. Start Ollama and pull a model first.`
      : provider?.available
        ? "The server has a provider key configured."
        : "The server does not have a provider key yet, so chat stays in fallback mode.";

  return `${modelText} ${sourceText} ${model?.description || ""}`.trim();
}

export default function App() {
  const [userId, setUserId] = useState(() => localStorage.getItem(USER_STORAGE_KEY) || "u123");
  const [provider, setProvider] = useState(() => localStorage.getItem(PROVIDER_STORAGE_KEY) || "");
  const [model, setModel] = useState(() => localStorage.getItem(MODEL_STORAGE_KEY) || "");
  const [config, setConfig] = useState(null);
  const [conversations, setConversations] = useState([]);
  const [memories, setMemories] = useState([]);
  const [review, setReview] = useState(null);
  const [message, setMessage] = useState("");
  const [chatStatus, setChatStatus] = useState("Ready.");
  const [memStatus, setMemStatus] = useState("No manual change pending.");
  const [configStatus, setConfigStatus] = useState("Loading configuration...");
  const [showInactive, setShowInactive] = useState(false);
  const [busy, setBusy] = useState(false);
  const [saving, setSaving] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [newMemory, setNewMemory] = useState({
    category: "preference",
    key: "language",
    value: "",
    confidence: 1,
    expiresInDays: "",
    pinned: false
  });

  const providers = config?.providers || [];
  const currentProvider = useMemo(() => {
    return providers.find((item) => item.id === provider) || providers[0] || null;
  }, [provider, providers]);
  const models = currentProvider?.models || [];
  const currentModel = models.find((item) => item.id === model) || models[0] || null;
  const aiAvailable = Boolean(currentProvider?.available);

  useEffect(() => {
    refreshAll();
  }, [showInactive]);

  useEffect(() => {
    if (!config) return;
    const nextProvider = provider || config.provider || providers[0]?.id || "";
    if (nextProvider && nextProvider !== provider) {
      setProvider(nextProvider);
      localStorage.setItem(PROVIDER_STORAGE_KEY, nextProvider);
    }
  }, [config, provider, providers]);

  useEffect(() => {
    if (!currentProvider) return;
    const nextModel =
      model && currentProvider.models.some((item) => item.id === model)
        ? model
        : config?.model || currentProvider.models[0]?.id || "";
    if (nextModel && nextModel !== model) {
      setModel(nextModel);
      localStorage.setItem(MODEL_STORAGE_KEY, nextModel);
    }
  }, [config, currentProvider, model]);

  async function refreshAll() {
    setRefreshing(true);
    await Promise.allSettled([refreshConfig(), refreshConversations(), refreshMemories(), refreshReview()]);
    setRefreshing(false);
  }

  async function refreshConfig() {
    try {
      const cfg = await api("/api/app-config", { method: "GET" }, { model });
      setConfig(cfg);
      setConfigStatus(configMessage(cfg, provider || cfg.provider, model || cfg.model));
    } catch (err) {
      setConfigStatus(err.message);
    }
  }

  async function refreshConversations() {
    try {
      const data = await api(`/api/users/${encodeURIComponent(currentUserId())}/conversations`);
      setConversations(data);
    } catch (err) {
      setChatStatus(err.message);
    }
  }

  async function refreshMemories() {
    try {
      const params = new URLSearchParams();
      params.set("includeInactive", String(showInactive));
      params.set("includeExpired", String(showInactive));
      const data = await api(`/api/users/${encodeURIComponent(currentUserId())}/memories?${params.toString()}`);
      setMemories(data);
    } catch (err) {
      setMemStatus(err.message);
    }
  }

  async function refreshReview() {
    try {
      const data = await api(`/api/users/${encodeURIComponent(currentUserId())}/memories/review`);
      setReview(data);
    } catch (err) {
      setMemStatus(err.message);
    }
  }

  function currentUserId() {
    return userId.trim() || "u123";
  }

  function handleUserChange(value) {
    setUserId(value);
    localStorage.setItem(USER_STORAGE_KEY, value.trim() || "u123");
  }

  function handleProviderChange(value) {
    setProvider(value);
    localStorage.setItem(PROVIDER_STORAGE_KEY, value);
    const nextProvider = providers.find((item) => item.id === value);
    const nextModel = nextProvider?.models?.[0]?.id || "";
    setModel(nextModel);
    if (nextModel) localStorage.setItem(MODEL_STORAGE_KEY, nextModel);
  }

  function handleModelChange(value) {
    setModel(value);
    localStorage.setItem(MODEL_STORAGE_KEY, value);
  }

  async function sendMessage(e) {
    e.preventDefault();
    const text = message.trim();
    if (!text) {
      setChatStatus("Message is empty.");
      return;
    }

    setBusy(true);
    setChatStatus("Requesting model...");
    setConversations((prev) => [
      ...prev,
      { id: `local-user-${Date.now()}`, role: "user", content: text, createdAt: new Date().toISOString() }
    ]);

    try {
      const payload = await api(
        "/api/chat",
        {
          method: "POST",
          body: JSON.stringify({ userId: currentUserId(), message: text })
        },
        { provider, model }
      );
      setMessage("");
      setConversations((prev) => [
        ...prev,
        { id: `local-assistant-${Date.now()}`, role: "assistant", content: payload.reply, createdAt: new Date().toISOString() }
      ]);
      setChatStatus(
        payload.mode === "ai"
          ? `AI reply from ${payload.provider}/${payload.model}`
          : `Fallback mode on ${payload.provider}/${currentModel?.id || model}${payload.error ? ` | ${payload.error}` : ""}`
      );
      await refreshAll();
    } catch (err) {
      setChatStatus(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function createMemory(e) {
    e.preventDefault();
    if (!newMemory.key.trim() || !newMemory.value.trim()) {
      setMemStatus("Key and value are required.");
      return;
    }

    const payload = {
      category: newMemory.category,
      key: newMemory.key.trim(),
      value: newMemory.value.trim(),
      confidence: Number(newMemory.confidence || "1"),
      pinned: newMemory.pinned
    };
    const expiryDays = Number(newMemory.expiresInDays || "0");
    if (expiryDays > 0) payload.expiresInDays = expiryDays;

    setSaving(true);
    setMemStatus("Saving...");
    try {
      await api(`/api/users/${encodeURIComponent(currentUserId())}/memories`, {
        method: "POST",
        body: JSON.stringify(payload)
      });
      setNewMemory((prev) => ({ ...prev, value: "", expiresInDays: "", pinned: false }));
      setMemStatus("Memory saved.");
      await refreshAll();
    } catch (err) {
      setMemStatus(err.message);
    } finally {
      setSaving(false);
    }
  }

  async function updateMemory(memory, changes) {
    setMemStatus("Updating...");
    try {
      await api(`/api/users/${encodeURIComponent(currentUserId())}/memories/${memory.id}`, {
        method: "PATCH",
        body: JSON.stringify(changes)
      });
      setMemStatus("Memory updated.");
      await refreshAll();
    } catch (err) {
      setMemStatus(err.message);
    }
  }

  async function deleteMemory(memory) {
    const confirmed = window.confirm(`Delete memory "${memory.key}"?`);
    if (!confirmed) return;

    setMemStatus("Deleting...");
    try {
      await api(`/api/users/${encodeURIComponent(currentUserId())}/memories/${memory.id}`, { method: "DELETE" });
      setMemStatus("Memory deleted.");
      await refreshAll();
    } catch (err) {
      setMemStatus(err.message);
    }
  }

  const stats = review?.stats || {};
  const byCategory = review?.byCategory || {};
  const maxCategory = Math.max(1, ...Object.values(byCategory));

  return (
    <main className="page-shell">
      <header className="hero">
        <div>
          <p className="eyebrow">Persistent Memory Console</p>
          <h1>聊天、查看记忆，并切换 DeepSeek 模型。</h1>
        </div>

        <section className="identity-panel">
          <div className="panel-stack">
            <div>
              <label className="field-label" htmlFor="user-id">User ID</label>
              <div className="identity-row">
                <input
                  id="user-id"
                  className="text-input"
                  value={userId}
                  onChange={(e) => handleUserChange(e.target.value)}
                  onBlur={refreshAll}
                  autoComplete="off"
                />
                <button className="ghost-button" type="button" onClick={refreshAll} disabled={refreshing}>
                  {refreshing ? "Refreshing" : "Refresh"}
                </button>
              </div>
            </div>

            <div className="form-grid">
              <div>
                <label className="field-label" htmlFor="provider-select">Provider</label>
                <select id="provider-select" className="text-input" value={currentProvider?.id || provider} onChange={(e) => handleProviderChange(e.target.value)}>
                  {providers.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}
                </select>
              </div>
              <div>
                <label className="field-label" htmlFor="model-select">Model</label>
                <select id="model-select" className="text-input" value={currentModel?.id || model} onChange={(e) => handleModelChange(e.target.value)}>
                  {models.map((item) => <option key={item.id} value={item.id}>{item.freeTier ? `${item.label} [free]` : item.label}</option>)}
                </select>
              </div>
            </div>

            <div className="identity-row compact">
              <StatusPill online={aiAvailable} providerLabel={currentProvider?.label} />
            </div>
            <p className="microcopy">{configStatus}</p>
          </div>
        </section>
      </header>

      <section className="layout">
        <section className="chat-panel panel">
          <div className="panel-header">
            <div>
              <p className="panel-kicker">Assistant</p>
              <h2>Chat</h2>
            </div>
            <button className="ghost-button" type="button" onClick={() => setConversations([])}>Clear View</button>
          </div>

          <div className="chat-thread">
            {conversations.length === 0 ? (
              <EmptyState>No stored conversation for this user yet.</EmptyState>
            ) : (
              conversations.map((turn) => (
                <article key={turn.id} className={`bubble ${turn.role === "assistant" ? "assistant" : "user"}`}>
                  <p className="bubble-role">{turn.role}</p>
                  <p className="bubble-content">{turn.content}</p>
                  <p className="bubble-meta">{formatTimestamp(turn.createdAt)}</p>
                </article>
              ))
            )}
          </div>

          <form onSubmit={sendMessage} className="composer">
            <label className="field-label" htmlFor="chat-input">Message</label>
            <textarea
              id="chat-input"
              className="composer-input"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  e.currentTarget.form?.requestSubmit();
                }
              }}
              placeholder="Type a message. Enter sends. Shift+Enter adds a new line."
              rows={4}
              disabled={busy}
            />
            <div className="composer-actions">
              <p className="microcopy">{chatStatus}</p>
              <button className="primary-button" disabled={busy} type="submit">{busy ? "Sending..." : "Send"}</button>
            </div>
          </form>
        </section>

        <aside className="sidebar">
          <section className="panel stats-panel">
            <div className="panel-header">
              <div>
                <p className="panel-kicker">Overview</p>
                <h2>Memory Health</h2>
              </div>
            </div>

            <div className="stats-grid">
              {[
                ["Active", stats.active],
                ["Pinned", stats.pinned],
                ["Archived", stats.archived],
                ["Expired", stats.expired],
                ["Stale", stats.stale],
                ["Soon", stats.expiringSoon]
              ].map(([label, value]) => (
                <div className="stat-card" key={label}>
                  <span className="microcopy">{label}</span>
                  <strong>{value ?? 0}</strong>
                </div>
              ))}
            </div>

            <div className="category-bars">
              {Object.keys(byCategory).length === 0 ? (
                <EmptyState>Category distribution appears after memory is stored.</EmptyState>
              ) : (
                Object.entries(byCategory).map(([category, count]) => (
                  <div className="bar-row" key={category}>
                    <span className="microcopy">{category}</span>
                    <div className="bar-track"><div className="bar-fill" style={{ width: `${(count / maxCategory) * 100}%` }} /></div>
                    <strong>{count}</strong>
                  </div>
                ))
              )}
            </div>
          </section>

          <section className="panel add-panel">
            <div className="panel-header">
              <div>
                <p className="panel-kicker">Manual Control</p>
                <h2>Add Memory</h2>
              </div>
            </div>

            <form onSubmit={createMemory} className="memory-form">
              <div className="form-grid">
                <div>
                  <label className="field-label" htmlFor="memory-category">Category</label>
                  <select id="memory-category" className="text-input" value={newMemory.category} onChange={(e) => setNewMemory({ ...newMemory, category: e.target.value })}>
                    <option value="preference">preference</option>
                    <option value="profile">profile</option>
                    <option value="ongoing_task">ongoing_task</option>
                    <option value="stack">stack</option>
                  </select>
                </div>
                <div>
                  <label className="field-label" htmlFor="memory-key">Key</label>
                  <input id="memory-key" className="text-input" value={newMemory.key} onChange={(e) => setNewMemory({ ...newMemory, key: e.target.value })} autoComplete="off" />
                </div>
              </div>

              <div>
                <label className="field-label" htmlFor="memory-value">Value</label>
                <input id="memory-value" className="text-input" value={newMemory.value} onChange={(e) => setNewMemory({ ...newMemory, value: e.target.value })} placeholder="zh-CN" autoComplete="off" />
              </div>

              <div className="form-grid">
                <div>
                  <label className="field-label" htmlFor="memory-confidence">Confidence</label>
                  <input id="memory-confidence" className="text-input" type="number" min="0" max="1" step="0.01" value={newMemory.confidence} onChange={(e) => setNewMemory({ ...newMemory, confidence: e.target.value })} />
                </div>
                <div>
                  <label className="field-label" htmlFor="memory-expiry">Expiry Days</label>
                  <input id="memory-expiry" className="text-input" type="number" min="1" step="1" value={newMemory.expiresInDays} onChange={(e) => setNewMemory({ ...newMemory, expiresInDays: e.target.value })} placeholder="optional" />
                </div>
              </div>

              <label className="checkbox">
                <input type="checkbox" checked={newMemory.pinned} onChange={(e) => setNewMemory({ ...newMemory, pinned: e.target.checked })} />
                <span>Pin this memory</span>
              </label>

              <div className="composer-actions">
                <p className="microcopy">{memStatus}</p>
                <button className="primary-button" disabled={saving} type="submit">{saving ? "Saving..." : "Store Memory"}</button>
              </div>
            </form>
          </section>

          <section className="panel memory-panel">
            <div className="panel-header">
              <div>
                <p className="panel-kicker">Long-Term State</p>
                <h2>Memories</h2>
              </div>
              <label className="toggle">
                <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
                <span>Show inactive</span>
              </label>
            </div>

            <div className="memory-list">
              {memories.length === 0 ? (
                <EmptyState>No long-term memory for this user.</EmptyState>
              ) : (
                memories.map((memory) => (
                  <article className="memory-card" key={memory.id}>
                    <div className="memory-head">
                      <div>
                        <p className="memory-category">{memory.category}</p>
                        <h3 className="memory-key">{memory.key}</h3>
                      </div>
                      <div className="memory-tags">
                        <Tag tone={memory.status === "archived" ? "archived" : ""}>{memory.status}</Tag>
                        {memory.pinned ? <Tag tone="pinned">pinned</Tag> : null}
                        {memory.expiresAt ? <Tag tone="expiring">expires {shortDate(memory.expiresAt)}</Tag> : null}
                      </div>
                    </div>
                    <p className="memory-value">{memory.value}</p>
                    <div className="memory-meta">
                      confidence {Number(memory.confidence || 0).toFixed(2)} | accesses {memory.accessCount || 0} | updated {formatTimestamp(memory.updatedAt)}
                    </div>
                    <div className="memory-actions">
                      <button className="small-button" type="button" onClick={() => updateMemory(memory, { pinned: !memory.pinned, status: "active" })}>
                        {memory.pinned ? "Unpin" : "Pin"}
                      </button>
                      <button className="small-button" type="button" onClick={() => updateMemory(memory, { status: memory.status === "archived" ? "active" : "archived" })}>
                        {memory.status === "archived" ? "Restore" : "Archive"}
                      </button>
                      <button className="small-button danger" type="button" onClick={() => deleteMemory(memory)}>Delete</button>
                    </div>
                  </article>
                ))
              )}
            </div>
          </section>
        </aside>
      </section>
    </main>
  );
}
