import express from "express";
import multer from "multer";
import fs from "node:fs";
import path from "node:path";
import { makeWorkdir, unzipSafe, gitClone, execOrThrow, resolveJavaToXmiJar } from "./utils.js";

const app = express();
const upload = multer({
  storage: multer.memoryStorage(),
  limits: {
    // Allow large uploads (zips and IR JSON files)
    fileSize: 300 * 1024 * 1024,
  },
});

const JAR_ENV = process.env.JAVA_TO_XMI_JAR;

app.get("/health", (_req, res) => res.json({ ok: true }));

app.post(
  "/v1/xmi",
  upload.fields([
    { name: "inputZip", maxCount: 1 },
    { name: "irFile", maxCount: 1 },
  ]),
  async (req, res) => {
  const wd = await makeWorkdir("xmi");
  try {
    const JAR = await resolveJavaToXmiJar(JAR_ENV);
    // IR can be sent either as a text field (irJson) or as a file (irFile).
    // Using a file avoids multipart field size limits.
    const irFile = req.files?.irFile?.[0];
    const irJson = typeof req.body.irJson === "string" ? req.body.irJson : null;
    const language = (req.body.language || "").toLowerCase();
    const resultFormat = (req.body.resultFormat || "xmi").toLowerCase();
    const repoUrl = req.body.repoUrl;

    if (resultFormat !== "xmi" && resultFormat !== "ir") {
      return res.status(400).json({ error: "Invalid field: resultFormat (expected 'xmi' or 'ir')" });
    }

    const outDir = path.join(wd.root, "out");
    await fs.promises.mkdir(outDir, { recursive: true });
    const outXmi = path.join(outDir, "model.xmi");
    const outIr = path.join(outDir, "model.ir.json");

    const args = ["-jar", JAR];

    // pass-through options
    const addOpt = (flag, val) => {
      if (val == null || val === "") return;
      args.push(flag, String(val));
    };
    addOpt("--name", req.body.name);
    addOpt("--associations", req.body.associations);
    addOpt("--deps", req.body.deps);
    addOpt("--nested-types", req.body.nestedTypes);
    addOpt("--include-accessors", req.body.includeAccessors);
    addOpt("--include-constructors", req.body.includeConstructors);
    addOpt("--fail-on-unresolved", req.body.failOnUnresolved);
    if (String(req.body.noStereotypes).toLowerCase() === "true") args.push("--no-stereotypes");

    // repeatable excludes for source mode
    const excludes = req.body.exclude;
    const exList = Array.isArray(excludes) ? excludes : (typeof excludes === "string" && excludes ? [excludes] : []);
    for (const ex of exList) args.push("--exclude", ex);

    if ((irFile && irFile.buffer?.length) || (irJson && irJson.trim().length)) {
      // IR mode
      const irPath = path.join(wd.root, "model.ir.json");
      if (irFile && irFile.buffer?.length) {
        await fs.promises.writeFile(irPath, irFile.buffer);
      } else {
        await fs.promises.writeFile(irPath, irJson, "utf-8");
      }

      if (resultFormat === "ir") {
        const irOut = await fs.promises.readFile(irPath, "utf-8");
        res.status(200);
        res.setHeader("content-type", "application/json");
        res.setHeader("content-disposition", 'attachment; filename="model.ir.json"');
        res.send(irOut);
        return;
      }

      args.push("--ir", irPath);
      args.push("--output", outXmi);
      await execOrThrow("java", args, { timeoutMs: 5 * 60_000 });

      const xmi = await fs.promises.readFile(outXmi);
      res.status(200).type("application/xml").send(xmi);
      return;
    }

    // Java source mode
    if (language !== "java") {
      return res.status(400).json({ error: "Provide irJson, or language=java with inputZip/repoUrl" });
    }

    const sourceDir = path.join(wd.root, "source");
    if (repoUrl) {
      await gitClone(repoUrl, sourceDir);
    } else if (req.files?.inputZip?.[0]) {
      const zipPath = path.join(wd.root, "input.zip");
      await fs.promises.writeFile(zipPath, req.files.inputZip[0].buffer);
      await unzipSafe(zipPath, sourceDir);
    } else {
      return res.status(400).json({ error: "Provide inputZip or repoUrl" });
    }

    args.push("--source", sourceDir);
    args.push("--output", outXmi);
    // Also materialize an IR snapshot (schema v2) so callers can request IR as final output.
    args.push("--write-ir", outIr);
    await execOrThrow("java", args, { timeoutMs: 8 * 60_000 });

    if (resultFormat === "ir") {
      const ir = await fs.promises.readFile(outIr, "utf-8");
      res.status(200);
      res.setHeader("content-type", "application/json");
      res.setHeader("content-disposition", 'attachment; filename="model.ir.json"');
      res.send(ir);
      return;
    }

    const xmi = await fs.promises.readFile(outXmi);
    res.status(200).type("application/xml").send(xmi);
  } catch (err) {
    res.status(500).json({ error: String(err?.message || err) });
  } finally {
    await wd.cleanup();
  }
  }
);

app.listen(7072, () => console.log("xmi-service listening on :7072"));
