package meghanada.analyze;

import static meghanada.config.Config.timeIt;
import static meghanada.config.Config.timeItF;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import meghanada.GradleTestBase;
import meghanada.config.Config;
import meghanada.docs.declaration.Declaration;
import meghanada.docs.declaration.DeclarationSearcher;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

@SuppressWarnings("CheckReturnValue")
public class JavaAnalyzerTest extends GradleTestBase {

  private static final Logger log = LogManager.getLogger(JavaAnalyzerTest.class);

  @BeforeClass
  public static void setup() throws Exception {
    GradleTestBase.setupReflector(false);
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

  @Test
  public void analyze01() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>(8);
    final File file = new File("./src/test/java/meghanada/Gen1.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeItF(
        "1st:{}",
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeItF(
        "2nd:{}",
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });
  }

  @Test
  public void analyze02() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/Gen2.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeIt(
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });
  }

  @Test
  public void analyze03() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/Gen3.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeIt(
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });
  }

  @Test
  public void analyze04() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/Gen4.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeIt(
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });

    final DeclarationSearcher searcher = new DeclarationSearcher(getProject());
    final Optional<Declaration> declaration = searcher.searchDeclaration(file, 9, 22, "value");
    assertNotNull(declaration);
  }

  @Test
  public void analyze05() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/Gen5.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeIt(
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });
  }

  @Test
  public void analyze06() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/Gen6.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeIt(
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });
  }

  @Test
  public void analyze07() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/Gen7.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeIt(
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });
  }

  @Test
  public void analyze08() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/Gen8.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeIt(
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });
  }

  @Test
  public void analyze09() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/Gen9.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeIt(
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });
  }

  @Test
  public void analyze10() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/Gen10.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeIt(
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });
  }

  @Test
  public void analyze11() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/Gen11.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeIt(
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });
  }

  @Test
  public void analyze12() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();
    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/GenArray1.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeIt(
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });
  }

  @Test
  public void analyze13() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();
    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/L1.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeIt(
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });
  }

  @Test
  public void analyze14() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/SelfRef1.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeIt(
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });
  }

  @Test
  public void analyze15() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file =
        new File("./src/main/java/meghanada/server/CommandHandler.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });
  }

  @Test
  public void analyze17() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file =
        new File("./src/main/java/meghanada/analyze/TreeAnalyzer.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });
  }

  @Test
  public void analyze18() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/GenArray1.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });
  }

  @Test
  public void analyze19() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file =
        new File("./src/main/java/meghanada/reflect/asm/MethodAnalyzeVisitor.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });
  }

  @Test
  public void analyze20() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/SelfRef2.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });
  }

  @Test
  public void analyze21() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file =
        new File("./src/main/java/meghanada/analyze/JavaAnalyzer.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });
  }

  @Test
  public void analyze22() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files = new ArrayList<>();
    final File file = new File("./src/test/java/meghanada/Gen12.java").getCanonicalFile();
    assertTrue(file.exists());
    files.add(file);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });

    timeIt(
        () -> {
          return analyzer.analyzeAndCompile(files, cp, tmp);
        });
  }

  @Test
  public void analyzeAll() throws Exception {
    System.setProperty(Source.REPORT_UNKNOWN_TREE, "true");
    // project.clearCache();
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    List<File> files =
        Files.walk(
                new File("./src/main/java").getCanonicalFile().toPath(),
                FileVisitOption.FOLLOW_LINKS)
            .filter(
                path -> {
                  File file = path.toFile();
                  return FileUtils.isJavaFile(file);
                })
            .map(Path::toFile)
            .collect(Collectors.toList());

    List<File> testFiles =
        Files.walk(
                new File("./src/test/java").getCanonicalFile().toPath(),
                FileVisitOption.FOLLOW_LINKS)
            .filter(
                path -> {
                  File file = path.toFile();
                  return FileUtils.isJavaFile(file);
                })
            .map(Path::toFile)
            .collect(Collectors.toList());

    files.addAll(testFiles);

    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });
  }

  @Test
  public void analyzeFail() throws Exception {
    System.setProperty(Source.REPORT_UNKNOWN_TREE, "false");
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getSystemClasspath();

    List<File> files =
        Files.walk(new File("./src/main/java").toPath(), FileVisitOption.FOLLOW_LINKS)
            .filter(
                path -> {
                  File file = path.toFile();
                  return FileUtils.isJavaFile(file);
                })
            .map(Path::toFile)
            .collect(Collectors.toList());
    List<File> testFiles =
        Files.walk(new File("./src/test/java").toPath(), FileVisitOption.FOLLOW_LINKS)
            .filter(
                path -> {
                  File file = path.toFile();
                  return FileUtils.isJavaFile(file);
                })
            .map(Path::toFile)
            .collect(Collectors.toList());
    files.addAll(testFiles);

    // System.setProperty(Source.REPORT_UNKNOWN_TREE, "true");
    final String tmp = System.getProperty("java.io.tmpdir");

    timeIt(
        () -> {
          final CompileResult compileResult = analyzer.analyzeAndCompile(files, cp, tmp);
          // compileResult.getSources().values().forEach(Source::dump);
          return compileResult;
        });
  }

  @Test
  public void analyzeFromString01() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    final String tmp = System.getProperty("java.io.tmpdir");
    String path = "./src/test/java/meghanada/Hello.java";
    String code = "class Hello {}";
    CompileResult result = analyzer.runAnalyzeAndCompile(cp, tmp, path, code, false, null);
    String summary = result.getDiagnosticsSummary();
    assertEquals(true, result.isSuccess());
  }

  @Test
  public void analyzeFromString02() throws Exception {
    final JavaAnalyzer analyzer = getAnalyzer();
    final String cp = getClasspath();

    final String tmp = System.getProperty("java.io.tmpdir");
    String path = "./src/test/java/meghanada/Hello.java";
    String code = "class Hello {a}";
    CompileResult result = analyzer.runAnalyzeAndCompile(cp, tmp, path, code, false, null);
    String summary = result.getDiagnosticsSummary();
    assertEquals(false, result.isSuccess());
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

  private String getSystemClasspath() throws IOException {

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

    final String out = getOutput().getCanonicalPath();
    classpath.add(out);
    classpath.add(getTestOutput().getCanonicalPath());

    return String.join(File.pathSeparator, classpath);
  }
}
