package meghanada.analyze;

import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import meghanada.GradleTestBase;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.project.gradle.GradleProject;
import meghanada.reflect.asm.CachedASMReflector;
import org.junit.BeforeClass;
import org.junit.Test;

@SuppressWarnings("CheckReturnValue")
public class SourceTest extends GradleTestBase {

  private static Project project;

  @BeforeClass
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
    return project.getOutput();
  }

  protected static File getTestOutputDir() {
    return project.getTestOutput();
  }

  @Test
  public void testOptimizeImports1() throws Exception {
    final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8");
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/AllTests.java").getCanonicalFile();
    assert file.exists();
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    final Source source =
        timeIt(
            () -> {
              final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
              return compileResult.getSources().get(file);
            });

    Map<String, List<String>> missingImport = timeIt(source::searchMissingImport);
    List<String> optimizeImports = timeIt(source::optimizeImports);
    assertEquals(0, missingImport.size());
    assertEquals(13, optimizeImports.size());
  }

  @Test
  public void testOptimizeImports2() throws Exception {
    final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8");
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/Opt.java").getCanonicalFile();
    assert file.exists();
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    final Source source =
        timeIt(
            () -> {
              final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
              return compileResult.getSources().get(file);
            });

    List<String> optimizeImports = timeIt(source::optimizeImports);
    System.out.println(optimizeImports);
    assertEquals(3, optimizeImports.size());
  }

  @Test
  public void tesMissingImports1() throws Exception {
    final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8");
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/resources/meghanada/AnnoTest1.java").getCanonicalFile();
    assert file.exists();
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    final Source source =
        timeIt(
            () -> {
              final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
              return compileResult.getSources().get(file);
            });

    Map<String, List<String>> missingImport = timeIt(source::searchMissingImport);
    System.out.println(missingImport);
    assertEquals(2, missingImport.size());
  }

  @Test
  public void tesMissingImports2() throws Exception {
    final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8");
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/resources/meghanada/AnnoTest2.java").getCanonicalFile();
    assert file.exists();
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    final Source source =
        timeIt(
            () -> {
              final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
              compileResult.getSources().values().forEach(Source::dump);
              return compileResult.getSources().get(file);
            });

    Map<String, List<String>> missingImport = timeIt(source::searchMissingImport);
    missingImport.forEach(
        (k, v) -> {
          System.out.println(k + ":" + v);
        });
    assertEquals(8, missingImport.size());
  }

  private String getClasspath() throws IOException {

    final List<String> classpath =
        getSystemJars()
            .stream()
            .map(
                file1 -> {
                  try {
                    return file1.getCanonicalPath();
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                })
            .collect(Collectors.toList());

    getJars()
        .forEach(
            file -> {
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
}
