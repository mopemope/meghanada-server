package meghanada.completion;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import meghanada.GradleTestBase;
import meghanada.analyze.CompileResult;
import meghanada.analyze.JavaAnalyzer;
import meghanada.config.Config;
import meghanada.reflect.asm.CachedASMReflector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class JavaImportCompletionTest extends GradleTestBase {

  private static final Logger log = LogManager.getLogger(JavaCompletionTest.class);

  @BeforeClass
  public static void setup() throws Exception {
    GradleTestBase.setupReflector(false);
    CachedASMReflector.getInstance().scanAllStaticMembers();
    Thread.sleep(1000 * 5);
  }

  @AfterClass
  public static void shutdown() throws Exception {
    GradleTestBase.shutdown();
  }

  private JavaAnalyzer getAnalyzer() {
    JavaAnalyzer analyzer = new JavaAnalyzer("1.8", "1.8");
    if (Config.load().isJava9()) {
      analyzer = new JavaAnalyzer("9", "9");
    }
    return analyzer;
  }

  private String getClasspath() throws IOException {

    List<String> classpath =
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

    String out = getOutput().getCanonicalPath();
    classpath.add(out);
    classpath.add(getTestOutput().getCanonicalPath());

    return String.join(File.pathSeparator, classpath);
  }

  @Ignore
  @Test
  public void testImportAtPoint1() throws IOException, ExecutionException {
    JavaImportCompletion completion = new JavaImportCompletion(project);
    File file =
        new File(project.getProjectRootPath(), "./src/test/java/meghanada/TopClass.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    Optional<Map<String, List<String>>> res = completion.importAtPoint(file, 13, 13, "String");
    assertFalse(res.isPresent());
  }

  @Ignore
  @Test
  public void testImportAtPoint2() throws IOException, ExecutionException {
    JavaImportCompletion completion = new JavaImportCompletion(project);
    File file =
        new File(project.getProjectRootPath(), "./src/test/resources/meghanada/AnnoTest2.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    Optional<Map<String, List<String>>> res = completion.importAtPoint(file, 10, 2, "RunWith");
    assertTrue(res.isPresent());
    Map<String, List<String>> map = res.get();
    assertEquals(1, map.get("class").size());
    System.out.println(map);
  }

  @Ignore
  @Test
  public void testImportAtPoint3() throws Exception {
    JavaAnalyzer analyzer = getAnalyzer();
    JavaImportCompletion completion = new JavaImportCompletion(project);
    String cp = getClasspath();

    List<File> files = new ArrayList<>();
    File file =
        new File(project.getProjectRootPath(), "./src/test/resources/MissingImport5.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);
    String tmp = System.getProperty("java.io.tmpdir");
    CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp, false);
    Optional<Map<String, List<String>>> res = completion.importAtPoint(file, 5, 5, "assertEquals");
    assertTrue(res.isPresent());
    List<String> methods = res.get().get("method");
    System.out.println(methods);
    assertEquals(3, methods.size());
  }

  @Ignore
  @Test
  public void testImportAtPoint4() throws Exception {
    JavaAnalyzer analyzer = getAnalyzer();
    JavaImportCompletion completion = new JavaImportCompletion(project);
    String cp = getClasspath();
    File file =
        new File(project.getProjectRootPath(), "./src/test/resources/MissingImport5.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    Optional<Map<String, List<String>>> map =
        completion.importAtPoint(file, 0, 0, "CASE_INSENSITIVE_ORDER");
    assertTrue(map.isPresent());
    System.out.println(map.get());
  }

  @Ignore
  @Test
  public void testImportAtPoint5() throws Exception {
    JavaAnalyzer analyzer = getAnalyzer();
    JavaImportCompletion completion = new JavaImportCompletion(project);
    String cp = getClasspath();
    File file =
        new File(project.getProjectRootPath(), "./src/test/resources/MissingImport6.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    Optional<Map<String, List<String>>> map = completion.importAtPoint(file, 4, 5, "timeIt");
    assertTrue(map.isPresent());
    System.out.println(map.get());
  }
}
