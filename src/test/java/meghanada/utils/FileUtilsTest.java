package meghanada.utils;

import org.junit.Test;

import java.io.File;

import static meghanada.config.Config.timeIt;

public class FileUtilsTest {

    @Test
    public void md5sum1() throws Exception {
        final String sum = timeIt(() -> {
            return FileUtils.md5sum(new File("./src/main/java/meghanada/compiler/SimpleJavaCompiler.java"));
        });
        System.out.println(sum);
    }

    @Test
    public void md5sum2() throws Exception {
        final String javaHome = System.getProperty("java.home");
        final String sum = timeIt(() -> {
            return FileUtils.md5sum(new File(javaHome + "/lib/rt.jar"));
        });
        System.out.println(sum);
    }

}