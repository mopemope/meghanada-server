package meghanada.location;

import com.google.common.cache.CacheBuilder;
import meghanada.GradleTestBase;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.session.JavaSourceLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static meghanada.config.Config.debugIt;
import static meghanada.config.Config.timeIt;
import static org.junit.Assert.*;

public class LocationSearcherTest extends GradleTestBase {

    private static LocationSearcher searcher;

    @org.junit.BeforeClass
    public static void beforeClass() throws Exception {
        GradleTestBase.setupReflector();
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addJar(getOutputDir());
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

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testJumpNS1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java");
        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = timeIt(() -> {
                // return new Session(project);
                return locationSearcher.searchDeclaration(f, 75, 29, "project");
            });
            assertNotNull(result);
            assertEquals(70, result.getLine());
            assertEquals(23, result.getColumn());
        }
    }

    @Test
    public void testJumpParamNS1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java");
        LocationSearcher locationSearcher = getSearcher();
        {
            //     private static Project findProject(File dir) throws IOException
            Location result = locationSearcher.searchDeclaration(f, 87, 37, "dir");
            assertNotNull(result);
            assertEquals(78, result.getLine());
            assertEquals(45, result.getColumn());
        }
    }

    @Test
    public void testJumpField1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java");
        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = locationSearcher.searchDeclaration(f, 141, 35, "currentProject");
            assertNotNull(result);
            assertEquals(44, result.getLine());
            assertEquals(27, result.getColumn());
        }
    }

    @Test
    public void testJumpMethod1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java");
        LocationSearcher locationSearcher = getSearcher();
        Location result = debugIt(() -> {
            return locationSearcher.searchDeclaration(f, 212, 39, "parseJavaSource");
        });

        assertNotNull(result);
        assertEquals(231, result.getLine());
        assertEquals(24, result.getColumn());
    }

    @Test
    public void testJumpMethod2() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java");
        LocationSearcher locationSearcher = getSearcher();
        {
            // return source.searchMissingImport();
            Location result = locationSearcher.searchDeclaration(f, 224, 24, "searchMissingImport");
            assertNotNull(result);
            assertTrue(result.getPath().contains("JavaSource.java"));
            assertEquals(326, result.getLine());
            assertEquals(38, result.getColumn());
        }
    }

    @Test
    public void testJumpMethod3() throws Exception {
        File f = new File("src/test/resources/Lambda4.java");
        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = timeIt(() -> {
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
            assertEquals(22, result.getLine());
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
