package meghanada.location;

import meghanada.GradleTestBase;
import meghanada.cache.GlobalCache;
import meghanada.reflect.asm.CachedASMReflector;
import org.junit.Test;

import java.io.File;

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

        LocationSearcher searcher = getSearcher();
        Location result = timeIt(() ->
                searcher.searchDeclarationLocation(f, 78, 16, "result"))
                .orElse(null);
        assertNotNull(result);
        assertEquals(77, result.getLine());
        assertEquals(33, result.getColumn());
    }

    @Test
    public void testJumpParamVariable01() throws Exception {
        final File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        final LocationSearcher searcher = getSearcher();
        // private static Project findProject(File base) throws IOException
        final Location result = timeIt(() ->
                searcher.searchDeclarationLocation(f, 101, 36, "base"))
                .orElse(null);
        assertNotNull(result);
        assertEquals(83, result.getLine());
        assertEquals(54, result.getColumn());
    }

    @Test
    public void testJumpField01() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher searcher = getSearcher();
        // Set<File> sources = this.currentProject.getSourceDirectories();
        Location result = timeIt(() ->
                searcher.searchDeclarationLocation(f, 242, 14, "currentProject"))
                .orElse(null);
        assertNotNull(result);
        assertEquals(56, result.getLine());
        assertEquals(21, result.getColumn());
    }

    @Test
    public void testJumpField3() throws Exception {
        File f = new File("./src/test/java/meghanada/Jump1.java").getCanonicalFile();
        assert f.exists();
        LocationSearcher searcher = getSearcher();
        // Set<File> sources = this.currentProject.getSourceDirectories();
        Location result = timeIt(() ->
                searcher.searchDeclarationLocation(f, 9, 55, "CASE_INSENSITIVE_ORDER"))
                .orElse(null);
        assertNotNull(result);
        assertEquals(1184, result.getLine());
        assertEquals(44, result.getColumn());
    }

    @Test
    public void testJumpMethod01() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher searcher = getSearcher();
        Location result = timeIt(() ->
                searcher.searchDeclarationLocation(f, 412, 14, "parseJavaSource"))
                .orElse(null);

        assertNotNull(result);
        assertEquals(464, result.getLine());
        assertEquals(30, result.getColumn());
    }

    @Test
    public void testJumpMethod02() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher searcher = getSearcher();
        // return source.searchMissingImport();
        Location result = searcher.searchDeclarationLocation(f, 460, 49, "searchMissingImport")
                .orElse(null);
        assertNotNull(result);
        assertTrue(result.getPath().contains("Source.java"));
        assertEquals(313, result.getLine());
        assertEquals(38, result.getColumn());
    }

    @Test
    public void testJumpMethod03() throws Exception {
        File f = new File("./src/test/java/meghanada/Overload1.java").getCanonicalFile();
        assert f.exists();
        LocationSearcher searcher = getSearcher();
        {
            GlobalCache.getInstance().invalidateSource(project, f);
            Location result = searcher.searchDeclarationLocation(f, 26, 9, "over")
                    .orElse(null);
            assertNotNull(result);
            assertTrue(result.getPath().contains("Overload1.java"));
            assertEquals(16, result.getLine());
            assertEquals(17, result.getColumn());
        }
        {
            GlobalCache.getInstance().invalidateSource(project, f);
            Location result = searcher.searchDeclarationLocation(f, 29, 9, "over")
                    .orElse(null);
            assertNotNull(result);
            assertTrue(result.getPath().contains("Overload1.java"));
            assertEquals(12, result.getLine());
            assertEquals(17, result.getColumn());
        }
        {
            GlobalCache.getInstance().invalidateSource(project, f);
            Location result = searcher.searchDeclarationLocation(f, 31, 9, "over")
                    .orElse(null);
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

        LocationSearcher searcher = getSearcher();
        Location result = searcher.searchDeclarationLocation(f, 10, 20, "thenComparing")
                .orElse(null);
        assertNotNull(result);
        assertTrue(result.getPath().contains(".java"));
        assertEquals(238, result.getLine());
        assertEquals(31, result.getColumn());
    }

    @Test
    public void testJumpMethod05() throws Exception {
        File f = new File("./src/main/java/meghanada/location/LocationSearcher.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher searcher = getSearcher();
        Location result = timeIt(() ->
                searcher.searchDeclarationLocation(f, 296, 24, "searchFieldAccess"))
                .orElse(null);
        assertNotNull(result);
        assertTrue(result.getPath().contains("LocationSearcher.java"));
        assertEquals(622, result.getLine());
        assertEquals(32, result.getColumn());
    }

    @Test
    public void testJumpMethod06() throws Exception {
        File f = new File("./src/main/java/meghanada/location/LocationSearcher.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher searcher = getSearcher();
        Location result = timeIt(() -> {
            System.setProperty("disable-source-jar", "true");
            return searcher.searchDeclarationLocation(f, 532, 76, "decompileArchive");
        }).orElse(null);
        assertNotNull(result);
        assertTrue(result.getPath().contains(".java"));
        assertEquals(103, result.getLine());
        assertEquals(31, result.getColumn());
    }

    @Test
    public void testJumpMethod07() throws Exception {
        File f = new File("./src/main/java/meghanada/reflect/asm/ASMReflector.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher searcher = getSearcher();
        Location result = timeIt(() ->
                searcher.searchDeclarationLocation(f, 50, 18, "getAllowClass"))
                .orElse(null);
        assertNotNull(result);
        assertTrue(result.getPath().contains("Config.java"));
        assertEquals(322, result.getLine());
        assertEquals(25, result.getColumn());
    }

    @Test
    public void testJumpMethod09() throws Exception {
        File f = new File("./src/test/java/meghanada/JumpWithNull.java").getCanonicalFile();
        assert f.exists();
        LocationSearcher searcher = getSearcher();
        {
            GlobalCache.getInstance().invalidateSource(project, f);
            Location result = searcher.searchDeclarationLocation(f, 12, 22, "main")
                    .orElse(null);
            assertNotNull(result);
            assertTrue(result.getPath().contains("JumpWithNull.java"));
            assertEquals(6, result.getLine());
            assertEquals(24, result.getColumn());
        }
    }

    @Test
    public void testJumpMethod08() throws Exception {
        File f = new File("./src/test/java/meghanada/ArrayOverload.java").getCanonicalFile();
        assert f.exists();
        LocationSearcher searcher = getSearcher();
        {
            GlobalCache.getInstance().invalidateSource(project, f);
            Location result = searcher.searchDeclarationLocation(f, 16, 23, "over")
                    .orElse(null);
            assertNotNull(result);
            assertTrue(result.getPath().contains("ArrayOverload.java"));
            assertEquals(7, result.getLine());
            assertEquals(24, result.getColumn());
        }
    }

    @Test
    public void testJumpClass01() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher searcher = getSearcher();
        Location result = timeIt(() ->
                searcher.searchDeclarationLocation(f, 407, 22, "Source"))
                .orElse(null);
        assertNotNull(result);
        assertTrue(result.getPath().contains("Source.java"));
        assertEquals(20, result.getLine());
        assertEquals(14, result.getColumn());
    }

    @Test
    public void testJumpClass02() throws Exception {
        File f = new File("./src/main/java/meghanada/reflect/asm/ASMReflector.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        Location result = locationSearcher.searchDeclarationLocation(f, 199, 20, "ClassAnalyzeVisitor")
                .orElse(null);
        assertNotNull(result);
        assertTrue(result.getPath().contains("ClassAnalyzeVisitor.java"));
        assertEquals(14, result.getLine());
        assertEquals(7, result.getColumn());
    }

    @Test
    public void testJumpClass03() throws Exception {
        File f = new File("./src/main/java/meghanada/reflect/asm/ASMReflector.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        Location result = locationSearcher.searchDeclarationLocation(f, 32, 56, "String")
                .orElse(null);
        assertNotNull(result);
        assertTrue(result.getPath().contains(".java"));
        assertEquals(111, result.getLine());
        assertEquals(20, result.getColumn());
    }

    @Test
    public void testJumpClass04() throws Exception {
        File f = new File("./src/main/java/meghanada/reflect/asm/ASMReflector.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher searcher = getSearcher();
        System.setProperty("disable-source-jar", "true");
        Location result = searcher.searchDeclarationLocation(f, 44, 40, "LogManager").orElse(null);
        assertNotNull(result);
        assertTrue(result.getPath().contains(".java"));
        assertEquals(21, result.getLine());
        assertEquals(14, result.getColumn());
    }

    @Test
    public void testJumpAnnotation01() throws Exception {
        File f = new File("./src/main/java/meghanada/analyze/FieldAccess.java").getCanonicalFile();
        assert f.exists();

        final LocationSearcher searcher = getSearcher();
        final Location result = timeIt(() ->
                searcher.searchDeclarationLocation(f, 15, 16, "@Override"))
                .orElse(null);
        assertNotNull(result);
        assertEquals(51, result.getLine());
        assertEquals(19, result.getColumn());
    }

    @Test
    public void testJumpEnum01() throws Exception {
        final File f = new File("./src/test/java/meghanada/Enum2.java").getCanonicalFile();
        assert f.exists();

        final LocationSearcher searcher = getSearcher();
        final Location l1 = timeIt(() ->
                searcher.searchDeclarationLocation(f, 5, 23, "Key"))
                .orElse(null);
        assertNotNull(l1);
        assertEquals(8, l1.getLine());
        assertEquals(17, l1.getColumn());
    }

    @Test
    public void testJumpEnum02() throws Exception {
        final File f = new File("./src/test/java/meghanada/Enum2.java").getCanonicalFile();
        assert f.exists();

        final LocationSearcher searcher = getSearcher();
        final Location l1 = timeIt(() ->
                searcher.searchDeclarationLocation(f, 5, 27, "ONE"))
                .orElse(null);
        assertNotNull(l1);
        assertEquals(9, l1.getLine());
        assertEquals(9, l1.getColumn());

        final Location l2 = timeIt(() ->
                searcher.searchDeclarationLocation(f, 6, 29, "TWO"))
                .orElse(null);
        assertNotNull(l2);
        assertEquals(9, l2.getLine());
        assertEquals(14, l2.getColumn());
    }

    @Test
    public void testJumpEnum03() throws Exception {
        final File f = new File("./src/test/java/meghanada/Enum3.java").getCanonicalFile();
        assert f.exists();

        final LocationSearcher searcher = getSearcher();
        final Location l1 = timeIt(() ->
                searcher.searchDeclarationLocation(f, 5, 5, "Key"))
                .orElse(null);
        assertNotNull(l1);
        assertEquals(7, l1.getLine());
        assertEquals(10, l1.getColumn());

    }

}
