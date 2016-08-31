package meghanada.session;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class SessionTest {

    @Ignore
    @Test
    public void testSwitchTest() throws Exception {
        Session session = null;
        try {
            session = Session.createSession("./");
            session.start();

            File root = session.getCurrentProject().getProjectRoot();
            File src = new File(root, "src/main/java/meghanada/parser/java/JavaParser.java");
            String srcPath = src.getCanonicalPath();
            String testPath = session.switchTest(srcPath);
            String srcPath2 = session.switchTest(testPath);
            assertEquals(srcPath, srcPath2);
        } finally {
            if (session != null) {
                session.shutdown(5);
            }

        }
    }

    @Ignore
    @Test
    public void testCreateTest() throws Exception {
        Session session = null;
        try {
            session = Session.createSession("./");
            session.start();

            File root = session.getCurrentProject().getProjectRoot();
            File src = new File(root, "src/java/meghanada/project/ProjectDependency.java");
            String srcPath = src.getCanonicalPath();
            String testFile = session.createJunitFile(srcPath);
            System.out.println(testFile);
        } finally {
            if (session != null) {
                session.shutdown(5);
            }

        }
    }

}