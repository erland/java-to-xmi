# samples/mini

A tiny Java source tree used for local smoke testing of the `java-to-xmi` CLI.

It is intentionally not a Maven/Gradle project—just a folder with Java files.

## Contents

This sample includes a few type-level annotations so you can manually verify:

- The `JavaAnnotations` UML Profile + Stereotypes are generated.
- Stereotype applications are emitted under `<xmi:Extension extender="java-to-xmi">`.
- Annotation member values become `j2x:tag` entries.

Files live under `samples/mini/src/`.

## Example usage

From the repository root:

```bash
java -jar target/java-to-xmi.jar \
  --input samples/mini/src \
  --output /tmp/mini.xmi \
  --report /tmp/mini-report.md
```

To generate legacy output without stereotypes:

```bash
java -jar target/java-to-xmi.jar \
  --no-stereotypes \
  --input samples/mini/src \
  --output /tmp/mini-legacy.xmi
```
