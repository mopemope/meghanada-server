package meghanada.location;

import com.google.common.cache.CacheBuilder;
import meghanada.GradleTestBase;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.session.JavaSourceLoader;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

import static meghanada.config.Config.timeIt;
import static meghanada.config.Config.traceIt;
import static org.junit.Assert.*;

public class LocationSearcherTest extends GradleTestBase {

    private static LocationSearcher searcher;

    @org.junit.BeforeClass
    public static void beforeClass() throws Exception {
        GradleTestBase.setupReflector();
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();
    }

    private static LocationSearcher getSearcher() throws Exception {
        if (searcher != null) {
            return searcher;
        }
        searcher = new LocationSearcher(getSourceDir(), CacheBuilder.newBuilder()
                .maximumSize(256)
                .build(new JavaSourceLoader()));
        return searcher;
    }

    @Test
    public void testJumpNS1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java");
        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = timeIt(() -> {
                // return new Session(project);
                return locationSearcher.searchDeclaration(f, 78, 29, "project");
            });
            assertNotNull(result);
            assertEquals(78, result.getLine());
            assertEquals(23, result.getColumn());
        }
    }

    @Test
    public void testJumpParamNS1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java");
        LocationSearcher locationSearcher = getSearcher();
        {
            //     private static Project findProject(File base) throws IOException
            Location result = locationSearcher.searchDeclaration(f, 98, 37, "base");
            assertNotNull(result);
            assertEquals(85, result.getLine());
            assertEquals(45, result.getColumn());
        }
    }

    @Test
    public void testJumpField1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java");
        LocationSearcher locationSearcher = getSearcher();
        {
            // Set<File> sources = this.currentProject.getSourceDirectories();
            Location result = locationSearcher.searchDeclaration(f, 212, 35, "currentProject");
            assertNotNull(result);
            assertEquals(51, result.getLine());
            assertEquals(21, result.getColumn());
        }
    }

    @Test
    public void testJumpMethod1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java");
        LocationSearcher locationSearcher = getSearcher();
        Location result = timeIt(() -> {
            return locationSearcher.searchDeclaration(f, 367, 39, "parseJavaSource");
        });

        assertNotNull(result);
        assertEquals(398, result.getLine());
        assertEquals(24, result.getColumn());
    }

    @Test
    public void testJumpMethod2() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java");
        LocationSearcher locationSearcher = getSearcher();
        {
            // return source.searchMissingImport();
            Location result = locationSearcher.searchDeclaration(f, 391, 24, "searchMissingImport");
            assertNotNull(result);
            assertTrue(result.getPath().contains("JavaSource.java"));
            assertEquals(308, result.getLine());
            assertEquals(38, result.getColumn());
        }
    }

    @Test
    @Ignore
    public void testJumpMethod3() throws Exception {
        File f = new File("src/test/resources/Lambda4.java");
        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = traceIt(() -> {
                return locationSearcher.searchDeclaration(f, 19, 27, "println");
            });
            assertNotNull(result);
            assertEquals(8, result.getLine());
            assertEquals(18, result.getColumn());
        }
    }

    @Test
    public void testJumpClass1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java");
        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = timeIt(() -> {
                return locationSearcher.searchDeclaration(f, 206, 15, "JavaSource");
            });
            assertNotNull(result);
            assertTrue(result.getPath().contains("JavaSource.java"));
            assertEquals(21, result.getLine());
            assertEquals(14, result.getColumn());
        }
    }

    @Test
    public void testJumpClass2() throws Exception {
        File f = new File("./src/main/java/meghanada/reflect/asm/ASMReflector.java");
        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = locationSearcher.searchDeclaration(f, 199, 20, "ClassAnalyzeVisitor");
            assertNotNull(result);
            assertTrue(result.getPath().contains("ClassAnalyzeVisitor.java"));
            assertEquals(14, result.getLine());
            assertEquals(7, result.getColumn());
        }
    }

}
