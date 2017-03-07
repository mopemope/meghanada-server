package meghanada.location;

import meghanada.GradleTestBase;
import meghanada.cache.GlobalCache;
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
    public void testJumpVariable01() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = debugIt(() -> {
                return locationSearcher.searchDeclarationLocation(f, 73, 18, "result");
            });
            assertNotNull(result);
            assertEquals(72, result.getLine());
            assertEquals(33, result.getColumn());
        }
    }

    @Test
    public void testJumpParamVariable01() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            // private static Project findProject(File base) throws IOException
            Location result = debugIt(() -> locationSearcher.searchDeclarationLocation(f, 94, 36, "base"));
            assertNotNull(result);
            assertEquals(79, result.getLine());
            assertEquals(54, result.getColumn());
        }
    }

    @Test
    public void testJumpField01() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        // Set<File> sources = this.currentProject.getSourceDirectories();
        Location result = timeIt(() -> {
            return locationSearcher.searchDeclarationLocation(f, 234, 14, "currentProject");
        });
        assertNotNull(result);
        assertEquals(51, result.getLine());
        assertEquals(21, result.getColumn());
    }

    @Test
    public void testJumpField02() throws Exception {
        File f = new File("./src/main/java/meghanada/analyze/JavaAnalyzer.java").getCanonicalFile();
        assert f.exists();
        LocationSearcher locationSearcher = getSearcher();
        // Set<File> sources = this.currentProject.getSourceDirectories();
        Location result = timeIt(() -> {
            return locationSearcher.searchDeclarationLocation(f, 83, 51, "treeAnalyzer");
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
            return locationSearcher.searchDeclarationLocation(f, 9, 55, "CASE_INSENSITIVE_ORDER");
        });
        assertNotNull(result);
        assertEquals(1184, result.getLine());
        assertEquals(44, result.getColumn());
    }

    @Test
    public void testJumpMethod01() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        Location result = timeIt(() -> {
            return locationSearcher.searchDeclarationLocation(f, 392, 31, "parseJavaSource");
        });

        assertNotNull(result);
        assertEquals(429, result.getLine());
        assertEquals(20, result.getColumn());
    }

    @Test
    public void testJumpMethod02() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            // return source.searchMissingImport();
            Location result = locationSearcher.searchDeclarationLocation(f, 422, 24, "searchMissingImport");
            assertNotNull(result);
            assertTrue(result.getPath().contains("Source.java"));
            assertEquals(287, result.getLine());
            assertEquals(38, result.getColumn());
        }
    }

    @Test
    public void testJumpMethod03() throws Exception {
        File f = new File("./src/test/java/meghanada/Overload1.java").getCanonicalFile();
        assert f.exists();
        LocationSearcher locationSearcher = getSearcher();
        {
            GlobalCache.getInstance().invalidateSource(project, f);
            Location result = locationSearcher.searchDeclarationLocation(f, 26, 9, "over");
            assertNotNull(result);
            assertTrue(result.getPath().contains("Overload1.java"));
            assertEquals(16, result.getLine());
            assertEquals(17, result.getColumn());
        }
        {
            GlobalCache.getInstance().invalidateSource(project, f);
            Location result = locationSearcher.searchDeclarationLocation(f, 29, 9, "over");
            assertNotNull(result);
            assertTrue(result.getPath().contains("Overload1.java"));
            assertEquals(12, result.getLine());
            assertEquals(17, result.getColumn());
        }
        {
            GlobalCache.getInstance().invalidateSource(project, f);
            Location result = locationSearcher.searchDeclarationLocation(f, 31, 9, "over");
            assertNotNull(result);
            assertTrue(result.getPath().contains("Overload1.java"));
            assertEquals(20, result.getLine());
            assertEquals(17, result.getColumn());
        }
    }

    @Test
    public void testJumpMethod04() throws Exception {
        File f = new File("./src/test/java/meghanada/Jump1.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = locationSearcher.searchDeclarationLocation(f, 10, 20, "thenComparing");
            assertNotNull(result);
            assertTrue(result.getPath().contains(".java"));
            assertEquals(238, result.getLine());
            assertEquals(13, result.getColumn());
        }
    }

    @Test
    public void testJumpMethod05() throws Exception {
        File f = new File("./src/main/java/meghanada/location/LocationSearcher.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = timeIt(() -> {
                return locationSearcher.searchDeclarationLocation(f, 197, 24, "searchField");
            });
            assertNotNull(result);
            assertTrue(result.getPath().contains("LocationSearcher.java"));
            assertEquals(475, result.getLine());
            assertEquals(22, result.getColumn());
        }
    }

    @Test
    public void testJumpMethod06() throws Exception {
        File f = new File("./src/main/java/meghanada/location/LocationSearcher.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = timeIt(() -> {
                return locationSearcher.searchDeclarationLocation(f, 388, 76, "decompileArchive");
            });
            assertNotNull(result);
            assertTrue(result.getPath().contains(".java"));
            assertEquals(103, result.getLine());
            assertEquals(4, result.getColumn());
        }
    }

    @Test
    public void testJumpMethod07() throws Exception {
        File f = new File("./src/main/java/meghanada/reflect/asm/ASMReflector.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = timeIt(() -> {
                return locationSearcher.searchDeclarationLocation(f, 50, 18, "getAllowClass");
            });
            assertNotNull(result);
            assertTrue(result.getPath().contains("Config.java"));
            assertEquals(272, result.getLine());
            assertEquals(25, result.getColumn());
        }
    }

    @Test
    public void testJumpClass01() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher searcher = getSearcher();
        {
            Location result = timeIt(() -> searcher.searchDeclarationLocation(f, 372, 15, "Source"));
            assertNotNull(result);
            assertTrue(result.getPath().contains("Source.java"));
            assertEquals(19, result.getLine());
            assertEquals(14, result.getColumn());
        }
    }

    @Test
    public void testJumpClass02() throws Exception {
        File f = new File("./src/main/java/meghanada/reflect/asm/ASMReflector.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = locationSearcher.searchDeclarationLocation(f, 199, 20, "ClassAnalyzeVisitor");
            assertNotNull(result);
            assertTrue(result.getPath().contains("ClassAnalyzeVisitor.java"));
            assertEquals(14, result.getLine());
            assertEquals(7, result.getColumn());
        }
    }

    @Test
    public void testJumpClass03() throws Exception {
        File f = new File("./src/main/java/meghanada/reflect/asm/ASMReflector.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            Location result = locationSearcher.searchDeclarationLocation(f, 32, 56, "String");
            assertNotNull(result);
            assertTrue(result.getPath().contains(".java"));
            assertEquals(111, result.getLine());
            assertEquals(1, result.getColumn());
        }
    }

    @Test
    public void testJumpClass04() throws Exception {
        File f = new File("./src/main/java/meghanada/reflect/asm/ASMReflector.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            System.setProperty("disable-source-jar", "true");
            Location result = locationSearcher.searchDeclarationLocation(f, 44, 40, "LogManager");
            assertNotNull(result);
            assertTrue(result.getPath().contains(".java"));
            assertEquals(21, result.getLine());
            assertEquals(1, result.getColumn());
        }
    }

}
