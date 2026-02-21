package info.isaksson.erland.javatoxmi.ir;

import java.util.List;

/**
 * Schema-stable conventions for representing framework-specific semantics in IR v1.
 *
 * <p>This class is intentionally small: it provides string constants and a couple of helpers
 * for building stereotypes/tagged values. Emitters should treat these as recommendations
 * (unknown values must still be handled gracefully).</p>
 */
public final class FrameworkConventions {

    private FrameworkConventions() {}

    public static final String TAG_FRAMEWORK = "framework";

    public static IrTaggedValue tv(String key, String value) {
        return new IrTaggedValue(key, value);
    }

    public static IrStereotype st(String name) {
        return new IrStereotype(name, null);
    }

    public static final class React {
        private React() {}

        public static final String FRAMEWORK = "react";

        public static final String STEREOTYPE_COMPONENT = "ReactComponent";

        public static final String TAG_COMPONENT_KIND = "react.componentKind"; // function|class
        public static final String TAG_HOOKS = "react.hooks"; // comma-separated
        public static final String TAG_MEMO = "react.memo"; // true|false
        public static final String TAG_FORWARD_REF = "react.forwardRef"; // true|false

        public static List<IrTaggedValue> baseTags(String componentKind) {
            return List.of(
                    tv(TAG_FRAMEWORK, FRAMEWORK),
                    tv(TAG_COMPONENT_KIND, componentKind)
            );
        }
    }

    public static final class Angular {
        private Angular() {}

        public static final String FRAMEWORK = "angular";

        public static final String STEREOTYPE_COMPONENT = "Component";
        public static final String STEREOTYPE_INJECTABLE = "Injectable";
        public static final String STEREOTYPE_NG_MODULE = "NgModule";

        public static final String TAG_SELECTOR = "angular.selector";
        public static final String TAG_TEMPLATE_URL = "angular.templateUrl";
        public static final String TAG_MODULE_TYPE = "angular.moduleType"; // root|feature|shared

        public static List<IrTaggedValue> baseTags() {
            return List.of(tv(TAG_FRAMEWORK, FRAMEWORK));
        }
    }
}
