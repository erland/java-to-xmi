# Downloadable step-by-step plan: Make **java-to-xmi** support *new stereotypes from IR without code changes*

## Goal
Modify the **java-to-xmi** project so it can consume IR files that define **new stereotypes** (and optional tagged values) and generate UML/XMI that **includes those stereotypes** automatically—without requiring any future changes to java-to-xmi whenever a new stereotype is introduced by other IR producers (e.g., frontend-to-ir).

## Starting state (assumed)
- Repo: `digest_java-to-xmi.zip` (a multi-module Maven project).
- java-to-xmi already supports:
  - parsing Java into an internal model / IR-like representation,
  - generating UML (via Eclipse UML2),
  - exporting XMI,
  - applying **some** stereotypes through existing mapping logic (likely hardcoded or limited).
- There exists an IR schema/module in this repo (often a module like `java-to-xmi-ir`) that defines types used to read/write IR JSON.

> If your actual module names differ, the steps still apply; map them to your existing IR types module and UML/XMI emitter module.

## Core idea
Treat stereotypes as **data** in the IR rather than **code** in java-to-xmi:
1. IR contains a **Stereotype Registry** (definitions: profile, name, applicability, optional property schema).
2. Each IR element references stereotypes by **stable id**.
3. java-to-xmi:
   - builds (or merges) UML Profiles + Stereotypes from the registry,
   - applies stereotype applications to UML elements based on references,
   - sets tagged values deterministically.

## Assumptions (explicit)
- The IR consumer in java-to-xmi can be extended to parse additional fields without breaking existing flows.
- java-to-xmi uses Eclipse UML2 APIs (or similar) that can:
  - create Profiles and Stereotypes,
  - create Extension relationships to UML metaclasses,
  - apply stereotypes and set values.
- Deterministic output is important (your existing tests likely rely on it).

## Out of scope (for this plan)
- Adding a UI in the modeller for authoring stereotypes.
- Changing your XMI import pipeline (pwa-modeller) unless it fails to read the resulting stereotype applications.

---

## Step 1 — Locate and document current stereotype handling in java-to-xmi
### Deliverables
- A short note in `docs/stereotypes-java-to-xmi-current.md` describing:
  - where stereotypes are mapped/applied today,
  - which stereotypes are supported,
  - where XMI is written.

### What to do
1. Search for keywords:
   - `stereotype`, `profile`, `applyStereotype`, `UMLPackage`, `EAnnotation`, `Extension`, `Profile`
2. Identify:
   - the module that generates UML model objects (often `*-core` or `*-emitter`)
   - the module that reads IR JSON (if present)

### Verification
- You can list occurrences:
  - `grep -R "stereotype" -n .`
  - `grep -R "Profile" -n .`

---

## Step 2 — Extend the IR schema to support a stereotype registry (backward compatible)
### Deliverables
- Updated IR schema/types:
  - `IrStereotypeDefinition`
  - `IrStereotypePropertyDefinition` (optional but recommended)
  - `IrProfileDefinition` (optional, if you want explicit profiles)
  - `IrStereotypeRef` (ref + tagged values)
- Updated JSON parsing/serialization (Jackson/Gson/etc.) if needed.
- A migration strategy note in `docs/ir-stereotype-registry.md`.

### Recommended IR shape (minimal viable)
Add to the IR root model:
- `stereotypeDefinitions: IrStereotypeDefinition[]`

And on each element:
- `stereotypes: IrStereotypeRef[]`

**IrStereotypeDefinition (minimal)**
- `id: string` (stable, unique, e.g. `"st:frontend.Component"`)
- `name: string` (display name)
- `profileName?: string` (e.g. `"Frontend"`)
- `appliesTo: string[]` (UML metaclass names, e.g. `["Class","Interface","Package","Operation","Property"]`)
- `properties?: IrStereotypePropertyDefinition[]` (optional schema for tagged values)

**IrStereotypeRef**
- `stereotypeId: string`
- `values?: Record<string, any>` (tagged values for that stereotype application)

### Backward compatibility
- Keep existing fields untouched.
- If `stereotypeDefinitions` is absent, java-to-xmi should still behave as before (hardcoded mapping / ignore unknown).

### Verification
- Add a unit test that deserializes an IR JSON containing `stereotypeDefinitions` and element `stereotypes` and asserts it round-trips.

---

## Step 3 — Add a UML “Profile Builder” that materializes stereotypes from IR
### Deliverables
- New component (example names; adapt to your structure):
  - `.../uml/ProfileBuilder.java`
  - `.../uml/StereotypeRegistryApplier.java`
- The builder can:
  - create/reuse UML `Profile` objects,
  - create `Stereotype` objects,
  - create `Extension` to each metaclass in `appliesTo`,
  - define stereotype properties (optional but recommended),
  - apply profile(s) to the UML model.

### What to do (high-level)
1. Group `IrStereotypeDefinition` by `profileName` (default `"IRProfile"` if missing).
2. For each profile:
   - create `Profile profile = UMLFactory.eINSTANCE.createProfile()`
   - set `profile.setName(profileName)`
3. For each stereotype definition:
   - `Stereotype st = profile.createOwnedStereotype(name, false)`
   - for each `appliesTo` metaclass:
     - locate the UML metaclass in `UMLPackage.eINSTANCE` (e.g., `UMLPackage.Literals.CLASS`)
     - create an `Extension` from stereotype to metaclass
4. For each property definition:
   - create an attribute on the stereotype (typed `String`, `Boolean`, `Integer`, etc.)
   - handle multi-valued with upper bound `-1` if needed
5. Apply the profile(s) to the model:
   - `profile.define()` (UML2 step)
   - `model.applyProfile(profile)`

### Determinism rules
- Sort profiles by `profileName`
- Sort stereotypes by `id` (or `name` + `profileName`)
- Sort `appliesTo`
- Sort properties by name

### Verification
- Unit test: given `stereotypeDefinitions`, build UML model and assert:
  - profile exists and has stereotypes,
  - stereotypes extend the expected metaclass(es),
  - profile is applied to model.

---

## Step 4 — Apply stereotypes to UML elements based on IR references
### Deliverables
- Updated UML generation pipeline to:
  - call ProfileBuilder before element emission completes,
  - apply `IrStereotypeRef` to each created UML element.

### What to do
1. When you create a UML element (e.g., `Class`, `Package`, `Operation`):
   - after creating it and naming it, apply stereotypes:
     - locate `Stereotype` by registry id:
       - map `id -> (Profile,Stereotype)` in the builder
     - verify applicability:
       - the UML element’s EClass should match one of the stereotype’s extensions
     - apply:
       - `umlElement.applyStereotype(stereotype)`
2. Tagged values:
   - for each key in `values`, set:
     - `umlElement.setValue(stereotype, propertyName, value)`
   - for multi-valued, set list.
3. If stereotype is missing from registry:
   - fallback (Step 5), or warn and skip deterministically.

### Verification
- Integration test: IR fixture where:
  - a class has stereotypes + values
  - output XMI contains stereotype application blocks (profile + application)
- If you maintain golden XMI snapshots, add one.

---

## Step 5 — Keep existing hardcoded mapping as fallback (optional but recommended)
### Deliverables
- A compatibility layer:
  - if `stereotypeDefinitions` is missing (old IR), keep current behavior.
  - if present, prefer registry-based behavior.

### What to do
- Add a feature switch in code:
  - `boolean registryMode = irModel.getStereotypeDefinitions() != null && !empty`
- In registryMode:
  - do NOT use the old mapping (unless you want to merge).
- In legacy mode:
  - keep existing mapping logic untouched.

### Verification
- Existing tests should continue to pass.
- Add a test that old IR still generates the same XMI as before.

---

## Step 6 — Add strict, deterministic warning reporting (non-fatal)
### Deliverables
- A “warnings collector” that records:
  - stereotype ref references unknown stereotype id
  - stereotype not applicable to element type
  - tagged values contain unknown property keys (if schema used)
- Optional: emit warnings into a report JSON for debugging.

### Determinism rules
- Sort warnings before output (or keep stable insertion order based on sorted traversal).

### Verification
- Unit test: invalid stereotype ref produces warning and still outputs stable XMI.

---

## Step 7 — Golden tests: ensure byte-for-byte stability (if this matters today)
### Deliverables
- One new fixture IR file:
  - `src/test/resources/ir/with-stereotype-registry.json`
- One golden XMI output:
  - `src/test/resources/golden/with-stereotypes.xmi`
- Test verifies:
  - deterministic ordering (profiles, stereotypes, applications)
  - stable GUID/IDs if you use them

### Verification
- `mvn test`

---

## Step 8 — Document the contract for third-party IR producers
### Deliverables
- `docs/ir-stereotypes-contract.md` explaining:
  - how to define new stereotypes in `stereotypeDefinitions`
  - how to reference them on elements
  - supported `appliesTo` metaclass names
  - supported property types for tagged values
  - determinism expectations

### Verification
- Manual: ensure docs include at least one end-to-end example JSON snippet.

---

## Verification commands (typical)
> Adjust modules/commands to match your build.
- `mvn -q test`
- `mvn -q -DskipTests package`
- Run CLI on sample IR:
  - `java -jar <cli-jar> --in sample.ir.json --out out.xmi`

---

## Expected outcome
After implementing this plan:
- Any IR producer can add a new stereotype by:
  1) adding a new entry in `stereotypeDefinitions`
  2) referencing it by `stereotypeId` on elements
- java-to-xmi will automatically:
  - define the stereotype in a UML Profile,
  - apply it in the UML model,
  - export it in XMI,
  - without java-to-xmi code changes for that stereotype.
