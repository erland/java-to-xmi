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


## Step 2: Source scanning options

- `--exclude <glob>` (repeatable) to ignore paths relative to `--source`.
- `--include-tests` to include `src/test` and other common test folders.

## Download & run

### From GitHub Actions artifacts (every push/PR)
1. Open the latest **Build** workflow run.
2. Download the `java-to-xmi-jar` artifact.
3. Run:
   ```bash
   java -jar java-to-xmi-*.jar --help
   ```

### From GitHub Releases (tags)
If you create a tag like `v0.1.0`, the **Release** workflow will publish a GitHub Release containing the runnable jar.

```bash
java -jar java-to-xmi-*.jar --source ./my-project --output ./out/model.xmi --report ./out/report.md
```
