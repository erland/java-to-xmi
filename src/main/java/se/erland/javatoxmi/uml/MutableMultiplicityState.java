package se.erland.javatoxmi.uml;

import java.util.LinkedHashMap;
import java.util.Map;

/** Internal mutable state used while applying multiplicity rule modules. */
final class MutableMultiplicityState {
    int lower;
    int upper; // MultiplicityResolver.STAR for '*'
    final Map<String, String> tags = new LinkedHashMap<>();

    MutableMultiplicityState(int lower, int upper) {
        this.lower = Math.max(0, lower);
        this.upper = upper < 0 ? MultiplicityResolver.STAR : upper;
    }
}
