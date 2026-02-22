import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import crypto from "node:crypto";
import { spawn } from "node:child_process";
import unzipper from "unzipper";

/**
 * @typedef {{ root: string, cleanup: () => Promise<void> }} Workdir
 */

/**
 * @param {string} prefix
 * @returns {Promise<Workdir>}
 */
export async function makeWorkdir(prefix) {
  const root = await fs.promises.mkdtemp(path.join(os.tmpdir(), `${prefix}-`));
  return {
    root,
    cleanup: async () => {
      // best-effort cleanup
      await fs.promises.rm(root, { recursive: true, force: true });
    },
  };
}

/**
 * Safely extracts a zip into destDir with zip-slip protection.
 * @param {string} zipPath
 * @param {string} destDir
 */
export async function unzipSafe(zipPath, destDir) {
  await fs.promises.mkdir(destDir, { recursive: true });

  const directory = await unzipper.Open.file(zipPath);

  for (const entry of directory.files) {
    const fileName = entry.path;

    // prevent zip-slip
    const resolved = path.resolve(destDir, fileName);
    if (!resolved.startsWith(path.resolve(destDir) + path.sep)) {
      throw new Error(`Unsafe zip entry path: ${fileName}`);
    }

    if (entry.type === "Directory") {
      await fs.promises.mkdir(resolved, { recursive: true });
      continue;
    }

    await fs.promises.mkdir(path.dirname(resolved), { recursive: true });
    await new Promise((resolve, reject) => {
      entry
        .stream()
        .pipe(fs.createWriteStream(resolved))
        .on("finish", () => resolve())
        .on("error", reject);
    });
  }
}

/**
 * @param {string} repoUrl
 * @param {string} destDir
 */
export async function gitClone(repoUrl, destDir) {
  await fs.promises.mkdir(path.dirname(destDir), { recursive: true });
  await execOrThrow("git", ["clone", "--depth", "1", repoUrl, destDir], { timeoutMs: 5 * 60_000 });
}

/**
 * Executes a command and rejects if exit code != 0.
 * @param {string} cmd
 * @param {string[]} args
 * @param {{cwd?: string, timeoutMs?: number, env?: Record<string, string | undefined>}=} opts
 * @returns {Promise<{stdout: string, stderr
 */
export async function execOrThrow(cmd, args, opts) {
  const cwd = opts?.cwd;
  const timeoutMs = opts?.timeoutMs ?? 5 * 60_000;
  const env = { ...process.env, ...(opts?.env ?? {}) };

  return await new Promise((resolve, reject) => {
    const child = spawn(cmd, args, { cwd, env, stdio: ["ignore", "pipe", "pipe"] });

    const chunksOut= [];
    const chunksErr= [];
    child.stdout.on("data", (d) => chunksOut.push(Buffer.from(d)));
    child.stderr.on("data", (d) => chunksErr.push(Buffer.from(d)));

    const to = setTimeout(() => {
      child.kill("SIGKILL");
      reject(new Error(`Command timed out: ${cmd} ${args.join(" ")}`));
    }, timeoutMs);

    child.on("error", (err) => {
      clearTimeout(to);
      reject(err);
    });

    child.on("close", (code) => {
      clearTimeout(to);
      const stdout = Buffer.concat(chunksOut).toString("utf-8");
      const stderr = Buffer.concat(chunksErr).toString("utf-8");
      if (code !== 0) {
        reject(new Error(`Command failed (${code}): ${cmd} ${args.join(" ")}\n${stderr}`));
        return;
      }
      resolve({ stdout, stderr });
    });
  });
}

/**
 * Tries to locate the java-to-xmi CLI jar in common build output locations.
 * This avoids hardcoding a single path since java-to-xmi is now a multi-module build.
 *
 * @param {string=} explicitPath
 * @returns {Promise<string>}
 */
export async function resolveJavaToXmiJar(explicitPath) {
  const candidates = [];

  if (explicitPath) candidates.push(explicitPath);

  // Historical single-module path
  candidates.push("/deps/java-to-xmi/target/java-to-xmi.jar");

  // Multi-module path (recommended)
  candidates.push("/deps/java-to-xmi/java-to-xmi-cli/target/java-to-xmi-cli-0.1.0-SNAPSHOT.jar");

  // Generic multi-module glob candidates
  const globDirs = [
    "/deps/java-to-xmi/java-to-xmi-cli/target",
    "/deps/java-to-xmi/target",
  ];

  for (const p of candidates) {
    try {
      const st = await fs.promises.stat(p);
      if (st.isFile()) return p;
    } catch {
      // ignore
    }
  }

  // Try to pick the newest jar in the known target folders
  for (const dir of globDirs) {
    try {
      const entries = await fs.promises.readdir(dir, { withFileTypes: true });
      const jars = [];
      for (const e of entries) {
        if (!e.isFile()) continue;
        if (!e.name.endsWith(".jar")) continue;
        const full = path.join(dir, e.name);
        const st = await fs.promises.stat(full);
        jars.push({ full, mtimeMs: st.mtimeMs });
      }
      jars.sort((a, b) => b.mtimeMs - a.mtimeMs);
      const best = jars.find((j) => j.full.includes("java-to-xmi"));
      if (best) return best.full;
    } catch {
      // ignore
    }
  }

  throw new Error(
    [
      "Unable to locate java-to-xmi CLI jar.",
      "Expected one of:",
      "- /deps/java-to-xmi/target/*.jar",
      "- /deps/java-to-xmi/java-to-xmi-cli/target/*.jar",
      "Build the project (e.g. mvn -q -DskipTests package) in your java-to-xmi repo, then restart containers.",
    ].join("\n")
  );
}
