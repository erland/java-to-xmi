package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.model.JAnnotationUse;

import java.util.List;

/** Validation/Nullability multiplicity tightening rules. */
final class ValidationMultiplicityRules {

    static void tightenWithValidation(MutableMultiplicityState st, List<JAnnotationUse> anns) {
        if (st == null) return;
        if (anns == null || anns.isEmpty()) return;

        ValidationInfo v = findValidationInfo(anns);

        if (v.lowerAtLeastOne) {
            st.lower = Math.max(st.lower, 1);
        }
        if (v.sizeMin != null) {
            st.lower = Math.max(st.lower, v.sizeMin);
        }
        if (v.sizeMax != null) {
            if (st.upper == MultiplicityResolver.STAR) {
                st.upper = v.sizeMax;
            } else {
                st.upper = Math.min(st.upper, v.sizeMax);
            }
        }

        if (v.sizeMin != null) st.tags.put("validationSizeMin", String.valueOf(v.sizeMin));
        if (v.sizeMax != null) st.tags.put("validationSizeMax", String.valueOf(v.sizeMax));
    }

    private static final class ValidationInfo {
        boolean lowerAtLeastOne;
        Integer sizeMin;
        Integer sizeMax;
    }

    private static ValidationInfo findValidationInfo(List<JAnnotationUse> anns) {
        ValidationInfo v = new ValidationInfo();
        for (JAnnotationUse a : anns) {
            String n = annName(a);
            if (isAny(n, "NotNull", "Nonnull")) {
                v.lowerAtLeastOne = true;
            }
            if (isAny(n, "NotEmpty", "NotBlank")) {
                v.lowerAtLeastOne = true;
            }
            if (isAny(n, "Size")) {
                Integer min = AnnotationValueUtil.parseInt(a.values.get("min"));
                Integer max = AnnotationValueUtil.parseInt(a.values.get("max"));
                if (min != null) v.sizeMin = min;
                if (max != null) v.sizeMax = max;
            }
        }
        return v;
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
