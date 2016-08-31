package meghanada.config;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class ConfigTest {

    private String getProjectRoot() throws IOException {
        File f = new File("./");
        return f.getCanonicalPath();
    }

    @Before
    public void setUp() throws Exception {
        System.setProperty("project.root", getProjectRoot());
        System.setProperty("log-level", "DEBUG");
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testLoad1() throws Exception {
        final String projectRootDir = Config.load().getProjectRootDir();
        assertEquals(getProjectRoot(), projectRootDir);
        System.out.println(projectRootDir);
    }


}