package meghanada.analyze;

import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import meghanada.GradleTestBase;
import org.junit.Test;

@SuppressWarnings("CheckReturnValue")
public class SourceTest extends GradleTestBase {

  @Test
  public void testOptimizeImports01() throws Exception {
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
              final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp, false);
              return compileResult.getSources().get(file);
            });

    List<String> optimizeImports = timeIt(source::optimizeImports);
    optimizeImports.forEach(System.out::println);
    assertEquals(3, optimizeImports.size());
  }

  @Test
  public void testOptimizeImports02() throws Exception {
    final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8");
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file =
        new File("./src/main/java/meghanada/server/emacs/EmacsServer.java").getCanonicalFile();
    assert file.exists();
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    final Source source =
        timeIt(
            () -> {
              final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp, false);
              return compileResult.getSources().get(file);
            });

    List<String> optimizeImports = timeIt(source::optimizeImports);
    optimizeImports.forEach(System.out::println);
    assertEquals(23, optimizeImports.size());
  }

  @Test
  public void testOptimizeImports03() throws Exception {

    final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8");
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/Opt2.java").getCanonicalFile();
    assert file.exists();
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    final Source source =
        timeIt(
            () -> {
              final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp, false);
              return compileResult.getSources().get(file);
            });

    List<String> optimizeImports = timeIt(source::optimizeImports);
    optimizeImports.forEach(System.out::println);
    assertEquals(1, optimizeImports.size());
  }

  @Test
  public void testMissingImports1() throws Exception {
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
              final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp, false);
              return compileResult.getSources().get(file);
            });

    Map<String, List<String>> missingImport = timeIt(source::searchMissingImport);
    System.out.println(missingImport);
    assertEquals(2, missingImport.size());
  }

  @Test
  public void testMissingImports2() throws Exception {
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
              final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp, false);
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

  @Test
  public void testMissingImports3() throws Exception {
    final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8");
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/resources/MissingImport3.java").getCanonicalFile();
    assert file.exists();
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    final Source source =
        timeIt(
            () -> {
              final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp, false);
              compileResult.getSources().values().forEach(Source::dump);
              return compileResult.getSources().get(file);
            });

    Map<String, List<String>> missingImport = timeIt(source::searchMissingImport);
    missingImport.forEach(
        (k, v) -> {
          System.out.println(k + ':' + v);
        });
    assertEquals(2, missingImport.size());
  }

  @Test
  public void testMissingImports4() throws Exception {
    final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8");
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/resources/MissingImport4.java").getCanonicalFile();
    assert file.exists();
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    final Source source =
        timeIt(
            () -> {
              final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp, false);
              compileResult.getSources().values().forEach(Source::dump);
              return compileResult.getSources().get(file);
            });

    Map<String, List<String>> missingImport = timeIt(source::searchMissingImport);
    missingImport.forEach(
        (k, v) -> {
          System.out.println(k + ':' + v);
        });
    assertEquals(9, missingImport.size());
  }

  @Test
  public void testSource01() throws Exception {
    final JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8");
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file =
        new File("./src/main/java/meghanada/analyze/ClassScope.java").getCanonicalFile();
    assert file.exists();
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    final Source source =
        timeIt(
            () -> {
              final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp, false);
              return compileResult.getSources().get(file);
            });

    assertNotNull(source);
    source.importClasses.forEach(s -> System.out.println(s));
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

    final String out = getOutput().getCanonicalPath();
    classpath.add(out);
    classpath.add(getTestOutput().getCanonicalPath());

    return String.join(File.pathSeparator, classpath);
  }
}
