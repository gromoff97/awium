package io.github.gromoff97.assertility;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class ArtifactContractIT {

    private static final Path JAR = Path.of(
            "target", "assertility-0.1.0-SNAPSHOT.jar");
    private static final Path MAIN_CLASS = Path.of("target", "classes", "io",
            "github", "gromoff97", "assertility", "Assertility.class");

    @Test
    void currentBuildJarHasTheStableAutomaticModuleIdentity() throws Exception {
        assertTrue(Files.isRegularFile(JAR), JAR.toString());
        assertTrue(Files.isRegularFile(MAIN_CLASS), MAIN_CLASS.toString());

        try (JarFile jar = new JarFile(JAR.toFile())) {
            Manifest manifest = jar.getManifest();
            assertNotNull(manifest);
            assertEquals("io.github.gromoff97.assertility",
                    manifest.getMainAttributes().getValue("Automatic-Module-Name"));
            assertFalse(jar.stream().anyMatch(entry ->
                    entry.getName().equals("module-info.class")
                            || entry.getName().endsWith("/module-info.class")));

            JarEntry packagedClass = jar.getJarEntry(
                    "io/github/gromoff97/assertility/Assertility.class");
            assertNotNull(packagedClass);
            try (InputStream content = jar.getInputStream(packagedClass)) {
                assertArrayEquals(Files.readAllBytes(MAIN_CLASS),
                        content.readAllBytes());
            }
        }

        Set<ModuleReference> modules = ModuleFinder.of(JAR).findAll();
        assertEquals(1, modules.size());
        ModuleReference module = modules.iterator().next();
        assertEquals("io.github.gromoff97.assertility",
                module.descriptor().name());
        assertTrue(module.descriptor().isAutomatic());
    }

    @Test
    void currentProjectPomDeclaresOnlyApprovedTestDependencies() throws Exception {
        Path pom = Path.of("pom.xml");
        assertTrue(Files.isRegularFile(pom), pom.toAbsolutePath().toString());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Element project = factory.newDocumentBuilder().parse(pom.toFile())
                .getDocumentElement();
        Element dependencies = child(project, "dependencies");
        List<String> coordinates = new ArrayList<>();

        for (Element dependency : children(dependencies, "dependency")) {
            coordinates.add(text(dependency, "groupId") + ":"
                    + text(dependency, "artifactId"));
            assertEquals("test", text(dependency, "scope"));
        }

        assertEquals(Set.of(
                        "org.junit.jupiter:junit-jupiter",
                        "org.openrewrite:rewrite-java",
                        "org.openrewrite:rewrite-java-21"),
                Set.copyOf(coordinates));
        assertEquals(3, coordinates.size());
    }

    private static Element child(Element parent, String name) {
        List<Element> matches = children(parent, name);
        assertEquals(1, matches.size(), name);
        return matches.getFirst();
    }

    private static String text(Element parent, String name) {
        return child(parent, name).getTextContent().strip();
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null;
                node = node.getNextSibling()) {
            if (node instanceof Element element
                    && name.equals(element.getLocalName())) {
                matches.add(element);
            }
        }
        return matches;
    }
}
