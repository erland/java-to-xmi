# Stereotypes, runtime semantics, and tagged values

This project represents **domain and framework/runtime semantics** in UML/XMI primarily using:

1) **Stereotype names** (e.g. `RestResource`, `FiresEvent`)  
2) **Tagged values** (key/value pairs, where keys are standardized under the prefix `runtime.`)  
3) A small amount of **XMI post-processing** to keep outputs deterministic across UML2 quirks.

The goal is that consumers (e.g. your modeller) can reliably browse architecture concerns such as:
REST endpoints, CDI events, interceptors/transactions, messaging/scheduling, Flyway migrations, and JPMS modules.

---

## How runtime semantics are encoded

### UML element annotations used by the generator

The UML builder annotates elements with two EAnnotations:

- `java-to-xmi:runtime`
  - details:
    - `stereotype` – the stereotype name to be injected/applied (e.g. `FiresEvent`)
- `java-to-xmi:tags`
  - details:
    - arbitrary key/value pairs (strings), with standardized keys under `runtime.*`

These annotations are stable and deterministic even when in-memory UML2 stereotype application is unreliable.

### XMI post-processing

During XMI writing, the existing XMI injector layer reads these annotations and injects:
- stereotype applications (e.g. `JavaAnnotations:FiresEvent`)
- tagged value structures (e.g. `JavaAnnotations:J2XTags`)

So consumers can consistently read stereotypes/tags from the resulting XMI.

---

## Standard runtime stereotype names

All runtime stereotype **names** are standardized in `IrRuntime.Stereotypes`:

| Stereotype | Applies to | Meaning |
|---|---|---|
| `RestResource` | `Class` | REST/JAX-RS or Spring MVC resource/controller |
| `RestOperation` | `Operation` | REST endpoint method |
| `FiresEvent` | `Dependency` | CDI event firing edge (publisher → event type) |
| `ObservesEvent` | `Dependency` | CDI observer edge (observer → event type) |
| `Interceptor` | `Class` | CDI/Jakarta interceptor |
| `Transactional` | `NamedElement` (typically Class/Operation) | Transaction boundary marker |
| `MessageConsumer` | `Operation` (sometimes Class) | Message listener/consumer |
| `MessageProducer` | `Operation` | Message producer |
| `ScheduledJob` | `Operation` | Scheduled/timer method |
| `FlywayMigration` | `Artifact` | Flyway migration artifact |
| `JavaModule` | `Package` | JPMS module boundary |

---

## Standard runtime tag keys

All standardized tag keys live in `IrRuntime.Tags` and begin with `runtime.`.

### REST
- `runtime.path` – normalized path (leading `/`, no duplicate slashes)
- `runtime.httpMethod` – normalized HTTP method(s), typically uppercased (comma-separated if multiple)
- `runtime.consumes` – comma-separated media types (optional)
- `runtime.produces` – comma-separated media types (optional)

### CDI events
- `runtime.qualifiers` – comma-separated qualifier annotation names (best-effort)
- `runtime.async` – `"true"` / `"false"` when async semantics exist
- `runtime.transactionPhase` – optional (best-effort)
- `runtime.priority` – optional (best-effort)

### Interceptors / transactions
- `runtime.bindings` – comma-separated interceptor binding annotations (best-effort)
- `runtime.transactional` – `"true"` when transactional applies
- `runtime.tx.propagation` – propagation/behavior (framework specific)
- `runtime.tx.readOnly` – `"true"`/`"false"` (Spring)
- `runtime.tx.isolation` – isolation level (Spring)

### Messaging
- `runtime.topic` – topic (if known)
- `runtime.queue` – queue (if known)
- `runtime.groupId` – consumer group id (Kafka/Spring)
- `runtime.concurrency` – concurrency setting (if known)

> Note: Some extractors may alternatively use a generic destination representation via tags,
> but `topic`/`queue` are the standardized keys in this repo.

### Scheduling
- `runtime.cron` – cron expression (if present)
- `runtime.fixedDelayMs` – fixed delay in milliseconds (string)
- `runtime.fixedRateMs` – fixed rate in milliseconds (string)

### Flyway migrations
- `runtime.migration.version` – version (e.g. `2.1` or `R` for repeatable)
- `runtime.migration.description` – normalized description
- `runtime.migration.type` – `sql` or `java`
- `runtime.migration.path` – relative path within the project

### JPMS modules
- `runtime.module.name` – module name
- `runtime.module.requires` – comma-separated required module names
- `runtime.module.exports` – comma-separated exported packages
- `runtime.module.opens` – comma-separated opened packages
- `runtime.module.external` – `"true"` on placeholder modules created for external requires
- `runtime.module.requiresStatic` – comma-separated modules required as `static` (optional)
- `runtime.module.requiresTransitive` – comma-separated modules required as `transitive` (optional)

---

## Determinism rules

To keep XMI stable across runs:
- Tagged values are sorted with `framework` first (legacy), then `runtime.*`, then other keys.
- Lists are represented as comma-separated strings where applicable.
- Path-like fields use `IrRuntime.normalizePath(...)`.

