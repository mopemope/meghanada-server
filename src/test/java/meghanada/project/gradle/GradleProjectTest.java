package meghanada.project.gradle;

import meghanada.analyze.CompileResult;
import meghanada.project.Project;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static meghanada.config.Config.debugIt;
import static meghanada.config.Config.timeIt;

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
        // System.setProperty("log-level", "DEBUG");
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
    }

    @Test
    public void testParse1() throws Exception {
        final Project project = debugIt(() -> {
            return this.project.parseProject();
        });

        final List<Project> projects = project.getDependencyProjects();
        System.out.println(projects.size());
        for (Project p : projects) {
            System.out.println(p);
        }
    }

    @Test
    public void testCompile1() throws Exception {
        project.parseProject();
        final CompileResult compileResult = timeIt(() -> {
            return this.project.compileJava(false);
        });
        if (!compileResult.isSuccess()) {
            System.out.println(compileResult.getDiagnosticsSummary());
        }
        // this.project.compileJava(false);
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