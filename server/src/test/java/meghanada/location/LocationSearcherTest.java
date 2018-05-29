package meghanada.location;

import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import meghanada.GradleTestBase;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class LocationSearcherTest extends GradleTestBase {
  private LocationSearcher searcher;

  @BeforeClass
  public static void setup() throws Exception {
    GradleTestBase.setupReflector(false);
  }

  @AfterClass
  public static void shutdown() throws Exception {
    GradleTestBase.shutdown();
  }

  private LocationSearcher getSearcher() throws Exception {
    if (searcher != null) {
      return searcher;
    }
    searcher = new LocationSearcher(getProject());
    return searcher;
  }

  @Test
  public void testJumpVariable01() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/session/Session.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    LocationSearcher searcher = getSearcher();
    Location result =
        timeIt(() -> searcher.searchDeclarationLocation(f, 104, 12, "result")).orElse(null);
    assertNotNull(result);
    assertEquals(103, result.getLine());
    assertEquals(29, result.getColumn());
  }

  @Test
  public void testJumpParamVariable01() throws Exception {
    final File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/session/Session.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    final LocationSearcher searcher = getSearcher();
    final Location result =
        timeIt(() -> searcher.searchDeclarationLocation(f, 125, 28, "base")).orElse(null);
    assertNotNull(result);
    assertEquals(109, result.getLine());
    assertEquals(52, result.getColumn());
  }

  @Test
  public void testJumpField01() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/session/Session.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    LocationSearcher searcher = getSearcher();
    Location result =
        timeIt(() -> searcher.searchDeclarationLocation(f, 279, 12, "currentProject")).orElse(null);
    assertNotNull(result);
    assertEquals(79, result.getLine());
    assertEquals(19, result.getColumn());
  }

  @Test
  public void testJumpField02() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/test/java/meghanada/Jump1.java")
            .getCanonicalFile();
    assertTrue(f.exists());
    LocationSearcher searcher = getSearcher();
    // Set<File> sources = this.currentProject.getSourceDirectories();
    Location result =
        timeIt(() -> searcher.searchDeclarationLocation(f, 8, 50, "CASE_INSENSITIVE_ORDER"))
            .orElse(null);
    assertNotNull(result);
    Config config = Config.load();

    if (config.isJava8()) {
      assertEquals(1184, result.getLine());
      assertEquals(44, result.getColumn());
    } else {
      assertEquals(1227, result.getLine());
      assertEquals(44, result.getColumn());
    }
  }

  @Test
  public void testJumpField03() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/test/java/meghanada/Jump2.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    LocationSearcher searcher = getSearcher();
    Location result =
        timeIt(() -> searcher.searchDeclarationLocation(f, 10, 12, "jumpTarget")).orElse(null);
    assertNotNull(result);
    assertEquals(5, result.getLine());
    assertEquals(18, result.getColumn());
  }

  @Test
  public void testJumpMethod01() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/session/Session.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    LocationSearcher searcher = getSearcher();
    Location result =
        timeIt(() -> searcher.searchDeclarationLocation(f, 473, 5, "parseJavaSource")).orElse(null);

    assertNotNull(result);
    assertEquals(565, result.getLine());
    assertEquals(28, result.getColumn());
  }

  @Test
  public void testJumpMethod02() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/session/Session.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    LocationSearcher searcher = getSearcher();
    // return source.searchMissingImport();
    Location result =
        searcher.searchDeclarationLocation(f, 562, 46, "searchMissingImport").orElse(null);
    assertNotNull(result);
    assertTrue(result.getPath().contains("Source.java"));
    assertEquals(455, result.getLine());
    assertEquals(36, result.getColumn());
  }

  @Test
  public void testJumpMethod03() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/test/java/meghanada/Overload1.java")
            .getCanonicalFile();
    assertTrue(f.exists());
    LocationSearcher searcher = getSearcher();
    {
      GlobalCache.getInstance().invalidateSource(project, f);
      Location result = searcher.searchDeclarationLocation(f, 18, 5, "over").orElse(null);
      assertNotNull(result);
      assertTrue(result.getPath().contains("Overload1.java"));
      assertEquals(12, result.getLine());
      assertEquals(15, result.getColumn());
    }
    {
      GlobalCache.getInstance().invalidateSource(project, f);
      Location result = searcher.searchDeclarationLocation(f, 21, 5, "over").orElse(null);
      assertNotNull(result);
      assertTrue(result.getPath().contains("Overload1.java"));
      assertEquals(10, result.getLine());
      assertEquals(15, result.getColumn());
    }
    {
      GlobalCache.getInstance().invalidateSource(project, f);
      Location result = searcher.searchDeclarationLocation(f, 23, 5, "over").orElse(null);
      assertNotNull(result);
      assertTrue(result.getPath().contains("Overload1.java"));
      assertEquals(14, result.getLine());
      assertEquals(15, result.getColumn());
    }
  }

  @Test
  public void testJumpMethod04() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/test/java/meghanada/Jump1.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    LocationSearcher searcher = getSearcher();
    Location result = searcher.searchDeclarationLocation(f, 9, 16, "thenComparing").orElse(null);
    assertNotNull(result);
    assertTrue(result.getPath().contains(".java"));
    assertEquals(238, result.getLine());
    assertEquals(31, result.getColumn());
  }

  @Test
  public void testJumpMethod05() throws Exception {
    File f =
        new File(
                project.getProjectRootPath(),
                "./src/main/java/meghanada/location/LocationSearcher.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    LocationSearcher searcher = getSearcher();
    Location result =
        timeIt(() -> searcher.searchDeclarationLocation(f, 346, 20, "searchFieldAccess"))
            .orElse(null);
    assertNotNull(result);
    assertTrue(result.getPath().contains("LocationSearcher.java"));
    assertEquals(735, result.getLine());
    assertEquals(30, result.getColumn());
  }

  @Test
  public void testJumpMethod06() throws Exception {
    File f =
        new File(
                project.getProjectRootPath(),
                "./src/main/java/meghanada/location/LocationSearcher.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    LocationSearcher searcher = getSearcher();
    Location result =
        timeIt(
                () -> {
                  System.setProperty("disable-source-jar", "true");
                  return searcher.searchDeclarationLocation(f, 620, 22, "decompileArchive");
                })
            .orElse(null);
    assertNotNull(result);
    assertTrue(result.getPath().contains(".java"));
    assertEquals(103, result.getLine());
    assertEquals(31, result.getColumn());
  }

  @Test
  public void testJumpMethod07() throws Exception {
    File f =
        new File(
                project.getProjectRootPath(),
                "./src/main/java/meghanada/reflect/asm/ASMReflector.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    LocationSearcher searcher = getSearcher();
    Location result =
        timeIt(() -> searcher.searchDeclarationLocation(f, 68, 19, "getAllowClass")).orElse(null);
    assertNotNull(result);
    assertTrue(result.getPath().contains("Config.java"));
    assertEquals(362, result.getLine());
    assertEquals(23, result.getColumn());
  }

  @Test
  public void testJumpMethod09() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/test/java/meghanada/JumpWithNull.java")
            .getCanonicalFile();
    assertTrue(f.exists());
    LocationSearcher searcher = getSearcher();
    {
      GlobalCache.getInstance().invalidateSource(project, f);
      Location result = searcher.searchDeclarationLocation(f, 12, 22, "main").orElse(null);
      assertNotNull(result);
      assertTrue(result.getPath().contains("JumpWithNull.java"));
      assertEquals(6, result.getLine());
      assertEquals(22, result.getColumn());
    }
  }

  @Test
  public void testJumpMethod10() throws Exception {
    File f =
        new File(
                project.getProjectRootPath(), "./src/main/java/meghanada/analyze/TreeAnalyzer.java")
            .getCanonicalFile();
    assertTrue(f.exists());
    LocationSearcher searcher = getSearcher();
    {
      GlobalCache.getInstance().invalidateSource(project, f);
      Location result =
          timeIt(
              () ->
                  searcher
                      .searchDeclarationLocation(f, 610, 13, "analyzeVariableDecl")
                      .orElse(null));
      assertNotNull(result);
      assertTrue(result.getPath().contains("TreeAnalyzer.java"));
      assertEquals(947, result.getLine());
      assertEquals(23, result.getColumn());
    }
  }

  @Test
  public void testJumpMethod08() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/test/java/meghanada/ArrayOverload.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    LocationSearcher searcher = getSearcher();
    {
      GlobalCache.getInstance().invalidateSource(project, f);
      Location result = searcher.searchDeclarationLocation(f, 12, 19, "over").orElse(null);
      assertNotNull(result);
      assertTrue(result.getPath().contains("ArrayOverload.java"));
      assertEquals(7, result.getLine());
      assertEquals(22, result.getColumn());
    }
  }

  @Test
  public void testJumpMethod11() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/project/Project.java")
            .getCanonicalFile();
    assertTrue(f.exists());
    LocationSearcher searcher = getSearcher();
    GlobalCache.getInstance().invalidateSource(project, f);
    Location result =
        timeIt(() -> searcher.searchDeclarationLocation(f, 589, 14, "runUnitTest").orElse(null));
    assertNotNull(result);
    assertTrue(result.getPath().contains("Project.java"));
    assertEquals(595, result.getLine());
    assertEquals(23, result.getColumn());
  }

  @Test
  public void testJumpClass01() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/session/Session.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    LocationSearcher searcher = getSearcher();
    Location result =
        timeIt(() -> searcher.searchDeclarationLocation(f, 554, 14, "Source")).orElse(null);
    assertNotNull(result);
    assertTrue(result.getPath().contains("Source.java"));
    assertEquals(58, result.getLine());
    assertEquals(14, result.getColumn());
  }

  @Test
  public void testJumpClass02() throws Exception {
    File f =
        new File(
                project.getProjectRootPath(),
                "./src/main/java/meghanada/reflect/asm/ASMReflector.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    LocationSearcher locationSearcher = getSearcher();
    Location result =
        locationSearcher.searchDeclarationLocation(f, 161, 13, "ClassAnalyzeVisitor").orElse(null);
    assertNotNull(result);
    assertTrue(result.getPath().contains("ClassAnalyzeVisitor.java"));
    assertEquals(21, result.getLine());
    assertEquals(7, result.getColumn());
  }

  @Test
  public void testJumpClass03() throws Exception {
    File f =
        new File(
                project.getProjectRootPath(),
                "./src/main/java/meghanada/reflect/asm/ASMReflector.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    LocationSearcher locationSearcher = getSearcher();
    Location result = locationSearcher.searchDeclarationLocation(f, 32, 56, "String").orElse(null);
    assertNotNull(result);
    assertTrue(result.getPath().contains(".java"));
    if (Config.load().isJava9()) {
      assertEquals(123, result.getLine());
      assertEquals(20, result.getColumn());
    } else {
      assertEquals(111, result.getLine());
      assertEquals(20, result.getColumn());
    }
  }

  @Test
  public void testJumpClass04() throws Exception {
    File f =
        new File(
                project.getProjectRootPath(),
                "./src/main/java/meghanada/reflect/asm/ASMReflector.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    LocationSearcher searcher = getSearcher();
    System.setProperty("disable-source-jar", "true");
    Location result = searcher.searchDeclarationLocation(f, 57, 40, "LogManager").orElse(null);
    assertNotNull(result);
    assertTrue(result.getPath().contains(".java"));
    assertEquals(21, result.getLine());
    assertEquals(14, result.getColumn());
  }

  @Test
  public void testJumpAnnotation01() throws Exception {
    File f =
        new File(project.getProjectRootPath(), "./src/main/java/meghanada/analyze/FieldAccess.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    final LocationSearcher searcher = getSearcher();
    final Location result =
        timeIt(() -> searcher.searchDeclarationLocation(f, 12, 3, "@Override")).orElse(null);
    assertNotNull(result);
    if (Config.load().isJava8()) {
      assertEquals(51, result.getLine());
      assertEquals(19, result.getColumn());
    } else {
      assertEquals(53, result.getLine());
      assertEquals(19, result.getColumn());
    }
  }

  @Test
  public void testJumpEnum01() throws Exception {
    final File f =
        new File(project.getProjectRootPath(), "./src/test/java/meghanada/Enum2.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    final LocationSearcher searcher = getSearcher();
    final Location l1 =
        timeIt(() -> searcher.searchDeclarationLocation(f, 5, 23, "Key")).orElse(null);
    assertNotNull(l1);
    assertEquals(8, l1.getLine());
    assertEquals(2, l1.getColumn());
  }

  @Test
  public void testJumpEnum02() throws Exception {
    final File f =
        new File(project.getProjectRootPath(), "./src/test/java/meghanada/Enum2.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    final LocationSearcher searcher = getSearcher();
    final Location l1 =
        timeIt(() -> searcher.searchDeclarationLocation(f, 5, 27, "ONE")).orElse(null);
    assertNotNull(l1);
    assertEquals(9, l1.getLine());
    assertEquals(5, l1.getColumn());

    final Location l2 =
        timeIt(() -> searcher.searchDeclarationLocation(f, 6, 29, "TWO")).orElse(null);
    assertNotNull(l2);
    assertEquals(10, l2.getLine());
    assertEquals(5, l2.getColumn());
  }

  @Test
  public void testJumpEnum03() throws Exception {
    final File f =
        new File(project.getProjectRootPath(), "./src/test/java/meghanada/Enum3.java")
            .getCanonicalFile();
    assertTrue(f.exists());

    final LocationSearcher searcher = getSearcher();
    final Location l1 =
        timeIt(() -> searcher.searchDeclarationLocation(f, 5, 5, "Key")).orElse(null);
    assertNotNull(l1);
    assertEquals(7, l1.getLine());
    assertEquals(8, l1.getColumn());
  }
}
