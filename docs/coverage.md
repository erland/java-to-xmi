# Test coverage

This project uses **JaCoCo** for coverage.

## Run

```bash
mvn clean test
```

A coverage report is generated automatically after tests.

## View report

- HTML: `target/site/jacoco/index.html`
- XML: `target/site/jacoco/jacoco.xml`


## Notes

If you run tests on very new JDKs (e.g. Java 24+), use JaCoCo 0.8.13+.
