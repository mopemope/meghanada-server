package meghanada.parser.source;

import meghanada.GradleTestBase;
import meghanada.parser.JavaParser;
import meghanada.reflect.asm.CachedASMReflector;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static meghanada.config.Config.debugIt;
import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;

public class JavaSourceTest extends GradleTestBase {

    @BeforeClass
    public static void beforeClass() throws Exception {
        GradleTestBase.setupReflector();
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();
    }

    @Test
    public void testOptimizeImports1() throws Exception {
        final JavaParser parser = new JavaParser();
        final JavaSource source = debugIt(() -> {
            return parser.parse(new File("./src/test/resources/MissingImport1.java"));
        });

        Map<String, List<String>> missingImport = debugIt(source::searchMissingImport);
        List<String> optimizeImports = debugIt(source::optimizeImports);

        System.out.println(missingImport);
        System.out.println(optimizeImports);
        assertEquals(5, missingImport.size());
        assertEquals(2, optimizeImports.size());
    }

    @Test
    public void testSearchMissingImport1() throws Exception {
        JavaSource source = timeIt(() -> {
            JavaParser parser = new JavaParser();
            return parser.parse(new File("./src/test/resources/MissingImport2.java"));
        });
        Map<String, List<String>> listMap = source.searchMissingImport();
        System.out.println(listMap);
        assertEquals(11, listMap.size());
    }
}
