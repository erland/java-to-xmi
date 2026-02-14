# java-to-xmi — Functional Specification (v1)

## 1. Purpose
Create a command-line tool that converts a **Java source code tree** into a **UML model** and exports it as **XMI**. The primary use case is importing the produced XMI into your React-based Modeller PWA, while keeping the output compatible enough to be imported into other UML tools when possible.

## 2. Scope
### In scope (v1)
- Read a local directory containing Java sources (single-module or multi-module layout).
- Build a UML *model* representation from the Java code.
- Export the UML model to an XMI file.
- Emit a conversion report (warnings, stats, and any unresolved references).
- Provide stable identifiers to support repeatable exports.

### Out of scope (v1)
- Round-trip editing (XMI → Java).
- Full diagram/layout export (tool-specific diagram metadata).
- Full behavioral UML (e.g., activity/sequence diagrams) beyond basic structural elements.

## 3. Inputs
### 3.1 Required
- `--source <path>`: Root directory of the Java project sources.

### 3.2 Optional
- `--output <path>`: Output XMI file path. Default: `./output/model.xmi`
- `--name <string>`: Logical model name. Default: derived from folder name.
- `--include-tests <true|false>`: Include test sources. Default: `false`
- `--exclude <glob...>`: One or more exclude globs (e.g., `**/generated/**`).
- `--classpath <path...>`: One or more classpath entries (directories or jars).
- `--fail-on-unresolved <true|false>`: Exit non-zero if unresolved types remain. Default: `false`
- `--report <path>`: Write a markdown report. Default: `./output/report.md`
- `--format <xmi>`: Placeholder for future formats; v1 supports `xmi`.

## 4. Outputs
### 4.1 Primary output
- A single XMI file representing a UML model.

### 4.2 Secondary outputs
- A report file (markdown) with:
  - Summary (counts of packages/classes/interfaces/enums/attributes/operations/relations)
  - Unresolved types list
  - Ignored files and reasons
  - Warnings and assumptions applied
- Console logs suitable for CI.

## 5. Functional Behavior
### 5.1 Source discovery
- Tool recursively discovers `.java` files under `--source`.
- Exclusion rules are applied before parsing.
- Test sources are excluded by default unless enabled.

### 5.2 Model extraction rules (Java → UML)
#### 5.2.1 Namespaces / Packages
- Each Java package becomes a UML package.
- Nested packages are represented as nested UML packages.

#### 5.2.2 Classifiers
- `class` → UML Class
- `interface` → UML Interface
- `enum` → UML Enumeration
- `@interface` → UML Annotation type (represented as a UML Class/Interface with a marker in documentation or a stereotype-like tag as needed)

#### 5.2.3 Visibility & modifiers
- `public/protected/private/package-private` map to UML visibility.
- `static`, `abstract`, `final` are captured as classifier and feature properties when applicable.

#### 5.2.4 Attributes (fields)
- Each field becomes a UML Property:
  - name, visibility
  - type (if resolvable)
  - multiplicity: inferred for arrays/collection-like types as `0..*` (heuristic)
- Constant fields may be tagged as such (e.g., documented as `const`).

#### 5.2.5 Operations (methods/constructors)
- Each method becomes a UML Operation:
  - name, visibility
  - parameters with types
  - return type
  - `static`, `abstract`
- Constructors become Operations marked as constructors if supported by the target UML representation.

#### 5.2.6 Generalization / realization
- `extends` → UML Generalization
- `implements` → UML Realization (or Generalization depending on UML element kind)

#### 5.2.7 Dependencies and associations
- If a field type is another classifier in the model, create an Association (or at minimum a typed Property reference).
- If a parameter/return type is another classifier in the model, create a Dependency (unless already represented by an Association).
- Composition/aggregation are not asserted unless explicitly configured (default: do not guess ownership semantics).

#### 5.2.8 Inner / nested types
- Nested types are represented as nested classifiers in the owning classifier’s namespace, and/or using a nesting relationship depending on UML capabilities.

#### 5.2.9 Annotations
- Annotations may be captured as:
  - documentation tags on elements, and/or
  - a lightweight annotation catalog inside the model.
- v1 should preserve annotation names and key-value pairs where practical.

#### 5.2.10 Generics
- Generic type arguments are preserved as:
  - stringified type names in documentation, and/or
  - type parameters where feasible.
- If full generic mapping is not feasible, the tool must still produce valid XMI and record limitations in the report.

### 5.3 Stable IDs and determinism
- The export should be deterministic given the same input.
- Element IDs must be stable across runs when element signatures do not change, based on:
  - package + classifier name
  - member name + signature (parameters/return type)

### 5.4 Error handling
- The tool must never produce malformed XMI; if critical failures occur, it should abort with an actionable error.
- Non-critical issues (unresolved types, parse errors in a subset of files) should be reported and can be configured to fail the build.

### 5.5 Performance
- Must handle typical enterprise codebases (thousands of classes) with reasonable memory usage.
- Provide progress output for large projects.

## 6. CLI UX
### 6.1 Examples
- Basic:
  - `java-to-xmi --source ./my-project --output ./output/model.xmi`
- With classpath:
  - `java-to-xmi --source ./my-project --classpath ./lib/deps.jar ./build/classes`

### 6.2 Exit codes
- `0` success
- `1` invalid arguments
- `2` conversion failed (critical error)
- `3` unresolved types present and `--fail-on-unresolved=true`

## 7. Compatibility Goals
- Output must import cleanly into the Modeller PWA.
- Output should be a reasonable UML XMI variant that other tools can often read, recognizing that perfect cross-tool compatibility is not guaranteed.
- Avoid tool-specific diagram/layout data in v1.

## 8. Testable Acceptance Criteria (v1)
1. Running the tool on a small sample project produces:
   - one XMI file
   - one report file
2. The XMI file imports into the Modeller PWA without errors.
3. Determinism: re-running on the same inputs produces identical output (byte-for-byte), except for timestamps if present.
4. For a project with known inheritance:
   - Generalization/realization links appear in the UML model.
5. When an external type is missing:
   - tool records unresolved type(s) in the report
   - exit code follows `--fail-on-unresolved` behavior.
