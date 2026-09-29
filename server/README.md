# Tripcart writing server

Runs the LaunchDarkly AgentControl config `sherif-writing-improver-agent` (Anthropic, agent mode) for the
**Improve writing** card on the app's Chaos screen. The LaunchDarkly SDK key is a server-side secret, so it
lives here and never in the APK.

```bash
cp .env.example .env   # fill in LD_SDK_KEY and ANTHROPIC_API_KEY
npm install
npm start              # http://localhost:8787
```

- `POST /improve` `{"text": "...", "userId": "..."}` → `{"improved": "...", "source": "launchdarkly" | "fallback"}`
- `GET /health`

The config is evaluated on every request, so prompt/model changes in LaunchDarkly apply immediately. If
LaunchDarkly or Anthropic is unavailable, the draft comes back unchanged with `source: "fallback"`.

The Android emulator reaches this server at `http://10.0.2.2:8787` (the default). For a physical device, set
`writing.serverUrl=http://<your-mac-ip>:8787` in `local.properties` and allow that host in
`app/src/main/res/xml/network_security_config.xml`.
