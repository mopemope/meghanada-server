package meghanada.reflect.asm;

import static meghanada.config.Config.timeIt;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import meghanada.GradleTestBase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;

public class MethodSignatureVisitorTest extends GradleTestBase {

  @BeforeClass
  public static void setup() throws Exception {
    GradleTestBase.setupReflector(false);
  }

  @AfterClass
  public static void shutdown() throws Exception {
    GradleTestBase.shutdown();
  }

  @Test
  public void testMethod1() throws Exception {
    final File f = new File(getTestOutput(), "meghanada/Gen3.class");
    final String fqcn = "meghanada.Gen3";
    TestVisitor visitor = timeIt(() -> doAnalyze(f, fqcn));
  }

  private TestVisitor doAnalyze(File file, String fqcn) throws IOException {
    // log.debug("class {}", name);
    try (InputStream in = new FileInputStream(file)) {
      ClassReader classReader = new ClassReader(in);
      String className = classReader.getClassName().replace("/", ".");

      if (className.equals(fqcn)) {
        TestVisitor testVisitor = new TestVisitor(className);
        classReader.accept(testVisitor, 0);
        return testVisitor;
      }
    }
    return null;
  }

  private static class TestVisitor extends ClassVisitor {

    Map<String, TypeInfo> result = new HashMap<>();
    private MethodSignatureVisitor visitor;
    private String name;

    TestVisitor(String name) {
      super(Opcodes.ASM5);
      this.name = name;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String desc, String signature, String[] exceptions) {

      String target = signature;
      if (target == null) {
        target = desc;
      }
      SignatureReader signatureReader = new SignatureReader(target);
      this.visitor = new MethodSignatureVisitor(name, new ArrayList<>());
      signatureReader.accept(this.visitor);

      System.out.println(name);
      System.out.println(this.visitor.getFormalType());
      System.out.println(this.visitor.getParameterTypes());
      System.out.println(this.visitor.getTypeParameters());
      System.out.println(this.visitor.getReturnType());

      // result.put(name, this.visitor.getFormalType());

      return super.visitMethod(access, name, desc, signature, exceptions);
    }

    MethodSignatureVisitor getVisitor() {
      return visitor;
    }
  }
}
