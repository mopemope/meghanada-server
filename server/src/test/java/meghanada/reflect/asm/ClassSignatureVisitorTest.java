package meghanada.reflect.asm;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import meghanada.GradleTestBase;
import meghanada.module.ModuleHelper;
import meghanada.reflect.ClassIndex;
import meghanada.utils.ClassNameUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;

public class ClassSignatureVisitorTest extends GradleTestBase {
  @BeforeClass
  public static void setup() throws Exception {
    GradleTestBase.setupReflector(false);
  }

  @AfterClass
  public static void shutdown() throws Exception {
    GradleTestBase.shutdown();
  }

  @Test
  public void testFormalType1() throws Exception {
    String fqcn = "java.util.Map";
    File jar = getRTJar();

    ClassSignatureVisitor visitor = doAnalyze(jar, fqcn);
    assertEquals("java.util.Map", visitor.getName());

    List<String> expected = new ArrayList<>();
    expected.add("K");
    expected.add("V");

    assertEquals(expected, visitor.getTypeParameters());
    final ClassIndex index = visitor.getClassIndex();
    System.out.println(index.isInterface());
    assertEquals(true, index.isInterface());
    assertEquals(0, index.getSupers().size());
  }

  @org.junit.Test
  public void testFormalType2() throws Exception {
    String fqcn = "java.util.List";
    File jar = getRTJar();
    ClassSignatureVisitor visitor = doAnalyze(jar, fqcn);

    assertEquals("java.util.List", visitor.getName());

    List<String> expected = new ArrayList<>();
    expected.add("E");

    assertEquals(expected, visitor.getTypeParameters());
    final ClassIndex index = visitor.getClassIndex();
    assertEquals(true, index.isInterface());
    assertEquals(1, index.getSupers().size());
  }

  @org.junit.Test
  public void testFunctionType1() throws Exception {
    String fqcn = "java.util.function.Predicate";
    File jar = getRTJar();
    ClassSignatureVisitor visitor = doAnalyze(jar, fqcn);

    assertEquals("java.util.function.Predicate", visitor.getName());

    List<String> expected = new ArrayList<>();
    expected.add("T");

    assertEquals(expected, visitor.getTypeParameters());
    final ClassIndex index = visitor.getClassIndex();
    assertEquals(true, index.isInterface());
    assertEquals(0, index.getSupers().size());
  }

  @org.junit.Test
  public void testComplexType1() throws Exception {
    String fqcn = "com.google.common.cache.AbstractLoadingCache";
    File jar = getJar("com.google.guava:guava:");
    ClassSignatureVisitor visitor = doAnalyze(jar, fqcn);

    assertEquals(fqcn, visitor.getName());
    List<String> expected = new ArrayList<>();
    expected.add("K");
    expected.add("V");
    assertEquals(expected, visitor.getTypeParameters());

    visitor.getSuperClasses().forEach(cd -> System.out.println("super:" + cd.getClassName()));
    final ClassIndex index = visitor.getClassIndex();
    assertEquals(false, index.isInterface());
    assertEquals(2, index.getSupers().size());
  }

  @org.junit.Test
  public void testInnerClass() throws Exception {
    String fqcn = "java.util.Map$Entry";
    File jar = getRTJar();
    ClassSignatureVisitor visitor = doAnalyze(jar, fqcn);

    assertEquals("java.util.Map$Entry", visitor.getName());

    List<String> expected = new ArrayList<>();
    expected.add("K");
    expected.add("V");

    assertEquals(expected, visitor.getTypeParameters());
  }

  @org.junit.Test
  public void testSuperClass1() throws Exception {
    String fqcn = "java.util.stream.Stream";
    File jar = getRTJar();
    ClassSignatureVisitor visitor = doAnalyze(jar, fqcn);

    assertEquals("java.util.stream.Stream", visitor.getName());

    List<String> expected = new ArrayList<>();
    expected.add("T");

    assertEquals(expected, visitor.getTypeParameters());

    String superClass = "java.util.stream.BaseStream<%%T, java.util.stream.Stream<%%T>>";

    visitor
        .getSuperClasses()
        .forEach(
            cd -> {
              String nm = cd.getClassName();
              System.out.println("super:" + nm);
              if (nm.equals(ClassNameUtils.OBJECT_CLASS)) {
                // skip
              } else {
                assertEquals(superClass, nm);
              }
            });
    // assertEquals(superClass, visitor.getSuperClasses().);
  }

  @org.junit.Test
  public void testSuperClass2() throws Exception {
    String fqcn = "java.util.stream.BaseStream";
    File jar = getRTJar();
    ClassSignatureVisitor visitor = doAnalyze(jar, fqcn);

    assertEquals("java.util.stream.BaseStream", visitor.getName());

    List<String> expected = new ArrayList<>();
    expected.add("T");
    expected.add("S");

    assertEquals(expected, visitor.getTypeParameters());

    String superClass = "java.lang.AutoCloseable";

    visitor
        .getSuperClasses()
        .forEach(
            cd -> {
              String nm = cd.getClassName();
              System.out.println("super:" + nm);
              if (nm.equals(ClassNameUtils.OBJECT_CLASS)) {
                // skip
              } else {
                assertEquals(superClass, cd.getClassName());
              }
            });
    // assertEquals(superClass, visitor.getSupers());
  }

  @org.junit.Test
  public void testSuperClass3() throws Exception {
    String fqcn = "java.util.AbstractMap";
    File jar = getRTJar();
    ClassSignatureVisitor visitor = doAnalyze(jar, fqcn);

    assertEquals("java.util.AbstractMap", visitor.getName());

    List<String> expected = new ArrayList<>();
    expected.add("K");
    expected.add("V");

    assertEquals(expected, visitor.getTypeParameters());

    String superClass = "java.util.Map<%%K, %%V>";

    visitor
        .getSuperClasses()
        .forEach(
            cd -> {
              String nm = cd.getClassName();
              System.out.println("super:" + nm);
              if (nm.equals(ClassNameUtils.OBJECT_CLASS)) {
                // skip
              } else {
                assertEquals(superClass, cd.getClassName());
              }
            });
  }

  @org.junit.Test
  public void testSuperClass4() throws Exception {
    String fqcn = "meghanada.IF3";
    File out = getTestOutput();
    ClassSignatureVisitor visitor = doAnalyze(out, fqcn);
    String name = visitor.getName();
    ClassIndex index = visitor.getClassIndex();
    List<String> supers = index.getSupers();
    System.out.println(supers);
    assertEquals(2, supers.size());
  }

  private ClassSignatureVisitor doAnalyze(File file, String fqcn) throws IOException {

    if (ModuleHelper.isJrtFsFile(file)) {

      return ModuleHelper.searchModule(
              path -> {
                return ModuleHelper.pathToClassData(path)
                    .map(
                        cd -> {
                          try (final InputStream in = cd.getInputStream()) {
                            ClassReader classReader = new ClassReader(in);
                            String className = classReader.getClassName().replace("/", ".");

                            if (className.equals(fqcn)) {
                              final int access = classReader.getAccess();
                              TestVisitor testVisitor = new TestVisitor(className);
                              classReader.accept(testVisitor, 0);
                              return testVisitor.getVisitor();
                            }
                          } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                          }
                          return null;
                        })
                    .orElse(null);
              })
          .orElse(null);

    } else if (isJar(file)) {
      try (final JarFile jarFile = new JarFile(file)) {
        final Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
          JarEntry entry = entries.nextElement();
          String name = entry.getName();

          if (name.endsWith(".class")) {
            // log.debug("class {}", name);
            try (InputStream in = jarFile.getInputStream(entry)) {
              ClassReader classReader = new ClassReader(in);
              String className = classReader.getClassName().replace("/", ".");

              if (className.equals(fqcn)) {
                final int access = classReader.getAccess();
                TestVisitor testVisitor = new TestVisitor(className);
                classReader.accept(testVisitor, 0);
                return testVisitor.getVisitor();
              }
            }
          }
        }
      }
    } else if (file.isDirectory()) {
      try (Stream<Path> pathStream = Files.walk(file.toPath())) {
        List<File> files =
            pathStream
                .map(Path::toFile)
                .filter(f -> f.isFile() && f.getName().endsWith(".class"))
                .collect(Collectors.toList());
        for (File classFile : files) {
          String entryName = classFile.getName();
          if (!entryName.endsWith(".class")) {
            continue;
          }
          //                  String className =
          //                      ClassNameUtils.replaceSlash(entryName.substring(0,
          // entryName.length() - 6));
          try (InputStream in = new FileInputStream(classFile)) {
            ClassReader classReader = new ClassReader(in);
            String className = classReader.getClassName().replace("/", ".");

            if (className.equals(fqcn)) {
              final int access = classReader.getAccess();
              TestVisitor testVisitor = new TestVisitor(className);
              classReader.accept(testVisitor, 0);
              return testVisitor.getVisitor();
            }
          }
        }
      }
    }
    return null;
  }

  private static class TestVisitor extends ClassVisitor {

    private ClassSignatureVisitor visitor;
    private String name;

    TestVisitor(String name) {
      super(Opcodes.ASM7);
      this.name = name;
    }

    @Override
    public void visit(
        int api, int access, String name, String sig, String superClass, String[] exceptions) {
      System.out.println("NAME:" + name);
      System.out.println("SUPER:" + superClass);
      System.out.println("sig:" + sig);
      if (sig != null) {
        boolean isInterface = (Opcodes.ACC_INTERFACE & access) == Opcodes.ACC_INTERFACE;
        SignatureReader signatureReader = new SignatureReader(sig);
        this.visitor = new ClassSignatureVisitor(this.name, isInterface);
        signatureReader.accept(this.visitor);
      }
    }

    ClassSignatureVisitor getVisitor() {
      return visitor;
    }
  }

  static boolean isJar(File file) {
    return file.isFile() && file.getName().endsWith("jar");
  }
}
