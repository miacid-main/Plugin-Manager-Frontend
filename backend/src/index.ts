import cors from 'cors'
import express from 'express'
import crypto from 'node:crypto'
import { z } from 'zod'
import { buildDocs } from './docs.js'
import { AppState } from './state.js'
import {
  authLoginSchema,
  pluginConsoleSchema,
  pluginEventSchema,
  pluginHeartbeatSchema,
  pluginPlayersSchema,
  pluginRegisterSchema,
  pluginStatusSchema,
  serverCommandSchema,
} from './schemas.js'

const app = express()
app.use(cors({ origin: true, credentials: true }))
app.use(express.json({ limit: '2mb' }))

const port = Number(process.env.PORT || 3001)
const sessionTtlMs = Number(process.env.SESSION_TTL_MS || 24 * 60 * 60 * 1000)

const state = new AppState({
  consoleMaxLines: Number(process.env.CONSOLE_MAX_LINES || 500),
  serverOfflineAfterMs: Number(process.env.SERVER_OFFLINE_AFTER_MS || 15_000),
})

setInterval(() => state.markOfflineServers(), 2_000).unref()

const USERNAME = 'Miacid'
const EMAIL = 'miacidsenpai@gmail.com'
const PASSWORD = 'takanashi_20'

function pluginNameKey(name: string) {
  return name.trim().toLowerCase()
}

function requireRegisteredPlugin(pluginName: string, res: express.Response) {
  const key = pluginNameKey(pluginName)
  if (!state.pluginIdByName.has(key)) {
    res.status(409).json({ ok: false, error: 'plugin_not_registered' })
    return false
  }
  return true
}

function createSession() {
  const token = `token_${crypto.randomUUID()}`
  state.sessionsByToken.set(token, {
    username: USERNAME,
    email: EMAIL,
    exp: Date.now() + sessionTtlMs,
  })
  return token
}

function getTokenFromReq(req: express.Request) {
  const auth = req.header('authorization')
  if (auth && auth.toLowerCase().startsWith('bearer ')) return auth.slice('bearer '.length).trim()
  const tokenQuery = req.query.token
  if (typeof tokenQuery === 'string' && tokenQuery.trim()) return tokenQuery.trim()
  return null
}

function requireAuth(req: express.Request, res: express.Response, next: express.NextFunction) {
  const token = getTokenFromReq(req)
  if (!token) return res.status(401).json({ ok: false, error: 'unauthorized' })
  const session = state.sessionsByToken.get(token)
  if (!session) return res.status(401).json({ ok: false, error: 'unauthorized' })
  if (Date.now() > session.exp) {
    state.sessionsByToken.delete(token)
    return res.status(401).json({ ok: false, error: 'session_expired' })
  }
  ;(req as any).user = session
  ;(req as any).token = token
  next()
}

function parseBody<T extends z.ZodTypeAny>(schema: T, req: express.Request, res: express.Response) {
  const parsed = schema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ ok: false, error: 'invalid_body', details: parsed.error.flatten() })
    return null
  }
  return parsed.data as z.infer<T>
}

function getActor(req: express.Request) {
  const hinted = req.header('x-pm-actor')
  if (typeof hinted === 'string' && hinted.trim()) return hinted.trim()
  const user = (req as any).user as { username?: string } | undefined
  return typeof user?.username === 'string' && user.username.trim() ? user.username.trim() : 'unknown'
}

app.get('/health', (_req, res) => {
  res.json({ ok: true })
})

app.get('/docs', (req, res) => {
  const base = typeof req.query.baseUrlExample === 'string' ? req.query.baseUrlExample : undefined
  res.json(buildDocs(base))
})

app.post('/auth/login', (req, res) => {
  const body = parseBody(authLoginSchema, req, res)
  if (!body) return

  const identifier = body.identifier.trim()
  const password = body.password
  const isUsernameMatch = identifier.toLowerCase() === USERNAME.toLowerCase()
  const isEmailMatch = identifier.toLowerCase() === EMAIL.toLowerCase()
  const ok = (isUsernameMatch || isEmailMatch) && password === PASSWORD

  if (!ok) {
    res.status(401).json({ ok: false, error: 'invalid_credentials' })
    return
  }

  const token = createSession()
  state.addAudit({ actor: USERNAME, action: 'auth_login_ok', meta: { identifier } })
  res.json({ ok: true, token, user: { username: USERNAME, email: EMAIL } })
})

app.get('/logs', requireAuth, (req, res) => {
  const limit = typeof req.query.limit === 'string' ? Number(req.query.limit) : 200
  const logs = state.listAudit(limit).slice().reverse()
  res.json(logs)
})

app.post('/plugin/register', (req, res) => {
  const body = parseBody(pluginRegisterSchema, req, res)
  if (!body) return

  const plugin = state.upsertPlugin({
    name: body.pluginName,
    description: body.pluginDescription,
    version: body.pluginVersion,
  })
  const server = state.upsertServer({ pluginId: plugin.id, ip: body.serverIp, port: body.serverPort })

  // Clear any pending commands from previous sessions on fresh register
  state.clearPendingCommands(server.id)

  if (typeof body.enabled === 'boolean') {
    const current = state.serversById.get(server.id)
    if (current?.pluginStatus !== 'disabled') state.setPluginStatus(server.id, body.enabled ? 'active' : 'disabled')
  }
  state.appendConsole(server.id, [`[backend] Plugin registered: ${plugin.name} v${plugin.version}`])
  state.addAudit({
    actor: `plugin:${body.pluginName}`,
    action: 'plugin_registered',
    meta: { pluginId: plugin.id, serverId: server.id, ip: body.serverIp, port: body.serverPort, enabled: body.enabled ?? null },
  })

  res.json({ ok: true, pluginId: plugin.id, serverId: server.id })
})

app.post('/plugin/heartbeat', (req, res) => {
  const body = parseBody(pluginHeartbeatSchema, req, res)
  if (!body) return

  if (!requireRegisteredPlugin(body.pluginName, res)) return

  const plugin = state.upsertPlugin({
    name: body.pluginName,
    description: body.pluginDescription,
    version: body.pluginVersion,
  })
  const server = state.upsertServer({ pluginId: plugin.id, ip: body.serverIp, port: body.serverPort })

  if (typeof body.enabled === 'boolean') {
    const current = state.serversById.get(server.id)
    if (current?.pluginStatus !== 'disabled') state.setPluginStatus(server.id, body.enabled ? 'active' : 'disabled')
  }
  if (typeof body.onlinePlayers === 'number') state.setPlayers(server.id, body.onlinePlayers, server.players)
  if (Array.isArray(body.ackCommandIds) && body.ackCommandIds.length > 0) state.acknowledgeCommands(server.id, body.ackCommandIds)
  // Heartbeat is frequent, so we skip auditing it to reduce noise
  
  const current = state.serversById.get(server.id)
  res.json({
    ok: true,
    pluginId: plugin.id,
    serverId: server.id,
    pendingCommands: current?.pendingCommands ?? [],
  })
})

app.post('/plugin/players', (req, res) => {
  const body = parseBody(pluginPlayersSchema, req, res)
  if (!body) return

  if (!requireRegisteredPlugin(body.pluginName, res)) return

  const plugin = state.upsertPlugin({
    name: body.pluginName,
    description: body.pluginDescription,
    version: body.pluginVersion,
  })
  const server = state.upsertServer({ pluginId: plugin.id, ip: body.serverIp, port: body.serverPort })
  state.setPlayers(server.id, body.onlinePlayers, body.players)
  // Skip auditing players_updated to reduce noise
  res.json({ ok: true })
})

app.post('/plugin/console', (req, res) => {
  const body = parseBody(pluginConsoleSchema, req, res)
  if (!body) return

  if (!requireRegisteredPlugin(body.pluginName, res)) return

  const plugin = state.upsertPlugin({
    name: body.pluginName,
    description: body.pluginDescription,
    version: body.pluginVersion,
  })
  const server = state.upsertServer({ pluginId: plugin.id, ip: body.serverIp, port: body.serverPort })

  const lines = Array.isArray(body.lines) ? body.lines : body.line ? [body.line] : []
  if (lines.length > 0) state.appendConsole(server.id, lines)
  // Skip auditing console_ingested to reduce noise
  res.json({ ok: true })
})

app.post('/plugin/status', (req, res) => {
  const body = parseBody(pluginStatusSchema, req, res)
  if (!body) return

  if (!requireRegisteredPlugin(body.pluginName, res)) return

  const plugin = state.upsertPlugin({
    name: body.pluginName,
    description: body.pluginDescription,
    version: body.pluginVersion,
  })
  const server = state.upsertServer({ pluginId: plugin.id, ip: body.serverIp, port: body.serverPort })
  const current = state.serversById.get(server.id)
  if (current?.pluginStatus !== 'disabled') state.setPluginStatus(server.id, body.enabled ? 'active' : 'disabled')
  // Skip auditing plugin_status to reduce noise
  res.json({ ok: true })
})

app.post('/plugin/event', (req, res) => {
  const body = parseBody(pluginEventSchema, req, res)
  if (!body) return
  if (!requireRegisteredPlugin(body.pluginName, res)) return

  const plugin = state.upsertPlugin({
    name: body.pluginName,
    description: body.pluginDescription,
    version: body.pluginVersion,
  })
  const server = state.upsertServer({ pluginId: plugin.id, ip: body.serverIp, port: body.serverPort })
  state.addAudit({
    actor: `plugin:${body.pluginName}`,
    action: `plugin_event:${body.event}`,
    meta: { pluginId: plugin.id, serverId: server.id, ip: body.serverIp, port: body.serverPort, details: body.details ?? null },
  })
  res.json({ ok: true })
})

app.get('/plugins', requireAuth, (_req, res) => {
  const servers = Array.from(state.serversById.values())
  const plugins = Array.from(state.pluginsById.values()).map((p) => {
    const pluginServers = servers.filter((s) => s.pluginId === p.id)
    const serverCount = pluginServers.length
    const anyActive = pluginServers.some((s) => s.pluginStatus === 'active')
    const allOffline = serverCount > 0 && pluginServers.every((s) => s.serverStatus === 'offline')
    return {
      id: p.id,
      name: p.name,
      description: p.description,
      version: p.version,
      status: serverCount === 0 ? 'disabled' : allOffline ? 'offline' : anyActive ? 'active' : 'disabled',
      serverCount,
    }
  })
  res.json(plugins)
})

app.get('/plugins/:pluginId/servers', requireAuth, (req, res) => {
  const pluginId = req.params.pluginId
  if (!state.pluginsById.has(pluginId)) {
    res.status(404).json({ ok: false, error: 'plugin_not_found' })
    return
  }
  const servers = Array.from(state.serversById.values())
    .filter((s) => s.pluginId === pluginId)
    .map((s) => ({
      id: s.id,
      ip: s.ip,
      port: s.port,
      onlinePlayers: s.onlinePlayers,
      pluginStatus: s.pluginStatus,
      serverStatus: s.serverStatus,
    }))
  res.json(servers)
})

app.post('/plugins/:pluginId/servers/:serverId/disable', requireAuth, (req, res) => {
  const { pluginId, serverId } = req.params
  const server = state.serversById.get(serverId)
  if (!server || server.pluginId !== pluginId) {
    res.status(404).json({ ok: false, error: 'server_not_found' })
    return
  }
  state.setPluginStatus(serverId, 'disabled')
  state.appendConsole(serverId, ['[backend] Disable requested from dashboard'])
  state.addAudit({ actor: getActor(req), action: 'plugin_disabled', meta: { pluginId, serverId } })
  res.json({ ok: true })
})

app.post('/plugins/:pluginId/servers/:serverId/enable', requireAuth, (req, res) => {
  const { pluginId, serverId } = req.params
  const server = state.serversById.get(serverId)
  if (!server || server.pluginId !== pluginId) {
    res.status(404).json({ ok: false, error: 'server_not_found' })
    return
  }
  state.setPluginStatus(serverId, 'active')
  state.appendConsole(serverId, ['[backend] Enable requested from dashboard'])
  state.addAudit({ actor: getActor(req), action: 'plugin_enabled', meta: { pluginId, serverId } })
  res.json({ ok: true })
})

app.get('/servers/:serverId/details', requireAuth, (req, res) => {
  const server = state.serversById.get(req.params.serverId)
  if (!server) {
    res.status(404).json({ ok: false, error: 'server_not_found' })
    return
  }
  const plugin = state.pluginsById.get(server.pluginId)
  res.json({
    id: server.id,
    ip: server.ip,
    port: server.port,
    plugin: plugin ? { id: plugin.id, name: plugin.name, version: plugin.version } : null,
    pluginStatus: server.pluginStatus,
    serverStatus: server.serverStatus,
  })
})

function writeSse(res: express.Response, event: string, data: unknown) {
  res.write(`event: ${event}\n`)
  res.write(`data: ${JSON.stringify(data)}\n\n`)
}

app.get('/servers/:serverId/console', requireAuth, (req, res) => {
  const serverId = req.params.serverId
  const server = state.serversById.get(serverId)
  if (!server) {
    res.status(404).json({ ok: false, error: 'server_not_found' })
    return
  }

  const stream = req.query.stream === 'true'
  if (!stream) {
    res.json({ lines: server.console })
    return
  }

  res.status(200)
  res.setHeader('Content-Type', 'text/event-stream')
  res.setHeader('Cache-Control', 'no-cache')
  res.setHeader('Connection', 'keep-alive')
  res.flushHeaders()

  writeSse(res, 'init', { lines: server.console })

  const sub = state.streamHub.subscribe(serverId, 'console')
  const onEvent = (payload: any) => writeSse(res, 'line', payload)
  sub.emitter.on('event', onEvent)

  const keepAlive = setInterval(() => res.write(':keep-alive\n\n'), 15_000)

  req.on('close', () => {
    clearInterval(keepAlive)
    sub.emitter.off('event', onEvent)
    sub.unsubscribe()
  })
})

app.get('/servers/:serverId/players', requireAuth, (req, res) => {
  const serverId = req.params.serverId
  const server = state.serversById.get(serverId)
  if (!server) {
    res.status(404).json({ ok: false, error: 'server_not_found' })
    return
  }

  const stream = req.query.stream === 'true'
  if (!stream) {
    res.json({ onlinePlayers: server.onlinePlayers, players: server.players })
    return
  }

  res.status(200)
  res.setHeader('Content-Type', 'text/event-stream')
  res.setHeader('Cache-Control', 'no-cache')
  res.setHeader('Connection', 'keep-alive')
  res.flushHeaders()

  writeSse(res, 'init', { onlinePlayers: server.onlinePlayers, players: server.players })

  const sub = state.streamHub.subscribe(serverId, 'players')
  const onEvent = (payload: any) => writeSse(res, 'players', payload)
  sub.emitter.on('event', onEvent)

  const keepAlive = setInterval(() => res.write(':keep-alive\n\n'), 15_000)

  req.on('close', () => {
    clearInterval(keepAlive)
    sub.emitter.off('event', onEvent)
    sub.unsubscribe()
  })
})

app.post('/servers/:serverId/command', requireAuth, (req, res) => {
  const serverId = req.params.serverId
  const server = state.serversById.get(serverId)
  if (!server) {
    res.status(404).json({ ok: false, error: 'server_not_found' })
    return
  }
  const body = parseBody(serverCommandSchema, req, res)
  if (!body) return

  const queued = state.enqueueCommand(serverId, body.command)
  if (!queued) {
    res.status(404).json({ ok: false, error: 'server_not_found' })
    return
  }
  state.appendConsole(serverId, [`[dashboard] ${body.command}`])
  state.addAudit({
    actor: getActor(req),
    action: 'server_command_queued',
    meta: { serverId, commandId: queued.id, command: body.command },
  })
  res.json({ ok: true, queued: true, commandId: queued.id })
})

app.listen(port, () => {
  console.log(`backend listening on http://localhost:${port}`)
})
