package meghanada.project.gradle;

import static java.util.Objects.nonNull;
import static meghanada.GradleTestBase.getJars;
import static meghanada.GradleTestBase.getSystemJars;
import static meghanada.config.Config.timeIt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.io.Files;
import java.io.File;
import meghanada.analyze.CompileResult;
import meghanada.project.Project;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.store.ProjectDatabaseHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@SuppressWarnings("CheckReturnValue")
public class GradleProjectTest {

  public static final String TEMP_PROJECT_SETTING_DIR = "meghanada.temp.project.setting.dir";
  private static final Logger log = LogManager.getLogger(GradleProjectTest.class);
  private Project project;
  private File projectRoot;
  private String projectRootPath;

  @AfterClass
  public static void shutdown() throws Exception {
    ProjectDatabaseHelper.shutdown();
    String p = System.getProperty(TEMP_PROJECT_SETTING_DIR);
    File file = new File(p);
    org.apache.commons.io.FileUtils.deleteDirectory(file);
    String tempPath = GradleProject.getTempPath();
    if (nonNull(tempPath)) {
      org.apache.commons.io.FileUtils.deleteDirectory(new File(tempPath));
    }
  }

  @Before
  public void setUp() throws Exception {

    if (this.project == null) {

      System.setProperty("meghanada.test", "true");
      this.projectRoot = new File("./").getCanonicalFile();
      // this.projectRoot = new File("./").getCanonicalFile();
      this.projectRootPath = this.projectRoot.getCanonicalPath();
      this.project = new GradleProject(projectRoot);
      String tmpdir = System.getProperty("java.io.tmpdir");
      File tmpParent = new File(tmpdir);
      if (!tmpParent.exists() && !tmpParent.mkdirs()) {
        log.warn("fail create tmpdir");
      }

      final File tempDir = Files.createTempDir();
      tempDir.deleteOnExit();
      final String path = tempDir.getCanonicalPath();
      System.setProperty(TEMP_PROJECT_SETTING_DIR, path);
    }
  }

  @Ignore
  @Test
  public void testParse01() throws Exception {
    File file = project.getProjectRoot();
    Project parsed = this.project.parseProject(file, file);
    final File outputDirectory = parsed.getOutput();
    assertNotNull(outputDirectory);
    System.out.println(outputDirectory);
  }

  @Ignore
  @Test
  public void testParse02() throws Exception {
    File file = project.getProjectRoot();
    final Project project = timeIt(() -> this.project.parseProject(file, file));

    final String classpath = project.classpath();
    assertNotNull(classpath);
    //    for (final String cp : StringUtils.split(classpath, File.pathSeparatorChar)) {
    //      System.out.println(cp);
    //    }
    final String all = project.classpath();
    assertNotNull(all);
  }

  @Ignore
  @Test
  public void testLoadProject01() throws Exception {
    File file = project.getProjectRoot();
    Project parsed = project.parseProject(file, file);
    String classpath1 = parsed.classpath();
    timeIt(parsed::saveProject);

    Thread.sleep(1000);

    Project load = timeIt(() -> Project.loadProject(this.projectRootPath));
    String classpath2 = load.classpath();
    assertEquals(classpath1, classpath2);
    timeIt(load::saveProject);
  }

  @Ignore
  @Test
  public void testCompile01() throws Exception {
    File file = project.getProjectRoot();
    final CompileResult compileResult =
        timeIt(
            () -> {
              project.parseProject(file, file);
              return this.project.compileJava();
            });

    log.info("compile message {}", compileResult.getDiagnosticsSummary());
    assertTrue(compileResult.isSuccess());
    Thread.sleep(3000);
  }

  @Ignore
  @Test
  public void testCompile02() throws Exception {
    File file = project.getProjectRoot();
    project.parseProject(file, file);
    setupReflector(project);
    Thread.sleep(1000 * 3);
    final CompileResult compileResult = this.project.compileJava(true);

    assertTrue(compileResult.isSuccess());

    String fqcn = "meghanada.store.ProjectDatabase";
    Thread.sleep(1000 * 5);
    ProjectDatabaseHelper.getClassIndexLinks(
        fqcn,
        "references",
        entities -> {
          log.info("references {}", entities.size());
        });

    for (int i = 0; i < 5; i++) {
      this.project.compileJava(true);
      Thread.sleep(1000 * 5);
      ProjectDatabaseHelper.getClassIndexLinks(
          fqcn,
          "references",
          entities -> {
            log.info("references {}", entities.size());
          });
    }
  }

  private void setupReflector(Project p) {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    cachedASMReflector.addClasspath(getSystemJars());
    cachedASMReflector.addClasspath(getJars(p));
    cachedASMReflector.addClasspath(p.getOutput());
    cachedASMReflector.addClasspath(p.getTestOutput());
    cachedASMReflector.createClassIndexes();
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
  //        try (BufferedReader br = this.project.runTest("meghanada.reflect.asm.ASMReflectorTest"))
  // {
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
