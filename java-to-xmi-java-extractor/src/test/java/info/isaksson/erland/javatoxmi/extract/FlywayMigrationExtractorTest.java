package info.isaksson.erland.javatoxmi.extract;

import info.isaksson.erland.javatoxmi.io.SourceScanner;
import info.isaksson.erland.javatoxmi.model.JMigrationArtifact;
import info.isaksson.erland.javatoxmi.model.JModel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FlywayMigrationExtractorTest {

    @Test
    void findsSqlMigrationsUnderDbMigration() throws Exception {
        Path root = Files.createTempDirectory("j2x-flyway-");

        // Minimal Java source (so extractor runs normally)
        Path pkg = root.resolve("com/example/app");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("App.java"), "package com.example.app; public class App {}\n", StandardCharsets.UTF_8);

        // Flyway migrations
        Path mig = root.resolve("src/main/resources/db/migration");
        Files.createDirectories(mig);
        Files.writeString(mig.resolve("V1__init.sql"), "-- init\n", StandardCharsets.UTF_8);
        Files.writeString(mig.resolve("V2_1__Add_table.sql"), "-- add\n", StandardCharsets.UTF_8);
        Files.writeString(mig.resolve("R__refresh_view.sql"), "-- refresh\n", StandardCharsets.UTF_8);

        List<Path> files = SourceScanner.scan(root, List.of(), false);
        JModel model = new JavaExtractor().extract(root, files);

        assertNotNull(model.migrationArtifacts);
        assertEquals(3, model.migrationArtifacts.size(), "migrationArtifacts: " + model.migrationArtifacts);

        JMigrationArtifact v1 = model.migrationArtifacts.stream().filter(a -> "1".equals(a.version)).findFirst().orElse(null);
        assertNotNull(v1);
        assertEquals(JMigrationArtifact.Kind.VERSIONED, v1.kind);
        assertTrue(v1.path.contains("db/migration/V1__init.sql"));

        JMigrationArtifact v21 = model.migrationArtifacts.stream().filter(a -> "2.1".equals(a.version)).findFirst().orElse(null);
        assertNotNull(v21);
        assertTrue(v21.description.toLowerCase().contains("add table"));

        JMigrationArtifact rep = model.migrationArtifacts.stream().filter(a -> a.kind == JMigrationArtifact.Kind.REPEATABLE).findFirst().orElse(null);
        assertNotNull(rep);
        assertNull(rep.version);
        assertTrue(rep.description.toLowerCase().contains("refresh view"));
    }
}
