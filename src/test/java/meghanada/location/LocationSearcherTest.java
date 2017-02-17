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
                return locationSearcher.searchDeclaration(f, 69, 18, "result");
            });
            assertNotNull(result);
            assertEquals(68, result.getLine());
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
            Location result = debugIt(() -> locationSearcher.searchDeclaration(f, 95, 37, "base"));
            assertNotNull(result);
            assertEquals(75, result.getLine());
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
            return locationSearcher.searchDeclaration(f, 211, 18, "currentProject");
        });
        assertNotNull(result);
        assertEquals(45, result.getLine());
        assertEquals(21, result.getColumn());
    }

    @Test
    public void testJumpMethod1() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        Location result = timeIt(() -> {
            return locationSearcher.searchDeclaration(f, 403, 31, "parseJavaSource");
        });

        assertNotNull(result);
        assertEquals(422, result.getLine());
        assertEquals(20, result.getColumn());
    }

    @Test
    public void testJumpMethod2() throws Exception {
        File f = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
        assert f.exists();

        LocationSearcher locationSearcher = getSearcher();
        {
            // return source.searchMissingImport();
            Location result = locationSearcher.searchDeclaration(f, 415, 24, "searchMissingImport");
            assertNotNull(result);
            assertTrue(result.getPath().contains("Source.java"));
            assertEquals(290, result.getLine());
            assertEquals(38, result.getColumn());
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

}
