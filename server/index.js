// Tripcart writing server: runs the LaunchDarkly AgentControl config for the app's "Improve writing" card.
// The LaunchDarkly SDK key is server-side only, so it lives here (server/.env), never in the APK.
import "dotenv/config";
import { createServer } from "node:http";
import { config, shutdown } from "@launchdarkly/ai-node";
import { createClaudeAgentsHandler } from "@launchdarkly/ai-claude-agents";

const PORT = Number(process.env.PORT ?? 8787);
const CONFIG_KEY = process.env.LAUNCHDARKLY_AI_CONFIG_KEY ?? "sherif-writing-improver-agent";

// Evaluated on every request (never cached) so config changes in LaunchDarkly apply immediately.
async function improve(text, userId) {
  // Without a key the Claude Agent SDK answers with a login prompt instead of failing, so check up front.
  if (!process.env.ANTHROPIC_API_KEY) {
    console.warn("ANTHROPIC_API_KEY is not set in server/.env");
    return { improved: text, source: "fallback", error: "Writing improver is not configured." };
  }
  try {
    const result = await config({ key: CONFIG_KEY, handler: [createClaudeAgentsHandler()] })
      .invoke(text, { kind: "user", key: userId }, {});
    return { improved: result.response, source: "launchdarkly" };
  } catch (err) {
    // Disabled config, unknown key, LaunchDarkly unreachable, or provider error: hand the draft back unchanged.
    console.warn(`[${CONFIG_KEY}] invoke failed:`, err?.message ?? err);
    return { improved: text, source: "fallback", error: "Writing improver is temporarily unavailable." };
  }
}

const send = (res, code, body) => {
  res.writeHead(code, { "Content-Type": "application/json" });
  res.end(JSON.stringify(body));
};

const server = createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") return send(res, 200, { ok: true });
  if (req.method !== "POST" || req.url !== "/improve") return send(res, 404, { error: "not found" });
  let raw = "";
  for await (const chunk of req) raw += chunk;
  let body;
  try {
    body = JSON.parse(raw);
  } catch {
    return send(res, 400, { error: "invalid JSON" });
  }
  const text = typeof body.text === "string" ? body.text.trim() : "";
  if (!text) return send(res, 400, { error: "text is required" });
  send(res, 200, await improve(text, String(body.userId || "anonymous")));
});

server.listen(PORT, () => console.log(`Writing server on http://localhost:${PORT} (config: ${CONFIG_KEY})`));

for (const sig of ["SIGINT", "SIGTERM"]) {
  process.on(sig, async () => {
    server.close();
    await shutdown(); // flush LaunchDarkly events and traces
    process.exit(0);
  });
}
