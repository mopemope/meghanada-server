package meghanada.analyze;

import meghanada.GradleTestBase;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.project.gradle.GradleProject;
import meghanada.reflect.asm.CachedASMReflector;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static meghanada.config.Config.debugIt;
import static org.junit.Assert.assertEquals;

public class SourceTest extends GradleTestBase {

    private static Project project;

    @org.junit.BeforeClass
    public static void beforeClass() throws Exception {
        GradleTestBase.setupReflector();
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.addClasspath(getTestOutputDir());
        cachedASMReflector.createClassIndexes();
    }

    @BeforeClass
    public static void setupProject() throws Exception {
        // System.setProperty("log-level", "DEBUG");

        if (project == null) {
            String tmp = System.getProperty("java.io.tmpdir");
            System.setProperty("project-cache-dir", new File(tmp, "meghanada/cache").getCanonicalPath());
            project = new GradleProject(new File("./").getCanonicalFile());
            project.parseProject();
        }
        Config.load();
    }

    protected static File getOutputDir() {
        return project.getOutputDirectory();
    }

    protected static File getTestOutputDir() {
        return project.getTestOutputDirectory();
    }

    @Test
    public void testOptimizeImports1() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/resources/MissingImport1.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        final Source source = debugIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            return compileResult.getSources().get(file);
        });

        Map<String, List<String>> missingImport = debugIt(source::searchMissingImport);
        List<String> optimizeImports = debugIt(source::optimizeImports);
        System.out.println(missingImport);
        System.out.println(optimizeImports);
        assertEquals(4, missingImport.size());
        assertEquals(2, optimizeImports.size());
    }
    
    private String getClasspath() throws IOException {

        final List<String> classpath = getSystemJars()
                .stream()
                .map(file1 -> {
                    try {
                        return file1.getCanonicalPath();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }).collect(Collectors.toList());

        getJars().forEach(file -> {
            try {
                classpath.add(file.getCanonicalPath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        final String out = getOutputDir().getCanonicalPath();
        classpath.add(out);
        classpath.add(getTestOutputDir().getCanonicalPath());

        return String.join(File.pathSeparator, classpath);
    }

    private List<File> getSystemJars() throws IOException {
        final String javaHome = Config.load().getJavaHomeDir();
        File jvmDir = new File(javaHome);
        return Files.walk(jvmDir.toPath())
                .filter(path -> {
                    String name = path.toFile().getName();
                    return name.endsWith(".jar") && !name.endsWith("policy.jar");
                })
                .map(path -> {
                    try {
                        return path.toFile().getCanonicalFile();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .collect(Collectors.toList());
    }

}