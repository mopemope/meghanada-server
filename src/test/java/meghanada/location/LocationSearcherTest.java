package meghanada.location;

import meghanada.GradleTestBase;
import meghanada.reflect.asm.CachedASMReflector;
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
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();
    }

    private static LocationSearcher getSearcher() throws Exception {
        if (searcher != null) {
            return searcher;
        }
        searcher = new LocationSearcher(getProject());
        return searcher;
    }

    @Test
    public void testJumpVariable1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = debugIt(() -> {
                return locationSearcher.searchDeclaration(f, 70, 18, "result");
            });
            assertNotNull(result);
            assertEquals(69, result.getLine());
            assertEquals(33, result.getColumn());
        }
    }

    @Test
    public void testJumpParamVariable1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            // private static Project findProject(File base) throws IOException
            Location result = debugIt(() -> locationSearcher.searchDeclaration(f, 94, 36, "base"));
            assertNotNull(result);
            assertEquals(76, result.getLine());
            assertEquals(54, result.getColumn());
        }
    }

    @Test
    public void testJumpField1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        // Set<File> sources = this.currentProject.getSourceDirectories();
        Location result = timeIt(() -> {
            return locationSearcher.searchDeclaration(f, 231, 14, "currentProject");
        });
        assertNotNull(result);
        assertEquals(49, result.getLine());
        assertEquals(21, result.getColumn());
    }

    @Test
    public void testJumpField2() throws Exception {
        File f = new File("./src/main/java/meghanada/analyze/JavaAnalyzer.java").getCanonicalFile();
        assert f.exists();
        LocationSearcher locationSearcher = getSearcher();
        // Set<File> sources = this.currentProject.getSourceDirectories();
        Location result = timeIt(() -> {
            return locationSearcher.searchDeclaration(f, 83, 51, "treeAnalyzer");
        });
        assertNotNull(result);
        assertEquals(26, result.getLine());
        assertEquals(32, result.getColumn());
    }

    @Test
    public void testJumpField3() throws Exception {
        File f = new File("./src/test/java/meghanada/Jump1.java").getCanonicalFile();
        assert f.exists();
        LocationSearcher locationSearcher = getSearcher();
        // Set<File> sources = this.currentProject.getSourceDirectories();
        Location result = timeIt(() -> {
            return locationSearcher.searchDeclaration(f, 9, 55, "CASE_INSENSITIVE_ORDER");
        });
        assertNotNull(result);
        assertEquals(1184, result.getLine());
        assertEquals(44, result.getColumn());
    }

    @Test
    public void testJumpMethod1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        Location result = timeIt(() -> {
            return locationSearcher.searchDeclaration(f, 389, 31, "parseJavaSource");
        });

        assertNotNull(result);
        assertEquals(426, result.getLine());
        assertEquals(20, result.getColumn());
    }

    @Test
    public void testJumpMethod2() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            // return source.searchMissingImport();
            Location result = locationSearcher.searchDeclaration(f, 419, 24, "searchMissingImport");
            assertNotNull(result);
            assertTrue(result.getPath().contains("Source.java"));
            assertEquals(287, result.getLine());
            assertEquals(38, result.getColumn());
        }
    }

    @Test
    public void testJumpMethod3() throws Exception {
        File f = new File("./src/test/java/meghanada/Overload1.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            // return source.searchMissingImport();
            Location result = locationSearcher.searchDeclaration(f, 22, 9, "over");
            assertNotNull(result);
            assertTrue(result.getPath().contains("Overload1.java"));
            assertEquals(16, result.getLine());
            assertEquals(17, result.getColumn());
        }
        {
            // return source.searchMissingImport();
            Location result = locationSearcher.searchDeclaration(f, 25, 9, "over");
            assertNotNull(result);
            assertTrue(result.getPath().contains("Overload1.java"));
            assertEquals(12, result.getLine());
            assertEquals(17, result.getColumn());
        }
    }

    @Test
    public void testJumpMethod4() throws Exception {
        File f = new File("./src/test/java/meghanada/Jump1.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = locationSearcher.searchDeclaration(f, 10, 20, "thenComparing");
            assertNotNull(result);
            assertTrue(result.getPath().contains(".java"));
            assertEquals(238, result.getLine());
            assertEquals(13, result.getColumn());
        }
    }

    @Test
    public void testJumpMethod5() throws Exception {
        File f = new File("./src/main/java/meghanada/location/LocationSearcher.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = timeIt(() -> {
                return locationSearcher.searchDeclaration(f, 170, 24, "searchField");
            });
            assertNotNull(result);
            assertTrue(result.getPath().contains("LocationSearcher.java"));
            assertEquals(344, result.getLine());
            assertEquals(22, result.getColumn());
        }
    }

    @Test
    public void testJumpClass1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher searcher = getSearcher();
        {
            Location result = timeIt(() -> searcher.searchDeclaration(f, 372, 15, "Source"));
            assertNotNull(result);
            assertTrue(result.getPath().contains("Source.java"));
            assertEquals(19, result.getLine());
            assertEquals(14, result.getColumn());
        }
    }

    @Test
    public void testJumpClass2() throws Exception {
        File f = new File("./src/main/java/meghanada/reflect/asm/ASMReflector.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = locationSearcher.searchDeclaration(f, 199, 20, "ClassAnalyzeVisitor");
            assertNotNull(result);
            assertTrue(result.getPath().contains("ClassAnalyzeVisitor.java"));
            assertEquals(14, result.getLine());
            assertEquals(7, result.getColumn());
        }
    }

    @Test
    public void testJumpClass3() throws Exception {
        File f = new File("./src/main/java/meghanada/reflect/asm/ASMReflector.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = locationSearcher.searchDeclaration(f, 32, 56, "String");
            assertNotNull(result);
            assertTrue(result.getPath().contains(".java"));
            assertEquals(111, result.getLine());
            assertEquals(1, result.getColumn());
        }
    }

}
