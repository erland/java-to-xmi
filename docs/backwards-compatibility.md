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
