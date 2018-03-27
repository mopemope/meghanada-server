package meghanada.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Ignore;
import org.junit.Test;

public class SessionTest {

  @Ignore
  @Test
  public void testSwitchTest() throws Exception {
    Session session = null;
    try {
      session = Session.createSession("./");
      session.start();
      File root = session.getCurrentProject().getProjectRoot();
      File src = new File("./src/main/java/meghanada/session/Session.java").getCanonicalFile();
      assert src.exists();
      String srcPath = src.getCanonicalPath();
      String testPath = session.switchTest(srcPath).orElse(null);
      assertNotNull(testPath);
      String srcPath2 = session.switchTest(testPath).orElse(null);
      assertEquals(srcPath, srcPath2);
      assertTrue(testPath.endsWith("src/test/java/meghanada/session/SessionTest.java"));
    } finally {
      if (session != null) {
        session.shutdown(5);
      }
    }
  }
}
