package meghanada.config;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ConfigTest {

  private String getProjectRoot() throws IOException {
    File f = new File("./").getCanonicalFile();
    assert f.exists();

    return f.getCanonicalPath();
  }

  @Before
  public void setUp() throws Exception {
    System.setProperty("project.root", getProjectRoot());
    System.setProperty("log-level", "DEBUG");
  }

  @After
  public void tearDown() throws Exception {}

  @Ignore
  @Test
  public void testLoad1() throws Exception {
    final String projectRootDir = Config.load().getProjectRootDir();
    System.out.println(projectRootDir);
    System.out.println(getProjectRoot());
    assertEquals(getProjectRoot(), projectRootDir);
  }
}
