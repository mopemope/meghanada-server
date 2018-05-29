package meghanada.reference;

import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import meghanada.GradleTestBase;
import meghanada.analyze.CompileResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReferenceSearcherTest extends GradleTestBase {
  private ReferenceSearcher searcher;

  @BeforeClass
  public static void setup() throws Exception {
    GradleTestBase.setupReflector(true);
    CompileResult compileResult1 = project.compileJava();
    CompileResult compileResult2 = project.compileTestJava();
    Thread.sleep(1000 * 5);
  }

  @AfterClass
  public static void shutdown() throws Exception {
    GradleTestBase.shutdown();
  }

  private ReferenceSearcher getSearcher() throws Exception {
    if (searcher != null) {
      return searcher;
    }
    searcher = new ReferenceSearcher(getProject());
    return searcher;
  }

  @Test
  public void testSearchField01() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/analyze/BlockScope.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    final ReferenceSearcher searcher = getSearcher();
    final List<Reference> result = timeIt(() -> searcher.searchReference(f, 145, 45, "scopes"));
    assertNotNull(result);
    assertEquals(29, result.size());
  }

  @Test
  public void testSearchField02() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/reflect/ClassIndex.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    final ReferenceSearcher searcher = getSearcher();
    final List<Reference> result = timeIt(() -> searcher.searchReference(f, 43, 10, "name"));
    assertNotNull(result);
    assertEquals(4, result.size());
  }

  @Test
  public void testSearchField03() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/analyze/BlockScope.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    final ReferenceSearcher searcher = getSearcher();
    final List<Reference> result = timeIt(() -> searcher.searchReference(f, 22, 33, "scopes"));
    assertNotNull(result);
    assertEquals(29, result.size());
  }

  @Test
  public void testSearchField04() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/reflect/ClassIndex.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    final ReferenceSearcher searcher = getSearcher();
    final List<Reference> result = timeIt(() -> searcher.searchReference(f, 21, 30, "ENTITY_TYPE"));
    assertNotNull(result);
    assertEquals(12, result.size());
  }

  @Test
  public void testSearchMethod01() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/utils/FileUtils.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    final ReferenceSearcher searcher = getSearcher();
    final List<Reference> result =
        timeIt(() -> searcher.searchReference(f, 350, 22, "formatJavaFile"));
    assertNotNull(result);
    assertEquals(1, result.size());
    Reference reference = result.get(0);
    assertEquals(728, reference.getLine());
  }

  @Test
  public void testSearchClass01() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/utils/FileUtils.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    final ReferenceSearcher searcher = getSearcher();
    final List<Reference> result = timeIt(() -> searcher.searchReference(f, 45, 20, "FileUtils"));
    assertNotNull(result);
    assertEquals(63, result.size());
    //    for (Reference reference : result) {
    //      System.out.println(reference.getCode());
    //    }
  }

  @Test
  public void testManyMethod() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/reflect/ClassIndex.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    final ReferenceSearcher searcher = getSearcher();
    final List<Reference> result = timeIt(() -> searcher.searchReference(f, 85, 47, "toString"));
    assertNotNull(result);
    assertEquals(43, result.size());
  }
}
