# Current stereotype handling in java-to-xmi (as of this snapshot)

This document summarizes **where stereotypes are built/applied/injected** in the current `java-to-xmi` codebase, based on the provided `digest_java-to-xmi.zip`.

---

## High-level approach used today

java-to-xmi currently emits stereotype-related information through **two complementary mechanisms**:

1) **UML Profile construction (JavaAnnotations)**  
   - A UML Profile named **`JavaAnnotations`** is created in the UML model.
   - For each Java annotation found on extracted Java types, a **UML Stereotype** is ensured in that profile.
   - The stereotype is extended to a UML metaclass target (`Class`, `Interface`, `Enumeration`).
   - Annotation member values become **String attributes** on the stereotype (schema-like).

2) **XMI post-processing injection (stereotype applications)**  
   - Instead of relying solely on `element.applyStereotype(...)` at UML build time, the XMI writer injects **UML2-style stereotype application XML fragments** into the serialized XMI.
   - This is used for:
     - Java annotation stereotype applications (on classifiers)
     - Tool-tag stereotype applications (for `java-to-xmi:tags`)
     - Runtime semantic stereotype applications (for `java-to-xmi:runtime` markers)

This design is intentionally conservative and deterministic: IDs, ordering, and injected fragments are built in stable order.

---

## Where stereotypes are handled (main files)

### A) UML builder entry point
**File:** `java-to-xmi-emitter/src/main/java/info/isaksson/erland/javatoxmi/uml/UmlBuilder.java`

**Key responsibilities:**
- Defines annotation sources used throughout:
  - `java-to-xmi:id`
  - `java-to-xmi:tags`
  - `java-to-xmi:runtime` (key `stereotype`)
- Controls whether stereotypes are included:
  - `build(..., boolean includeStereotypes, ...)`
- Builds runtime profile + Java annotation profile when `includeStereotypes` is true:
  - `runtimeProfileApplicator.applyRuntimeProfile(ctx);`
  - `profileApplicator.applyJavaAnnotationProfile(ctx, types);`
- Ensures deterministic IDs for all elements:
  - `UmlBuilderSupport.ensureAllElementsHaveId(model);`

**Relevant code location:** near the end of `build(...)`, section `// Profile + stereotypes`.

---

### B) Java annotation profile construction
#### 1) Profile applicator (coordinates creation based on extracted Java annotations)
**File:** `java-to-xmi-emitter/src/main/java/info/isaksson/erland/javatoxmi/uml/UmlProfileApplicator.java`

**What it does:**
- Iterates extracted `JType` annotations and ensures:
  - stereotypes exist for each annotation (`ensureStereotype`)
  - the stereotype extends the correct UML metaclass (`ensureMetaclassExtension`)
  - annotation value keys exist as String attributes on stereotype (`ensureStringAttribute`)
- Performs deterministic ordering:
  - annotation list is sorted by qualified/simple name
  - value keys are sorted

#### 2) Profile builder façade
**File:** `java-to-xmi-emitter/src/main/java/info/isaksson/erland/javatoxmi/uml/JavaAnnotationProfileBuilder.java`

**What it defines:**
- Profile name + URI:
  - `PROFILE_NAME = "JavaAnnotations"`
  - `PROFILE_URI = "http://java-to-xmi/schemas/JavaAnnotations/1.0"`
- Meta annotation source for stereotype qualified names:
  - `JAVA_ANN_SOURCE = "java-to-xmi:java"`
- Tool tags stereotype:
  - `TOOL_TAGS_STEREOTYPE = "J2XTags"`
  - `TOOL_TAG_KEYS = [...]` (stable list of keys)
- Delegates to:
  - `JavaAnnotationProfileStructureBuilder` (stereotypes + attributes)
  - `JavaAnnotationMetaclassExtensionHelper` (extensions to UML metaclasses)
- Provides `annotateIdIfMissing(...)` helper used to stabilize IDs on UML elements.

#### 3) Structure builder (creates stereotypes/attributes and handles collisions)
**File:** `java-to-xmi-emitter/src/main/java/info/isaksson/erland/javatoxmi/uml/JavaAnnotationProfileStructureBuilder.java`

**What it does:**
- Ensures profile exists under the UML model (reuses if present).
- Ensures a shared `String` primitive exists (created under model `_primitives` if missing).
- Ensures **tool tags stereotype** `J2XTags` exists and has all expected String attributes (from `TOOL_TAG_KEYS`).
- Ensures stereotypes for annotations:
  - Uses sanitized UML-safe names
  - Handles collisions deterministically when simple name matches but qualified name differs
- Stores annotation qualified name as EAnnotation:
  - source `java-to-xmi:java`, detail key `qualifiedName`

---

### C) Runtime semantics stereotypes
**File:** `java-to-xmi-emitter/src/main/java/info/isaksson/erland/javatoxmi/uml/UmlRuntimeProfileApplicator.java`

**What it does (by design):**
- Ensures a stable set of runtime semantics stereotypes exist in the `JavaAnnotations` profile.
- Focus is on availability + applicability (extensions), not rich attributes.
- Additional metadata is persisted via tool tags (`J2XTags`) rather than typed stereotype attributes.

Related emission:
- Runtime relations as stereotyped dependencies:
  - `java-to-xmi-emitter/src/main/java/info/isaksson/erland/javatoxmi/uml/UmlRuntimeRelationEmitter.java`
- Runtime stereotype markers:
  - `java-to-xmi-emitter/src/main/java/info/isaksson/erland/javatoxmi/uml/UmlBuilderSupport.java` (`annotateRuntimeStereotype`)

---

### D) IR → Java model adapter (stereotype-related notes)
**File:** `java-to-xmi-emitter/src/main/java/info/isaksson/erland/javatoxmi/emitter/IrToJModelAdapter.java`

**What it indicates:**
- Contains comments/logic suggesting some **framework stereotypes/tags are not mapped to Java annotations** (“best-effort”).
- This matters when ingesting IR from frontend-to-ir: those stereotypes may not reach the Java annotation pipeline.

---

### E) XMI writer and stereotype application injection (critical)
#### 1) Stereotype application injector
**File:** `java-to-xmi-emitter/src/main/java/info/isaksson/erland/javatoxmi/xmi/StereotypeApplicationInjector.java`

**What it does:**
- Builds deterministic UML2-style stereotype application XML fragments and injects them into emitted XMI.
- Handles three categories:
  1) Java annotation stereotype applications (classifiers)
  2) Tool tags (`J2XTags`) for elements carrying `java-to-xmi:tags`
  3) Runtime stereotypes for elements carrying `java-to-xmi:runtime` (detail key `stereotype`)

**Key behaviors:**
- Indexes stereotypes from the `JavaAnnotations` profile using:
  - qualified name from `java-to-xmi:java` `qualifiedName`
  - fallback key `#<stereotypeName>` for simple matches
- Uses stable ids based on `java-to-xmi:id` annotations and `UmlIdStrategy`.
- Sorts injected applications for deterministic output.
- When injecting tool tags, emits only the predefined keys from `JavaAnnotationProfileBuilder.TOOL_TAG_KEYS`.

#### 2) XMI writer entry
**File:** `java-to-xmi-emitter/src/main/java/info/isaksson/erland/javatoxmi/xmi/XmiWriter.java`

**What to look for:**
- Where it calls profile/stereotype injectors:
  - `ProfileApplicationInjector`
  - `StereotypeApplicationInjector`
  - `StereotypeXmiInjector`

---

## What stereotypes are supported today (by intent)

### 1) Java annotation stereotypes (dynamic, based on annotations found on types)
- For each Java annotation used on a `JType`, a stereotype is created in `JavaAnnotations` profile:
  - stereotype name derived from annotation simple name (sanitized)
  - qualified name stored in `java-to-xmi:java` EAnnotation
- Attributes:
  - each annotation member key becomes a **String attribute** on the stereotype
- Applicability is currently limited to:
  - `Class`, `Interface`, `Enumeration` (see `JavaAnnotationProfileBuilder.MetaclassTarget`)

### 2) Tool metadata stereotype: `J2XTags`
- Always ensured when stereotypes are enabled.
- Extends `NamedElement` (broad applicability).
- Has a stable list of String attributes (`TOOL_TAG_KEYS`).

### 3) Runtime semantics stereotypes
- Ensured by `UmlRuntimeProfileApplicator`.
- Applied via runtime markers + XMI injection.

---

## Tests that characterize stereotype output
Located under `java-to-xmi-emitter/src/test/java/...` including:
- `uml/JavaAnnotationProfileBuilderToolTagsTest.java` (tool-tags stereotype structure + extension)
- `xmi/XmiWriterStereotypeInjectionTest.java` (annotation stereotype applications in XMI)
- `xmi/XmiWriterToolTagsInjectionTest.java` (tool tags injection)
- `xmi/XmiWriterProfileApplicationWithoutTypeAnnotationsTest.java` (profile application for tags)
- `uml/UmlRuntimeRelationEmitterTest.java` (runtime stereotypes + injection)

Golden IR fixtures include:
- `java-to-xmi-emitter/src/test/resources/ir/golden/angular-mini.json`
- `java-to-xmi-emitter/src/test/resources/ir/golden/react-mini.json`

---

## Summary: why frontend-to-ir stereotypes may not show up today
In the current design, stereotypes typically become visible in XMI when they are:
- mapped into **Java annotations** on `JType` (-> JavaAnnotations profile + injected applications), or
- represented as **tool tags** (`java-to-xmi:tags`) or **runtime stereotype markers** (`java-to-xmi:runtime`) that the XMI injector recognizes.

If frontend-to-ir emits stereotypes that are not mapped into those channels, java-to-xmi will not automatically materialize/apply them today—hence the need for the upcoming **IR stereotype registry + refs** approach.
