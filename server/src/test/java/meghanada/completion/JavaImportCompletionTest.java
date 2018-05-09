package meghanada.completion;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import meghanada.GradleTestBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class JavaImportCompletionTest extends GradleTestBase {

  private static final Logger log = LogManager.getLogger(JavaCompletionTest.class);

  @BeforeClass
  public static void setup() throws Exception {
    GradleTestBase.setupReflector(false);
  }

  @AfterClass
  public static void shutdown() throws Exception {
    GradleTestBase.shutdown();
  }

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

  @Test
  public void testImportAtPoint2() throws IOException, ExecutionException {
    JavaImportCompletion completion = new JavaImportCompletion(project);
    final File file =
        new File(project.getProjectRootPath(), "./src/test/resources/meghanada/AnnoTest2.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    Optional<Map<String, List<String>>> res = completion.importAtPoint(file, 10, 2, "RunWith");
    assertTrue(res.isPresent());
    Map<String, List<String>> map = res.get();
    assertEquals(1, map.get("class").size());
    log.info("{}", map);
  }
}
