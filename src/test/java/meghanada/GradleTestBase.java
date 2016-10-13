package meghanada;

import com.google.common.base.Stopwatch;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.gradle.GradleProject;
import meghanada.reflect.asm.CachedASMReflector;
import org.junit.BeforeClass;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GradleTestBase {

    private static Project project;

    @BeforeClass
    public static void setupProject() throws Exception {
        // System.setProperty("log-level", "DEBUG");

        if (project == null) {
            project = new GradleProject(new File("./").getCanonicalFile());
            project.parseProject();
            String tmp = System.getProperty("java.io.tmpdir");
            System.setProperty("project-cache-dir", new File(tmp, "meghanada/cache").getCanonicalPath());
        }
        Config.load();
    }

    protected static void setupReflector() throws Exception {
        GradleTestBase.setupProject();
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getSystemJars());
        cachedASMReflector.addClasspath(getJars());
        final Stopwatch stopwatch = Stopwatch.createStarted();
        cachedASMReflector.createClassIndexes();
        System.out.println("createClassIndexes elapsed:" + stopwatch.stop());
    }

    protected static File getJar(String name) {
        return project.getDependencies()
                .stream()
                .map(pd -> {
                    System.out.println(pd.getId());
                    if (pd.getId().contains(name)) {
                        return pd.getFile();
                    }
                    return null;
                })
                .filter(f -> f != null)
                .findFirst().orElse(null);
    }

    protected static File getRTJar() {
        return new File(Config.load().getJavaHomeDir(), "/lib/rt.jar");
    }

    private static Set<File> getJars() {
        return project.getDependencies()
                .stream()
                .map(ProjectDependency::getFile)
                .collect(Collectors.toSet());
    }

    private static List<File> getSystemJars() throws IOException {
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

    protected static File getOutputDir() {
        return project.getOutputDirectory();
    }

    protected static File getTestOutputDir() {
        return project.getTestOutputDirectory();
    }

    protected static Set<File> getSourceDir() {
        return project.getAllSources();
    }
}
