package meghanada.project.maven;

import static java.util.Objects.isNull;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.ProjectParseException;
import meghanada.telemetry.TelemetryUtils;
import meghanada.utils.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MavenProject extends Project {

  private static final String REPOSITORY = "repository";
  private static final String RESOLVE_TASK = "dependency:resolve";
  private static final String SOURCES_TASK = "dependency:sources";
  private static final String BUILD_CLASSPATH_TASK = "dependency:build-classpath";

  private static final Logger log = LogManager.getLogger(MavenProject.class);
  private static final long serialVersionUID = 5078373387618330618L;

  private File pomFile;
  private String mavenCmd = "mvn";
  private boolean depSources;

  public MavenProject(final File projectRoot) throws IOException {
    super(projectRoot);
    this.pomFile = new File(projectRoot, Project.MVN_PROJECT_FILE);
  }

  private static String getVersion(final String path) {
    return new File(path).getName();
  }

  private static String getArtifactCode(final String path) {
    final File parentFile = new File(path).getParentFile();
    final String artifactID = parentFile.getName();

    String parent = parentFile.getParent();
    final int i = parent.indexOf(REPOSITORY);
    if (i > 0) {
      parent = parent.substring(i + 11);
    }
    final String groupID = StringUtils.replace(parent, File.separator, ".");
    return groupID + ':' + artifactID;
  }

  private void setMavenPath(String maven) {
    this.mavenCmd = maven;
  }

  @Override
  @SuppressWarnings("try")
  public Project parseProject(File projectRoot, File current) throws ProjectParseException {
    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("MavenProject.parseProject")) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("projectRoot", projectRoot.getPath())
              .put("current", current.getPath())
              .build("args"));
      final String mavenPath = Config.load().getMavenPath();
      if (!Strings.isNullOrEmpty(mavenPath)) {
        this.mavenCmd = mavenPath;
      }

      final File logFile = File.createTempFile("meghanada-maven-classpath", ".log");
      logFile.deleteOnExit();
      final String logPath = logFile.getCanonicalPath();

      log.info("running maven. build classpath ...");
      File outFile = File.createTempFile("maven-run-", ".log");
      if (this.depSources) {
        if (this.runMvn(
                outFile,
                RESOLVE_TASK,
                SOURCES_TASK,
                BUILD_CLASSPATH_TASK,
                String.format("-Dmdep.outputFile=%s", logPath))
            != 0) {
          throw new ProjectParseException(
              String.format(
                  "MEGHANADA_FAILED maven build classpath. Please check FILE:%s", outFile));
        }
      } else {
        if (this.runMvn(
                outFile, BUILD_CLASSPATH_TASK, String.format("-Dmdep.outputFile=%s", logPath))
            != 0) {
          throw new ProjectParseException(
              String.format(
                  "MEGHANADA_FAILED maven build classpath. Please check FILE:%s", outFile));
        }
      }
      final String cpTxt = Files.asCharSource(logFile, StandardCharsets.UTF_8).readFirstLine();
      if (cpTxt != null && !cpTxt.isEmpty()) {
        for (final String dep : Splitter.on(File.pathSeparator).split(cpTxt)) {
          final File file = new File(dep);
          final String parentPath = file.getParent();
          final String version = MavenProject.getVersion(parentPath);
          final String code = MavenProject.getArtifactCode(parentPath);
          final ProjectDependency.Type type = ProjectDependency.getFileType(file);
          final ProjectDependency dependency =
              new ProjectDependency(code, "COMPILE", version, file, type);
          super.dependencies.add(dependency);
        }
      }

      final POMParser pomParser = new POMParser(this.projectRoot);
      final POMInfo pomInfo = pomParser.parsePom(this.pomFile);

      {
        super.sources.addAll(pomInfo.getSourceDirectory());
        File src = new File(this.projectRoot, String.join(File.separator, "src", "main", "java"));
        super.sources.add(src);

        super.resources.addAll(pomInfo.getResourceDirectories());
        File resource =
            new File(this.projectRoot, String.join(File.separator, "src", "main", "resource"));
        super.resources.add(resource);

        File output = pomInfo.getOutputDirectory();
        if (output == null) {
          output = new File(this.projectRoot, String.join(File.separator, "target", "classes"));
        }
        super.output = output;
      }
      {
        super.testSources.addAll(pomInfo.getTestSourceDirectory());
        File src = new File(this.projectRoot, String.join(File.separator, "src", "test", "java"));
        super.testSources.add(src);

        super.testResources.addAll(pomInfo.getTestResourceDirectories());
        File testResource =
            new File(this.projectRoot, String.join(File.separator, "src", "test", "resource"));
        super.testResources.add(testResource);

        File output = pomInfo.getTestOutputDirectory();
        if (output == null) {
          output =
              new File(this.projectRoot, String.join(File.separator, "target", "test-classes"));
        }
        super.testOutput = output;
      }
      super.compileSource = pomInfo.compileSource;
      super.compileTarget = pomInfo.compileTarget;
    } catch (IOException | InterruptedException e) {
      throw new ProjectParseException(e);
    }
    return this;
  }

  @Override
  public InputStream runTask(List<String> args) throws IOException {
    List<String> mvnCmd = new ArrayList<>(8);
    mvnCmd.add(this.mavenCmd);
    mvnCmd.addAll(args);
    return super.runProcess(mvnCmd);
  }

  private int runMvn(final File logFile, final String... args)
      throws IOException, InterruptedException {
    final List<String> cmd = new ArrayList<>(4);
    cmd.add(this.mavenCmd);
    Collections.addAll(cmd, args);

    final ProcessBuilder processBuilder = new ProcessBuilder(cmd);
    processBuilder.directory(this.projectRoot);
    final String cmdString = String.join(" ", cmd);

    log.info("run mvn: {}", cmdString);

    processBuilder.redirectErrorStream(true);
    final Process process = processBuilder.start();

    if (isNull(logFile)) {
      try (InputStream in = process.getInputStream()) {
        byte[] buf = new byte[8192];
        while (in.read(buf) != -1) {}
      }
    } else {
      try (InputStream in = process.getInputStream()) {
        java.nio.file.Files.copy(in, logFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    }

    process.waitFor();
    return process.exitValue();
  }

  @Override
  public String getProjectType() {
    return "maven";
  }
}
