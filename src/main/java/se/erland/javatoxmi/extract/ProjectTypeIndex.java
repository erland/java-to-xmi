package se.erland.javatoxmi.extract;

import java.util.Map;
import java.util.Set;

/**
 * Holds the precomputed project-local type index and nested-member mapping.
 */
final class ProjectTypeIndex {
    final Map<String, TypeStub> projectTypes;
    final Map<String, Map<String, String>> nestedByOuter;
    final Set<String> projectTypeQualifiedNames;

    ProjectTypeIndex(
            Map<String, TypeStub> projectTypes,
            Map<String, Map<String, String>> nestedByOuter,
            Set<String> projectTypeQualifiedNames
    ) {
        this.projectTypes = projectTypes;
        this.nestedByOuter = nestedByOuter;
        this.projectTypeQualifiedNames = projectTypeQualifiedNames;
    }
}
