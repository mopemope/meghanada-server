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
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static meghanada.config.Config.timeIt;
import static meghanada.config.Config.traceIt;

public class JavaAnalyzerTest extends GradleTestBase {

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
    public void analyze1() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/java/meghanada/Gen1.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

        timeIt(() -> {
            return analyzer.analyzeAndCompile(files, cp, tmp);
        });
    }

    @Test
    public void analyze2() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/java/meghanada/Gen2.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

        timeIt(() -> {
            return analyzer.analyzeAndCompile(files, cp, tmp);
        });
    }

    @Test
    public void analyze3() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/java/meghanada/Gen3.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

        timeIt(() -> {
            return analyzer.analyzeAndCompile(files, cp, tmp);
        });
    }

    @Test
    public void analyze4() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/java/meghanada/Gen4.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

        timeIt(() -> {
            return analyzer.analyzeAndCompile(files, cp, tmp);
        });
    }

    @Test
    public void analyze5() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/java/meghanada/Gen5.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

        timeIt(() -> {
            return analyzer.analyzeAndCompile(files, cp, tmp);
        });
    }

    @Test
    public void analyze6() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/java/meghanada/Gen6.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

        timeIt(() -> {
            return analyzer.analyzeAndCompile(files, cp, tmp);
        });
    }

    @Test
    public void analyze7() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/java/meghanada/Gen7.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

        timeIt(() -> {
            return analyzer.analyzeAndCompile(files, cp, tmp);
        });
    }

    @Test
    public void analyze8() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/java/meghanada/Gen8.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

        timeIt(() -> {
            return analyzer.analyzeAndCompile(files, cp, tmp);
        });
    }

    @Test
    public void analyze9() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/java/meghanada/Gen9.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

        timeIt(() -> {
            return analyzer.analyzeAndCompile(files, cp, tmp);
        });
    }

    @Test
    public void analyze10() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/java/meghanada/Gen10.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

        timeIt(() -> {
            return analyzer.analyzeAndCompile(files, cp, tmp);
        });
    }

    @Test
    public void analyze11() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/java/meghanada/Gen11.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

        timeIt(() -> {
            return analyzer.analyzeAndCompile(files, cp, tmp);
        });
    }

    @Test
    public void analyze12() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();
        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/java/meghanada/GenArray1.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

        timeIt(() -> {
            return analyzer.analyzeAndCompile(files, cp, tmp);
        });
    }

    @Test
    public void analyze13() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();
        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/java/meghanada/L1.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

        timeIt(() -> {
            return analyzer.analyzeAndCompile(files, cp, tmp);
        });
    }

    @Test
    public void analyze14() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = new ArrayList<>();
        final File file = new File("./src/test/java/meghanada/SelfRef1.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

        timeIt(() -> {
            return analyzer.analyzeAndCompile(files, cp, tmp);
        });
    }

    @Test
    public void analyze15() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = new ArrayList<>();
        final File file = new File("./src/main/java/meghanada/server/CommandHandler.java").getCanonicalFile();
        files.add(file);

        final String tmp = System.getProperty("java.io.tmpdir");

        traceIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

    }

    @Test
    public void analyzeAll() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getClasspath();

        List<File> files = Files.walk(new File("./src/main/java").toPath(), FileVisitOption.FOLLOW_LINKS)
                .filter(path -> path.toFile().isFile())
                .map(Path::toFile)
                .collect(Collectors.toList());
        List<File> testFiles = Files.walk(new File("./src/test/java").toPath(), FileVisitOption.FOLLOW_LINKS)
                .filter(path -> path.toFile().isFile())
                .map(Path::toFile)
                .collect(Collectors.toList());

        files.addAll(testFiles);

        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

    }

    @Test
    public void analyzeFail() throws Exception {
        final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8", project.getSourceDirectories());
        final String cp = getSystemClasspath();

        List<File> files = Files.walk(new File("./src/main/java").toPath(), FileVisitOption.FOLLOW_LINKS)
                .filter(path -> path.toFile().isFile())
                .map(Path::toFile)
                .collect(Collectors.toList());
        List<File> testFiles = Files.walk(new File("./src/test/java").toPath(), FileVisitOption.FOLLOW_LINKS)
                .filter(path -> path.toFile().isFile())
                .map(Path::toFile)
                .collect(Collectors.toList());
        files.addAll(testFiles);

        // System.setProperty(Source.REPORT_UNKNOWN_TREE, "true");
        final String tmp = System.getProperty("java.io.tmpdir");

        timeIt(() -> {
            final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
            // compileResult.getSources().values().forEach(Source::dump);
            return compileResult;
        });

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

    private String getSystemClasspath() throws IOException {

        final List<String> classpath = getSystemJars()
                .stream()
                .map(file1 -> {
                    try {
                        return file1.getCanonicalPath();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }).collect(Collectors.toList());

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