#!/usr/bin/env node
/**
 * Публикация рабочего каталога в GitHub без локального git-транспорта.
 *
 * В песочнице Windows git не может работать (schannel/sh pipe ограничения),
 * поэтому коммиты создаются через GitHub Git Data API. Скрипт инкрементальный:
 * неизменённые blob'ы не отправляются повторно (сравнение по git-blob SHA-1),
 * удалённые файлы удаляются из дерева.
 *
 * Использование:
 *   node tools/gh-push.mjs "feat: сообщение коммита"
 *
 * Токен: %USERPROFILE%\.config\bond-strategy-planner\github.token (или GH_TOKEN).
 */
import { createHash } from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import os from "node:os";

const OWNER = process.env.GH_OWNER || "yuriiborisov";
const REPO = process.env.GH_REPO || "bond-strategy-planner-deepseek2";
const BRANCH = process.env.GH_BRANCH || "main";
const ROOT = process.cwd();
const STATE_DIR = path.join(ROOT, ".github-sync");
const STATE_FILE = path.join(STATE_DIR, "manifest.json");

const EXCLUDED_DIRS = new Set([
  ".git", ".github-sync", "node_modules", "target", "dist", "build", "out",
  "coverage", ".vite", ".idea", ".vscode", "playwright-report", "test-results",
  "data", "logs", ".mvn", ".m2repo", ".gradle",
]);
const EXCLUDED_FILES = new Set([".env", ".env.local", "thumbs.db", ".ds_store"]);
const EXCLUDED_EXT = /\.(log|tmp|db|mv\.db|trace\.db|jar|class|zip|7z)$/i;

function readToken() {
  if (process.env.GH_TOKEN) return process.env.GH_TOKEN.trim();
  const p = path.join(os.homedir(), ".config", "bond-strategy-planner", "github.token");
  return fs.readFileSync(p, "utf8").trim();
}

const TOKEN = readToken();
const API = "https://api.github.com";

async function api(method, url, body) {
  for (let attempt = 0; attempt < 4; attempt++) {
    const res = await fetch(API + url, {
      method,
      headers: {
        Authorization: `Bearer ${TOKEN}`,
        Accept: "application/vnd.github+json",
        "User-Agent": "bond-strategy-planner-ci",
        "X-GitHub-Api-Version": "2022-11-28",
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (res.status === 429 || res.status >= 500) {
      await new Promise((r) => setTimeout(r, 1000 * 2 ** attempt));
      continue;
    }
    const text = await res.text();
    if (!res.ok) {
      throw new Error(`${method} ${url} -> ${res.status}: ${text.slice(0, 800)}`);
    }
    return text ? JSON.parse(text) : null;
  }
  throw new Error(`${method} ${url}: exhausted retries`);
}

function walk(dir, rel, acc) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const name = entry.name;
    const full = path.join(dir, name);
    const relPath = rel ? `${rel}/${name}` : name;
    if (entry.isDirectory()) {
      if (EXCLUDED_DIRS.has(name.toLowerCase())) continue;
      walk(full, relPath, acc);
    } else if (entry.isFile()) {
      const lower = name.toLowerCase();
      if (EXCLUDED_FILES.has(lower) || EXCLUDED_EXT.test(lower)) continue;
      const st = fs.statSync(full);
      if (st.size > 8 * 1024 * 1024) {
        console.warn(`skip large file: ${relPath} (${st.size} bytes)`);
        continue;
      }
      acc.push({ relPath, full });
    }
  }
}

function blobSha(buf) {
  const header = Buffer.from(`blob ${buf.length}\0`, "utf8");
  return createHash("sha1").update(header).update(buf).digest("hex");
}

function loadState() {
  try {
    return JSON.parse(fs.readFileSync(STATE_FILE, "utf8"));
  } catch {
    return { commitSha: null, treeSha: null, files: {} };
  }
}

async function main() {
  const message = process.argv[2];
  if (!message) {
    console.error('usage: node tools/gh-push.mjs "commit message"');
    process.exit(2);
  }

  const state = loadState();
  const files = [];
  walk(ROOT, "", files);
  files.sort((a, b) => a.relPath.localeCompare(b.relPath));

  const current = new Map();
  for (const f of files) {
    const buf = fs.readFileSync(f.full);
    current.set(f.relPath, { buf, sha: blobSha(buf) });
  }

  const previous = state.files || {};
  const changed = [...current.entries()].filter(([p, v]) => previous[p] !== v.sha);
  const deleted = Object.keys(previous).filter((p) => !current.has(p));

  console.log(`files: ${current.size}, changed: ${changed.length}, deleted: ${deleted.length}`);

  // Если ref существует, но локального состояния нет — берём текущий HEAD,
  // чтобы не перезаписать историю (первый запуск на непустом репозитории).
  let parentSha = state.commitSha;
  let baseTree = state.treeSha;
  if (!parentSha) {
    try {
      const ref = await api("GET", `/repos/${OWNER}/${REPO}/git/ref/heads/${BRANCH}`);
      parentSha = ref.object.sha;
      const commit = await api("GET", `/repos/${OWNER}/${REPO}/git/commits/${parentSha}`);
      baseTree = commit.tree.sha;
      console.log(`existing head: ${parentSha}`);
    } catch (e) {
      const msg = String(e.message);
      // 404 — ветки нет; 409 — репозиторий пуст.
      if (!msg.includes("404") && !msg.includes("409")) throw e;
      // Git Data API не работает на пустом репозитории: создаём первый коммит
      // через Contents API, дальше — обычный инкрементальный путь.
      const bootstrapPath = current.has("README.md") ? "README.md" : current.keys().next().value;
      if (!bootstrapPath) throw new Error("nothing to commit");
      const resp = await api("PUT", `/repos/${OWNER}/${REPO}/contents/${encodeURI(bootstrapPath)}`, {
        message: "chore: initial commit",
        content: current.get(bootstrapPath).buf.toString("base64"),
      });
      parentSha = resp.commit.sha;
      baseTree = resp.commit.tree.sha;
      console.log(`bootstrapped empty repository: ${parentSha}`);
    }
  }

  const tree = [];
  const uploads = changed.slice();
  const concurrency = 6;
  const newShas = new Map();
  while (uploads.length) {
    const batch = uploads.splice(0, concurrency);
    await Promise.all(
      batch.map(async ([p, v]) => {
        const isBinary = v.buf.includes(0);
        const body = isBinary
          ? { content: v.buf.toString("base64"), encoding: "base64" }
          : { content: v.buf.toString("utf8"), encoding: "utf8" };
        const blob = await api("POST", `/repos/${OWNER}/${REPO}/git/blobs`, body);
        newShas.set(p, blob.sha);
      }),
    );
  }

  for (const [p, v] of current) {
    if (newShas.has(p)) {
      tree.push({ path: p, mode: "100644", type: "blob", sha: newShas.get(p) });
    } else if (changed.length || deleted.length) {
      tree.push({ path: p, mode: "100644", type: "blob", sha: v.sha });
    }
  }
  for (const p of deleted) {
    tree.push({ path: p, mode: "100644", type: "blob", sha: null });
  }

  if (state.commitSha && changed.length === 0 && deleted.length === 0) {
    console.log("nothing to commit");
    return;
  }

  const treeBody = { tree };
  if (baseTree) treeBody.base_tree = baseTree;
  const newTree = await api("POST", `/repos/${OWNER}/${REPO}/git/trees`, treeBody);

  const commitBody = { message, tree: newTree.sha };
  if (parentSha) commitBody.parents = [parentSha];
  const commit = await api("POST", `/repos/${OWNER}/${REPO}/git/commits`, commitBody);

  if (parentSha) {
    await api("PATCH", `/repos/${OWNER}/${REPO}/git/refs/heads/${BRANCH}`, {
      sha: commit.sha,
      force: false,
    });
  } else {
    await api("POST", `/repos/${OWNER}/${REPO}/git/refs`, {
      ref: `refs/heads/${BRANCH}`,
      sha: commit.sha,
    });
  }

  const filesState = {};
  for (const [p, v] of current) filesState[p] = v.sha;
  fs.mkdirSync(STATE_DIR, { recursive: true });
  fs.writeFileSync(
    STATE_FILE,
    JSON.stringify({ commitSha: commit.sha, treeSha: newTree.sha, files: filesState, at: new Date().toISOString() }, null, 2),
  );
  console.log(`pushed ${commit.sha.slice(0, 10)} to ${OWNER}/${REPO}#${BRANCH}`);
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
