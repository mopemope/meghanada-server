package meghanada.project.gradle;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static meghanada.config.Config.debugTimeItF;
import static meghanada.utils.FunctionUtils.wrapIOConsumer;

import com.android.builder.model.AndroidProject;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import meghanada.analyze.CompileResult;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.ProjectParseException;
import meghanada.telemetry.TelemetryUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaJavaLanguageSettings;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.gradle.tooling.model.idea.IdeaSourceDirectory;
import org.gradle.util.GradleVersion;

public class GradleProject extends Project {

  private static final long serialVersionUID = 1L;
  private static final Logger log = LogManager.getLogger(GradleProject.class);
  public static final String[] STRINGS = new String[0];
  private static String tempPath;
  private static Set<String> validConfiguration =
      new HashSet<>(Arrays.asList("annotationProcessor", "optional", "compileClasspath"));
  private static Set<String> validTestConfiguration =
      new HashSet<>(Arrays.asList("testCompileClasspath", "testRuntimeClasspath"));
  transient Map<String, File> allModules;
  private File rootProject;
  private boolean kts;
  private transient List<String> prepareCompileTask;
  private transient List<String> prepareTestCompileTask;
  private transient ComparableVersion gradleVersion = null;

  public GradleProject(File projectRoot) throws IOException {
    this(projectRoot, false);
  }

  public GradleProject(File projectRoot, boolean kts) throws IOException {
    super(projectRoot);
    this.kts = kts;
    this.initialize();
  }

  private static String getTmpDir() throws IOException {
    if (nonNull(tempPath)) {
      return tempPath;
    }
    File tempDir = Files.createTempDir();
    tempDir.deleteOnExit();
    String path = tempDir.getCanonicalPath();
    System.setProperty("java.io.tmpdir", path);
    tempPath = path;
    return tempPath;
  }

  private static File searchRootProject(File dir) throws IOException {

    File result = dir;
    dir = dir.getParentFile();
    if (isNull(dir)) {
      System.setProperty("user.dir", result.getCanonicalPath());
      return result;
    }
    while (true) {

      if (isNull(dir.getParent())) {
        break;
      }

      if (!isGradleProject(dir).isPresent()) {
        break;
      }
      result = dir;
      dir = dir.getParentFile();
      if (isNull(dir)) {
        break;
      }
    }
    System.setProperty("user.dir", result.getCanonicalPath());
    return result;
  }

  private static String convertName(String path) {
    String replaced = meghanada.utils.StringUtils.replace(path, ":", "-");
    if (replaced.startsWith("-")) {
      return replaced.substring(1);
    }
    return replaced;
  }

  public static String getTempPath() {
    return tempPath;
  }

  public static long getSerialVersionUID() {
    return serialVersionUID;
  }

  @Override
  protected void initialize() throws IOException {
    super.initialize();
    this.allModules = new ConcurrentHashMap<>(4);
    this.prepareCompileTask = new ArrayList<>(2);
    this.prepareTestCompileTask = new ArrayList<>(2);
    this.rootProject = GradleProject.searchRootProject(projectRoot);
    String tmp = getTmpDir();
  }

  @Override
  @SuppressWarnings("try")
  public Project parseProject(File projectRoot, File current) throws ProjectParseException {
    try (TelemetryUtils.ScopedSpan scope =
            TelemetryUtils.startScopedSpan("GradleProject.parseProject");
        final ProjectConnection connection = getProjectConnection()) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("projectRoot", projectRoot.getPath())
              .put("current", current.getPath())
              .build("args"));

      log.info(
          "loading gradle project:{}",
          new File(this.projectRoot, isGradleProject(this.projectRoot).get()));

      BuildEnvironment env = connection.getModel(BuildEnvironment.class);
      String version = env.getGradle().getGradleVersion();
      if (isNull(version)) {
        version = GradleVersion.current().getVersion();
      }
      this.gradleVersion = new ComparableVersion(version);

      IdeaProject ideaProject =
          debugTimeItF(
              "get idea project model elapsed={}", () -> connection.getModel(IdeaProject.class));
      this.setCompileTarget(ideaProject);
      log.trace("load root project path:{}", this.rootProject);
      DomainObjectSet<? extends IdeaModule> modules = ideaProject.getModules();
      List<? extends IdeaModule> mainModules =
          modules
              .parallelStream()
              .filter(
                  ideaModule -> {
                    org.gradle.tooling.model.GradleProject gradleProject =
                        ideaModule.getGradleProject();
                    File moduleProjectRoot = gradleProject.getProjectDirectory();
                    String name = ideaModule.getName();
                    log.trace("find sub-module name {} path {} ", name, moduleProjectRoot);
                    this.allModules.putIfAbsent(name, moduleProjectRoot);
                    return moduleProjectRoot.equals(this.getProjectRoot());
                  })
              .collect(Collectors.toList());
      mainModules.forEach(
          wrapIOConsumer(
              p -> {
                this.parseIdeaModule(connection, p);
              }));
      // set default output
      if (isNull(super.output)) {

        String build = Joiner.on(File.separator).join(this.projectRoot, "build", "classes", "main");
        if (nonNull(gradleVersion) && gradleVersion.compareTo(new ComparableVersion("4.0")) >= 0) {
          build =
              Joiner.on(File.separator).join(this.projectRoot, "build", "classes", "java", "main");
        }
        super.output = this.normalize(build);
      }
      if (isNull(super.testOutput)) {
        String build = Joiner.on(File.separator).join(this.projectRoot, "build", "classes", "test");
        if (nonNull(gradleVersion) && gradleVersion.compareTo(new ComparableVersion("4.0")) >= 0) {
          build =
              Joiner.on(File.separator).join(this.projectRoot, "build", "classes", "java", "test");
        }
        super.testOutput = this.normalize(build);
      }

      return this;
    } catch (Exception e) {
      Throwable cause = findLocationException(e);
      throw new ProjectParseException(cause);
    }
  }

  private static Throwable findLocationException(Throwable e) {
    Throwable cause = e.getCause();
    if (nonNull(cause)) {
      String name = cause.getClass().getName();
      if (name.contains("Location")) {
        return cause;
      }
      return findLocationException(cause);
    }
    return e;
  }

  private void parseIdeaModule(ProjectConnection connection, IdeaModule ideaModule)
      throws IOException {
    org.gradle.tooling.model.GradleProject gradleProject = ideaModule.getGradleProject();
    String name = convertName(gradleProject.getPath());
    if (nonNull(name) && !name.isEmpty()) {
      this.name = name;
    }
    AndroidProject androidProject =
        AndroidSupport.getAndroidProject(this.rootProject, gradleProject);
    if (nonNull(androidProject)) {
      Set<ProjectDependency> projectDependencies = analyzeDependencies(connection, ideaModule);
      this.dependencies.addAll(projectDependencies);
      // parse android
      this.isAndroidProject = true;
      this.androidApiVersion = androidProject.getApiVersion();
      this.androidModelVersion = androidProject.getModelVersion();
      log.info(
          "detect android project {}. api {} model {}",
          name,
          androidApiVersion,
          androidModelVersion);
      System.setProperty("meghanada.android.project", "true");
      System.setProperty("meghanada.android.project.name", name);
      AndroidSupport androidSupport = new AndroidSupport(this);
      androidSupport.parseAndroidProject(androidProject);
    } else {
      // normal
      this.parseIdeaModule(connection, gradleProject, ideaModule);
    }
  }

  private void setCompileTarget(IdeaProject ideaProject) {
    IdeaJavaLanguageSettings javaLanguageSettings = ideaProject.getJavaLanguageSettings();
    try {
      String srcLevel = javaLanguageSettings.getLanguageLevel().toString();
      String targetLevel = javaLanguageSettings.getTargetBytecodeVersion().toString();
      super.compileSource = srcLevel;
      super.compileTarget = targetLevel;
    } catch (UnsupportedMethodException e) {
      log.warn(e.getMessage());
    }
  }

  private void parseIdeaModule(
      ProjectConnection connection,
      org.gradle.tooling.model.GradleProject gradleProject,
      IdeaModule ideaModule)
      throws IOException {

    if (isNull(this.output)) {
      String buildDir = gradleProject.getBuildDirectory().getCanonicalPath();
      String build = Joiner.on(File.separator).join(buildDir, "classes", "main");
      if (nonNull(gradleVersion) && gradleVersion.compareTo(new ComparableVersion("4.0")) >= 0) {
        build = Joiner.on(File.separator).join(buildDir, "classes", "java", "main");
      }
      this.output = this.normalize(build);
    }

    if (isNull(this.testOutput)) {
      String buildDir = gradleProject.getBuildDirectory().getCanonicalPath();
      String build = Joiner.on(File.separator).join(buildDir, "classes", "test");
      if (nonNull(gradleVersion) && gradleVersion.compareTo(new ComparableVersion("4.0")) >= 0) {
        build = Joiner.on(File.separator).join(buildDir, "classes", "java", "test");
      }
      this.testOutput = this.normalize(build);
    }
    Set<ProjectDependency> dependencies = this.analyzeDependencies(connection, ideaModule);
    Map<String, Set<File>> sources = this.searchProjectSources(ideaModule);

    this.sources.addAll(sources.get("sources"));
    this.resources.addAll(sources.get("resources"));
    this.testSources.addAll(sources.get("testSources"));
    this.testResources.addAll(sources.get("testResources"));
    this.dependencies.addAll(dependencies);

    // merge other project
    if (this.sources.isEmpty()) {
      File file =
          new File(Joiner.on(File.separator).join("src", "main", "java")).getCanonicalFile();
      this.sources.add(file);
    }
    if (this.testSources.isEmpty()) {
      File file =
          new File(Joiner.on(File.separator).join("src", "test", "java")).getCanonicalFile();
      this.testSources.add(file);
    }

    if (isNull(this.output)) {
      String buildDir = new File(this.getProjectRoot(), "build").getCanonicalPath();
      String build = Joiner.on(File.separator).join(buildDir, "classes", "main");
      if (nonNull(gradleVersion) && gradleVersion.compareTo(new ComparableVersion("4.0")) >= 0) {
        build = Joiner.on(File.separator).join(buildDir, "classes", "java", "main");
      }
      this.output = this.normalize(build);
    }
    if (isNull(this.testOutput)) {
      String buildDir = new File(this.getProjectRoot(), "build").getCanonicalPath();
      String build = Joiner.on(File.separator).join(buildDir, "classes", "test");
      if (nonNull(gradleVersion) && gradleVersion.compareTo(new ComparableVersion("4.0")) >= 0) {
        build = Joiner.on(File.separator).join(buildDir, "classes", "java", "test");
      }
      this.testOutput = this.normalize(build);
    }

    log.debug("sources {}", this.sources);
    log.debug("resources {}", this.resources);
    log.debug("output {}", this.output);
    log.debug("test sources {}", this.testSources);
    log.debug("test resources {}", this.testResources);
    log.debug("test output {}", this.testOutput);

    for (ProjectDependency projectDependency : this.getDependencies()) {
      log.debug(
          "Scope:{} Type:{} {} ",
          projectDependency.getScope(),
          projectDependency.getType(),
          projectDependency.getId());
    }
  }

  ProjectConnection getProjectConnection() {
    String gradleVersion = Config.load().getGradleVersion();
    GradleConnector connector;
    if (gradleVersion.isEmpty()) {
      connector = GradleConnector.newConnector().forProjectDirectory(this.rootProject);
    } else {
      log.debug("use gradle version:'{}'", gradleVersion);
      connector =
          GradleConnector.newConnector()
              .useGradleVersion(gradleVersion)
              .forProjectDirectory(this.rootProject);
    }

    if (connector instanceof DefaultGradleConnector) {
      DefaultGradleConnector defaultGradleConnector = (DefaultGradleConnector) connector;
      defaultGradleConnector.daemonMaxIdleTime(1, TimeUnit.HOURS);
    }
    return connector.connect();
  }

  @Override
  public InputStream runTask(List<String> args) throws IOException {
    try {
      List<String> tasks = new ArrayList<>(4);
      List<String> taskArgs = new ArrayList<>(4);
      for (String temp : args) {
        for (String arg : Splitter.on(" ").split(temp)) {
          if (arg.startsWith("-")) {
            taskArgs.add(arg.trim());
          } else {
            tasks.add(arg.trim());
          }
        }
      }

      log.debug("task:{}:{} args:{}:{}", tasks, tasks.size(), taskArgs, taskArgs.size());

      ProjectConnection projectConnection = getProjectConnection();
      BuildLauncher build = projectConnection.newBuild();
      GradleProject.setBuildJVMArgs(build);
      build.forTasks(tasks.toArray(STRINGS));
      if (taskArgs.size() > 0) {
        build.withArguments(taskArgs.toArray(STRINGS));
      }

      PipedOutputStream outputStream = new PipedOutputStream();
      PipedInputStream inputStream = new PipedInputStream(outputStream);
      build.setStandardError(outputStream);
      build.setStandardOutput(outputStream);
      VoidResultHandler handler =
          new VoidResultHandler(outputStream, inputStream, projectConnection);
      build.run(handler);
      return inputStream;
    } finally {
      Config.setProjectRoot(this.projectRoot.getCanonicalPath());
    }
  }

  private static void setBuildJVMArgs(BuildLauncher build) throws IOException {
    build.setJvmArguments("-Djava.io.tmpdir=" + getTmpDir());
  }

  private Map<String, Set<File>> searchProjectSources(IdeaModule ideaModule) throws IOException {
    Map<String, Set<File>> result = new HashMap<>(8);
    result.put("sources", new HashSet<>(2));
    result.put("resources", new HashSet<>(2));
    result.put("testSources", new HashSet<>(2));
    result.put("testResources", new HashSet<>(2));

    for (IdeaContentRoot ideaContentRoot : ideaModule.getContentRoots().getAll()) {
      for (IdeaSourceDirectory sourceDirectory : ideaContentRoot.getSourceDirectories().getAll()) {
        File file = normalizeFile(sourceDirectory.getDirectory());
        String path = file.getCanonicalPath();
        if (path.contains("resources")) {
          result.get("resources").add(file);
        } else {
          result.get("sources").add(file);
        }
      }
      for (IdeaSourceDirectory sourceDirectory : ideaContentRoot.getTestDirectories().getAll()) {
        File file = normalizeFile(sourceDirectory.getDirectory());
        String path = file.getCanonicalPath();
        if (path.contains("resources")) {
          result.get("testResources").add(file);
        } else {
          result.get("testSources").add(file);
        }
      }
    }
    return result;
  }

  private Set<ProjectDependency> analyzeDependencies(
      ProjectConnection connection, IdeaModule ideaModule) throws IOException {
    Set<ProjectDependency> dependencies = new HashSet<>(32);
    for (IdeaDependency dependency : ideaModule.getDependencies().getAll()) {
      if (dependency instanceof IdeaSingleEntryLibraryDependency) {
        IdeaSingleEntryLibraryDependency libraryDependency =
            (IdeaSingleEntryLibraryDependency) dependency;

        File file = libraryDependency.getFile();
        GradleModuleVersion gradleModuleVersion = libraryDependency.getGradleModuleVersion();
        String scope = libraryDependency.getScope().getScope();
        String id;
        String version;
        if (isNull(gradleModuleVersion)) {
          id = file.getName();
          // dummy
          version = "1.0.0";
        } else {
          id =
              String.join(
                  ":",
                  gradleModuleVersion.getGroup(),
                  gradleModuleVersion.getName(),
                  gradleModuleVersion.getVersion());
          version = gradleModuleVersion.getVersion();
        }
        if (isNull(scope)) {
          scope = "COMPILE";
        }

        ProjectDependency.Type type = ProjectDependency.getFileType(file);
        ProjectDependency projectDependency = new ProjectDependency(id, scope, version, file, type);
        dependencies.add(projectDependency);
      } else if (dependency instanceof IdeaModuleDependency) {
        IdeaModuleDependency moduleDependency = (IdeaModuleDependency) dependency;
        String scope = moduleDependency.getScope().getScope();
        String moduleName = moduleDependency.getTargetModuleName();
        this.allModules.computeIfPresent(
            moduleName,
            (key, projectRoot) -> {
              ProjectDependency projectDependency =
                  new ProjectDependency(
                      key, scope, "1.0.0", projectRoot, ProjectDependency.Type.PROJECT);
              dependencies.add(projectDependency);
              return projectRoot;
            });

        if (!this.allModules.containsKey(moduleName)) {
          log.warn("missing module:{}", moduleName);
        }

      } else {
        log.warn("dep ??? class={}", dependency.getClass());
      }
    }
    analyzeFromDependencyTree(connection);
    return dependencies;
  }

  @Override
  public CompileResult compileJava() throws IOException {
    return this.compileJava(false);
  }

  @Override
  public CompileResult compileJava(boolean force) throws IOException {
    this.runPrepareCompileTask();
    if (this.isAndroidProject) {
      new AndroidSupport(this).prepareCompileAndroidJava();
    }
    return super.compileJava(force);
  }

  private void runPrepareCompileTask() throws IOException {
    if (!this.prepareCompileTask.isEmpty()) {
      try (ProjectConnection connection = this.getProjectConnection()) {
        String[] tasks = prepareCompileTask.toArray(STRINGS);
        BuildLauncher buildLauncher = connection.newBuild();
        log.info("project {} run tasks:{}", this.name, tasks);
        GradleProject.setBuildJVMArgs(buildLauncher);
        buildLauncher.forTasks(tasks).run();
      }
    }
  }

  @Override
  public CompileResult compileTestJava() throws IOException {
    return this.compileTestJava(false);
  }

  @Override
  public CompileResult compileTestJava(boolean force) throws IOException {
    this.runPrepareTestCompileTask();
    if (this.isAndroidProject) {
      new AndroidSupport(this).prepareCompileAndroidTestJava();
      return super.compileTestJava(force);
    } else {
      return super.compileTestJava(force);
    }
  }

  private void runPrepareTestCompileTask() throws IOException {
    if (!this.prepareTestCompileTask.isEmpty()) {
      try (ProjectConnection connection = this.getProjectConnection()) {
        String[] tasks = prepareTestCompileTask.toArray(STRINGS);
        BuildLauncher buildLauncher = connection.newBuild();
        log.info("project {} run tasks:{}", this.name, tasks);
        GradleProject.setBuildJVMArgs(buildLauncher);
        buildLauncher.forTasks(tasks).run();
      }
    }
  }

  @Override
  public Project mergeFromProjectConfig() throws IOException {
    Config config1 = Config.load();
    this.prepareCompileTask.addAll(config1.gradlePrepareCompileTask());
    this.prepareTestCompileTask.addAll(config1.gradlePrepareTestCompileTask());

    File configFile = new File(this.projectRoot, Config.MEGHANADA_CONF_FILE);
    if (configFile.exists()) {
      com.typesafe.config.Config config = ConfigFactory.parseFile(configFile);
      if (config.hasPath(Config.GRADLE_PREPARE_COMPILE_TASK)) {
        String taskConfig = config.getString(Config.GRADLE_PREPARE_COMPILE_TASK);
        String[] tasks = StringUtils.split(taskConfig, ",");
        if (nonNull(tasks)) {
          Collections.addAll(this.prepareCompileTask, tasks);
        }
      }
      if (config.hasPath(Config.GRADLE_PREPARE_TEST_COMPILE_TASK)) {
        String taskConfig = config.getString(Config.GRADLE_PREPARE_TEST_COMPILE_TASK);
        String[] tasks = StringUtils.split(taskConfig, ",");
        if (nonNull(tasks)) {
          Collections.addAll(this.prepareTestCompileTask, tasks);
        }
      }
    }
    return super.mergeFromProjectConfig();
  }

  @Override
  public String getProjectType() {
    return "gradle";
  }

  private static class VoidResultHandler implements ResultHandler<Void> {
    private PipedOutputStream outputStream;
    private PipedInputStream inputStream;
    private ProjectConnection projectConnection;

    VoidResultHandler(
        PipedOutputStream outputStream,
        PipedInputStream inputStream,
        ProjectConnection projectConnection) {
      this.outputStream = outputStream;
      this.inputStream = inputStream;
      this.projectConnection = projectConnection;
    }

    @Override
    public void onComplete(Void result) {
      try {
        outputStream.close();
        inputStream.close();
      } catch (IOException e) {
        log.error(e.getMessage(), e);
      } finally {
        projectConnection.close();
      }
    }

    @Override
    public void onFailure(GradleConnectionException failure) {
      try {
        log.catching(failure.getCause());
        outputStream.close();
        inputStream.close();
      } catch (IOException e) {
        log.error(e.getMessage(), e);
      } finally {
        projectConnection.close();
      }
    }
  }

  public boolean isKTS() {
    return this.kts;
  }

  public static Optional<String> isGradleProject(File dir) {
    File[] files = dir.listFiles();
    if (isNull(files)) {
      return Optional.empty();
    }

    for (File f : files) {
      String name = f.getName();
      if ((name.endsWith(GRADLE_PROJECT_EXT) || name.endsWith(GRADLE_KTS_PROJECT_FILE))
          && (!name.equals("settings.gradle") && !name.equals("settings.build.kts"))) {
        return Optional.of(name);
      }
    }
    return Optional.empty();
  }

  private void analyzeFromDependencyTree(ProjectConnection connection) throws IOException {
    File initScriptFile = File.createTempFile("init", ".gradle");
    URL url = Resources.getResource("init.gradle");
    byte[] a = Resources.toByteArray(url);
    Files.write(a, initScriptFile);

    DependencyTreeModel dependencyTreeModel =
        connection
            .model(DependencyTreeModel.class)
            .withArguments("-I", initScriptFile.getCanonicalPath())
            .get();

    List<Configuration> configurations = dependencyTreeModel.getConfigurations();
    Set<Dependency> dependencySet = new HashSet<>(16);
    Set<Dependency> testDependencySet = new HashSet<>(16);
    for (Configuration config : configurations) {
      String name = config.getName();
      if (validConfiguration.contains(name)) {
        List<Dependency> dependencies = config.getDependencies();
        for (Dependency d1 : dependencies) {
          dependencySet.add(d1);
          for (Dependency d2 : d1.getDependencies()) {
            dependencySet.add(d2);
            for (Dependency d3 : d2.getDependencies()) {
              dependencySet.add(d3);
              for (Dependency d4 : d3.getDependencies()) {
                dependencySet.add(d4);
              }
            }
          }
        }
      } else if (validTestConfiguration.contains(name)) {
        List<Dependency> dependencies = config.getDependencies();
        for (Dependency d1 : dependencies) {
          testDependencySet.add(d1);
          for (Dependency d2 : d1.getDependencies()) {
            testDependencySet.add(d2);
            for (Dependency d3 : d2.getDependencies()) {
              testDependencySet.add(d3);
              for (Dependency d4 : d3.getDependencies()) {
                testDependencySet.add(d4);
              }
            }
          }
        }
      }
    }
    Path cacheRoot =
        new File(Config.load().getUserHomeDir(), ".gradle/caches/modules-2/files-2.1").toPath();
    try (final Stream<Path> stream = java.nio.file.Files.walk(cacheRoot)) {
      stream.forEach(
          path -> {
            String pathStr = path.normalize().toString();
            if (!pathStr.endsWith(".jar")) {
              return;
            }
            for (Dependency dependency : dependencySet) {
              if (isNull(dependency.getLocalPath())) {
                Path p =
                    Paths.get(
                        dependency.getGroupId(),
                        dependency.getArtifactId(),
                        dependency.getVersion());
                if (pathStr.contains(p.normalize().toString())) {
                  String id =
                      String.join(
                          ":",
                          dependency.getGroupId(),
                          dependency.getArtifactId(),
                          dependency.getVersion());
                  ProjectDependency projectDependency =
                      new ProjectDependency(
                          id,
                          "COMPILE",
                          dependency.getVersion(),
                          path.toFile(),
                          ProjectDependency.Type.JAR);
                  dependencies.add(projectDependency);
                }
              }
            }
            for (Dependency dependency : testDependencySet) {
              if (isNull(dependency.getLocalPath())) {
                Path p =
                    Paths.get(
                        dependency.getGroupId(),
                        dependency.getArtifactId(),
                        dependency.getVersion());
                if (pathStr.contains(p.normalize().toString())) {
                  String id =
                      String.join(
                          ":",
                          dependency.getGroupId(),
                          dependency.getArtifactId(),
                          dependency.getVersion());
                  ProjectDependency projectDependency =
                      new ProjectDependency(
                          id,
                          "TEST",
                          dependency.getVersion(),
                          path.toFile(),
                          ProjectDependency.Type.JAR);
                  dependencies.add(projectDependency);
                }
              }
            }
          });
    }
  }
}
