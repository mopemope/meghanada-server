package meghanada.project.gradle;

import meghanada.project.Project;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Map;

public class GradleProjectTest {

    private Project project;

    private void printEnv() {
        Map<String, String> env = System.getenv();

        for (Map.Entry<String, String> envEntry : env.entrySet()) {
            System.out.println(envEntry);
        }
    }

    @Before
    public void setUp() throws Exception {
        System.setProperty("log-level", "DEBUG");
        // printEnv();
        if (this.project == null) {
            this.project = new GradleProject(new File("./"));
        }
    }

    @After
    public void tearDown() throws Exception {
        System.out.printf("call teardown");
    }

    @Test
    public void testRunTask1() throws Exception {
        this.project.parseProject();
        final File outputDirectory = this.project.getOutputDirectory();
        System.out.println(outputDirectory);

//        try (BufferedReader br = this.project.runTask("clean")) {
//            for (; ; ) {
//                String line = br.readLine();
//                if (line == null) {
//                    break;
//                }
//                System.out.println(line);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

//    @Ignore
//    @Test
//    public void testRunTask2() throws Exception {
//        try (BufferedReader br = this.project.runTask("compileJava")) {
//            for (; ; ) {
//                String line = br.readLine();
//                if (line == null) {
//                    break;
//                }
//                System.out.println(line);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

//    @Ignore
//    @Test
//    public void testRunTest() throws Exception {
//        try (BufferedReader br = this.project.runTest("meghanada.reflect.asm.ASMReflectorTest")) {
//            for (; ; ) {
//                String line = br.readLine();
//                if (line == null) {
//                    break;
//                }
//                System.out.println(line);
//            }
//            System.out.println("Fin");
//        } catch (Exception e) {
//            // ignore
//            e.printStackTrace();
//        }
//    }

}