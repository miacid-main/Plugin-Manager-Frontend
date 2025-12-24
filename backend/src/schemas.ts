import { z } from 'zod'

export const authLoginSchema = z.object({
  identifier: z.string().min(1),
  password: z.string().min(1),
})

const pluginBaseSchema = z.object({
  pluginName: z.string().min(1),
  pluginDescription: z.string().optional(),
  pluginVersion: z.string().optional(),
  serverIp: z.string().min(1),
  serverPort: z.number().int().min(1).max(65535),
})

export const pluginRegisterSchema = pluginBaseSchema.extend({
  enabled: z.boolean().optional(),
})

export const pluginHeartbeatSchema = pluginBaseSchema.extend({
  enabled: z.boolean().optional(),
  onlinePlayers: z.number().int().min(0).optional(),
  ackCommandIds: z.array(z.string().min(1)).optional(),
})

export const pluginPlayersSchema = pluginBaseSchema.extend({
  onlinePlayers: z.number().int().min(0),
  players: z.array(z.string()),
})

export const pluginConsoleSchema = pluginBaseSchema.extend({
  line: z.string().optional(),
  lines: z.array(z.string()).optional(),
})

export const pluginStatusSchema = pluginBaseSchema.extend({
  enabled: z.boolean(),
})

export const pluginEventSchema = pluginBaseSchema.extend({
  event: z.string().min(1),
  details: z.unknown().optional(),
})

export const serverCommandSchema = z.object({
  command: z.string().min(1),
})
