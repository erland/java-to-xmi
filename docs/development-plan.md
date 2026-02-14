# java-to-xmi — Implementation Plan (LLM-friendly, single-prompt steps)

This plan is designed so each step is small enough that an LLM can implement it in a single prompt, while keeping the number of steps minimal.

## Step 1 — Repository scaffold (Maven + CLI entrypoint)
**Goal:** Create a working Maven project that builds a runnable CLI.

**Deliverables**
- `pom.xml` with:
  - Java version target
  - shaded/uber-jar packaging (so users can download one file and run it)
- `src/main/java/.../Main.java`:
  - minimal argument parsing
  - `--help` output
  - validates `--source` exists
  - creates output folder if needed
- `README.md`:
  - build and run instructions
  - example commands
- Sample folder `samples/mini/` with a tiny Java project for local testing.

**Notes (tech choices allowed here)**
- Use a small CLI parser (e.g., picocli) *or* implement a minimal parser manually.
- Ensure deterministic output options are planned (e.g., sorted traversal).

---

## Step 2 — Java source discovery + exclude/include rules
**Goal:** Find all relevant `.java` files deterministically.

**Deliverables**
- `SourceScanner` utility:
  - collects `.java` files under `--source`
  - applies glob excludes
  - optionally includes/excludes test paths
  - returns a sorted list (stable order)
- Unit tests for scanner behavior using `samples/`.

---

## Step 3 — Java model extraction (AST + type resolution baseline)
**Goal:** Build an internal “Java semantic model” sufficient for UML export.

**Deliverables**
- A compact internal model (POJOs), e.g.:
  - `JPackage`, `JType` (class/interface/enum/annotation), `JField`, `JMethod`, `JParam`
  - relationship fields: `extends`, `implements`, referenced types in fields/method signatures
- `JavaExtractor`:
  - parses all source files
  - captures:
    - package name
    - type kind + modifiers + visibility
    - fields + methods + constructors
    - inheritance and interface realization
- Report tracking:
  - parse errors
  - unresolved type references (as strings for now)

**Notes (library suggestions)**
- Start with **Spoon** or **JavaParser** for extraction (both are CLI-friendly).
- For type resolution, implement baseline resolution:
  - resolve types inside the same source set by fully-qualified name map
  - external types remain “unresolved” but preserved as strings.

---

## Step 4 — UML model builder (IR → UML object graph)
**Goal:** Map the extracted Java model into a UML model representation.

**Deliverables**
- `UmlBuilder`:
  - creates packages
  - creates classifiers (Class/Interface/Enumeration)
  - adds attributes (Properties) and operations (Operations)
  - adds generalizations/realizations
  - adds dependencies/associations according to the spec
- Stable ID strategy:
  - deterministic ID function based on qualified name + signature
  - applied consistently across all UML elements
- A “model stats” collector for report summary.

**Notes (library suggestions)**
- Use **Eclipse UML2 (EMF)** as the UML metamodel implementation.
  - This gives you “real UML objects” and well-formed serialization.

---

## Step 5 — XMI export (UML2/EMF serialization) + determinism hardening
**Goal:** Write the UML model to XMI consistently and compatibly.

**Deliverables**
- `XmiWriter`:
  - writes `.xmi` to `--output`
  - ensures:
    - stable ordering where applicable (packages/types/features sorted before creation)
    - no timestamps in output (or make them optional/off by default)
- Golden test:
  - run tool on `samples/mini/`
  - compare output XMI against a committed golden file (byte-for-byte)

**Notes**
- EMF serialization can be influenced by creation order; sorting before creation is a practical determinism lever.

---

## Step 6 — Report generation + CLI exit codes
**Goal:** Produce a human-friendly report and enforce `--fail-on-unresolved`.

**Deliverables**
- `Report.md` output:
  - summary counts
  - unresolved types list
  - parse errors list
  - ignored files list
- Exit code logic:
  - `0/1/2/3` as per spec

---

## Step 7 — GitHub Actions packaging + release artifact
**Goal:** On push/tag, build and publish a downloadable runnable artifact.

**Deliverables**
- `.github/workflows/build.yml`:
  - runs `mvn -B test package`
  - uploads the shaded jar as an artifact
- Optional `.github/workflows/release.yml`:
  - on tag `v*`, create a GitHub Release and attach the jar
- README update:
  - “Download & run” section pointing to Actions artifacts / Releases

**Notes**
- Prefer a single-file distribution:
  - `java -jar java-to-xmi.jar --help`

---

## Recommended “definition of done” for v1
- `mvn test package` passes on GitHub Actions.
- Running the jar on `samples/mini` produces:
  - `output/model.xmi`
  - `output/report.md`
- Output determinism test passes.
- Import works in your Modeller PWA.

## Optional v1.1 extensions (only after v1 is stable)
- Better classpath integration for symbol resolution (Maven/Gradle classpath discovery).
- Annotation capture improvements.
- Multiplicity heuristics and association refinement.
