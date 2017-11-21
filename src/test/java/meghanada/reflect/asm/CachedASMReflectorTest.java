package meghanada.reflect.asm;

import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Stopwatch;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import meghanada.GradleTestBase;
import meghanada.cache.GlobalCache;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.MemberDescriptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class CachedASMReflectorTest extends GradleTestBase {

  private static Logger log = LogManager.getLogger(CachedASMReflectorTest.class);

  @BeforeClass
  public static void setup() throws Exception {
    GradleTestBase.setupReflector(false);
  }

  @AfterClass
  public static void shutdown() throws Exception {
    GradleTestBase.shutdown();
  }

  @Ignore
  @Test
  public void testCreateClassIndexes() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    Stopwatch stopwatch = Stopwatch.createStarted();
    cachedASMReflector.createClassIndexes();
    System.out.println(stopwatch.stop());
    // System.out.println(cachedASMReflector.getGlobalClassIndex().size());
    assertTrue(9055 <= cachedASMReflector.getGlobalClassIndex().size());
  }

  @Ignore
  @Test
  public void testSearchMap() throws Exception {
    final CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    boolean isOpenJDK = System.getProperty("java.home").contains("openjdk");

    Stopwatch stopwatch = Stopwatch.createStarted();
    Collection<? extends CandidateUnit> candidateUnits = cachedASMReflector.searchClasses("Map");
    System.out.println(stopwatch.stop());
    candidateUnits.forEach(u -> System.out.println(u.getDeclaration()));
    if (isOpenJDK) {
      assertEquals(245, candidateUnits.size());
    } else {
      assertEquals(265, candidateUnits.size());
    }
  }

  @Test
  public void testSearchClasses01() throws Exception {
    final CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    final Collection<? extends CandidateUnit> candidateUnits =
        cachedASMReflector.searchClasses("Map", false, false);
    candidateUnits.forEach(c -> System.out.println(c));
    assertEquals(1, candidateUnits.size());
  }

  @Test
  public void testGetPackageClasses1() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    Map<String, String> map = cachedASMReflector.getPackageClasses("java.lang");
    assertEquals(104, map.size());
    // System.out.println(map);
  }

  @Test
  public void testGetPackageClasses2() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    Map<String, String> map = cachedASMReflector.getPackageClasses("java.util.*");
    assertEquals(128, map.size());
    // System.out.println(map);
  }

  @Test
  public void testReflectJavaLangString() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

    Stopwatch stopwatch = Stopwatch.createUnstarted();
    {
      String fqcn = "java.lang.String";

      stopwatch.start();
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      System.out.println(stopwatch.stop());
      stopwatch.reset();
      memberDescriptors.forEach(m -> System.out.println(m));
      assertEquals(100, memberDescriptors.size());
    }
    {
      String fqcn = "java.lang.String";
      GlobalCache.getInstance().invalidateMemberDescriptors(fqcn);
      stopwatch.start();
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      System.out.println(stopwatch.stop());
      memberDescriptors.forEach(m -> System.out.println(m));
      stopwatch.reset();
      assertEquals(100, memberDescriptors.size());
    }
  }

  @Test
  public void testReflectList() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

    {
      String fqcn = "java.util.List";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
      assertEquals(41, memberDescriptors.size());
    }
  }

  @Test
  public void testReflectTypeParam1() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

    {
      String fqcn = "java.util.Map<String, Long>";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      memberDescriptors.forEach(md -> System.out.println(md.getDisplayDeclaration()));
      assertEquals(34, memberDescriptors.size());
    }
  }

  @Test
  public void testReflectTypeParam2() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

    {
      String fqcn = "com.google.common.collect.FluentIterable<String>";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      memberDescriptors.forEach(md -> System.out.println(md.getDisplayDeclaration()));
      assertEquals(56, memberDescriptors.size());
    }
  }

  @Test
  public void testReflectTypeParam3() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

    {
      String fqcn = "java.util.stream.Stream<String>";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      memberDescriptors.forEach(md -> System.out.println(md.getDeclaration()));
      assertEquals(58, memberDescriptors.size());
    }
  }

  @Test
  public void testReflectTypeParam4() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

    {
      String fqcn = "com.google.common.base.Joiner";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      memberDescriptors.forEach(md -> System.out.println(md.getDisplayDeclaration()));
      assertEquals(31, memberDescriptors.size());
    }
  }

  @Test
  public void testReflect1() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

    {
      String fqcn = "org.nustaq.serialization.FSTConfiguration";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      // for (MemberDescriptor md : memberDescriptors) {
      // System.out.println(md);
      // }
      memberDescriptors.forEach(md -> System.out.println(md.getDisplayDeclaration()));
      assertEquals(120, memberDescriptors.size());
    }
  }

  @Test
  public void testReflect2() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

    {
      String fqcn = "java.util.Iterator<Map.Entry<WatchKey, Path>>";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      // for (MemberDescriptor md : memberDescriptors) {
      // System.out.println(md);
      // }
      memberDescriptors.forEach(
          memberDescriptor -> System.out.println(memberDescriptor.getDeclaration()));
      assertEquals(15, memberDescriptors.size());
    }
  }

  @Test
  public void testReflect3() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

    {
      String fqcn = "java.util.jar.JarFile";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      memberDescriptors.sort(
          (o1, o2) -> {
            final String name1 = o1.getName();
            final String name2 = o2.getName();
            return name1.compareTo(name2);
          });
      memberDescriptors.forEach(
          md -> {
            System.out.println(md.getDeclaringClass() + " # " + md.getDeclaration());
          });
      assertEquals(37, memberDescriptors.size());
    }
  }

  @Test
  public void testReflect4() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

    {
      String fqcn = "javax.management.relation.RoleList";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      memberDescriptors.sort(
          (o1, o2) -> {
            final String name1 = o1.getName();
            final String name2 = o2.getName();
            return name1.compareTo(name2);
          });
      memberDescriptors.forEach(
          md -> {
            System.out.println(md.getDisplayDeclaration());
            // System.out.println(md.getDeclaringClass() + " # " +
            // md.getDisplayDeclaration());
          });
      assertEquals(59, memberDescriptors.size());
    }
  }

  @Test
  public void testLocalReflect3() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    cachedASMReflector.addClasspath(getOutput());
    cachedASMReflector.createClassIndexes();

    {
      String fqcn = "meghanada.reflect.MemberDescriptor";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
      assertEquals(51, memberDescriptors.size());
    }
  }

  @Test
  public void testLocalReflect5() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    cachedASMReflector.addClasspath(getOutput());
    cachedASMReflector.createClassIndexes();

    {
      String fqcn = "meghanada.reflect.asm.MethodAnalyzeVisitor";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
      assertEquals(70, memberDescriptors.size());
    }
  }

  @Test
  public void testLocalReflect6() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    cachedASMReflector.addClasspath(getOutput());
    cachedASMReflector.createClassIndexes();

    {
      String fqcn = "meghanada.utils.ClassNameUtils";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
      assertEquals(61, memberDescriptors.size());
    }
  }

  @Test
  public void testLocalReflect7() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    cachedASMReflector.addClasspath(getOutput());
    cachedASMReflector.addClasspath(getTestOutput());
    cachedASMReflector.createClassIndexes();

    {
      String fqcn = "meghanada.Gen1<Long, String>";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
      assertEquals(16, memberDescriptors.size());
    }
  }

  @Test
  public void testLocalReflect8() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    cachedASMReflector.addClasspath(getOutput());
    cachedASMReflector.addClasspath(getTestOutput());
    cachedASMReflector.createClassIndexes();
    String fqcn = "meghanada.Gen3<Long>";
    List<MemberDescriptor> memberDescriptors = timeIt(() -> cachedASMReflector.reflect(fqcn));
    memberDescriptors.forEach(
        m -> {
          System.out.println(m.getDeclaration());
        });
    assertEquals(13, memberDescriptors.size());
  }

  @Test
  public void testLocalReflect9() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    cachedASMReflector.addClasspath(getOutput());
    cachedASMReflector.addClasspath(getTestOutput());
    cachedASMReflector.createClassIndexes();

    {
      String fqcn = "meghanada.SelfRef1";
      List<MemberDescriptor> memberDescriptors =
          timeIt(
              () -> {
                return cachedASMReflector.reflect(fqcn);
              });
      memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
      assertEquals(13, memberDescriptors.size());
    }
    {
      String fqcn = "meghanada.SelfRef1$Ref";
      List<MemberDescriptor> memberDescriptors =
          timeIt(
              () -> {
                return cachedASMReflector.reflect(fqcn);
              });
      memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
      assertEquals(20, memberDescriptors.size());
    }
  }

  @Test
  public void testLocalReflect10() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    cachedASMReflector.addClasspath(getOutput());
    cachedASMReflector.addClasspath(getTestOutput());
    cachedASMReflector.createClassIndexes();

    {
      String fqcn = "meghanada.SelfRef2";
      List<MemberDescriptor> memberDescriptors =
          timeIt(
              () -> {
                return cachedASMReflector.reflect(fqcn);
              });
      memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
      assertEquals(13, memberDescriptors.size());
    }
    {
      String fqcn = "meghanada.SelfRef2$Ref";
      List<MemberDescriptor> memberDescriptors =
          timeIt(
              () -> {
                return cachedASMReflector.reflect(fqcn);
              });
      memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
      assertEquals(20, memberDescriptors.size());
    }
  }

  @Test
  public void testLocalInterface1() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    cachedASMReflector.addClasspath(getOutput());
    cachedASMReflector.createClassIndexes();
    {
      String fqcn = "meghanada.reflect.CandidateUnit";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
      assertEquals(16, memberDescriptors.size());
    }
  }

  @Test
  public void testLocalInterface2() throws Exception {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    cachedASMReflector.addClasspath(getOutput());
    cachedASMReflector.createClassIndexes();

    {
      String fqcn = "com.google.common.eventbus.SubscriberExceptionHandler";
      List<MemberDescriptor> memberDescriptors = cachedASMReflector.reflect(fqcn);
      memberDescriptors.forEach(m -> System.out.println(m.getDisplayDeclaration()));
      assertEquals(12, memberDescriptors.size());
    }
  }

  @Test
  public void testGetSupers01() throws Exception {
    final CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    Collection<String> superClass =
        cachedASMReflector.getSuperClass("meghanada.analyze.ClassScope");
    // for (String clazz : superClass) {
    //   log.info("class {}", clazz);
    // }
    assertEquals(6, superClass.size());
  }

  @Test
  public void testGetSupers02() throws Exception {
    final CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    Collection<String> superClass = cachedASMReflector.getSuperClass("java.util.ArrayList");
    for (String clazz : superClass) {
      System.out.println(clazz);
    }
  }
}
