# java-to-xmi

Convert a Java source tree into a UML model and export it as **XMI**.

This project is optimized for importing into your React Modeller PWA, while keeping the output
reasonably compatible with other UML tools.

## Build

Requirements:
- JDK 17+
- Maven 3.9+

```bash
mvn -B test package
```

Produces:
- `target/java-to-xmi.jar` (fat jar)

## Run

Show CLI help:

```bash
java -jar target/java-to-xmi.jar --help
```

Run on the included sample project:

```bash
java -jar target/java-to-xmi.jar --source samples/mini --output out
```

Outputs (by default):
- `out/model.xmi`
- `out/report.md`

### Output naming

- `--output` can be either an **output folder** or a direct **`.xmi` file path**.
- Use `--name <ModelName>` to control the UML model name (otherwise derived from the source folder).

### Key CLI options

Source selection:
- `--exclude <glob>` (repeatable) exclude paths relative to `--source`
  - also supports `--exclude=<glob>`
- `--include-tests` include common test folders (default: excluded)

Model shaping:
- `--associations <none|jpa|resolved|smart>` control when fields become association lines (vs attribute-only)
- `--nested-types <uml|uml+import|flatten>` control nested/member type exposure
  - `uml` keeps true nested containment
  - `uml+import` additionally mirrors nested types into the package namespace via imports (for consumers that struggle with nested lookup)
  - `flatten` places nested types in the package as top-level types (lossy but widely compatible)

Dependencies:
- `--deps <true|false>` emit UML `Dependency` edges (default: `false`)
  - when enabled, dependencies are derived from **method signatures** and conservatively from **method bodies** (approx. call graph)
  - dependencies that duplicate an existing **Association** between the same two classifiers are suppressed (reduces noise)

Quality gates:
- `--fail-on-unresolved <true|false>` exit with code `3` if unknown types remain

Reporting:
- `--report <path>` write the report markdown to a specific location (default: `<output>/report.md`)

## Stereotypes / annotations

Type-level Java annotations are represented as:

1) A UML Profile named `JavaAnnotations` embedded in the XMI
2) Stereotype applications emitted in an `xmi:Extension` block (namespace `j2x`)

This avoids relying on UML2's runtime stereotype application APIs, while keeping the output deterministic.

If you want legacy output without any annotation profile or stereotype applications, use:

```bash
java -jar target/java-to-xmi.jar --source ... --no-stereotypes
```

## More details

- `docs/functional-specification.md`
- `docs/backwards-compatibility.md`
- `docs/associations.md`
