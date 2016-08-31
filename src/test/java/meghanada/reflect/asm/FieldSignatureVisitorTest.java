package meghanada.reflect.asm;

import meghanada.GradleTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class FieldSignatureVisitorTest extends GradleTestBase {
    @Before
    public void setUp() throws Exception {
        System.setProperty("log-level", "TRACE");
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testReturnClass1() throws Exception {
        File f = new File(getOutputDir(), "meghanada/reflect/ClassIndex.class");
        String fqcn = "meghanada.reflect.ClassIndex";
        TestVisitor visitor = doAnalyze(f, fqcn);
        System.out.println(visitor.result);
    }

    @Test
    public void testReturnClass2() throws Exception {
        File f = new File(getOutputDir(), "meghanada/reflect/asm/MethodAnalyzeVisitor.class");
        String fqcn = "meghanada.reflect.asm.MethodAnalyzeVisitor";
        TestVisitor visitor = doAnalyze(f, fqcn);
        System.out.println(visitor.result.get("lvtSlotIndex"));
        //System.out.println(visitor.classFileMap);
        visitor.result.entrySet().stream().forEach(entry -> {
            System.out.println("" + entry.getKey() + " : " + entry.getValue());
        });
    }

    @Test
    public void testReturnClass3() throws Exception {
        File f = new File(getTestOutputDir(), "meghanada/Gen1.class");
        String fqcn = "meghanada.Gen1";
        TestVisitor visitor = doAnalyze(f, fqcn);

        System.out.println(visitor.visitor.getTypeParameters());
        visitor.result.entrySet().stream().forEach(entry -> {
            System.out.println("" + entry.getKey() + " : " + entry.getValue());
        });
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

    private FieldSignatureVisitor doAnalyzeJar(File file, String fqcn) throws IOException {
        JarFile jarFile = new JarFile(file);
        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();

            if (name.endsWith(".class")) {
                // log.debug("class {}", name);
                try (InputStream in = jarFile.getInputStream(entry)) {
                    ClassReader classReader = new ClassReader(in);
                    String className = classReader.getClassName().replace("/", ".");

                    if (className.equals(fqcn)) {
                        TestVisitor testVisitor = new TestVisitor(className);
                        classReader.accept(testVisitor, 0);
                        return testVisitor.getVisitor();
                    }
                }
            }
        }
        return null;
    }

    private static class TestVisitor extends ClassVisitor {

        Map<String, String> result = new HashMap<>();
        private FieldSignatureVisitor visitor;
        private String name;

        TestVisitor(String name) {
            super(Opcodes.ASM5);
            this.name = name;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            String target = signature;
            if (target == null) {
                target = desc;
            }
            SignatureReader signatureReader = new SignatureReader(target);
            this.visitor = new FieldSignatureVisitor(name, new ArrayList<>());
            signatureReader.acceptType(this.visitor);
            result.put(name, this.visitor.getResult());
            return super.visitField(access, name, desc, signature, value);
        }

        FieldSignatureVisitor getVisitor() {
            return visitor;
        }
    }

}