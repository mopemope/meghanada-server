package meghanada.completion;

import com.google.common.cache.CacheBuilder;
import meghanada.GradleTestBase;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.session.JavaSourceLoader;
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
        File file = new File("./src/test/java/meghanada/TopClass.java");
        final Collection<? extends CandidateUnit> units = debugIt(() -> {
            return completion.completionAt(file, 8, 9, "*this");
        });
        units.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(15, units.size());
    }

    @Test
    public void testCompletion2() throws Exception {
        JavaCompletion completion = getCompletion();
        File file = new File("./src/test/java/meghanada/TopClass.java");
        final Collection<? extends CandidateUnit> units = debugIt(() -> {
            return completion.completionAt(file, 14, 9, "*this");
        });
        units.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(15, units.size());
    }

    @Test
    public void testCompletion3() throws Exception {
        JavaCompletion completion = getCompletion();
        File file = new File("./src/test/java/meghanada/TopClass.java");
        final Collection<? extends CandidateUnit> units = debugIt(() -> {
            return completion.completionAt(file, 8, 9, "fo");
        });
        units.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(1, units.size());
    }

    @Test
    public void testCompletion4() throws Exception {
        JavaCompletion completion = getCompletion();
        File file = new File("./src/test/java/meghanada/TopClass.java");
        final Collection<? extends CandidateUnit> units = debugIt(() -> {
            return completion.completionAt(file, 8, 9, "@Test");
        });
        units.forEach(a -> System.out.println(a.getDeclaration()));
        assertEquals(units.size(), 18);
    }

    private JavaCompletion getCompletion() throws Exception {
        return new JavaCompletion(CacheBuilder.newBuilder()
                .maximumSize(256)
                .build(new JavaSourceLoader(getProject())));
    }

}
