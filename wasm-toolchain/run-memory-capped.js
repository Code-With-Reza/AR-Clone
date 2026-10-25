#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const { execFileSync, spawn } = require("node:child_process");
const { quoteWindowsShellArguments } = require("./windows-shell-args.js");

const DEFAULT_LIMIT_MIB = 13824;
const POLL_INTERVAL_MS = 100;
const KILL_GRACE_MS = 2000;

const argv = process.argv.slice(2);
let limitMiB = DEFAULT_LIMIT_MIB;
let logFile = null;
let memoryMetric = "rss";
if (argv[0] === "--limit-mib") {
  limitMiB = Number(argv[1]);
  argv.splice(0, 2);
}
if (argv[0] === "--log-file") {
  logFile = argv[1];
  argv.splice(0, 2);
}
if (argv[0] === "--metric") {
  memoryMetric = argv[1];
  argv.splice(0, 2);
}

if (!Number.isFinite(limitMiB) || limitMiB <= 0 || argv.length === 0 ||
    (memoryMetric !== "rss" && memoryMetric !== "pss")) {
  console.error(
    "usage: run-memory-capped.js [--limit-mib N] [--log-file PATH] " +
    "[--metric rss|pss] <command> [args...]"
  );
  process.exit(2);
}

function readLinuxProcess(pid, includePss) {
  try {
    const stat = fs.readFileSync(`/proc/${pid}/stat`, "utf8");
    const closeParen = stat.lastIndexOf(")");
    const fields = stat.slice(closeParen + 2).split(" ");
    const ppid = Number(fields[1]);
    const rssPages = Number(fields[21]);
    let pssBytes = 0;
    if (includePss) {
      try {
        const rollup = fs.readFileSync(`/proc/${pid}/smaps_rollup`, "utf8");
        const match = /^Pss:\s+(\d+)\s+kB$/m.exec(rollup);
        if (match) pssBytes = Number(match[1]) * 1024;
      } catch (_err) {
        // Very short-lived Chromium helpers can disappear between /proc scans.
      }
    }
    return { pid, ppid, rssBytes: rssPages * 4096, pssBytes };
  } catch (_err) {
    return null;
  }
}

function processTreeMemory(rootPid, includePss) {
  if (process.platform === "win32") {
    return { rssBytes: 0, pssBytes: 0 };
  }

  const processes = [];
  for (const name of fs.readdirSync("/proc")) {
    if (!/^\d+$/.test(name)) continue;
    const process = readLinuxProcess(Number(name), includePss);
    if (process) processes.push(process);
  }

  const descendants = new Set([rootPid]);
  let changed;
  do {
    changed = false;
    for (const process of processes) {
      if (!descendants.has(process.pid) && descendants.has(process.ppid)) {
        descendants.add(process.pid);
        changed = true;
      }
    }
  } while (changed);

  let rssBytes = 0;
  let pssBytes = 0;
  for (const process of processes) {
    if (descendants.has(process.pid)) {
      rssBytes += process.rssBytes;
      pssBytes += process.pssBytes;
    }
  }
  return { rssBytes, pssBytes };
}

function killGroup(pid, signal) {
  if (process.platform === "win32") {
    execFileSync("taskkill.exe", ["/PID", String(pid), "/T", "/F"], {
      stdio: "ignore"
    });
    return;
  }
  try {
    process.kill(-pid, signal);
  } catch (err) {
    if (err.code !== "ESRCH") throw err;
  }
}

const windowsGradleWrapper = process.platform === "win32" && argv[0] === "./gradlew";
if (windowsGradleWrapper) argv[0] = "gradlew.bat";
const command = argv[0];
const commandArgs = argv.slice(1);
const windowsBatchCommand = process.platform === "win32" &&
  (windowsGradleWrapper || command.toLowerCase().endsWith(".bat"));
const spawnArgs = windowsBatchCommand
  ? quoteWindowsShellArguments(commandArgs) : commandArgs;
if (process.platform === "win32" && memoryMetric === "pss") {
  console.error("[memory-cap] PSS monitoring is unavailable on Windows; use RSS monitoring");
  process.exit(2);
}
const limitBytes = limitMiB * 1024 * 1024;
const startedAt = process.hrtime.bigint();
let peakBytes = 0;
let peakRssBytes = 0;
let peakPssBytes = 0;
let memoryExceeded = false;

console.log(`[memory-cap] limit=${limitMiB} MiB metric=${memoryMetric} command=${command}`);
const child = spawn(command, spawnArgs, {
  cwd: process.cwd(),
  detached: process.platform !== "win32",
  shell: windowsBatchCommand,
  stdio: logFile ? ["inherit", "pipe", "pipe"] : "inherit"
});

let logStream = null;
if (logFile) {
  logStream = fs.createWriteStream(logFile, { flags: "w" });
  child.stdout.on("data", (chunk) => {
    process.stdout.write(chunk);
    logStream.write(chunk);
  });
  child.stderr.on("data", (chunk) => {
    process.stderr.write(chunk);
    logStream.write(chunk);
  });
}

function forwardTermination(signal) {
  killGroup(child.pid, signal);
  if (process.platform === "win32") return;
  setTimeout(() => killGroup(child.pid, "SIGKILL"), KILL_GRACE_MS).unref();
}

process.once("SIGINT", () => forwardTermination("SIGINT"));
process.once("SIGTERM", () => forwardTermination("SIGTERM"));
process.once("SIGHUP", () => forwardTermination("SIGHUP"));

let monitorFailure = null;
const monitor = setInterval(() => {
  let memory;
  try {
    memory = processTreeMemory(child.pid, memoryMetric === "pss");
  } catch (error) {
    if (monitorFailure) return;
    monitorFailure = error;
    console.error(`[memory-cap] failed to inspect build process memory: ${error.message}`);
    try {
      killGroup(child.pid, "SIGTERM");
    } catch (killError) {
      console.error(`[memory-cap] failed to stop build after monitoring error: ${killError.message}`);
    }
    return;
  }
  const measuredBytes = memoryMetric === "pss" ? memory.pssBytes : memory.rssBytes;
  peakBytes = Math.max(peakBytes, measuredBytes);
  peakRssBytes = Math.max(peakRssBytes, memory.rssBytes);
  peakPssBytes = Math.max(peakPssBytes, memory.pssBytes);
  if (!memoryExceeded && measuredBytes > limitBytes) {
    memoryExceeded = true;
    console.error(
      `[memory-cap] build tree reached ${(measuredBytes / 1048576).toFixed(0)} MiB ${memoryMetric}; ` +
      `terminating it because it exceeded the configured ${limitMiB} MiB ${memoryMetric} limit`
    );
    try {
      killGroup(child.pid, "SIGTERM");
      if (process.platform !== "win32") {
        setTimeout(() => killGroup(child.pid, "SIGKILL"), KILL_GRACE_MS).unref();
      }
    } catch (error) {
      monitorFailure = error;
      console.error(`[memory-cap] failed to stop build after memory limit was exceeded: ${error.message}`);
    }
  }
}, process.platform === "win32" || memoryMetric === "pss" ? 1000 : POLL_INTERVAL_MS);

child.on("error", (err) => {
  clearInterval(monitor);
  console.error(`[memory-cap] failed to start ${command}: ${err.message}`);
  process.exit(1);
});

child.on("exit", (code, signal) => {
  clearInterval(monitor);
  if (logStream) logStream.end();
  const elapsedSeconds = Number(process.hrtime.bigint() - startedAt) / 1e9;
  if (memoryMetric === "pss") {
    console.log(`[memory-cap] peak child-tree PSS=${(peakPssBytes / 1048576).toFixed(0)} MiB`);
    console.log(`[memory-cap] peak child-tree RSS=${(peakRssBytes / 1048576).toFixed(0)} MiB (shared mappings double-counted)`);
  } else {
    console.log(`[memory-cap] peak child-tree RSS=${(peakBytes / 1048576).toFixed(0)} MiB`);
  }
  console.log(`[memory-cap] wall time=${elapsedSeconds.toFixed(1)} s`);
  if (memoryExceeded) process.exit(137);
  if (monitorFailure) process.exit(1);
  if (signal) {
    console.error(`[memory-cap] ${command} exited from signal ${signal}`);
    process.exit(1);
  }
  process.exit(code === null ? 1 : code);
});
