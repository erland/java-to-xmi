# Framework conventions (IR v1)

This document defines **recommended, schema-stable conventions** for representing **React** and **Angular** models in the IR.

The IR core stays small; framework details are expressed via:
- `stereotypes[]` (classification)
- `taggedValues[]` (metadata)
- a small set of framework relation kinds (`RENDER`, `DI`, `TEMPLATE_USES`, `ROUTE_TO`)

These conventions are **recommendations**. Emitters should treat unknown stereotypes/tags as opaque metadata.

---

## Common conventions

### Tagged value key namespace
Use a dotted prefix:
- `framework` (shared)
- `react.*`
- `angular.*`

### Source mapping
When possible, populate:

- `IrClassifier.source` (file/line/col)
- `IrRelation.source` (file/line/col)

---

## React conventions

### Classifier kinds
- Use `IrClassifier.kind = COMPONENT` for React components.

### Stereotypes
- `ReactComponent`

### Tagged values
Minimum recommended tags:
- `framework = react`
- `react.componentKind = function | class`

Optional tags (useful for analysis, still schema-stable):
- `react.hooks = useState,useEffect,...` (comma-separated string)
- `react.memo = true|false`
- `react.forwardRef = true|false`

### Relations
- `RENDER`: component composition graph
  - `sourceId` = rendering component
  - `targetId` = rendered/used component

Recommended tag:
- `origin = jsx` (or `origin = createElement`)

---

## Angular conventions

### Classifier kinds
- Use `IrClassifier.kind = COMPONENT` for `@Component` classes.
- Use `IrClassifier.kind = SERVICE` for `@Injectable` classes (services).
- Use `IrClassifier.kind = MODULE` for `@NgModule` classes.

### Stereotypes
- `Component`
- `Injectable`
- `NgModule`

### Tagged values
Minimum recommended tags:
- `framework = angular`

For components:
- `angular.selector = app-root`
- `angular.templateUrl = src/app/app.component.html` (optional)

For modules:
- `angular.moduleType = root|feature|shared` (optional)

### Relations
- `DI`: dependency injection edge
  - `sourceId` = consumer (component/service)
  - `targetId` = injected service
  - recommended tag: `origin = constructor`

- `TEMPLATE_USES`: template references
  - `sourceId` = component
  - `targetId` = referenced component/directive/pipe
  - recommended tag: `origin = template`

- `ROUTE_TO`: router graph
  - `sourceId` = module or router config owner
  - `targetId` = component
  - recommended tag: `origin = router`
