import { nanoid } from 'nanoid';
import { EventEmitter } from 'node:events';
export class StreamHub {
    subscriptions = new Map();
    publish(serverId, type, data) {
        const serverMap = this.subscriptions.get(serverId);
        const subs = serverMap?.get(type);
        if (!subs || subs.size === 0)
            return;
        for (const emitter of subs) {
            emitter.emit('event', data);
        }
    }
    subscribe(serverId, type) {
        const emitter = new EventEmitter();
        let serverMap = this.subscriptions.get(serverId);
        if (!serverMap) {
            serverMap = new Map();
            this.subscriptions.set(serverId, serverMap);
        }
        let subs = serverMap.get(type);
        if (!subs) {
            subs = new Set();
            serverMap.set(type, subs);
        }
        subs.add(emitter);
        const unsubscribe = () => {
            subs?.delete(emitter);
            if (subs && subs.size === 0)
                serverMap?.delete(type);
            if (serverMap && serverMap.size === 0)
                this.subscriptions.delete(serverId);
        };
        return { emitter, unsubscribe };
    }
}
export class AppState {
    options;
    pluginsById = new Map();
    pluginIdByName = new Map();
    serversById = new Map();
    serverIdByPluginAndAddr = new Map();
    sessionsByToken = new Map();
    streamHub = new StreamHub();
    audit = [];
    constructor(options) {
        this.options = options;
    }
    addAudit(input) {
        const entry = {
            id: nanoid(),
            ts: Date.now(),
            actor: String(input.actor || 'unknown'),
            action: String(input.action || ''),
            meta: input.meta ?? null,
        };
        this.audit.push(entry);
        if (this.audit.length > 2000)
            this.audit.splice(0, this.audit.length - 2000);
    }
    listAudit(limit) {
        const n = Number.isFinite(limit) ? Math.max(1, Math.min(2000, Math.floor(limit))) : 200;
        return this.audit.slice(Math.max(0, this.audit.length - n));
    }
    upsertPlugin(input) {
        const now = Date.now();
        const normalizedName = input.name.trim();
        const existingId = this.pluginIdByName.get(normalizedName.toLowerCase());
        if (existingId) {
            const existing = this.pluginsById.get(existingId);
            if (!existing) {
                this.pluginIdByName.delete(normalizedName.toLowerCase());
            }
            else {
                const updated = {
                    ...existing,
                    name: normalizedName,
                    description: input.description?.trim() || existing.description,
                    version: input.version?.trim() || existing.version,
                    updatedAt: now,
                };
                this.pluginsById.set(existingId, updated);
                return updated;
            }
        }
        const plugin = {
            id: nanoid(),
            name: normalizedName,
            description: input.description?.trim() || 'No description',
            version: input.version?.trim() || 'unknown',
            createdAt: now,
            updatedAt: now,
        };
        this.pluginsById.set(plugin.id, plugin);
        this.pluginIdByName.set(normalizedName.toLowerCase(), plugin.id);
        return plugin;
    }
    upsertServer(input) {
        const key = `${input.pluginId}::${input.ip}::${input.port}`;
        const now = Date.now();
        const existingId = this.serverIdByPluginAndAddr.get(key);
        if (existingId) {
            const server = this.serversById.get(existingId);
            if (server) {
                const updated = {
                    ...server,
                    ip: input.ip,
                    port: input.port,
                    serverStatus: 'online',
                    lastHeartbeatAt: now,
                };
                this.serversById.set(existingId, updated);
                return updated;
            }
            this.serverIdByPluginAndAddr.delete(key);
        }
        const server = {
            id: nanoid(),
            pluginId: input.pluginId,
            ip: input.ip,
            port: input.port,
            pluginStatus: 'active',
            serverStatus: 'online',
            onlinePlayers: 0,
            players: [],
            console: [],
            lastHeartbeatAt: now,
            pendingCommands: [],
        };
        this.serversById.set(server.id, server);
        this.serverIdByPluginAndAddr.set(key, server.id);
        return server;
    }
    appendConsole(serverId, lines) {
        const server = this.serversById.get(serverId);
        if (!server)
            return;
        const now = Date.now();
        const next = [...server.console];
        for (const text of lines) {
            next.push({ ts: now, text });
            this.streamHub.publish(serverId, 'console', { ts: now, text });
        }
        const trimmed = next.length > this.options.consoleMaxLines ? next.slice(next.length - this.options.consoleMaxLines) : next;
        this.serversById.set(serverId, { ...server, console: trimmed });
    }
    setPlayers(serverId, onlinePlayers, players) {
        const server = this.serversById.get(serverId);
        if (!server)
            return;
        const unique = Array.from(new Set(players.map((p) => p.trim()).filter(Boolean)));
        this.serversById.set(serverId, { ...server, onlinePlayers, players: unique });
        this.streamHub.publish(serverId, 'players', { onlinePlayers, players: unique });
    }
    setPluginStatus(serverId, pluginStatus) {
        const server = this.serversById.get(serverId);
        if (!server)
            return;
        this.serversById.set(serverId, { ...server, pluginStatus });
    }
    markOfflineServers() {
        const now = Date.now();
        for (const [id, server] of this.serversById) {
            const shouldBeOffline = now - server.lastHeartbeatAt > this.options.serverOfflineAfterMs;
            if (!shouldBeOffline)
                continue;
            if (server.serverStatus === 'offline')
                continue;
            this.serversById.set(id, { ...server, serverStatus: 'offline' });
            this.appendConsole(id, ['[backend] Server marked offline (missed heartbeat)']);
            this.addAudit({
                actor: 'backend',
                action: 'server_marked_offline',
                meta: { serverId: id, pluginId: server.pluginId, ip: server.ip, port: server.port },
            });
        }
    }
    enqueueCommand(serverId, command) {
        const server = this.serversById.get(serverId);
        if (!server)
            return null;
        const item = { id: nanoid(), command, ts: Date.now() };
        this.serversById.set(serverId, { ...server, pendingCommands: [...server.pendingCommands, item] });
        return item;
    }
    acknowledgeCommands(serverId, ids) {
        const server = this.serversById.get(serverId);
        if (!server)
            return;
        const remove = new Set(ids);
        const remaining = server.pendingCommands.filter((c) => !remove.has(c.id));
        if (remaining.length === server.pendingCommands.length)
            return;
        this.serversById.set(serverId, { ...server, pendingCommands: remaining });
    }
    clearPendingCommands(serverId) {
        const server = this.serversById.get(serverId);
        if (!server)
            return;
        if (server.pendingCommands.length === 0)
            return;
        this.serversById.set(serverId, { ...server, pendingCommands: [] });
    }
}
