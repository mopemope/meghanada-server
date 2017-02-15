package meghanada.session;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

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
            String testPath = session.switchTest(srcPath);
            assertNotNull(testPath);
            String srcPath2 = session.switchTest(testPath);
            assertEquals(srcPath, srcPath2);
            assertTrue(testPath.endsWith("src/test/java/meghanada/session/SessionTest.java"));
        } finally {
            if (session != null) {
                session.shutdown(5);
            }

        }
    }

}