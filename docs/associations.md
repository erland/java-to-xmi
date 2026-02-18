# Associations, attribute-only fields, and composition

This tool can export Java fields either as:

1) **Attribute-only** (UML Property only), or
2) **Association-backed** (UML Property + UML Association).

The decision is controlled by `--associations`.

## Association policies (`--associations`)

* `resolved` (default): create an association when the field's (target) type resolves to an in-model classifier.
* `jpa`: create associations only when a JPA relationship annotation is present on the field.
* `smart`: JPA relationships always create associations, value-like types remain attribute-only, otherwise fall back to `resolved`.
* `none`: never create associations (attributes only).

### JPA attribute-like annotations

The following JPA annotations force **attribute-only** representation (no association line):

* `@Embedded`
* `@ElementCollection`

These often represent containment/value semantics rather than a navigable entity relationship.

## Composition (aggregation kind)

When an association is created, the field end aggregation is normally `none`.

The exporter currently infers **composition** (`composite`) conservatively:

* `@OneToMany(orphanRemoval=true)` → composite
* `@OneToOne(orphanRemoval=true)` → composite

This maps to a filled diamond in many UML tools.

## Traceability tags

When enabled, the exporter adds tool tags (via the `J2XTags` stereotype) to explain decisions, for example:

* `relationSource=jpa|embedded|elementCollection|resolved|valueBlacklist`
* `aggregation=composite|none`
* `jpaOrphanRemoval=true|false`

These tags are best-effort metadata and may not be shown by all UML tools.
