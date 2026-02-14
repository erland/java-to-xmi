# java-to-xmi

A CLI tool that will convert Java source code into **UML XMI**.

This repository currently contains **Step 1 (scaffold)**: a Maven build that produces a runnable fat-jar and a minimal CLI entrypoint.

## Build

Requirements:
- JDK 17+
- Maven 3.9+

```bash
mvn -B test package
```

This produces:
- `target/java-to-xmi.jar` (fat jar)

## Run

```bash
java -jar target/java-to-xmi.jar --help
```

Run on the included sample project:

```bash
java -jar target/java-to-xmi.jar --source samples/mini --output out
```

Outputs (Step 1 placeholders):
- `out/model.xmi`
- `out/report.md`

## Notes

- Real parsing/extraction and UML/XMI export will be implemented in later steps.
- The CLI is intentionally dependency-free in Step 1; we can switch to `picocli` later if you prefer.
