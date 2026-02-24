# Stereotype handling fallback behavior

This repo supports two stereotype channels:

1. **Legacy / hardcoded mappings**
   - Java annotations → `JavaAnnotations` profile stereotypes
   - Tool tags (`J2XTags`) and runtime semantics (`java-to-xmi:runtime`)
   - These are emitted through existing UML build + XMI post-processing injectors.

2. **IR registry + references (new)**
   - IR may include `stereotypeDefinitions` and per-element `stereotypeRefs`.
   - When present, java-to-xmi materializes the stereotypes into the `JavaAnnotations` profile and marks elements with runtime stereotypes so they are injected into XMI.

## Fallback rule

- If the IR **does not** contain `stereotypeDefinitions` and **no** element contains `stereotypeRefs`,
  then the IR-driven stereotype path is skipped entirely, and only the legacy stereotype channels apply.

This keeps the pipeline stable for older IR fixtures and for Java-only extraction flows.
