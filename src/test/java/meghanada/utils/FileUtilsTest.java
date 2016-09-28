package meghanada.utils;

import meghanada.Main;
import org.junit.Test;

import java.io.File;

import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FileUtilsTest {

    @Test
    public void testMd5sum1() throws Exception {
        final String sum = timeIt(() -> {
            return FileUtils.md5sum(new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java"));
        });
        System.out.println(sum);
    }

    @Test
    public void testMd5sum2() throws Exception {
        final String javaHome = System.getProperty("java.home");
        final String sum = timeIt(() -> {
            return FileUtils.md5sum(new File(javaHome + "/lib/rt.jar"));
        });
        System.out.println(sum);
    }

    @Test
    public void testGetVersionInfo() throws Exception {
        final String version = timeIt(FileUtils::getVersionInfo);
        System.out.println(version);
        assertNotNull(version);
        assertTrue(version.startsWith(Main.VERSION));
    }

    @Test
    public void testToHashFile() throws Exception {
        final String res = timeIt(() -> {
            return FileUtils.toHashedPath(new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java"), ".sdat");
        });
        System.out.println(res);
    }

}