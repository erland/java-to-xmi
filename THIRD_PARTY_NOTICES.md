# Third-Party Notices

This project is licensed under the MIT License (see `LICENSE`), but it depends on third‑party
libraries that are distributed under their own licenses. When redistributing this project
(including packaged CLI distributions), you must comply with those licenses as well.

This file is a practical summary for common redistribution scenarios; it is not legal advice.

## Notable dependencies

### Eclipse UML2 / Eclipse EMF (EPL-2.0)
Used for building and writing UML/XMI models.

- Group/artifacts (non-exhaustive): `org.eclipse.uml2:*`, `org.eclipse.emf:*`
- License: Eclipse Public License 2.0 (EPL-2.0)

**Typical obligations when redistributing:**
- Include the EPL-2.0 license text and required notices for these components.
- If you modify EPL-licensed code and distribute the modified version, you must make the
  corresponding source for those modifications available under EPL terms.

### JUnit 5 (EPL-2.0)
Used for tests only (scope `test`), typically not redistributed with runtime artifacts.

- Artifact: `org.junit.jupiter:junit-jupiter`
- License: EPL-2.0

### Jackson Databind (Apache-2.0)
Used for JSON (IR) serialization/deserialization.

- Artifact: `com.fasterxml.jackson.core:jackson-databind`
- License: Apache License 2.0

**Typical obligations when redistributing:**
- Include the Apache-2.0 license text.
- Preserve any NOTICE file content if the dependency provides one (if applicable).

### JavaParser (Apache-2.0)
Used for parsing Java source code.

- Artifact: `com.github.javaparser:javaparser-core`
- License: Apache License 2.0

## Where to find exact license texts
For exact license texts and any NOTICE files, see:
- The dependency JARs’ `META-INF/` entries (LICENSE/NOTICE), and/or
- The dependencies’ upstream repositories.

If you want a fully automated license report, you can integrate a Maven license plugin
(e.g., to generate a “THIRD-PARTY” report during CI builds).
