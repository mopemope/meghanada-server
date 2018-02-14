package meghanada;

import static java.util.Objects.nonNull;

import com.google.common.base.Stopwatch;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import meghanada.config.Config;
import meghanada.module.ModuleHelper;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.gradle.GradleProject;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.store.ProjectDatabaseHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("CheckReturnValue")
public class GradleTestBase {

  public static final String TEMP_PROJECT_SETTING_DIR = "meghanada.temp.project.setting.dir";
  private static final Logger log = LogManager.getLogger(GradleTestBase.class);
  public static Project project;

  @SuppressWarnings("CheckReturnValue")
  public static void setupProject(boolean useCache) throws Exception {
    if (useCache) {
      System.setProperty("meghanada.source.cache", "true");
    } else {
      System.setProperty("meghanada.source.cache", "false");
    }

    if (project == null) {
      // replace tmp
      Project newProject = new GradleProject(new File("./").getCanonicalFile());

      final File tempDir = Files.createTempDir();
      tempDir.deleteOnExit();
      final String path = tempDir.getCanonicalPath();
      System.setProperty(TEMP_PROJECT_SETTING_DIR, path);
      log.info("create database {}", path);
      project = newProject.parseProject().mergeFromProjectConfig();
    }
    Config config = Config.load();
    if (useCache) {
      config.update("source-cache", true);
    } else {
      config.update("source-cache", false);
    }
    log.info("finish setupProject");
  }

  public static File getJar(String name) {
    return project
        .getDependencies()
        .stream()
        .map(
            pd -> {
              System.out.println(pd.getId());
              if (pd.getId().contains(name)) {
                return pd.getFile();
              }
              return null;
            })
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  public static File getRTJar() {
    Config config = Config.load();
    if (config.isJava8()) {
      File jar = new File(Config.load().getJavaHomeDir(), "/lib/rt.jar");
      if (jar.exists()) {
        return jar;
      }
    } else {
      return ModuleHelper.getJrtFsFile();
    }
    return new File(Config.load().getJavaHomeDir(), "/lib/jrt-fs.jar");
  }

  public static Set<File> getJars() {
    return project
        .getDependencies()
        .stream()
        .map(ProjectDependency::getFile)
        .collect(Collectors.toSet());
  }

  public static Set<File> getJars(Project tmp) {
    return tmp.getDependencies()
        .stream()
        .map(ProjectDependency::getFile)
        .collect(Collectors.toSet());
  }

  public static List<File> getSystemJars() {
    Config config = Config.load();
    if (config.isJava8()) {
      final String javaHome = Config.load().getJavaHomeDir();
      File jvmDir = new File(javaHome);
      try (Stream<Path> s = java.nio.file.Files.walk(jvmDir.toPath())) {
        return s.filter(
                path -> {
                  String name = path.toFile().getName();
                  return name.endsWith(".jar") && !name.endsWith("policy.jar");
                })
            .map(
                path -> {
                  try {
                    return path.toFile().getCanonicalFile();
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                })
            .collect(Collectors.toList());

      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      List<File> res = new ArrayList<>(1);
      res.add(ModuleHelper.getJrtFsFile());
      return res;
    }
  }

  public static Project getProject() {
    return project;
  }

  protected static File getOutput() {
    return project.getOutput();
  }

  protected static File getTestOutput() {
    return project.getTestOutput();
  }

  public static void setupReflector(boolean useCache) throws Exception {
    setupProject(useCache);
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    cachedASMReflector.getGlobalClassIndex().clear();
    addClasspath(cachedASMReflector);
    final Stopwatch stopwatch = Stopwatch.createStarted();
    cachedASMReflector.createClassIndexes();
    log.info("createClassIndexes elapsed: {}", stopwatch.stop());
  }

  public static void shutdown() throws Exception {
    ProjectDatabaseHelper.shutdown();
    String p = System.getProperty(TEMP_PROJECT_SETTING_DIR);
    File file = new File(p);
    org.apache.commons.io.FileUtils.deleteDirectory(file);
    String tempPath = GradleProject.getTempPath();
    if (nonNull(tempPath)) {
      org.apache.commons.io.FileUtils.deleteDirectory(new File(tempPath));
    }
    log.info("deleted database {}", file);
  }

  private static void addClasspath(CachedASMReflector cachedASMReflector) {
    cachedASMReflector.addClasspath(getSystemJars());
    cachedASMReflector.addClasspath(getJars());
    cachedASMReflector.addClasspath(getOutput());
    cachedASMReflector.addClasspath(getTestOutput());
  }
}
