const origin = process.argv[2];
if (!origin || !/^https?:\/\//.test(origin)) throw new Error("Usage: node scripts/check-health.mjs https://your-trial-host");
for (const path of ["/login", "/backend-api/health/ready"]) {
  const response = await fetch(new URL(path, origin), { signal: AbortSignal.timeout(10000), redirect: "error" });
  if (!response.ok) throw new Error(`Health check failed: ${path} (${response.status})`);
  if (path.endsWith("/ready") && (await response.json()).status !== "ok") throw new Error("Database readiness check failed");
}
console.log("Website and database readiness checks passed.");
