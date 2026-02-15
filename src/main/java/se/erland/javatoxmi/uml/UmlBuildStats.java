package se.erland.javatoxmi.uml;

/**
 * Simple counters for what was created in the UML object graph.
 *
 * Step 4 only requires stats collection for reporting; serialization comes in Step 5.
 */
public final class UmlBuildStats {
    public int packagesCreated;
    public int classifiersCreated;
    public int attributesCreated;
    public int operationsCreated;
    public int parametersCreated;
    public int generalizationsCreated;
    public int interfaceRealizationsCreated;
    public int dependenciesCreated;
    public int associationsCreated;
    public int enumLiteralsCreated;
    public int externalStubsCreated;
}

