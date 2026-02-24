# Contract for third‑party IR producers (stereotypes)

This document describes how **any IR producer** can emit stereotypes in a way that `java-to-xmi` will include them in the generated UML/XMI **without requiring changes** to `java-to-xmi`.

It focuses on the *IR → UML stereotype* pipeline introduced in this repo:
- IR provides a **stereotype registry** (`stereotypeDefinitions`)
- IR elements reference stereotypes via **stable ids** (`stereotypeRefs`)
- `java-to-xmi` **materializes** stereotypes into a UML profile and marks elements with `java-to-xmi:runtime` so the existing **XMI post‑processor** injects stereotype applications.

---

## 1. Minimum requirements

### 1.1 Provide a stereotype registry (recommended)
At the top level of `IrModel`, include:

- `stereotypeDefinitions: IrStereotypeDefinition[]`

Each `IrStereotypeDefinition` must have:

- `id` (string, required): stable identifier used by references (examples: `st:frontend.Component`, `st:angular.Injectable`)
- `name` (string, required): the UML stereotype name to show in UML tools (example: `Component`)
- `profileName` (string, required): the UML profile name that groups the stereotype (example: `Frontend`)
- `qualifiedName` (string, optional): informational only; may be used by tooling
- `appliesTo` (string[], required): list of UML metaclass names the stereotype can apply to
  - Supported metaclass names are those used by the existing metaclass extension helper; typical values:
    - `Class`, `Interface`, `Enumeration`, `Package`, `Operation`, `Property`, `Parameter`, `Dependency`, `NamedElement`
- `properties` (`IrStereotypePropertyDefinition[]`, optional): typed stereotype attributes

If you do not provide a registry, `java-to-xmi` can still mark runtime stereotypes **only if it can resolve names** from elsewhere. For predictability, always include the registry.

### 1.2 Reference stereotypes from elements
Any IR element may include:

- `stereotypeRefs: IrStereotypeRef[]`

Each `IrStereotypeRef` must have:

- `stereotypeId` (string, required): must match a definition `id`
- `values` (object/map, optional): key/value pairs for stereotype properties
  - Currently, values are **not required** for injection; they are reserved for future typed property injection.

Elements that can carry `stereotypeRefs` today:
- `IrClassifier`
- `IrAttribute`
- `IrOperation`
- `IrRelation`

---

## 2. Determinism rules (important)

To keep output stable across runs (golden tests):
- Use **stable ids** for:
  - elements (`id` fields)
  - stereotype definitions (`id`)
- Keep lists deterministic:
  - `stereotypeDefinitions` order is normalized, but you should still emit in stable order when possible
  - `stereotypeRefs` can be emitted in any order; they will be sorted by `stereotypeId` before marking
- Avoid random GUIDs in IR.

`java-to-xmi` will:
- normalize IR (`IrNormalizer`)
- sort warnings deterministically
- sort runtime stereotype names before writing runtime markers

---

## 3. How java-to-xmi consumes this contract

### 3.1 Profile / stereotype materialization
If `includeStereotypes=true` and IR contains stereotype info:
- `IrStereotypeProfileBuilder` creates UML stereotypes for each `stereotypeDefinitions` entry.
- Stereotypes are created in the `JavaAnnotations`-style profile infrastructure used by the emitter.

### 3.2 Applying stereotypes (post‑processing friendly)
`IrStereotypeApplicator` does **not** use UML2 `applyStereotype(...)`:
- It resolves the UML element (primarily by `java-to-xmi:id`, with a fallback by classifier name)
- It writes a runtime marker annotation:
  - `EAnnotation` source: `java-to-xmi:runtime`
  - detail key: `stereotype`
  - value: comma-separated stereotype names (sorted)

Then `StereotypeApplicationInjector` injects corresponding stereotype application XML into XMI.

---

## 4. Validation and warnings (non‑fatal)

Emission will continue even if there are issues, but `XmiEmitter.Result.warnings` may include:

- `IR_STEREO_TARGET_NOT_FOUND` – stereotype refs could not be attached (UML element not resolved)
- `IR_STEREO_UNKNOWN_ID` – ref points at unknown `stereotypeId`
- `IR_STEREO_UNKNOWN_APPLIES_TO` – appliesTo metaclass not recognized; extension skipped
- `IR_STEREO_UNKNOWN_PROPERTY_TYPE` – property type not recognized; property skipped

You should treat warnings as contract violations to fix in the IR producer.

---

## 5. Example (minimal)

```json
{
  "schemaVersion": "1.0",
  "stereotypeDefinitions": [
    {
      "id": "st:frontend.Component",
      "name": "Component",
      "qualifiedName": null,
      "profileName": "Frontend",
      "appliesTo": ["Class"],
      "properties": []
    }
  ],
  "classifiers": [
    {
      "id": "c:AppComponent",
      "name": "AppComponent",
      "qualifiedName": "AppComponent",
      "packageId": null,
      "kind": "COMPONENT",
      "visibility": "PUBLIC",
      "attributes": [],
      "operations": [],
      "stereotypes": [],
      "stereotypeRefs": [
        { "stereotypeId": "st:frontend.Component", "values": {} }
      ],
      "taggedValues": [],
      "source": { "file": "src/app/app.component.ts", "line": 1, "col": 1 }
    }
  ],
  "relations": []
}
```

---

## 6. Forward compatibility notes

The contract is designed so new stereotypes can be added **only in the IR producer**:
- Add a new `IrStereotypeDefinition` (new `id`, `name`, etc.)
- Add `stereotypeRefs` pointing to the new id

No change in `java-to-xmi` should be needed as long as:
- the appliesTo metaclass is supported
- property types are among the supported set

