package meghanada.utils;

import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.sun.tools.javac.resources.version;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import meghanada.GradleTestBase;
import meghanada.Main;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class FileUtilsTest extends GradleTestBase {

  private static final Logger log = LogManager.getLogger(FileUtilsTest.class);

  @BeforeClass
  public static void setup() throws Exception {
    GradleTestBase.setupReflector(false);
  }

  @AfterClass
  public static void shutdown() throws Exception {
    GradleTestBase.shutdown();
  }

  @Test
  public void testMd5sum1() throws Exception {

    final String sum =
        timeIt(
            () -> {
              return FileUtils.getChecksum(
                  new File(
                          project.getProjectRootPath(),
                          "./src/main/java/meghanada/analyze/JavaAnalyzer.java")
                      .getCanonicalFile());
            });
    System.out.println(sum);
  }

  @Test
  public void testMd5sum2() throws Exception {
    final String javaHome = System.getProperty("java.home");
    final String sum =
        timeIt(
            () -> {
              File jar = new File(javaHome, "/lib/rt.jar");
              if (!jar.exists()) {
                jar = new File(javaHome, "/lib/jrt-fs.jar");
              }
              return FileUtils.getChecksum(jar.getCanonicalFile());
            });
    System.out.println(sum);
  }

  @Ignore
  @Test
  public void testGetVersionInfo() throws Exception {
    final String version = timeIt(FileUtils::getVersionInfo);
    System.out.println(version);
    assertNotNull(version);
    assertTrue(version.startsWith(Main.VERSION));
  }

  @Test
  public void testConvertPathToClass1() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/session/Session.java")
            .getCanonicalFile();

    try {
      Optional<String> s = FileUtils.convertPathToClass(project.getAllSources(), f);
      log.info("{}", s);
    } catch (IOException e) {
      log.catching(e);
    }
  }

  @Test
  public void testShortenPath() throws Exception {
    String s = FileUtils.shortenPath("/foo/bar/Test.java");
    assertEquals("/f/b/Test.java", s);
  }

  //  @Test
  //  public void testToHashFile() throws Exception {
  //    final String res =
  //        timeIt(
  //            () -> {
  //              return FileUtils.toHashedPath(
  //                  new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java")
  //                      .getCanonicalFile(),
  //                  ".sdat");
  //            });
  //    System.out.println(res);
  //  }
}
