package meghanada.completion;

import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collection;
import meghanada.GradleTestBase;
import meghanada.config.Config;
import meghanada.reflect.CandidateUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class JavaCompletionTest extends GradleTestBase {

  private static final Logger log = LogManager.getLogger(JavaCompletionTest.class);

  @BeforeClass
  public static void setup() throws Exception {
    GradleTestBase.setupReflector(false);
    Config config = Config.load();
    config.update("camel-case-completion", false);
  }

  @AfterClass
  public static void shutdown() throws Exception {
    GradleTestBase.shutdown();
  }

  @Test
  public void testCompletion01() throws Exception {
    JavaCompletion completion = getCompletion();
    File file =
        new File(project.getProjectRootPath(), "./src/test/java/meghanada/TopClass.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    final Collection<? extends CandidateUnit> units =
        timeIt(() -> completion.completionAt(file, 8, 9, "*this"));
    units.forEach(a -> System.out.println(a.getDeclaration()));
    assertEquals(15, units.size());
  }

  @Test
  public void testCompletion02() throws Exception {
    JavaCompletion completion = getCompletion();
    File file =
        new File(project.getProjectRootPath(), "./src/test/java/meghanada/TopClass.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    final Collection<? extends CandidateUnit> units =
        timeIt(() -> completion.completionAt(file, 14, 9, "*this"));
    units.forEach(a -> System.out.println(a.getDeclaration()));
    assertEquals(15, units.size());
  }

  @Test
  public void testCompletion03() throws Exception {
    JavaCompletion completion = getCompletion();
    File file =
        new File(project.getProjectRootPath(), "./src/test/java/meghanada/TopClass.java")
            .getCanonicalFile();
    assertTrue(file.exists());

    final Collection<? extends CandidateUnit> units =
        timeIt(() -> completion.completionAt(file, 8, 9, "fo"));
    units.forEach(a -> System.out.println(a.getDeclaration()));
    assertEquals(1, units.size());
  }

  @Test
  public void testCompletion04() throws Exception {
    JavaCompletion completion = getCompletion();
    File file =
        new File(project.getProjectRootPath(), "./src/test/java/meghanada/TopClass.java")
            .getCanonicalFile();
    assertTrue(file.exists());

    final Collection<? extends CandidateUnit> units =
        timeIt(() -> completion.completionAt(file, 8, 9, "@Test"));
    units.forEach(a -> System.out.println(a.getDeclaration()));
    assertEquals(3, units.size());
    // assertEquals(6, units.size());
  }

  @Test
  public void testCompletion05() throws Exception {
    JavaCompletion completion = getCompletion();
    File file =
        new File(
                project.getProjectRootPath(),
                "./src/main/java/meghanada/analyze/ExpressionScope.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    final Collection<? extends CandidateUnit> staticLog =
        timeIt(() -> completion.completionAt(file, 16, 4, "lo"));
    staticLog.forEach(a -> System.out.println(a.getDeclaration()));
    assertEquals(1, staticLog.size());

    final Collection<? extends CandidateUnit> pos =
        timeIt(() -> completion.completionAt(file, 22, 8, "po"));
    pos.forEach(a -> System.out.println(a.getDeclaration()));
    assertEquals(1, pos.size());
  }

  @Test
  public void testCompletion06() throws Exception {
    JavaCompletion completion = getCompletion();
    File file =
        new File(
                project.getProjectRootPath(),
                "./src/main/java/meghanada/analyze/ExpressionScope.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    final Collection<? extends CandidateUnit> logMethod =
        timeIt(() -> completion.completionAt(file, 18, 4, "*log#"));
    logMethod.forEach(a -> System.out.println(a.getDisplayDeclaration()));
    assertEquals(362, logMethod.size());
  }

  @Test
  public void testCompletion07() throws Exception {
    JavaCompletion completion = getCompletion();
    File file =
        new File(
                project.getProjectRootPath(),
                "./src/main/java/meghanada/analyze/ExpressionScope.java")
            .getCanonicalFile();
    assertTrue(file.exists());

    final Collection<? extends CandidateUnit> logMethod =
        timeIt(() -> completion.completionAt(file, 17, 4, "*method:java.lang.System#"));
    // logMethod.forEach(a -> System.out.println(a.getDeclaration()));
    Config config = Config.load();
    if (config.isJava8()) {
      assertEquals(39, logMethod.size());
    } else {
      assertEquals(41, logMethod.size());
    }
  }

  @Test
  public void testCompletion08() throws Exception {
    JavaCompletion completion = getCompletion();
    File file =
        new File(
                project.getProjectRootPath(), "./src/main/java/meghanada/analyze/JavaAnalyzer.java")
            .getCanonicalFile();
    assertTrue(file.exists());

    final Collection<? extends CandidateUnit> units =
        timeIt(
            () ->
                completion.completionAt(
                    file,
                    79,
                    35,
                    "*method:java.util.Iterator<capture of ? extends com.sun.source.tree.CompilationUnitTree>#"));
    // units.forEach(a -> System.out.println(a.getDisplayDeclaration()));
    assertEquals(13, units.size());
    for (CandidateUnit unit : units) {
      if (unit.getName().equals("next")) {
        final String returnType = unit.getReturnType();
        assertEquals("capture of ? extends com.sun.source.tree.CompilationUnitTree", returnType);
      }
    }
  }

  @Test
  public void testCompletion09() throws Exception {
    JavaCompletion completion = getCompletion();
    File file =
        new File(
                project.getProjectRootPath(), "./src/main/java/meghanada/analyze/JavaAnalyzer.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    final Collection<? extends CandidateUnit> units =
        timeIt(
            () ->
                completion.completionAt(
                    file,
                    79,
                    35,
                    "*method:capture of ? extends com.sun.source.tree.CompilationUnitTree#"));
    // units.forEach(a -> System.out.println(a.getDisplayDeclaration()));
    Config config = Config.load();
    if (config.isJava8()) {
      assertEquals(17, units.size());
    } else {
      assertEquals(18, units.size());
    }
  }

  @Ignore
  @Test
  public void testCompletion10() throws Exception {
    JavaCompletion completion = getCompletion();
    File file =
        new File(
                project.getProjectRootPath(), "./src/main/java/meghanada/analyze/JavaAnalyzer.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    final Collection<? extends CandidateUnit> units =
        timeIt(() -> completion.completionAt(file, 79, 35, "Dia"));
    units.forEach(a -> System.out.println(a.getDisplayDeclaration()));
    assertEquals(2329, units.size());
  }

  @Test
  public void testSmartCompletionNoType() throws Exception {
    JavaCompletion completion = getCompletion();
    File file =
        new File(
                project.getProjectRootPath(), "./src/main/java/meghanada/analyze/JavaAnalyzer.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    final Collection<? extends CandidateUnit> units =
        timeIt(() -> completion.completionAt(file, 88, 6, "*diagnostic*fileObject#"));
    units.forEach(a -> System.out.println(a.getDisplayDeclaration()));
    CandidateUnit unit = (CandidateUnit) (units.toArray())[0];
    assertEquals("getSource", unit.getName());
  }

  @Test
  public void testSmartCompletionWithType() throws Exception {
    JavaCompletion completion = getCompletion();
    File file =
        new File(
                project.getProjectRootPath(), "./src/main/java/meghanada/analyze/JavaAnalyzer.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    final Collection<? extends CandidateUnit> units =
        timeIt(() -> completion.completionAt(file, 88, 6, "*kind*String#"));
    units.forEach(a -> System.out.println(a.getDisplayDeclaration()));
    Object[] objs = (units.toArray());
    CandidateUnit unit1 = (CandidateUnit) objs[0];
    CandidateUnit unit2 = (CandidateUnit) objs[1];
    assertEquals("name", unit1.getName());
    assertEquals("toString", unit2.getName());
  }

  @Test
  public void testSmartCompletionPrimaryType() throws Exception {
    JavaCompletion completion = getCompletion();
    File file =
        new File(
                project.getProjectRootPath(), "./src/main/java/meghanada/analyze/JavaAnalyzer.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    final Collection<? extends CandidateUnit> units =
        timeIt(() -> completion.completionAt(file, 304, 4, "*code*int#"));
    units.forEach(a -> System.out.println(a.getDisplayDeclaration()));
    Object[] objs = (units.toArray());
    CandidateUnit unit1 = (CandidateUnit) objs[0];
    CandidateUnit unit2 = (CandidateUnit) objs[1];
    CandidateUnit unit3 = (CandidateUnit) objs[2];
    CandidateUnit unit4 = (CandidateUnit) objs[3];
    assertEquals("length", unit1.getName());
    assertEquals("hashCode", unit2.getName());
    assertEquals("indexOf", unit3.getName());
    assertEquals("lastIndexOf", unit4.getName());
  }

  @Test
  public void testSmartCompletionGenericType() throws Exception {
    JavaCompletion completion = getCompletion();
    File file =
        new File(
                project.getProjectRootPath(), "./src/main/java/meghanada/analyze/JavaAnalyzer.java")
            .getCanonicalFile();
    assertTrue(file.exists());
    final Collection<? extends CandidateUnit> units =
        timeIt(() -> completion.completionAt(file, 192, 22, "a"));
    units.forEach(a -> System.out.println(a.getDisplayDeclaration()));
    Object[] objs = (units.toArray());
    CandidateUnit unit1 = (CandidateUnit) objs[0];
    assertEquals("analyze", unit1.getName());
  }

  private JavaCompletion getCompletion() throws Exception {
    return new JavaCompletion(getProject());
  }
}
