package meghanada.completion;

import meghanada.GradleTestBase;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.asm.CachedASMReflector;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Collection;

import static meghanada.config.Config.debugIt;
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
        JavaCompletion completion = getCompletion();
        File file = new File("./src/test/java/meghanada/TopClass.java").getCanonicalFile();
        assert file.exists();
        final Collection<? extends CandidateUnit> units = debugIt(() -> {
            return completion.completionAt(file, 8, 9, "*this");
        });
        units.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(15, units.size());
    }

    @Test
    public void testCompletion2() throws Exception {
        JavaCompletion completion = getCompletion();
        File file = new File("./src/test/java/meghanada/TopClass.java").getCanonicalFile();
        assert file.exists();
        final Collection<? extends CandidateUnit> units = debugIt(() -> {
            return completion.completionAt(file, 14, 9, "*this");
        });
        units.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(15, units.size());
    }

    @Test
    public void testCompletion3() throws Exception {
        JavaCompletion completion = getCompletion();
        File file = new File("./src/test/java/meghanada/TopClass.java").getCanonicalFile();
        assert file.exists();

        final Collection<? extends CandidateUnit> units = debugIt(() -> {
            return completion.completionAt(file, 8, 9, "fo");
        });
        units.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(1, units.size());
    }

    @Test
    public void testCompletion4() throws Exception {
        JavaCompletion completion = getCompletion();
        File file = new File("./src/test/java/meghanada/TopClass.java").getCanonicalFile();
        assert file.exists();

        final Collection<? extends CandidateUnit> units = debugIt(() -> {
            return completion.completionAt(file, 8, 9, "@Test");
        });
        units.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(22, units.size());
    }

    @Test
    public void testCompletion5() throws Exception {
        JavaCompletion completion = getCompletion();
        File file = new File("./src/main/java/meghanada/analyze/ExpressionScope.java").getCanonicalFile();
        assert file.exists();
        final Collection<? extends CandidateUnit> staticLog = debugIt(() -> {
            return completion.completionAt(file, 17, 4, "lo");
        });
        staticLog.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(staticLog.size(), 1);

        final Collection<? extends CandidateUnit> pos = debugIt(() -> {
            return completion.completionAt(file, 17, 8, "po");
        });
        pos.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(pos.size(), 1);
    }

    @Test
    public void testCompletion6() throws Exception {
        JavaCompletion completion = getCompletion();
        File file = new File("./src/main/java/meghanada/analyze/ExpressionScope.java").getCanonicalFile();
        assert file.exists();
        final Collection<? extends CandidateUnit> logMethod = debugIt(() -> {
            return completion.completionAt(file, 17, 4, "*log#");
        });
        logMethod.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(logMethod.size(), 369);

    }

    @Test
    public void testCompletion7() throws Exception {
        JavaCompletion completion = getCompletion();
        File file = new File("./src/main/java/meghanada/analyze/ExpressionScope.java").getCanonicalFile();
        assert file.exists();

        final Collection<? extends CandidateUnit> logMethod = debugIt(() -> {
            return completion.completionAt(file, 17, 4, "*method:java.lang.System#");
        });
        logMethod.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(logMethod.size(), 39);

    }

    @Test
    public void testCompletion8() throws Exception {
        JavaCompletion completion = getCompletion();
        File file = new File("./src/main/java/meghanada/project/gradle/GradleProject.java").getCanonicalFile();
        assert file.exists();

        final Collection<? extends CandidateUnit> logMethod = debugIt(() -> {
            return completion.completionAt(file, 31, 4, "lo");
        });
        logMethod.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(4, logMethod.size());

    }

    private JavaCompletion getCompletion() throws Exception {
        return new JavaCompletion(getProject());
    }

}
