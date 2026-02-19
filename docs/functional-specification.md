# java-to-xmi — Functional Specification (v1)

## 1. Purpose
Create a command-line tool that converts a **Java source code tree** into a **UML model** and exports it as **XMI**. The primary use case is importing the produced XMI into the React-based Modeller PWA, while keeping the output compatible enough to be imported into other UML tools when possible.

## 2. Scope
### In scope (v1)
- Read a local directory containing Java sources (single-module or multi-module layouts).
- Build a UML model representation from the Java code:
  - packages
  - classes, interfaces, enums (including nested/member types)
  - attributes (fields)
  - operations (methods/constructors) with parameters and return type
  - inheritance and interface realization
  - optional association lines derived from fields
  - dependencies derived from:
    - method signatures (parameters/return types)
    - optionally, conservative method-body usage (approx. call graph)
- Export the UML model to an XMI file.
- Emit a conversion report (warnings, stats, and unresolved references).
- Provide stable identifiers to support repeatable exports.

### Out of scope (v1)
- Round-trip editing (XMI → Java).
- Full diagram/layout export (DI, layout coordinates, or diagram styling).
- Perfect cross-tool stereotype/annotation portability (annotations are best-effort).
- Full semantic call graph resolution (no bytecode analysis; conservative AST-based extraction only).

## 3. Inputs
- A folder containing `.java` files.
- Optional exclude globs to skip generated or irrelevant folders.
- Optional inclusion of common test source folders.

## 4. Outputs
### 4.1 XMI
- One UML XMI file (UML2-compatible serialization).
- Deterministic `xmi:id` generation for repeatable output.
- Optional embedded UML Profile (`JavaAnnotations`) and stereotype applications for Java annotations.

### 4.2 Report
- One Markdown report summarizing:
  - counts (types, fields, methods, associations, dependencies)
  - unresolved type references
  - warnings (e.g., ambiguous resolutions, skipped constructs)

## 5. Model Mapping Rules
### 5.1 Packages
- Java packages map to UML `Package`s.
- Package hierarchy follows the Java package name segmentation.

### 5.2 Types
- `class` → UML `Class`
- `interface` → UML `Interface`
- `enum` → UML `Enumeration` with `EnumerationLiteral`s

### 5.3 Nested / member types
Nested/member types are preserved and can be exposed in three modes:
- `uml`: true UML nesting (member classifiers owned by the outer classifier)
- `uml+import`: true nesting **plus** an import-style mirror into the enclosing package namespace for consumer compatibility
- `flatten`: nested types become package-level classifiers (lossy, but simple for many consumers)

### 5.4 Fields (attributes)
- Java fields map to UML `Property` elements owned by the containing classifier.
- Visibility and common modifiers are preserved (e.g., `static`, `final`).
- Multiplicity is inferred conservatively (arrays/collections/Optional, plus selected annotation hints).
- Optional association emission from fields is controlled by the `--associations` policy.

### 5.5 Methods and constructors (operations)
- Java methods map to UML `Operation`s with `Parameter`s.
- Return types become a return `Parameter` (`direction=return`).
- Visibility and common modifiers are preserved (`static`, `abstract`, etc.).
- By default, simple property accessors are suppressed when a corresponding field exists:
  - `getX()` / `setX(T)` and boolean `isX()` for field `x`
  - can be enabled with `--include-accessors true`
- By default, constructors are suppressed to reduce noise.
  - can be enabled with `--include-constructors true`

### 5.6 Inheritance and realization
- `extends` relationships map to UML `Generalization`.
- `implements` relationships map to UML `InterfaceRealization` (or equivalent UML2 construct).

### 5.7 Associations derived from fields
Association emission is policy-driven:
- `none`: do not create association lines (attributes only)
- `jpa`: create associations only when JPA-style annotations strongly suggest one
- `resolved`: create associations when the referenced type can be resolved within the model
- `smart`: best-effort heuristic combining resolution + common collection patterns

For common **bidirectional JPA** mappings, the generator may merge two field-derived associations into a
**single UML association** when it is safe and unambiguous:

- If one side declares `mappedBy`, the inverse end is matched by name.
- Otherwise, the association is merged only when there is a **unique** inverse relationship field on each side.

If there is any ambiguity (e.g. multiple candidate inverse fields such as `customer` and `originalCustomer`),
the generator keeps separate associations.

### 5.8 Dependencies
The tool can emit UML `Dependency` edges for relationships that are useful for navigation and analysis.

- Dependency output is controlled by `--deps <true|false>` (default: `false`).
- When enabled, the tool emits:
  1) **Signature dependencies** derived from method parameter and return types.
  2) **Method-body call dependencies** derived conservatively from method/constructor bodies (approx. call graph), including:
     - `new Type(...)`
     - `var.method()` where `var` is a typed local/parameter
     - `Type.staticCall()` where `Type` resolves as a type in scope/imports
     - `this.field.method()` when the field type is known
     - invocation-derived dependencies are tagged with `kind=invocation`

To reduce diagram noise, when dependencies are enabled the tool suppresses dependencies that duplicate an
existing **Association** between the same two classifiers. Multiple dependency findings between the same
two classifiers are merged into a single UML `Dependency`.

## 6. Annotations and stereotypes
Java type-level annotations can be represented as:
- an embedded UML Profile named `JavaAnnotations`
- stereotype applications emitted via an `xmi:Extension` block

For backwards compatibility, stereotype/profile emission can be disabled (`--no-stereotypes`).

## 7. CLI UX
### 7.1 Usage and options

```
java -jar java-to-xmi.jar --source <path> [--output <dir|file.xmi>] [options]
```

Options:
- `--source <path>`: Root folder containing Java sources (required)
- `--output <path>`: Output folder or `.xmi` file path (default: `./output`)
- `--name <string>`: UML model name (default: derived from source folder name)
- `--report <path>`: Report markdown path (default: `<output>/report.md`)
- `--exclude <glob>`: Exclude paths matching a glob (repeatable); evaluated relative to `--source` using `/` separators  
  - also supports `--exclude=<glob>`
- `--include-tests`: Include common test folders (default: excluded)
- `--associations <mode>`: `none | jpa | resolved | smart` (default: `resolved`)
- `--nested-types <mode>`: `uml | uml+import | flatten` (default: `uml`)
- `--deps <true|false>`: Emit dependency edges (signatures + conservative call graph) (default: `false`)
- `--include-accessors <true|false>`: Include getter/setter operations when a corresponding field exists (default: `false`)
- `--include-constructors <true|false>`: Include constructors as operations (default: `false`)
- `--no-stereotypes`: Skip JavaAnnotations profile and do not emit stereotype applications
- `--fail-on-unresolved <true|false>`: Exit with code 3 if unresolved types remain (default: `false`)
- `-h, --help`: Show help

### 7.2 Examples
- Basic:
  - `java -jar target/java-to-xmi.jar --source ./my-project --output ./out`
- Excluding generated sources:
  - `java -jar target/java-to-xmi.jar --source . --exclude "**/generated/**" --exclude "**/*Test.java"`
- More tool-friendly nested types:
  - `java -jar target/java-to-xmi.jar --source . --nested-types uml+import`
- Include dependencies (signature + conservative call graph):
  - `java -jar target/java-to-xmi.jar --source . --deps true`

### 7.3 Exit codes
- `0` success
- `1` invalid arguments
- `2` conversion failed (critical error)
- `3` unresolved types present and `--fail-on-unresolved=true`

## 8. Compatibility Goals
- Output must import cleanly into the Modeller PWA.
- Output should be a reasonable UML XMI variant that other tools can often read, recognizing that perfect cross-tool compatibility is not guaranteed.
- Avoid tool-specific diagram/layout data in v1.
- Keep output deterministic across runs given the same inputs and flags.

## 9. Testable Acceptance Criteria (v1)
1. Running the tool on a small sample project produces:
   - one XMI file
   - one report file
2. The XMI file imports into the Modeller PWA without errors.
3. Determinism: re-running on the same inputs produces identical output (byte-for-byte).
4. For a project with known inheritance:
   - Generalization/realization links appear in the UML model.
5. When external types are missing:
   - unresolved references are recorded in the report
   - exit code follows `--fail-on-unresolved` behavior.
6. When `--deps true` is used:
   - dependency edges (signature + conservative call graph) appear in the model, without affecting determinism.
