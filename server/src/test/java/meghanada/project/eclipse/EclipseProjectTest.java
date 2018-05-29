package meghanada.project.eclipse;

import static meghanada.GradleTestBase.TEMP_PROJECT_SETTING_DIR;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import meghanada.project.Project;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class EclipseProjectTest {

  @Before
  public void setUp() throws Exception {
    System.setProperty("meghanada.source.cache", "false");
    final File tempDir = Files.createTempDir();
    tempDir.deleteOnExit();
    final String path = tempDir.getCanonicalPath();
    System.setProperty(TEMP_PROJECT_SETTING_DIR, path);
  }

  @After
  public void tearDown() throws Exception {}

  @Ignore
  @Test
  public void testParseProject1() throws IOException {
    File classPathFile = new File("./src/test/resources/.classpath").getCanonicalFile();
    File root = classPathFile.getParentFile();
    EclipseProject eclipseProject = new EclipseProject(root);
    Project project = eclipseProject.parseProject(root, root);
    System.out.println(project.toString());
  }
}
