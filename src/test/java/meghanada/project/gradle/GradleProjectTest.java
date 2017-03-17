package meghanada.project.gradle;

import meghanada.analyze.CompileResult;
import meghanada.project.Project;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static meghanada.config.Config.timeIt;
import static meghanada.config.Config.traceIt;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("CheckReturnValue")
public class GradleProjectTest {

    private Project project;

    @Before
    public void setUp() throws Exception {
        if (this.project == null) {
            this.project = new GradleProject(new File("./").getCanonicalFile());
        }
    }

    @After
    public void tearDown() throws Exception {
        System.out.printf("call teardown");
    }

    @Test
    public void testRunTask1() throws Exception {
        this.project.parseProject();
        final File outputDirectory = this.project.getOutput();
        System.out.println(outputDirectory);
    }

    @Test
    public void testParse1() throws Exception {
        final Project project = traceIt(() -> {
            return this.project.parseProject();
        });
        final String classpath = project.classpath();
        for (final String cp : StringUtils.split(classpath, File.pathSeparatorChar)) {
            System.out.println(cp);
        }
    }

    @Test
    public void testCompile1() throws Exception {
        final CompileResult compileResult = timeIt(() -> {
            project.parseProject();
            return this.project.compileJava();
        });
        final String classpath = project.classpath();
        for (final String cp : StringUtils.split(classpath, File.pathSeparatorChar)) {
            System.out.println(cp);
        }

        System.out.println(compileResult.getDiagnosticsSummary());
        assertTrue(compileResult.isSuccess());
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