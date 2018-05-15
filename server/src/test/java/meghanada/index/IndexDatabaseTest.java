package meghanada.index;

import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;

import java.util.List;
import meghanada.GradleTestBase;
import meghanada.analyze.CompileResult;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class IndexDatabaseTest extends GradleTestBase {

  private static Logger log = LogManager.getLogger(IndexDatabaseTest.class);

  @BeforeClass
  public static void setup() throws Exception {
    System.setProperty("meghanada.full.text.search", "false");
    GradleTestBase.setupReflector(false);
    CompileResult result = project.compileJava(true);
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    addClasspath(cachedASMReflector);
    cachedASMReflector.createClassIndexes();
  }

  @AfterClass
  public static void shutdown() throws Exception {
    GradleTestBase.shutdown();
  }

  @Ignore
  @Test
  public void searchMembers1() throws InterruptedException {
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    timeIt(() -> reflector.scanAllStaticMembers());
    Thread.sleep(1000 * 10);
    List<MemberDescriptor> members =
        IndexDatabase.getInstance().searchMembers("", "public static", "METHOD", "isNull");
    members.forEach(d -> log.info("{} {}", d.getDeclaringClass(), d.getDeclaration()));
    assertEquals(5, members.size());
  }

  @Ignore
  @Test
  public void searchMembers2() throws InterruptedException {
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    timeIt(() -> reflector.scanAllStaticMembers());
    Thread.sleep(1000 * 10);
    List<MemberDescriptor> members =
        IndexDatabase.getInstance().searchMembers("java.util.List", "", "METHOD", "");
    members.forEach(d -> log.info("{} {}", d.getDeclaringClass(), d.getDeclaration()));
    String declaringClass = "java.util.List<String>";
    ClassNameUtils.replaceDescriptorsType(declaringClass, members);
    members.forEach(d -> log.info("{} {}", declaringClass, d.getDeclaration()));
  }

  @Ignore
  @Test
  public void searchMembers3() throws InterruptedException {
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    timeIt(() -> reflector.scanAllStaticMembers());
    Thread.sleep(1000 * 10);
    List<MemberDescriptor> members =
        IndexDatabase.getInstance()
            .searchMembers(
                "(java.util.Objects OR org.junit.Assert)",
                IndexDatabase.doubleQuote("public static"),
                IndexDatabase.doubleQuote("METHOD"),
                "");
    members.forEach(d -> log.info("{} {}", d.getDeclaringClass(), d.getDeclaration()));
  }
}
