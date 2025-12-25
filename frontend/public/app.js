const appEl = document.getElementById('app')

const STORAGE = {
  backendUrl: 'plugin_manager_backend_url',
  backendToken: 'plugin_manager_backend_token',
  backendUser: 'plugin_manager_backend_user',
  ownerAccount: 'plugin_manager_owner_account',
  account: 'plugin_manager_account',
  accounts: 'plugin_manager_accounts',
  accountsReset: 'plugin_manager_accounts_reset_v1',
  presence: 'plugin_manager_presence',
  timeouts: 'plugin_manager_timeouts',
  messages: 'plugin_manager_messages',
  logs: 'plugin_manager_logs',
  dashboardTab: 'plugin_manager_dashboard_tab',
}

const ENDPOINTS = {
  authLogin: () => '/auth/login',
  plugins: () => '/plugins',
  pluginServers: (pluginId) => `/plugins/${encodeURIComponent(pluginId)}/servers`,
  disableServerPlugin: (pluginId, serverId) =>
    `/plugins/${encodeURIComponent(pluginId)}/servers/${encodeURIComponent(serverId)}/disable`,
  enableServerPlugin: (pluginId, serverId) =>
    `/plugins/${encodeURIComponent(pluginId)}/servers/${encodeURIComponent(serverId)}/enable`,
  serverDetails: (serverId) => `/servers/${encodeURIComponent(serverId)}/details`,
  serverConsole: (serverId) => `/servers/${encodeURIComponent(serverId)}/console`,
  serverPlayers: (serverId) => `/servers/${encodeURIComponent(serverId)}/players`,
  serverCommand: (serverId) => `/servers/${encodeURIComponent(serverId)}/command`,
  health: () => '/health',
}

const BACKEND_OWNER_CREDENTIALS = {
  identifier: 'miacid',
  password: 'takanashi_20',
}

const DEFAULT_OWNER_ACCOUNT = {
  role: 'owner',
  username: 'miacid',
  email: 'miacidsenpai@gmail.com',
  password: 'takanashi_20',
}

function loadString(key) {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function saveString(key, value) {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {}
}

function loadJson(key) {
  const raw = loadString(key)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function saveJson(key, value) {
  if (value == null) return saveString(key, null)
  return saveString(key, JSON.stringify(value))
}

function makeId() {
  try {
    return crypto.randomUUID()
  } catch {
    return `id_${Date.now()}_${Math.random().toString(16).slice(2)}`
  }
}

function userKey(usernameOrEmail) {
  return String(usernameOrEmail || '').trim().toLowerCase()
}

function normalizeBackendUrl(input) {
  const raw = String(input || '').trim()
  if (!raw) return ''
  const withProto = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(raw) ? raw : `http://${raw}`
  try {
    const u = new URL(withProto)
    return u.origin
  } catch {
    return ''
  }
}

function getBackendUrl() {
  const stored = normalizeBackendUrl(loadString(STORAGE.backendUrl))
  if (stored) return stored
  const fromConfig = normalizeBackendUrl(window.__BACKEND_URL__)
  if (fromConfig) return fromConfig
  return ''
}

function setBackendUrl(url) {
  const norm = normalizeBackendUrl(url)
  saveString(STORAGE.backendUrl, norm || null)
  return norm
}

function getOwnerAccount() {
  const stored = loadJson(STORAGE.ownerAccount)
  if (!stored || typeof stored !== 'object') return { ...DEFAULT_OWNER_ACCOUNT }
  return {
    role: 'owner',
    username: String(stored.username || DEFAULT_OWNER_ACCOUNT.username),
    email: String(stored.email || DEFAULT_OWNER_ACCOUNT.email),
    password: String(stored.password || DEFAULT_OWNER_ACCOUNT.password),
  }
}

function setOwnerAccount(owner) {
  saveJson(STORAGE.ownerAccount, owner)
}

function getLogs() {
  const stored = loadJson(STORAGE.logs)
  return Array.isArray(stored) ? stored : []
}

function saveLogs(logs) {
  saveJson(STORAGE.logs, logs)
}

function addLog(actor, action, meta) {
  const logs = getLogs()
  logs.push({ id: makeId(), ts: Date.now(), actor: actor || 'unknown', action: String(action || ''), meta: meta ?? null })
  if (logs.length > 1500) logs.splice(0, logs.length - 1500)
  saveLogs(logs)
}

function getPresence() {
  const stored = loadJson(STORAGE.presence)
  return stored && typeof stored === 'object' ? stored : {}
}

function savePresence(map) {
  saveJson(STORAGE.presence, map)
}

function setUserPresence(username, online) {
  const key = userKey(username)
  if (!key) return
  const map = getPresence()
  map[key] = { online: Boolean(online), lastSeen: Date.now() }
  savePresence(map)
}

function getUserPresence(username) {
  const map = getPresence()
  const key = userKey(username)
  const item = map[key]
  if (!item || typeof item !== 'object') return { online: false, lastSeen: 0 }
  const lastSeen = typeof item.lastSeen === 'number' ? item.lastSeen : 0
  const online = Boolean(item.online) && Date.now() - lastSeen < 35_000
  return { online, lastSeen }
}

function getTimeouts() {
  const stored = loadJson(STORAGE.timeouts)
  return stored && typeof stored === 'object' ? stored : {}
}

function saveTimeouts(map) {
  saveJson(STORAGE.timeouts, map)
}

function setUserTimeout(username, timeoutUntilMsOrNull) {
  const key = userKey(username)
  if (!key) return
  const map = getTimeouts()
  if (timeoutUntilMsOrNull == null) delete map[key]
  else map[key] = timeoutUntilMsOrNull
  saveTimeouts(map)
}

function getTimeoutRemainingDays(username) {
  const key = userKey(username)
  if (!key) return 0
  const map = getTimeouts()
  const until = map[key]
  if (typeof until !== 'number' || until <= Date.now()) return 0
  const ms = until - Date.now()
  return Math.max(1, Math.ceil(ms / (24 * 60 * 60 * 1000)))
}

function getMessages() {
  const stored = loadJson(STORAGE.messages)
  return Array.isArray(stored) ? stored : []
}

function saveMessages(messages) {
  saveJson(STORAGE.messages, messages)
}

function sendMessage(toUsername, fromUsername, body) {
  const toKey = userKey(toUsername)
  if (!toKey) return
  const messages = getMessages()
  messages.push({
    id: makeId(),
    ts: Date.now(),
    to: toKey,
    from: String(fromUsername || '').trim(),
    body: String(body || ''),
    deliveredAt: null,
    readAt: null,
  })
  if (messages.length > 2000) messages.splice(0, messages.length - 2000)
  saveMessages(messages)
}

function pullUnreadMessagesFor(username) {
  const key = userKey(username)
  if (!key) return []
  const messages = getMessages()
  const unread = []
  const now = Date.now()
  for (const m of messages) {
    if (!m || typeof m !== 'object') continue
    if (m.to !== key) continue
    if (m.readAt != null) continue
    if (m.deliveredAt == null) m.deliveredAt = now
    unread.push(m)
  }
  if (unread.length > 0) saveMessages(messages)
  return unread
}

function markMessagesRead(ids) {
  const idSet = new Set(ids || [])
  if (idSet.size === 0) return
  const messages = getMessages()
  const now = Date.now()
  let changed = false
  for (const m of messages) {
    if (!m || typeof m !== 'object') continue
    if (!idSet.has(m.id)) continue
    if (m.readAt == null) {
      m.readAt = now
      changed = true
    }
  }
  if (changed) saveMessages(messages)
}

function resetMembersOnce() {
  const flag = loadString(STORAGE.accountsReset)
  if (flag === '1') return
  saveAccounts([])
  saveString(STORAGE.accountsReset, '1')
}

function getBackendSession() {
  const token = loadString(STORAGE.backendToken)
  const userRaw = loadString(STORAGE.backendUser)
  if (!token || !userRaw) return { token: null, user: null }
  try {
    return { token, user: JSON.parse(userRaw) }
  } catch {
    return { token: null, user: null }
  }
}

function setBackendSession(token, user) {
  saveString(STORAGE.backendToken, token)
  saveString(STORAGE.backendUser, JSON.stringify(user))
}

function clearBackendSession() {
  saveString(STORAGE.backendToken, null)
  saveString(STORAGE.backendUser, null)
}

function getAccountSession() {
  const acc = loadJson(STORAGE.account)
  if (!acc || typeof acc !== 'object') return null
  const role = acc.role === 'owner' ? 'owner' : acc.role === 'member' ? 'member' : null
  const username = typeof acc.username === 'string' ? acc.username : null
  const email = typeof acc.email === 'string' ? acc.email : null
  if (!role || !username || !email) return null
  if (role === 'owner') {
    const owner = getOwnerAccount()
    const ok = userKey(username) === userKey(owner.username) && userKey(email) === userKey(owner.email)
    if (!ok) return null
  }
  return { role, username, email }
}

function setAccountSession(account) {
  saveJson(STORAGE.account, account)
}

function clearAccountSession() {
  saveString(STORAGE.account, null)
}

function getAccounts() {
  const stored = loadJson(STORAGE.accounts)
  if (!Array.isArray(stored)) return []
  return stored
    .filter((a) => a && typeof a === 'object')
    .map((a) => ({
      role: 'member',
      username: String(a.username || '').trim(),
      email: String(a.email || '').trim(),
      password: String(a.password || ''),
    }))
    .filter((a) => a.username && a.email)
}

function saveAccounts(accounts) {
  saveJson(STORAGE.accounts, accounts)
}

function getAllAccounts() {
  return [getOwnerAccount(), ...getAccounts()]
}

function findAccountByIdentifier(identifier) {
  const ident = String(identifier || '').trim().toLowerCase()
  if (!ident) return null
  return (
    getAllAccounts().find(
      (a) => String(a.username || '').toLowerCase() === ident || String(a.email || '').toLowerCase() === ident,
    ) || null
  )
}

function setDashboardTab(tab) {
  saveString(STORAGE.dashboardTab, tab || null)
}

function getDashboardTab() {
  const raw = loadString(STORAGE.dashboardTab)
  return raw || null
}

let presenceHeartbeatTimer = null

function ensurePresenceHeartbeat() {
  const acc = getAccountSession()
  if (!acc) {
    if (presenceHeartbeatTimer) clearInterval(presenceHeartbeatTimer)
    presenceHeartbeatTimer = null
    return
  }
  setUserPresence(acc.username, true)
  if (presenceHeartbeatTimer) return
  presenceHeartbeatTimer = setInterval(() => {
    const current = getAccountSession()
    if (!current) {
      if (presenceHeartbeatTimer) clearInterval(presenceHeartbeatTimer)
      presenceHeartbeatTimer = null
      return
    }
    setUserPresence(current.username, true)
  }, 10_000)
}

window.addEventListener('beforeunload', () => {
  const acc = getAccountSession()
  if (acc) setUserPresence(acc.username, false)
})

class HttpError extends Error {
  constructor(status, body) {
    super(`HTTP ${status}`)
    this.status = status
    this.body = body
  }
}

async function requestJson(method, path, body) {
  const base = getBackendUrl()
  if (!base) throw new Error('backend_url_missing')

  const { token } = getBackendSession()
  const headers = new Headers()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const actor = getAccountSession()?.username
  if (actor) headers.set('X-PM-Actor', actor)
  if (body !== undefined) headers.set('Content-Type', 'application/json')

  const res = await fetch(`${base}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  const text = await res.text()
  const parsed = text ? safeParse(text) ?? text : null
  if (!res.ok) {
    if (res.status === 401) {
      clearBackendSession()
    }
    throw new HttpError(res.status, parsed)
  }
  return parsed
}

function safeParse(text) {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function el(tag, attrs, ...children) {
  const node = document.createElement(tag)
  if (attrs) {
    for (const [k, v] of Object.entries(attrs)) {
      if (k === 'class') node.className = v
      else if (k === 'style') Object.assign(node.style, v)
      else if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.slice(2).toLowerCase(), v)
      else if (v === true) node.setAttribute(k, '')
      else if (v !== false && v != null) node.setAttribute(k, String(v))
    }
  }
  for (const c of children.flat()) {
    if (c == null) continue
    node.appendChild(typeof c === 'string' ? document.createTextNode(c) : c)
  }
  return node
}

function setView(node, cleanup) {
  if (activeCleanup) activeCleanup()
  activeCleanup = cleanup || null
  appEl.innerHTML = ''
  appEl.appendChild(node)
}

function pill(status) {
  const label =
    status === 'active'
      ? 'Active'
      : status === 'disabled'
        ? 'Disabled'
        : status === 'online'
          ? 'Online'
          : 'Offline'

  const cls =
    status === 'active' || status === 'online'
      ? 'pill pillOk'
      : status === 'disabled'
        ? 'pill pillWarn'
        : 'pill pillDanger'

  return el('div', { class: cls }, label)
}

function spinner(label) {
  return el(
    'div',
    { class: 'panel', style: { display: 'flex', alignItems: 'center', gap: '10px' } },
    el('div', { class: 'pill' }, 'Loading'),
    el('div', { style: { color: 'var(--muted)', fontSize: '13px' } }, label || 'Fetching data...'),
  )
}

function showModal(title, contentNode, onClose) {
  const overlay = el('div', {
    class: 'modalOverlay',
    onmousedown: () => onClose(),
  })
  const modal = el('div', { class: 'modal', onmousedown: (e) => e.stopPropagation() }, [
    el('div', { class: 'modalTitle' }, title),
    el('div', { style: { marginTop: '10px' } }, contentNode),
  ])
  overlay.appendChild(modal)
  const onKeyDown = (e) => {
    if (e.key === 'Escape') onClose()
  }
  window.addEventListener('keydown', onKeyDown)
  document.body.appendChild(overlay)
  return () => {
    window.removeEventListener('keydown', onKeyDown)
    overlay.remove()
  }
}

function requireLogin() {
  const account = getAccountSession()
  if (!account) {
    navigate('/login')
    return false
  }
  return true
}

function loginPage() {
  const backendUrl = getBackendUrl()
  const owner = getOwnerAccount()
  const state = { identifier: '', password: '', loading: false, error: null }

  const errorBox = el('div', { class: 'error', style: { display: 'none' } })

  const identifierInput = el('input', {
    class: 'input',
    placeholder: 'Username or Email',
    autocomplete: 'username',
    value: state.identifier,
    oninput: (e) => (state.identifier = e.target.value),
  })
  const passwordInput = el('input', {
    class: 'input',
    placeholder: 'Password',
    type: 'password',
    autocomplete: 'current-password',
    value: state.password,
    oninput: (e) => (state.password = e.target.value),
  })
  const submitBtn = el('button', { class: 'btn btnPrimary', type: 'submit' }, 'Login')

  function setError(msg) {
    state.error = msg
    if (!msg) {
      errorBox.style.display = 'none'
      errorBox.textContent = ''
    } else {
      errorBox.style.display = ''
      errorBox.textContent = msg
    }
  }

  async function onSubmit(e) {
    e.preventDefault()
    if (state.loading) return
    const identifier = state.identifier.trim()
    if (!identifier || !state.password) return
    state.loading = true
    submitBtn.disabled = true
    submitBtn.textContent = 'Logging in...'
    setError(null)
    try {
      let acc = findAccountByIdentifier(identifier)

      // Auto-register owner if not set (first run on device)
      const currentOwner = getOwnerAccount()
      if (!acc && (!currentOwner.username || !currentOwner.password)) {
        const newOwner = {
          role: 'owner',
          username: identifier,
          email: identifier.includes('@') ? identifier : `${identifier}@example.com`,
          password: state.password,
        }
        setOwnerAccount(newOwner)
        acc = newOwner
      }

      if (!acc || String(acc.password) !== String(state.password)) throw new Error('invalid_credentials')
      const role = acc.role === 'owner' ? 'owner' : 'member'
      setAccountSession({ role, username: acc.username, email: acc.email })
      setUserPresence(acc.username, true)
      addLog(acc.username, 'login', { role })
      navigate('/dashboard')
    } catch {
      setError('Invalid credentials.')
    } finally {
      state.loading = false
      submitBtn.disabled = false
      submitBtn.textContent = 'Login'
    }
  }

  const form = el(
    'form',
    { onsubmit: onSubmit, style: { display: 'grid', gap: '10px' } },
    identifierInput,
    passwordInput,
    errorBox,
    submitBtn,
  )

  const root = el('div', { class: 'appShell' }, [
    el('div', { class: 'topBar' }, [
      el('div', null, [
        el('div', { class: 'title' }, 'Plugin Manager'),
        el('div', { class: 'subTitle' }, 'Sign in to continue'),
      ]),
    ]),
    el('div', { class: 'panel', style: { maxWidth: '520px' } }, form),
  ])

  setView(root, null)
}

function mainDashboardPage() {
  if (!requireLogin()) return
  const account = getAccountSession()
  if (!account) return

  const isOwner = account.role === 'owner'
  const timeoutDays = isOwner ? 0 : getTimeoutRemainingDays(account.username)
  if (!isOwner && timeoutDays > 0) {
    addLog(account.username, 'timeout_blocked', { days: timeoutDays })
    const header = el('div', { class: 'topBar' }, [
      el('div', null, [el('div', { class: 'title' }, 'Plugin Dashboard'), el('div', { class: 'subTitle' }, `${account.username} · member`)]),
      el(
        'div',
        { class: 'row' },
        el(
          'button',
          {
            class: 'btn',
            onclick: () => {
              setUserPresence(account.username, false)
              addLog(account.username, 'logout', { role: account.role })
              clearAccountSession()
              navigate('/login')
            },
          },
          'Logout',
        ),
      ),
    ])
    const root = el('div', { class: 'appShell' }, [
      header,
      el('div', { class: 'welcome' }, [`Welcome, ${account.username}`, el('div', { class: 'welcomeSub' }, 'Account access is limited right now.')]),
      el('div', { class: 'panel' }, [
        el('div', { class: 'title', style: { fontSize: '14px' } }, 'Timed Out'),
        el('div', { class: 'cardBody', style: { marginTop: '6px' } }, `You cannot use this panel for ${timeoutDays} day${timeoutDays === 1 ? '' : 's'}.`),
      ]),
    ])
    setView(root, null)
    return
  }

  const state = {
    plugins: [],
    loading: false,
    error: null,
    tab: null,
    backendUrlInput: getBackendUrl(),
    connection: { status: getBackendSession().token ? 'ok' : 'idle', message: '', pingMs: null },
    accountsView: 'home',
    logs: [],
    logsLoading: false,
    logsError: null,
    logsLastLoadedAt: 0,
  }

  const initialTab = getDashboardTab() || 'plugins'
  state.tab = !isOwner && initialTab === 'accounts' ? 'plugins' : initialTab

  const listNode = el('div', { class: 'grid', style: { marginTop: '12px' } })
  const errorNode = el('div', { class: 'error', style: { display: 'none' } })
  const loadingNode = spinner('Loading plugins...')
  const contentNode = el('div', null)
  const emptyNode = el('div', { class: 'panel' }, [el('div', { class: 'cardBody' }, 'No plugins connected')])
  const tabsNode = el('div', { class: 'tabs' })

  function setError(msg) {
    state.error = msg
    if (!msg) {
      errorNode.style.display = 'none'
      errorNode.textContent = ''
    } else {
      errorNode.style.display = ''
      errorNode.textContent = msg
    }
  }

  function renderPluginList() {
    listNode.innerHTML = ''
    for (const p of state.plugins) {
      const card = el('div', { class: 'card', onclick: () => navigate(`/plugins/${p.id}`) }, [
        el('div', { class: 'cardHeader' }, [
          el('div', null, [
            el('div', { class: 'cardTitle' }, p.name),
            el('div', { class: 'cardBody' }, `${p.serverCount} server${p.serverCount === 1 ? '' : 's'}`),
          ]),
          pill(p.status),
        ]),
        el('div', { class: 'cardBody' }, p.description || 'No description'),
      ])
      listNode.appendChild(card)
    }
    return listNode
  }

  async function loadPlugins() {
    state.loading = true
    setError(null)
    renderContent()
    try {
      const data = await requestJson('GET', ENDPOINTS.plugins())
      state.plugins = Array.isArray(data) ? data : []
      addLog(account.username, 'plugins_loaded', { count: state.plugins.length })
    } catch {
      setError('Failed to load plugins. Connect backend first.')
    } finally {
      state.loading = false
      renderContent()
      headerSub.textContent = `${state.plugins.length} plugin${state.plugins.length === 1 ? '' : 's'} · ${getAccountSession()?.username || 'User'} · ${getAccountSession()?.role || ''}`
    }
  }

  async function loadAuditLogs() {
    if (state.logsLoading) return
    const { token } = getBackendSession()
    if (!token) return
    state.logsLoading = true
    state.logsError = null
    renderContent()
    try {
      const data = await requestJson('GET', `/logs?limit=250`)
      state.logs = Array.isArray(data) ? data : []
      state.logsLastLoadedAt = Date.now()
    } catch {
      state.logsError = 'Failed to load logs.'
    } finally {
      state.logsLoading = false
      renderContent()
    }
  }

  async function connectBackend() {
    state.connection.status = 'loading'
    state.connection.message = 'Connecting...'
    state.connection.pingMs = null
    renderContent()
    try {
      if (!isOwner) throw new Error('readonly')
      const before = getBackendUrl()
      const base = setBackendUrl(state.backendUrlInput)
      if (!base) throw new Error('invalid_url')
      state.backendUrlInput = base
      if (before !== base) addLog(account.username, 'backend_url_changed', { from: before || null, to: base })

      const t0 = performance.now()
      const healthRes = await fetch(`${base}${ENDPOINTS.health()}`)
      if (!healthRes.ok) throw new Error('bad_status')
      state.connection.pingMs = Math.round(performance.now() - t0)

      const loginRes = await fetch(`${base}${ENDPOINTS.authLogin()}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ identifier: BACKEND_OWNER_CREDENTIALS.identifier, password: BACKEND_OWNER_CREDENTIALS.password }),
      })
      const text = await loginRes.text()
      const parsed = text ? safeParse(text) ?? text : null
      if (!loginRes.ok || !parsed || parsed.ok !== true || !parsed.token) throw new Error('login_failed')

      setBackendSession(parsed.token, parsed.user)
      state.connection.status = 'ok'
      state.connection.message = 'Connected successfully'
      addLog(account.username, 'backend_connected', { base })
      if (state.tab === 'plugins') await loadPlugins()
    } catch (e) {
      state.connection.status = 'error'
      if (String(e?.message || '') === 'readonly') state.connection.message = 'Read-only access'
      else if (String(e?.message || '') === 'invalid_url') state.connection.message = 'Invalid backend URL'
      else state.connection.message = 'Failed to connect'
      state.connection.pingMs = null
      addLog(account.username, 'backend_connect_failed', { message: state.connection.message })
    }
    renderContent()
    renderTabs()
  }

  async function checkBackendConnection() {
    state.connection.status = 'loading'
    state.connection.message = 'Checking connection...'
    state.connection.pingMs = null
    renderContent()
    try {
      const base = normalizeBackendUrl(state.backendUrlInput) || getBackendUrl()
      if (!base) throw new Error('invalid_url')
      const t0 = performance.now()
      const res = await fetch(`${base}${ENDPOINTS.health()}`)
      const pingMs = Math.round(performance.now() - t0)
      if (!res.ok) throw new Error('bad_status')
      const data = await res.json().catch(() => null)
      if (!data || data.ok !== true) throw new Error('bad_payload')
      state.connection.status = getBackendSession().token ? 'ok' : 'idle'
      state.connection.pingMs = pingMs
      state.connection.message = `Status: OK · Ping: ${pingMs}ms`
      addLog(getAccountSession()?.username || 'unknown', 'backend_check_ok', { base, pingMs })
    } catch {
      state.connection.status = 'error'
      state.connection.pingMs = null
      state.connection.message = 'Status: Error'
      addLog(getAccountSession()?.username || 'unknown', 'backend_check_error', null)
    }
    renderContent()
    renderTabs()
  }

  function renderBackendTab() {
    const statusPill =
      state.connection.status === 'ok'
        ? el('div', { class: 'pill pillOk' }, 'OK')
        : state.connection.status === 'error'
          ? el('div', { class: 'pill pillDanger' }, 'Error')
          : state.connection.status === 'loading'
            ? el('div', { class: 'pill pillWarn' }, 'Checking')
            : el('div', { class: 'pill' }, 'Idle')

    const urlInput = el('input', {
      class: 'input',
      placeholder: 'http://your-backend:3001',
      value: state.backendUrlInput,
      disabled: !isOwner,
      oninput: (e) => (state.backendUrlInput = e.target.value),
    })

    return el('div', { class: 'panel' }, [
      el('div', { class: 'row', style: { justifyContent: 'space-between', gap: '12px' } }, [
        el('div', null, [
          el('div', { class: 'title', style: { fontSize: '14px' } }, 'Backend URL'),
          el('div', { class: 'subTitle' }, isOwner ? 'Owner can edit and connect.' : 'Read-only for members.'),
        ]),
        el('div', { class: 'row' }, [
          statusPill,
          el('button', { class: 'btn', onclick: () => checkBackendConnection() }, 'Check'),
          el('button', { class: 'btn btnPrimary', onclick: () => connectBackend(), disabled: !isOwner }, 'Connect'),
        ]),
      ]),
      el('div', { style: { marginTop: '10px', display: 'grid', gap: '10px' } }, [
        urlInput,
        state.connection.message ? el('div', { class: 'cardBody' }, state.connection.message) : null,
      ]),
    ])
  }

  function renderPluginsTab() {
    const { token } = getBackendSession()
    if (!token) {
      return el('div', { class: 'panel' }, [
        el('div', { class: 'title', style: { fontSize: '14px' } }, 'Not connected'),
        el('div', { class: 'cardBody', style: { marginTop: '6px' } }, 'Connect your backend URL to load plugins.'),
        el(
          'div',
          { style: { marginTop: '12px' } },
          el('button', { class: 'btn btnPrimary', onclick: () => setTab('backend') }, 'Open Backend URL'),
        ),
      ])
    }
    if (!state.loading && !state.error && state.plugins.length === 0) return emptyNode
    return renderPluginList()
  }

  function renderAccountsTab() {
    if (!isOwner) return el('div', { class: 'panel' }, el('div', { class: 'cardBody' }, 'Read-only access'))

    function setAccountsView(view) {
      state.accountsView = view
      renderContent()
    }

    function renderAccountsHome() {
      const grid = el('div', { class: 'grid', style: { marginTop: '12px' } }, [
        el('div', { class: 'card', onclick: () => setAccountsView('create') }, [
          el('div', { class: 'cardHeader' }, [el('div', { class: 'cardTitle' }, 'Create Account'), el('div', { class: 'pill pillOk' }, 'Owner')]),
          el('div', { class: 'cardBody' }, 'Create a new member account.'),
        ]),
        el('div', { class: 'card', onclick: () => setAccountsView('list') }, [
          el('div', { class: 'cardHeader' }, [el('div', { class: 'cardTitle' }, 'Account List'), el('div', { class: 'pill' }, 'Manage')]),
          el('div', { class: 'cardBody' }, 'Manage member accounts (delete, timeout, message, status).'),
        ]),
      ])

      return el('div', { class: 'panel' }, [
        el('div', { class: 'title', style: { fontSize: '14px' } }, 'Accounts'),
        el('div', { class: 'cardBody', style: { marginTop: '6px' } }, 'Owner-only account management.'),
        grid,
      ])
    }

    function renderCreateAccount() {
      const formState = { username: '', email: '', password: '' }
      const msg = el('div', { class: 'cardBody', style: { display: 'none' } })

      function setMsg(text) {
        msg.style.display = text ? '' : 'none'
        msg.textContent = text || ''
      }

      const usernameInput = el('input', {
        class: 'input',
        placeholder: 'Username',
        oninput: (e) => (formState.username = e.target.value),
      })
      const emailInput = el('input', {
        class: 'input',
        placeholder: 'Email',
        oninput: (e) => (formState.email = e.target.value),
      })
      const passwordInput = el('input', {
        class: 'input',
        placeholder: 'Password',
        type: 'password',
        oninput: (e) => (formState.password = e.target.value),
      })

      function onCreate(e) {
        e.preventDefault()
        const username = String(formState.username || '').trim()
        const email = String(formState.email || '').trim()
        const password = String(formState.password || '')
        if (!username || !email || !password) return

        const all = getAllAccounts()
        const unameTaken = all.some((a) => String(a.username || '').toLowerCase() === username.toLowerCase())
        if (unameTaken) return setMsg('Username already taken')
        const emailTaken = all.some((a) => String(a.email || '').toLowerCase() === email.toLowerCase())
        if (emailTaken) return setMsg('Email already in use')

        const members = getAccounts()
        members.push({ role: 'member', username, email, password })
        saveAccounts(members)
        addLog(account.username, 'account_created', { username, email, role: 'member' })

        usernameInput.value = ''
        emailInput.value = ''
        passwordInput.value = ''
        formState.username = ''
        formState.email = ''
        formState.password = ''
        setMsg('Member account created')
      }

      const form = el(
        'form',
        { onsubmit: onCreate, style: { display: 'grid', gap: '10px', maxWidth: '520px' } },
        usernameInput,
        emailInput,
        passwordInput,
        el('div', { class: 'row' }, [
          el('button', { class: 'btn', type: 'button', onclick: () => setAccountsView('home') }, 'Back'),
          el('button', { class: 'btn btnPrimary', type: 'submit' }, 'Create Account'),
        ]),
        msg,
      )

      return el('div', { class: 'panel' }, [
        el('div', { class: 'title', style: { fontSize: '14px' } }, 'Create Account'),
        el('div', { class: 'cardBody', style: { marginTop: '6px' } }, 'Creates a member role account.'),
        el('div', { style: { marginTop: '12px' } }, form),
      ])
    }

    function renderAccountList() {
      const members = getAccounts()
      const list = el('div', { style: { marginTop: '12px', display: 'grid', gap: '10px' } })

      function refresh() {
        list.innerHTML = ''
        if (members.length === 0) {
          list.appendChild(el('div', { class: 'cardBody' }, 'No member accounts.'))
          return
        }
        for (const m of getAccounts()) {
          const p = getUserPresence(m.username)
          const timeoutDays = getTimeoutRemainingDays(m.username)
          const statusPill = p.online ? el('div', { class: 'pill pillOk' }, 'Online') : el('div', { class: 'pill pillDanger' }, 'Offline')
          const timeoutPill = timeoutDays > 0 ? el('div', { class: 'pill pillWarn' }, `Timed out: ${timeoutDays}d`) : null

          const onDelete = () => {
            const close = showModal(
              'Delete Account',
              el('div', { style: { display: 'grid', gap: '10px' } }, [
                el('div', { class: 'cardBody' }, `Delete account "${m.username}"?`),
                el('div', { class: 'modalFooter' }, [
                  el('button', { class: 'btn', onclick: () => close() }, 'Cancel'),
                  el(
                    'button',
                    {
                      class: 'btn btnDanger',
                      onclick: () => {
                        const remaining = getAccounts().filter(
                          (x) => userKey(x.username) !== userKey(m.username) || userKey(x.email) !== userKey(m.email),
                        )
                        saveAccounts(remaining)
                        setUserTimeout(m.username, null)
                        addLog(account.username, 'account_deleted', { username: m.username, email: m.email })
                        close()
                        refresh()
                      },
                    },
                    'Delete',
                  ),
                ]),
              ]),
              () => close(),
            )
          }

          const onTimeout = () => {
            const input = el('input', { class: 'input', placeholder: 'Days (0 to remove)' })
            const close = showModal(
              'Timeout User',
              el('div', { style: { display: 'grid', gap: '10px' } }, [
                el('div', { class: 'cardBody' }, `Set timeout for "${m.username}"`),
                input,
                el('div', { class: 'modalFooter' }, [
                  el('button', { class: 'btn', onclick: () => close() }, 'Cancel'),
                  el(
                    'button',
                    {
                      class: 'btn btnPrimary',
                      onclick: () => {
                        const days = Number(String(input.value || '').trim())
                        if (!Number.isFinite(days) || days < 0) return
                        if (days === 0) {
                          setUserTimeout(m.username, null)
                          addLog(account.username, 'timeout_removed', { username: m.username })
                        } else {
                          const until = Date.now() + days * 24 * 60 * 60 * 1000
                          setUserTimeout(m.username, until)
                          addLog(account.username, 'timeout_set', { username: m.username, days })
                        }
                        close()
                        refresh()
                      },
                    },
                    'Save',
                  ),
                ]),
              ]),
              () => close(),
            )
          }

          const onSendMsg = () => {
            const textarea = el('textarea', { class: 'input', style: { minHeight: '120px', resize: 'vertical' }, placeholder: 'Type message...' })
            const close = showModal(
              'Send Message',
              el('div', { style: { display: 'grid', gap: '10px' } }, [
                el('div', { class: 'cardBody' }, `To: ${m.username}`),
                textarea,
                el('div', { class: 'modalFooter' }, [
                  el('button', { class: 'btn', onclick: () => close() }, 'Cancel'),
                  el(
                    'button',
                    {
                      class: 'btn btnPrimary',
                      onclick: () => {
                        const body = String(textarea.value || '').trim()
                        if (!body) return
                        sendMessage(m.username, account.username, body)
                        addLog(account.username, 'message_sent', { to: m.username, online: getUserPresence(m.username).online })
                        close()
                      },
                    },
                    'Send',
                  ),
                ]),
              ]),
              () => close(),
            )
          }

          list.appendChild(
            el('div', { class: 'panel' }, [
              el('div', { class: 'row', style: { justifyContent: 'space-between' } }, [
                el('div', null, [
                  el('div', { class: 'title', style: { fontSize: '14px' } }, m.username),
                  el('div', { class: 'cardBody' }, m.email),
                ]),
                el('div', { class: 'row' }, [statusPill, timeoutPill].filter(Boolean)),
              ]),
              el('div', { class: 'row', style: { marginTop: '10px' } }, [
                el('button', { class: 'btn', onclick: () => onSendMsg() }, 'Send Msg'),
                el('button', { class: 'btn', onclick: () => onTimeout() }, 'Timeout'),
                el('button', { class: 'btn btnDanger', onclick: () => onDelete() }, 'Delete'),
              ]),
            ]),
          )
        }
      }

      refresh()

      return el('div', { class: 'panel' }, [
        el('div', { class: 'row', style: { justifyContent: 'space-between' } }, [
          el('div', null, [
            el('div', { class: 'title', style: { fontSize: '14px' } }, 'Account List'),
            el('div', { class: 'cardBody', style: { marginTop: '6px' } }, 'Manage member accounts.'),
          ]),
          el('button', { class: 'btn', onclick: () => setAccountsView('home') }, 'Back'),
        ]),
        list,
      ])
    }

    if (state.accountsView === 'create') return renderCreateAccount()
    if (state.accountsView === 'list') return renderAccountList()
    return renderAccountsHome()
  }

  function renderLogsTab() {
    if (!isOwner) return el('div', { class: 'panel' }, el('div', { class: 'cardBody' }, 'Read-only access'))
    const { token } = getBackendSession()
    if (!token) {
      return el('div', { class: 'panel' }, [
        el('div', { class: 'title', style: { fontSize: '14px' } }, 'Logs'),
        el('div', { class: 'cardBody', style: { marginTop: '6px' } }, 'Connect your backend to view logs.'),
      ])
    }
    if (!state.logsLoading && Date.now() - state.logsLastLoadedAt > 5_000) void loadAuditLogs()
    return el('div', { class: 'panel' }, [
      el('div', { class: 'title', style: { fontSize: '14px' } }, 'Logs'),
      el('div', { class: 'cardBody', style: { marginTop: '6px' } }, 'Tracks backend server changes and dashboard actions.'),
      state.logsError ? el('div', { class: 'error', style: { marginTop: '10px' } }, state.logsError) : null,
      el(
        'div',
        { class: 'row', style: { justifyContent: 'space-between', marginTop: '10px' } },
        el('div', { class: 'cardBody' }, state.logsLoading ? 'Loading...' : `${state.logs.length} event${state.logs.length === 1 ? '' : 's'}`),
        el('button', { class: 'btn', onclick: () => void loadAuditLogs(), disabled: state.logsLoading }, 'Refresh'),
      ),
      el(
        'div',
        { style: { marginTop: '12px', display: 'grid', gap: '8px' } },
        state.logs.length === 0
          ? el('div', { class: 'cardBody' }, 'No logs yet.')
          : state.logs.map((l) =>
              el('div', { class: 'logRow' }, [
                el('div', { class: 'logMeta' }, new Date(l.ts).toLocaleString()),
                el('div', { class: 'logTitle' }, `${l.actor} · ${l.action}`),
                l.meta ? el('div', { class: 'logBody' }, typeof l.meta === 'string' ? l.meta : JSON.stringify(l.meta)) : null,
              ]),
            ),
      ),
    ])
  }

  function renderSettingsTab() {
    const current = getAccountSession()
    if (!current) return el('div', { class: 'panel' }, el('div', { class: 'cardBody' }, 'Not logged in'))

    const currentOwner = current.role === 'owner' ? getOwnerAccount() : null
    const currentUsername = currentOwner ? currentOwner.username : current.username
    const currentEmail = currentOwner ? currentOwner.email : current.email

    const formState = { username: currentUsername, email: currentEmail, password: '' }
    const msg = el('div', { class: 'cardBody', style: { display: 'none' } })

    function setMsg(text) {
      msg.style.display = text ? '' : 'none'
      msg.textContent = text || ''
    }

    const usernameInput = el('input', {
      class: 'input',
      placeholder: 'Username',
      value: formState.username,
      oninput: (e) => (formState.username = e.target.value),
    })
    const emailInput = el('input', {
      class: 'input',
      placeholder: 'Email',
      value: formState.email,
      oninput: (e) => (formState.email = e.target.value),
    })
    const passwordInput = el('input', {
      class: 'input',
      placeholder: 'New Password (optional)',
      type: 'password',
      oninput: (e) => (formState.password = e.target.value),
    })

    function onSave(e) {
      e.preventDefault()
      const nextUsername = String(formState.username || '').trim()
      const nextEmail = String(formState.email || '').trim()
      const nextPassword = formState.password ? String(formState.password) : null
      if (!nextUsername || !nextEmail) return

      const all = getAllAccounts()
      const unameTaken = all.some(
        (a) => String(a.username || '').toLowerCase() === nextUsername.toLowerCase() && String(a.username || '').toLowerCase() !== currentUsername.toLowerCase(),
      )
      if (unameTaken) return setMsg('Username already taken')
      const emailTaken = all.some(
        (a) => String(a.email || '').toLowerCase() === nextEmail.toLowerCase() && String(a.email || '').toLowerCase() !== currentEmail.toLowerCase(),
      )
      if (emailTaken) return setMsg('Email already in use')

      if (current.role === 'owner') {
        const updatedOwner = { ...getOwnerAccount(), username: nextUsername, email: nextEmail, password: nextPassword || getOwnerAccount().password }
        setOwnerAccount(updatedOwner)
        setAccountSession({ role: 'owner', username: updatedOwner.username, email: updatedOwner.email })
        headerSub.textContent = `${state.plugins.length} plugin${state.plugins.length === 1 ? '' : 's'} · ${updatedOwner.username} · owner`
        addLog(current.username, 'account_updated', { role: 'owner' })
        return setMsg('Account updated')
      }

      const members = getAccounts()
      const idx = members.findIndex(
        (a) =>
          a &&
          typeof a === 'object' &&
          String(a.role) === 'member' &&
          String(a.username || '').toLowerCase() === current.username.toLowerCase() &&
          String(a.email || '').toLowerCase() === current.email.toLowerCase(),
      )
      if (idx === -1) return setMsg('Account not found')

      const updated = {
        ...members[idx],
        username: nextUsername,
        email: nextEmail,
        password: nextPassword ? nextPassword : members[idx].password,
      }
      members[idx] = updated
      saveAccounts(members)
      setAccountSession({ role: 'member', username: updated.username, email: updated.email })
      headerSub.textContent = `${state.plugins.length} plugin${state.plugins.length === 1 ? '' : 's'} · ${updated.username} · member`
      addLog(current.username, 'account_updated', { role: 'member' })
      return setMsg('Account updated')
    }

    const form = el(
      'form',
      { onsubmit: onSave, style: { display: 'grid', gap: '10px', maxWidth: '520px' } },
      usernameInput,
      emailInput,
      passwordInput,
      el('button', { class: 'btn btnPrimary', type: 'submit' }, 'Save Changes'),
      msg,
    )

    return el('div', { class: 'panel' }, [
      el('div', { class: 'title', style: { fontSize: '14px' } }, 'Account Settings'),
      el('div', { class: 'cardBody', style: { marginTop: '6px' } }, `Role: ${current.role}`),
      el('div', { style: { marginTop: '12px' } }, form),
    ])
  }

  function renderTabs() {
    tabsNode.innerHTML = ''
    const mk = (key, label) =>
      el('button', { class: `tab ${state.tab === key ? 'tabActive' : ''}`, onclick: () => setTab(key) }, label)
    tabsNode.appendChild(mk('plugins', 'Plugins'))
    tabsNode.appendChild(mk('backend', 'Backend URL'))
    if (isOwner) tabsNode.appendChild(mk('accounts', 'Accounts'))
    if (isOwner) tabsNode.appendChild(mk('logs', 'Logs'))
    tabsNode.appendChild(mk('settings', 'Account Settings'))
    return tabsNode
  }

  function setTab(next) {
    state.tab = next
    setDashboardTab(next)
    renderContent()
    renderTabs()
  }

  function renderContent() {
    contentNode.innerHTML = ''
    if (state.loading) contentNode.appendChild(loadingNode)
    if (state.error) contentNode.appendChild(errorNode)

    if (state.tab === 'backend') return contentNode.appendChild(renderBackendTab())
    if (state.tab === 'accounts') return contentNode.appendChild(renderAccountsTab())
    if (state.tab === 'logs') return contentNode.appendChild(renderLogsTab())
    if (state.tab === 'settings') return contentNode.appendChild(renderSettingsTab())
    return contentNode.appendChild(renderPluginsTab())
  }

  const headerSub = el(
    'div',
    { class: 'subTitle' },
    `${state.plugins.length} plugin${state.plugins.length === 1 ? '' : 's'} · ${account.username || 'User'} · ${account.role || ''}`,
  )

  const header = el('div', { class: 'topBar' }, [
    el('div', null, [el('div', { class: 'title' }, 'Plugin Dashboard'), headerSub]),
    el('div', { class: 'row' }, [
      el('button', { class: 'btn', onclick: () => loadPlugins(), disabled: !getBackendSession().token }, 'Refresh'),
      el(
        'button',
        {
          class: 'btn',
          onclick: () => {
            setUserPresence(account.username, false)
            addLog(account.username, 'logout', { role: account.role })
            clearAccountSession()
            navigate('/login')
          },
        },
        'Logout',
      ),
    ]),
  ])

  const welcome = el('div', { class: 'welcome' }, [
    `Welcome back, ${account.username}!`,
    el(
      'div',
      { class: 'welcomeSub' },
      isOwner
        ? 'Manage plugins, servers, accounts, and review backend logs.'
        : 'View plugins and servers. Your dashboard actions can be audited in the logs.',
    ),
  ])
  const root = el('div', { class: 'appShell' }, [header, welcome, renderTabs(), contentNode])

  let presenceTimer = null
  let messagesTimer = null
  const onStorage = (e) => {
    if (e.key === STORAGE.backendUrl) {
      state.backendUrlInput = getBackendUrl()
      renderContent()
    }
  }
  window.addEventListener('storage', onStorage)

  setView(root, () => {
    window.removeEventListener('storage', onStorage)
    if (presenceTimer) clearInterval(presenceTimer)
    if (messagesTimer) clearInterval(messagesTimer)
    setUserPresence(account.username, false)
  })

  setUserPresence(account.username, true)
  presenceTimer = setInterval(() => setUserPresence(account.username, true), 10_000)

  if (!isOwner) {
    const deliver = () => {
      const unread = pullUnreadMessagesFor(account.username)
      if (unread.length === 0) return
      const ids = unread.map((m) => m.id)
      const close = showModal(
        'Message',
        el(
          'div',
          { style: { display: 'grid', gap: '10px' } },
          unread.map((m) => el('div', { class: 'panel' }, [el('div', { class: 'cardBody' }, `From: ${m.from}`), el('div', { style: { marginTop: '6px' } }, m.body)])),
        ),
        () => {
          markMessagesRead(ids)
          addLog(account.username, 'messages_read', { count: ids.length })
          close()
        },
      )
    }
    messagesTimer = setInterval(() => deliver(), 4_000)
    setTimeout(() => deliver(), 300)
  }

  const base = getBackendUrl()
  if (base && getBackendSession().token) {
    void (async () => {
      try {
        const res = await fetch(`${base}${ENDPOINTS.health()}`)
        if (!res.ok) throw new Error('bad_status')
        state.connection.status = 'ok'
        state.connection.message = 'Connected'
        renderContent()
        if (state.tab === 'plugins') await loadPlugins()
      } catch {
        clearBackendSession()
        state.connection.status = 'error'
        state.connection.message = 'Backend disconnected'
        renderContent()
      } finally {
        renderTabs()
      }
    })()
  }

  renderContent()
  renderTabs()
}

function pluginDetailPage(pluginId) {
  if (!requireLogin()) return
  if (!getBackendSession().token) {
    setDashboardTab('backend')
    navigate('/dashboard')
    return
  }
  const state = { loading: true, error: null, plugin: null, servers: [] }
  const content = el('div', null)
  const errorNode = el('div', { class: 'error', style: { display: 'none' } })
  const loadingNode = spinner('Loading servers...')

  function setError(msg) {
    state.error = msg
    if (!msg) {
      errorNode.style.display = 'none'
      errorNode.textContent = ''
    } else {
      errorNode.style.display = ''
      errorNode.textContent = msg
    }
  }

  function render() {
    content.innerHTML = ''
    if (state.loading) content.appendChild(loadingNode)
    if (state.error) content.appendChild(errorNode)

    const grid = el('div', { class: 'grid', style: { marginTop: '12px' } })
    for (const s of state.servers) {
      const card = el('div', { class: 'card', onclick: () => navigate(`/servers/${s.id}`) }, [
        el('div', { class: 'cardHeader' }, [
          el('div', null, [
            el('div', { class: 'cardTitle' }, `${s.ip}:${s.port}`),
            el('div', { class: 'cardBody' }, `${s.onlinePlayers} player${s.onlinePlayers === 1 ? '' : 's'} online`),
          ]),
          el('div', { class: 'row', style: { gap: '8px' } }, [pill(s.serverStatus), pill(s.pluginStatus)]),
        ]),
        el('div', { class: 'row', style: { justifyContent: 'space-between', marginTop: '10px' } }, [
          el('div', { class: 'cardBody' }, s.serverStatus === 'offline' ? 'Offline server: actions may fail.' : 'Click to open server panel.'),
          el('div', { class: 'row', style: { gap: '8px' } }, [
            el(
              'button',
              {
                class: 'btn',
                onclick: (e) => {
                  e.stopPropagation()
                  navigate(`/servers/${s.id}`)
                },
              },
              'Open',
            ),
            s.pluginStatus === 'disabled'
              ? el(
                  'button',
                  {
                    class: 'btn btnPrimary',
                    onclick: async (e) => {
                      e.stopPropagation()
                      try {
                        await requestJson('POST', ENDPOINTS.enableServerPlugin(pluginId, s.id))
                        await load()
                      } catch {
                        setError('Failed to reactivate plugin on this server.')
                      }
                    },
                  },
                  'Reactivate',
                )
              : el(
                  'button',
                  {
                    class: 'btn btnDanger',
                    onclick: async (e) => {
                      e.stopPropagation()
                      try {
                        await requestJson('POST', ENDPOINTS.disableServerPlugin(pluginId, s.id))
                        await load()
                      } catch {
                        setError('Failed to disable plugin on this server.')
                      }
                    },
                  },
                  'Revoke / Disable',
                ),
          ]),
        ]),
      ])
      grid.appendChild(card)
    }
    content.appendChild(grid)
  }

  async function load() {
    state.loading = true
    setError(null)
    render()
    try {
      const [plugins, servers] = await Promise.all([
        requestJson('GET', ENDPOINTS.plugins()),
        requestJson('GET', ENDPOINTS.pluginServers(pluginId)),
      ])
      state.plugin = Array.isArray(plugins) ? plugins.find((p) => p.id === pluginId) || null : null
      state.servers = Array.isArray(servers) ? servers : []
    } catch {
      setError('Failed to load plugin servers.')
    } finally {
      state.loading = false
      render()
      title.textContent = state.plugin?.name || 'Plugin'
    }
  }

  const title = el('div', { class: 'title' }, 'Plugin')
  const header = el('div', { class: 'topBar' }, [
    el('div', null, [title, el('div', { class: 'subTitle' }, 'Servers using this plugin')]),
    el('div', { class: 'row' }, [
      el('button', { class: 'btn', onclick: () => navigate('/dashboard') }, 'Back'),
      el('button', { class: 'btn', onclick: () => load() }, 'Refresh'),
    ]),
  ])

  const root = el('div', { class: 'appShell' }, [header, content])
  setView(root, null)
  void load()
}

function serverPanelPage(serverId) {
  if (!requireLogin()) return
  const { token } = getBackendSession()
  if (!token) {
    setDashboardTab('backend')
    navigate('/dashboard')
    return
  }
  const state = {
    tab: 'details',
    details: null,
    loading: true,
    error: null,
    consoleLines: [],
    consoleError: null,
    players: [],
    onlinePlayers: 0,
    playersError: null,
  }

  const headerTitle = el('div', { class: 'title' }, 'Server')
  const headerSub = el('div', { class: 'subTitle' }, '')
  const errorNode = el('div', { class: 'error', style: { display: 'none' } })
  const loadingNode = spinner('Loading server...')
  const tabsNode = el('div', { class: 'tabs', style: { display: 'none' } })
  const bodyNode = el('div', null)
  const consoleEl = el('div', { class: 'console' }, 'No console output yet.')
  const cmdInput = el('input', { class: 'input', placeholder: 'Type a command...' })
  const sendBtn = el('button', { class: 'btn btnPrimary' }, 'Send Command')
  const stopBtn = el('button', { class: 'btn btnDanger' }, 'Stop')
  const restartBtn = el('button', { class: 'btn' }, 'Restart')

  let consoleEs = null
  let playersEs = null

  function setError(msg) {
    state.error = msg
    if (!msg) {
      errorNode.style.display = 'none'
      errorNode.textContent = ''
    } else {
      errorNode.style.display = ''
      errorNode.textContent = msg
    }
  }

  function setHeader() {
    const d = state.details
    headerTitle.textContent = d?.plugin?.name || 'Server'
    headerSub.textContent = d ? `${d.ip}:${d.port}` : ''
  }

  function renderTabs() {
    tabsNode.innerHTML = ''
    const mk = (key, label) =>
      el('button', { class: `tab ${state.tab === key ? 'tabActive' : ''}`, onclick: () => setTab(key) }, label)
    tabsNode.appendChild(mk('details', 'Server Detail'))
    tabsNode.appendChild(mk('console', 'Console'))
    tabsNode.appendChild(mk('players', 'Player List'))
    tabsNode.style.display = state.details ? '' : 'none'
    return tabsNode
  }

  function setTab(tab) {
    state.tab = tab
    renderBody()
    renderTabs()
  }

  function renderBody() {
    bodyNode.innerHTML = ''
    if (state.loading) bodyNode.appendChild(loadingNode)
    if (state.error) bodyNode.appendChild(errorNode)

    if (!state.details) return

    if (state.tab === 'details') {
      bodyNode.appendChild(
        el('div', { class: 'panel' }, [
          el('div', { class: 'kpiRow' }, [
            el('div', { class: 'kpi' }, [
              el('div', { class: 'kpiLabel' }, 'Server'),
              el('div', { class: 'kpiValue' }, `${state.details.ip}:${state.details.port}`),
            ]),
            el('div', { class: 'kpi' }, [
              el('div', { class: 'kpiLabel' }, 'Plugin'),
              el(
                'div',
                { class: 'kpiValue' },
                state.details.plugin ? `${state.details.plugin.name} v${state.details.plugin.version}` : 'Unknown',
              ),
            ]),
            el('div', { class: 'kpi' }, [el('div', { class: 'kpiLabel' }, 'Server Status'), el('div', { class: 'kpiValue' }, pill(state.details.serverStatus))]),
            el('div', { class: 'kpi' }, [el('div', { class: 'kpiLabel' }, 'Plugin Status'), el('div', { class: 'kpiValue' }, pill(state.details.pluginStatus))]),
          ]),
        ]),
      )
    }

    if (state.tab === 'console') {
      if (state.consoleError) bodyNode.appendChild(el('div', { class: 'error' }, state.consoleError))
      consoleEl.textContent = state.consoleLines.length === 0 ? 'No console output yet.' : state.consoleLines.join('\n')
      bodyNode.appendChild(consoleEl)

      const sendQuick = async (command, button) => {
        button.disabled = true
        const prev = button.textContent
        button.textContent = 'Sending...'
        try {
          await requestJson('POST', ENDPOINTS.serverCommand(serverId), { command })
        } catch {
          state.consoleError = 'Failed to send command.'
          renderBody()
        } finally {
          button.disabled = false
          button.textContent = prev
        }
      }

      stopBtn.disabled = false
      stopBtn.onclick = () => sendQuick('stop', stopBtn)
      restartBtn.disabled = false
      restartBtn.onclick = () => sendQuick('restart', restartBtn)

      sendBtn.disabled = false
      sendBtn.onclick = async () => {
        const cmd = cmdInput.value.trim()
        if (!cmd) return
        sendBtn.disabled = true
        sendBtn.textContent = 'Sending...'
        try {
          await requestJson('POST', ENDPOINTS.serverCommand(serverId), { command: cmd })
          cmdInput.value = ''
        } catch {
          state.consoleError = 'Failed to send command.'
          renderBody()
        } finally {
          sendBtn.disabled = false
          sendBtn.textContent = 'Send Command'
        }
      }
      cmdInput.onkeydown = (e) => {
        if (e.key === 'Enter') sendBtn.click()
      }
      bodyNode.appendChild(el('div', { class: 'row', style: { marginTop: '12px' } }, [stopBtn, restartBtn]))
      bodyNode.appendChild(el('div', { class: 'row', style: { marginTop: '10px' } }, [cmdInput, sendBtn]))
    }

    if (state.tab === 'players') {
      if (state.playersError) bodyNode.appendChild(el('div', { class: 'error' }, state.playersError))

      const onKick = (playerName) => {
        const formState = { reason: '' }
        const reasonInput = el('input', {
          class: 'input',
          placeholder: 'Reason (optional)',
          oninput: (e) => (formState.reason = e.target.value),
        })
        const close = showModal(
          'Kick Player',
          el('div', { style: { display: 'grid', gap: '10px' } }, [
            el('div', { class: 'cardBody' }, `Kick ${playerName}?`),
            reasonInput,
            el(
              'div',
              { class: 'row', style: { justifyContent: 'flex-end' } },
              el(
                'button',
                {
                  class: 'btn btnDanger',
                  onclick: async () => {
                    const reason = String(formState.reason || '').trim()
                    const cmd = reason ? `kick ${playerName} ${reason}` : `kick ${playerName}`
                    try {
                      await requestJson('POST', ENDPOINTS.serverCommand(serverId), { command: cmd })
                      close()
                    } catch {
                      state.playersError = 'Failed to kick player.'
                      renderBody()
                      close()
                    }
                  },
                },
                'Kick',
              ),
            ),
          ]),
          () => close(),
        )
      }

      bodyNode.appendChild(
        el('div', { class: 'panel' }, [
          el('div', { class: 'title', style: { fontSize: '14px' } }, `Total players online: ${state.onlinePlayers}`),
          el(
            'div',
            { style: { marginTop: '10px', display: 'grid', gap: '8px' } },
            state.players.length === 0
              ? el('div', { class: 'cardBody' }, 'No players online.')
              : state.players.map((p) =>
                  el('div', { class: 'panel', style: { padding: '10px 12px' } }, [
                    el('div', { class: 'row', style: { justifyContent: 'space-between' } }, [
                      el('div', { style: { fontWeight: 700 } }, p),
                      el('button', { class: 'btn btnDanger', onclick: () => onKick(p) }, 'Kick'),
                    ]),
                  ]),
                ),
          ),
        ]),
      )
    }
  }

  function startConsoleStream() {
    if (!token) return
    if (consoleEs) consoleEs.close()
    const base = getBackendUrl()
    const url = `${base}${ENDPOINTS.serverConsole(serverId)}?stream=true&token=${encodeURIComponent(token)}`
    const es = new EventSource(url)
    es.addEventListener('init', (e) => {
      const payload = safeParse(e.data)
      const lines = payload?.lines?.map((l) => l.text) ?? []
      state.consoleLines = lines
      if (state.tab === 'console') renderBody()
      scrollConsole()
    })
    es.addEventListener('line', (e) => {
      const payload = safeParse(e.data)
      if (!payload) return
      state.consoleLines.push(payload.text)
      if (state.consoleLines.length > 800) state.consoleLines = state.consoleLines.slice(state.consoleLines.length - 800)
      if (state.tab === 'console') {
        consoleEl.textContent = state.consoleLines.join('\n')
        scrollConsole()
      }
    })
    es.onerror = () => {
      state.consoleError = 'Console stream disconnected.'
      if (state.tab === 'console') renderBody()
      es.close()
    }
    consoleEs = es
  }

  function startPlayersStream() {
    if (!token) return
    if (playersEs) playersEs.close()
    const base = getBackendUrl()
    const url = `${base}${ENDPOINTS.serverPlayers(serverId)}?stream=true&token=${encodeURIComponent(token)}`
    const es = new EventSource(url)
    es.addEventListener('init', (e) => {
      const payload = safeParse(e.data)
      state.onlinePlayers = payload?.onlinePlayers ?? 0
      state.players = payload?.players ?? []
      if (state.tab === 'players') renderBody()
    })
    es.addEventListener('players', (e) => {
      const payload = safeParse(e.data)
      if (!payload) return
      state.onlinePlayers = payload.onlinePlayers
      state.players = payload.players
      if (state.tab === 'players') renderBody()
    })
    es.onerror = () => {
      state.playersError = 'Player stream disconnected.'
      if (state.tab === 'players') renderBody()
      es.close()
    }
    playersEs = es
  }

  function scrollConsole() {
    consoleEl.scrollTop = consoleEl.scrollHeight
  }

  async function loadDetails() {
    state.loading = true
    setError(null)
    renderBody()
    try {
      const data = await requestJson('GET', ENDPOINTS.serverDetails(serverId))
      state.details = data
      setHeader()
      renderTabs()
      startConsoleStream()
      startPlayersStream()
    } catch {
      setError('Failed to load server details.')
    } finally {
      state.loading = false
      renderBody()
    }
  }

  const header = el('div', { class: 'topBar' }, [
    el('div', null, [headerTitle, headerSub]),
    el('div', { class: 'row' }, [
      el('button', { class: 'btn', onclick: () => history.back() }, 'Back'),
      el('button', { class: 'btn', onclick: () => loadDetails() }, 'Refresh'),
    ]),
  ])

  const root = el('div', { class: 'appShell' }, [header, renderTabs(), bodyNode])
  setView(root, () => {
    if (consoleEs) consoleEs.close()
    if (playersEs) playersEs.close()
  })

  void loadDetails()
}

function notFoundPage() {
  const root = el('div', { class: 'appShell' }, [
    el('div', { class: 'panel' }, [
      el('div', { class: 'title' }, 'Not Found'),
      el('div', { class: 'subTitle', style: { marginTop: '6px' } }, 'The page does not exist.'),
      el(
        'div',
        { style: { marginTop: '12px' } },
        el('a', { class: 'btn btnPrimary', href: '/dashboard', 'data-link': true, style: { textDecoration: 'none', display: 'inline-block' } }, 'Back to Dashboard'),
      ),
    ]),
  ])
  setView(root, null)
}

function currentPath() {
  return window.location.pathname || '/'
}

function navigate(path) {
  history.pushState({}, '', path)
  renderRoute()
}

let activeCleanup = null

function renderRoute() {
  const path = currentPath()
  if (path === '/' || path === '') return navigate('/dashboard')
  if (path === '/login') return loginPage()
  if (path === '/dashboard') return mainDashboardPage()

  const pluginMatch = path.match(/^\/plugins\/([^/]+)$/)
  if (pluginMatch) return pluginDetailPage(pluginMatch[1])

  const serverMatch = path.match(/^\/servers\/([^/]+)$/)
  if (serverMatch) return serverPanelPage(serverMatch[1])

  return notFoundPage()
}

window.addEventListener('popstate', () => renderRoute())

document.addEventListener('click', (e) => {
  const a = e.target.closest('a[data-link]')
  if (!a) return
  const href = a.getAttribute('href')
  if (!href) return
  e.preventDefault()
  navigate(href)
})

resetMembersOnce()
renderRoute()
