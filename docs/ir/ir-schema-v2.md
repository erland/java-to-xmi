# IR schema v2

This is the **v2** JSON schema for the `java-to-xmi` tool-neutral intermediate representation (IR).

## What changed from v1

v2 adds a **stereotype registry + references** so IR producers (React/Angular/Java/etc.) can introduce new stereotypes without requiring code changes in `java-to-xmi`.

New top-level field:
- `stereotypeDefinitions: IrStereotypeDefinition[]` *(optional)*

New per-element field (optional) on:
- `IrClassifier.stereotypeRefs`
- `IrAttribute.stereotypeRefs`
- `IrOperation.stereotypeRefs`
- `IrRelation.stereotypeRefs`

These are **in addition to** the existing legacy `stereotypes: IrStereotype[]` fields, which may be kept for display/backward compatibility.

## Key types

### IrStereotypeDefinition
Defines a stereotype once, so downstream tools can materialize UML profiles/stereotypes.

Fields:
- `id` *(string, required)*: stable reference id (e.g. `st:frontend.Component`)
- `name` *(string, required)*: UML stereotype name (e.g. `Component`)
- `qualifiedName` *(string|null, optional)*: e.g. `Frontend::Component`
- `profileName` *(string|null, optional)*: grouping profile name (e.g. `Frontend`)
- `appliesTo` *(string[], optional)*: UML metaclass names (e.g. `Class`, `Interface`, `Package`, `Operation`, `Property`, `Dependency`, …)
- `properties` *(IrStereotypePropertyDefinition[], optional)*: optional tagged value schema

### IrStereotypeRef
References a stereotype by id from an element, optionally with values.

Fields:
- `stereotypeId` *(string, required)*: must match `IrStereotypeDefinition.id`
- `values` *(object, optional)*: tagged values (reserved for future typed injection; safe to emit now)

### IrStereotypePropertyDefinition
A typed property/attribute on a stereotype.

Fields:
- `name` *(string, required)*
- `type` *(enum)*: `string | boolean | integer | number`
- `isMulti` *(boolean)*: whether the property is multi-valued

## Files

- `ir-schema-v2.json` (this schema)
- `examples/*.json` include v2 fields where relevant

Last updated: 2026-02-24
