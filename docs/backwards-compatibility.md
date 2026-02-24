# Backwards compatibility

## CLI flag

Use `--no-stereotypes` to disable:

- creation of the `JavaAnnotations` profile
- emitting stereotype applications via `xmi:Extension`

This is useful if you have older consumers that only understand base UML elements.

## Library API

If you use `XmiWriter.write(Model, Path)` directly (the 2-argument overload), it will **not**
inject stereotype applications.

To include stereotype applications, call:

```java
XmiWriter.write(umlModel, jModel, out);
```


## Runtime semantics (REST/CDI/etc.)

Runtime semantics are emitted as:
- UML base elements (Dependencies/Artifacts/Packages) plus
- `java-to-xmi:runtime` and `java-to-xmi:tags` annotations on those elements.

Stereotype applications for these semantics are injected **only when stereotypes are enabled**
(e.g. CLI default, and using `XmiWriter.write(umlModel, jModel, out)`).

Consumers that don’t understand the runtime stereotypes can still read the underlying UML graph.
