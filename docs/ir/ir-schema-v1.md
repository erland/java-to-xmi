# IR schema v1 (minimal, extensible)

This IR is designed as a **cross-language interchange format** for:
- Java structural models (your current extractor)
- TypeScript / JavaScript structural models
- React and Angular *framework graphs* via stereotypes/tags + a small set of relation kinds

The guiding principle is: **keep the core small** and push language/framework specifics into
`stereotypes[]` and `taggedValues[]`.

## Standardized runtime semantics (recommended)

This IR intentionally keeps the core minimal. For **framework/runtime semantics** (REST, CDI, transactions,
messaging, scheduling, migrations, JPMS modules), the recommended convention in this repo is:

- Use stereotype names from `IrRuntime.Stereotypes` (e.g. `RestOperation`, `FiresEvent`)
- Use tagged value keys from `IrRuntime.Tags` (all prefixed with `runtime.`)

These conventions are **optional and additive**: producers that don’t emit them remain compatible.

Common examples:
- REST endpoint method: add stereotype `RestOperation` and tags:
  - `runtime.path`, `runtime.httpMethod`, optionally `runtime.consumes`/`runtime.produces`
- CDI wiring: represent `publisher -> eventType` as a relation with stereotype `FiresEvent`
- Flyway migrations: represent each migration as an `Artifact`-like classifier or as tagged model metadata
  (this repo emits them as UML Artifacts during XMI generation)

See `docs/stereotypes.md` for the current list of standardized stereotype names and tag keys.



## Root: `IrModel`
Fields:
- `schemaVersion` (string, required) – currently `"1.0"`
- `packages[]` (optional)
- `classifiers[]` (required)
- `relations[]` (optional)
- `taggedValues[]` (optional) – model-wide metadata

## Classifiers
`IrClassifier.kind` supports:
- `CLASS`, `INTERFACE`, `ENUM`, `RECORD`
- `TYPE_ALIAS` (TS), `FUNCTION` (top-level function)
- `COMPONENT` (React/Angular), `SERVICE` (Angular), `MODULE` (Angular/TS module)

Add framework meaning using stereotypes/tags, e.g.:
- React: stereotype `ReactComponent`, tags like `react.componentKind=function|class`
- Angular: stereotypes `Component` / `Injectable` / `NgModule`, tags like `angular.selector`

## Relations
Core UML-ish relations:
- `GENERALIZATION`, `REALIZATION`, `ASSOCIATION`, `DEPENDENCY`, `COMPOSITION`, `AGGREGATION`

Framework graph relations (optional):
- `RENDER` (React component composition graph)
- `DI` (Angular dependency injection graph)
- `TEMPLATE_USES` (Angular template references)
- `ROUTE_TO` (Angular router edges)

## Types
`IrTypeRef` supports:
- `NAMED`, `PRIMITIVE`, `GENERIC`, `ARRAY`, `UNION`, `INTERSECTION`, `UNKNOWN`

For any extra language detail (e.g. TS `optional`, `readonly`, mapped types), attach `taggedValues[]`.

## IDs
IDs are string values defined by the extractor. They should be:
- stable/deterministic for a given codebase version
- unique within the model

The emitter must treat IDs as opaque identifiers.

---

## Framework conventions
See: `docs/ir/framework-conventions.md` for recommended React/Angular stereotypes, tags and relation semantics.

