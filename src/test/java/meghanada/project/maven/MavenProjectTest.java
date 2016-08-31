package meghanada.project.maven;

import meghanada.project.Project;
import org.junit.Before;
import org.junit.Ignore;

import java.io.File;
import java.util.Map;


@Ignore
public class MavenProjectTest {
    private Project project;

    private void printEnv() {
        Map<String, String> envs = System.getenv();

        for (Map.Entry<String, String> env : envs.entrySet()) {
            System.out.println(env);
        }
    }

    @Before
    public void setUp() throws Exception {
        project = new MavenProject(new File("./"));
        // printEnv();
    }


//    @Test
//    public void testRunTask() throws Exception {
//
//        List<String> cmd = new ArrayList<>();
//        cmd.add("compile");
//        try (BufferedReader br = project.runTask(cmd.toArray(new String[cmd.size()]))) {
//            for (; ; ) {
//                String line = br.readLine();
//                if (line == null) {
//                    break;
//                }
//                System.out.println(line);
//            }
//        }
//
//    }

//    @Test
//    public void testRunTest() throws Exception {
//        project.parseProject();
//        String test = "meghanada.reflect.asm.ASMReflectorTest";
//        try (BufferedReader br = project.runTest(test)) {
//            for (; ; ) {
//                String line = br.readLine();
//                if (line == null) {
//                    break;
//                }
//                System.out.println(line);
//            }
//        }
//
//    }


//    @Test
//    public void testRunJUnit1() throws Exception {
//        project.parseProject();
//        String test1 = "JavaParserTest#testParseClass1";
//        String test2 = "JavaParserTest#testParseClass2";
//        try (BufferedReader br = project.runUnit(test1, test2)) {
//            for (; ; ) {
//                String line = br.readLine();
//                if (line == null) {
//                    break;
//                }
//                System.out.println(line);
//            }
//        }
//    }
//
//    @Test
//    public void testRunJUnit2() throws Exception {
//        project.parseProject();
//        String test1 = ".*JavaParserTest";
//        String test2 = "JavaParserTest";
//        try (BufferedReader br = project.runUnit(test1, test2)) {
//            for (; ; ) {
//                String line = br.readLine();
//                if (line == null) {
//                    break;
//                }
//                System.out.println(line);
//            }
//        }
//
//    }
}
