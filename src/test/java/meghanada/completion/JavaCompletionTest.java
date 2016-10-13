package meghanada.completion;

import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import meghanada.GradleTestBase;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.session.JavaSourceLoader;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.Collection;

import static meghanada.config.Config.debugIt;
import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;

public class JavaCompletionTest extends GradleTestBase {

    @BeforeClass
    public static void beforeClass() throws Exception {
        GradleTestBase.setupReflector();
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.addClasspath(getTestOutputDir());
        cachedASMReflector.createClassIndexes();
    }

    @Test
    public void testCompletion1() throws Exception {
        JavaCompletion completion = getCompletion1();
        File file = new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java");
        final Collection<? extends CandidateUnit> units = completion.completionAt(file, 0, 0, "*package");
        assertEquals(1, units.size());
        units.forEach(a -> System.out.println(a.getDeclaration()));
    }

    @Test
    public void testCompletion2() throws Exception {
        JavaCompletion completion = getCompletion1();
        File file = new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java");

        final Collection<? extends CandidateUnit> units1 = completion.completionAt(file, 0, 0, "*method:java.util.Map");
        units1.forEach(md -> System.out.println(md.getDeclaration()));
        assertEquals(32, units1.size());
        System.out.println(Strings.repeat("-", 120));
        final Collection<? extends CandidateUnit> units2 = completion.completionAt(file, 0, 0, "*method:java.util.Map<? extends String, ? extends Long>");
        units2.forEach(md -> {
            System.out.println(md.getDeclaration());
            System.out.println(md.getReturnType());
        });
        assertEquals(32, units1.size());
    }

    @Test
    public void testCompletion3() throws Exception {
        JavaCompletion completion = getCompletion1();
        File file = new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java");

        // return CompileResult
        final Collection<? extends CandidateUnit> units1 = timeIt(() -> {
            return completion.completionAt(file, 40, 80, "*method");
        });
        units1.forEach(md -> System.out.println(md));
        assertEquals(15, units1.size());
    }

    @Test
    public void testCompletion4() throws Exception {
        JavaCompletion completion = getCompletion1();
        File file = new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java");

        // this.compileTarget = compileTarget;
        final Collection<? extends CandidateUnit> units1 = timeIt(() -> {
            return completion.completionAt(file, 34, 43, "*method");
        });
        units1.forEach(md -> System.out.println(md.getDisplayDeclaration()));
        assertEquals(61, units1.size());
    }

    @Test
    public void testCompletion5() throws Exception {
        JavaCompletion completion = getCompletion1();
        File file = new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java");

        final Collection<? extends CandidateUnit> units1 = completion.completionAt(file, 0, 0, "*method:java.util.Enumeration<? extends String>");
        units1.forEach(md -> {
            System.out.println(md.getDeclaration());
            System.out.println(md.getReturnType());
        });
        assertEquals(11, units1.size());
    }

    @Ignore
    @Test
    public void testCompletion6() throws Exception {
        // TODO FIX
        JavaCompletion completion = getCompletion1();
        File file = new File("./src/main/java/meghanada/reflect/asm/ASMReflector.java");

        final Collection<? extends CandidateUnit> units1 = debugIt(() -> {
            return completion.completionAt(file, 154, 24, "ja");
        });
        units1.forEach(System.out::println);
        assertEquals(2, units1.size());
    }

    @Test
    public void testCompletion7() throws Exception {
        JavaCompletion completion = getCompletion1();
        File file = new File("./src/test/java/meghanada/TopClass.java");
        final Collection<? extends CandidateUnit> units = debugIt(() -> {
            return completion.completionAt(file, 8, 9, "*this");
        });
        units.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(15, units.size());
    }

    @Test
    public void testCompletion8() throws Exception {
        JavaCompletion completion = getCompletion1();
        File file = new File("./src/test/java/meghanada/TopClass.java");
        final Collection<? extends CandidateUnit> units = debugIt(() -> {
            return completion.completionAt(file, 14, 9, "*this");
        });
        units.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(14, units.size());
    }

    @Test
    public void testCompletion9() throws Exception {
        JavaCompletion completion = getCompletion1();
        File file = new File("./src/test/java/meghanada/TopClass.java");
        final Collection<? extends CandidateUnit> units = debugIt(() -> {
            return completion.completionAt(file, 8, 9, "fo");
        });
        units.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(units.size(), 1);
    }

    @Test
    public void testCompletion10() throws Exception {
        JavaCompletion completion = getCompletion1();
        File file = new File("./src/test/java/meghanada/TopClass.java");
        final Collection<? extends CandidateUnit> units = debugIt(() -> {
            return completion.completionAt(file, 8, 9, "@Test");
        });
        units.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(units.size(), 18);
    }

    private JavaCompletion getCompletion1() throws Exception {
        return new JavaCompletion(CacheBuilder.newBuilder()
                .maximumSize(256)
                .build(new JavaSourceLoader()));
    }

}
