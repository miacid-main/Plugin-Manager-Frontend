export function buildDocs(baseUrlExample = 'http://localhost:3001') {
  return {
    title: 'Plugin Manager Shared Backend API',
    baseUrlExample,
    auth: {
      note: 'Dashboard endpoints require auth. Plugin ingestion endpoints do not.',
    },
    pluginToBackend: {
      note: 'All plugin ingestion endpoints share the same addressing fields.',
      requiredFields: ['pluginName', 'serverIp', 'serverPort'],
      endpoints: {
        'GET /health': {
          response: { ok: true },
        },
        'POST /plugin/register': {
          requestBody: {
            pluginName: 'ExamplePlugin',
            pluginDescription: 'Short plugin description',
            pluginVersion: '1.0.0',
            serverIp: '127.0.0.1',
            serverPort: 25565,
            enabled: true,
          },
          response: { pluginId: 'plg_...', serverId: 'srv_...', ok: true },
        },
        'POST /plugin/heartbeat': {
          requestBody: {
            pluginName: 'ExamplePlugin',
            serverIp: '127.0.0.1',
            serverPort: 25565,
            enabled: true,
            onlinePlayers: 3,
            ackCommandIds: ['cmd_...'],
          },
          response: {
            ok: true,
            pluginId: 'plg_...',
            serverId: 'srv_...',
            pendingCommands: [{ id: 'cmd_...', command: 'say hello', ts: 0 }],
          },
        },
        'POST /plugin/players': {
          requestBody: {
            pluginName: 'ExamplePlugin',
            serverIp: '127.0.0.1',
            serverPort: 25565,
            onlinePlayers: 3,
            players: ['Steve', 'Alex', 'Notch'],
          },
          response: { ok: true },
        },
        'POST /plugin/console': {
          requestBody: {
            pluginName: 'ExamplePlugin',
            serverIp: '127.0.0.1',
            serverPort: 25565,
            lines: ['[Server] Started', '[Plugin] Enabled'],
          },
          response: { ok: true },
        },
        'POST /plugin/status': {
          requestBody: {
            pluginName: 'ExamplePlugin',
            serverIp: '127.0.0.1',
            serverPort: 25565,
            enabled: true,
          },
          response: { ok: true },
        },
        'POST /plugin/event': {
          requestBody: {
            pluginName: 'ExamplePlugin',
            serverIp: '127.0.0.1',
            serverPort: 25565,
            event: 'command_executed',
            details: { commandId: 'cmd_...', command: 'say hello' },
          },
          response: { ok: true },
        },
      },
    },
    backendToFrontend: {
      authHeader: 'Authorization: Bearer <token>',
      actorHeader: 'X-PM-Actor: <username> (optional audit attribution)',
      sseNote:
        'SSE endpoints accept token via ?token=... (EventSource cannot set Authorization headers).',
      endpoints: {
        'POST /auth/login': {
          requestBody: { identifier: 'Miacid or miacidsenpai@gmail.com', password: 'takanashi_20' },
          response: { ok: true, token: 'token_...', user: { username: 'Miacid', email: 'miacidsenpai@gmail.com' } },
        },
        'GET /logs': {
          query: { limit: 200 },
          response: [{ id: 'log_...', ts: 0, actor: 'Miacid', action: 'server_command_queued', meta: {} }],
        },
        'GET /plugins': {
          response: [
            {
              id: 'plg_...',
              name: 'ExamplePlugin',
              description: 'Short plugin description',
              status: 'active',
              serverCount: 2,
            },
          ],
        },
        'GET /plugins/{pluginId}/servers': {
          response: [
            {
              id: 'srv_...',
              ip: '127.0.0.1',
              port: 25565,
              onlinePlayers: 3,
              pluginStatus: 'active',
              serverStatus: 'online',
            },
          ],
        },
        'POST /plugins/{pluginId}/servers/{serverId}/disable': {
          response: { ok: true },
        },
        'POST /plugins/{pluginId}/servers/{serverId}/enable': {
          response: { ok: true },
        },
        'GET /servers/{serverId}/details': {
          response: {
            id: 'srv_...',
            ip: '127.0.0.1',
            port: 25565,
            plugin: { id: 'plg_...', name: 'ExamplePlugin', version: '1.0.0' },
            pluginStatus: 'active',
            serverStatus: 'online',
          },
        },
        'GET /servers/{serverId}/console': {
          response: { lines: [{ ts: 0, text: '[Server] Started' }] },
          sse: `${baseUrlExample}/servers/srv_.../console?stream=true&token=token_...`,
        },
        'POST /servers/{serverId}/command': {
          requestBody: { command: 'say hello' },
          response: { ok: true, queued: true, commandId: 'cmd_...' },
        },
        'GET /servers/{serverId}/players': {
          response: { onlinePlayers: 0, players: [] },
          sse: `${baseUrlExample}/servers/srv_.../players?stream=true&token=token_...`,
        },
      },
    },
  }
}
