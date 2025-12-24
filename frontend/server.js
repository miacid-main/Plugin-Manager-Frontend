const express = require('express')
const path = require('path')

const app = express()

const port = Number(process.env.PORT || 5173)
const publicDir = path.join(__dirname, 'public')
const backendUrl = String(process.env.BACKEND_URL || 'http://localhost:3001')

app.get('/health', (_req, res) => res.json({ ok: true }))

app.get('/config.js', (_req, res) => {
  res.setHeader('Content-Type', 'application/javascript; charset=utf-8')
  res.send(`window.__BACKEND_URL__ = ${JSON.stringify(backendUrl)};`)
})

app.use(express.static(publicDir, { index: false }))

app.get(/.*/, (_req, res) => {
  res.sendFile(path.join(publicDir, 'index.html'))
})

app.listen(port, () => {
  console.log(`frontend listening on http://localhost:${port}`)
})
