package info.isaksson.erland.javatoxmi.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Java Platform Module System (JPMS) module metadata extracted from {@code module-info.java}.
 *
 * <p>This is used to emit UML packages representing modules and dependencies representing {@code requires} edges.</p>
 */
public final class JJavaModule {
    public final String name;

    /** Packages exported by this module (as qualified package names). */
    public final List<String> exports = new ArrayList<>();

    /** Packages opened by this module (as qualified package names). */
    public final List<String> opens = new ArrayList<>();

    /** Required module edges. */
    public final List<JJavaModuleRequire> requires = new ArrayList<>();

    public JJavaModule(String name) {
        this.name = name;
    }
}
