package info.isaksson.erland.javatoxmi.model;

/** A JPMS {@code requires} directive. */
public final class JJavaModuleRequire {
    public final String moduleName;
    public final boolean isStatic;
    public final boolean isTransitive;

    public JJavaModuleRequire(String moduleName, boolean isStatic, boolean isTransitive) {
        this.moduleName = moduleName;
        this.isStatic = isStatic;
        this.isTransitive = isTransitive;
    }
}
