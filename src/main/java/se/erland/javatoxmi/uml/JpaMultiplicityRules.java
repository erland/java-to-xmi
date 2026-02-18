package se.erland.javatoxmi.uml;

import se.erland.javatoxmi.model.JAnnotationUse;

import java.util.List;

/** JPA-specific multiplicity baseline rules (highest precedence). */
final class JpaMultiplicityRules {

    static MutableMultiplicityState tryResolveJpaBaseline(List<JAnnotationUse> anns) {
        if (anns == null || anns.isEmpty()) return null;

        JpaInfo jpa = findJpaInfo(anns);
        if (jpa.relation == null) return null;

        MutableMultiplicityState st;
        if (jpa.toMany) {
            st = new MutableMultiplicityState(0, MultiplicityResolver.STAR);
        } else {
            st = new MutableMultiplicityState(0, 1);
        }

        st.tags.put("jpaRelation", jpa.relation);
        if (jpa.lowerIsOne) {
            st.lower = 1;
            if (jpa.lowerSource != null && !jpa.lowerSource.isBlank()) {
                st.tags.put("nullableSource", jpa.lowerSource);
            }
        }
        return st;
    }

    private static final class JpaInfo {
        String relation; // OneToMany etc
        boolean toMany;
        boolean lowerIsOne;
        String lowerSource;
    }

    private static JpaInfo findJpaInfo(List<JAnnotationUse> anns) {
        JpaInfo j = new JpaInfo();

        // First: relationship type
        for (JAnnotationUse a : anns) {
            String n = annName(a);
            if (isAny(n, "OneToOne", "ManyToOne", "OneToMany", "ManyToMany")) {
                j.relation = AnnotationValueUtil.stripPkg(n);
                j.toMany = isAny(n, "OneToMany", "ManyToMany");

                // optional=false on to-one relations
                if (!j.toMany) {
                    String opt = a.values.get("optional");
                    if (opt != null && "false".equalsIgnoreCase(opt.trim())) {
                        j.lowerIsOne = true;
                        j.lowerSource = j.relation + ".optional=false";
                    }
                }
            }
        }

        // Second: nullable=false sources (also for basic attributes)
        for (JAnnotationUse a : anns) {
            String n = annName(a);
            if (isAny(n, "JoinColumn", "Column")) {
                String nullable = a.values.get("nullable");
                if (nullable != null && "false".equalsIgnoreCase(nullable.trim())) {
                    j.lowerIsOne = true;
                    j.lowerSource = AnnotationValueUtil.stripPkg(n) + ".nullable=false";
                }
            }
            if (isAny(n, "Basic")) {
                String optional = a.values.get("optional");
                if (optional != null && "false".equalsIgnoreCase(optional.trim())) {
                    j.lowerIsOne = true;
                    j.lowerSource = "Basic.optional=false";
                }
            }
        }

        return j;
    }

    private static String annName(JAnnotationUse a) {
        if (a == null) return "";
        if (a.qualifiedName != null && !a.qualifiedName.isBlank()) return a.qualifiedName;
        return a.simpleName == null ? "" : a.simpleName;
    }

    private static boolean isAny(String annName, String... simpleNames) {
        String s = AnnotationValueUtil.stripPkg(annName);
        for (String n : simpleNames) {
            if (n.equals(s)) return true;
        }
        return false;
    }
}
