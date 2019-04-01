package meghanada.typeinfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Optional;
import meghanada.GradleTestBase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TypeInfoSearcherTest extends GradleTestBase {
  private TypeInfoSearcher searcher;

  @BeforeClass
  public static void setup() throws Exception {
    GradleTestBase.setupReflector(false);
  }

  @AfterClass
  public static void shutdown() throws Exception {
    GradleTestBase.shutdown();
  }

  private TypeInfoSearcher getSearcher() throws Exception {
    if (searcher != null) {
      return searcher;
    }
    searcher = new TypeInfoSearcher(GradleTestBase::getProject);
    return searcher;
  }

  @Test
  public void testSearch01() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/analyze/ClassScope.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    TypeInfoSearcher searcher = getSearcher();
    Optional<TypeInfo> result = searcher.search(f, 20, 10, "");
    TypeInfo typeInfo = result.get();
    assertEquals(6, typeInfo.getHierarchy().size());
  }

  @Test
  public void testSearch02() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/analyze/ClassScope.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    TypeInfoSearcher searcher = getSearcher();
    Optional<TypeInfo> result = searcher.search(f, 23, 50, "ArrayList");

    TypeInfo typeInfo = result.get();
    assertEquals(4, typeInfo.getHierarchy().size());
    assertEquals(6, typeInfo.getInterfaces().size());
  }
}
