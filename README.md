# java-to-xmi

Convert a Java source tree into a UML model and export it as **XMI**.

This project is optimized for importing into your React modeller PWA, while keeping the output
reasonably compatible with UML tools.

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

```bash
java -jar target/java-to-xmi.jar --help
```

Run on the included sample project:

```bash
java -jar target/java-to-xmi.jar --source samples/mini --output out
```

Outputs:
- `out/model.xmi`
- `out/report.md`

## Stereotypes / annotations

Type-level Java annotations are represented as:

1) A UML Profile named `JavaAnnotations` embedded in the XMI
2) Stereotype applications emitted in an `xmi:Extension` block (namespace `j2x`)

This avoids relying on UML2's runtime stereotype application APIs, while keeping the output deterministic.

If you want legacy output without any annotation profile or stereotype applications, use:

```bash
java -jar target/java-to-xmi.jar --source ... --no-stereotypes
```

## Key CLI options

- `--exclude <glob>` (repeatable) exclude paths relative to `--source`
- `--include-tests` include common test folders
- `--fail-on-unresolved <true|false>` exit with code 3 if unknown types remain
- `--no-stereotypes` skip the JavaAnnotations profile + stereotype output

More details:
- `docs/functional-specification.md`
- `docs/backwards-compatibility.md`
