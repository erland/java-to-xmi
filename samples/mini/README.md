# samples/mini

A tiny Java source tree used for local smoke testing of the `java-to-xmi` CLI.

It is intentionally not a Maven/Gradle project—just a folder with Java files.

## Contents

This sample includes a few type-level annotations so you can manually verify:

- The `JavaAnnotations` UML Profile + Stereotypes are generated.
- Stereotype applications are emitted under `<xmi:Extension extender="java-to-xmi">`.
- Annotation member values become `j2x:tag` entries.

Files live under `samples/mini/src/`.

This sample also includes a small "JPA-like" relationship example that is intentionally
dependency-free:

- `com.acme.model.EntityA` has a backing field `attrB : EntityB`
- The relationship annotation (`@ManyToOne`) is placed on the **getter** `getAttrB()`
  (property access), not on the field.

The generator propagates relationship annotations from getters to their backing fields,
so you can validate that `--associations jpa` still produces an **Association** between
`EntityA` and `EntityB`.

## Example usage

From the repository root:

```bash
java -jar target/java-to-xmi.jar \
  --input samples/mini/src \
  --output /tmp/mini.xmi \
  --report /tmp/mini-report.md
```

### Validate property-access relationship propagation

Run with JPA-only association mode to make the test sensitive to relationship annotations:

```bash
java -jar target/java-to-xmi.jar \
  --associations jpa \
  --input samples/mini/src \
  --output /tmp/mini-jpa.xmi \
  --report /tmp/mini-jpa-report.md
```

Expected result:

- The model should contain an **Association** from `EntityA` to `EntityB` created from
  the backing field `attrB`, even though `@ManyToOne` is located on `getAttrB()`.
- You may still see a **Dependency** from `EntityA` to `EntityB` due to method signature
  dependencies (return type of `getAttrB()`).

To generate legacy output without stereotypes:

```bash
java -jar target/java-to-xmi.jar \
  --no-stereotypes \
  --input samples/mini/src \
  --output /tmp/mini-legacy.xmi
```
