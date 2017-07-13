package meghanada.project;

import static java.util.Objects.nonNull;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.StoreTransaction;
import meghanada.analyze.ClassScope;
import meghanada.analyze.CompileResult;
import meghanada.analyze.JavaAnalyzer;
import meghanada.analyze.Source;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import meghanada.formatter.JavaFormatter;
import meghanada.store.ProjectDatabaseHelper;
import meghanada.store.Storable;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jdt.core.JavaCore;

public abstract class Project implements Serializable, Storable {

  public static final String GRADLE_PROJECT_FILE = "build.gradle";
  public static final String MVN_PROJECT_FILE = "pom.xml";

  // public static final String PROJECT_ROOT_KEY = "project.root";

  public static final Map<String, Project> loadedProject = new HashMap<>(4);
  public static final String ENTITY_TYPE = "Project";

  private static final long serialVersionUID = 7172580558461159805L;
  private static final String FORMATTER_FILE_KEY = "meghanada.formatter.file";
  private static final Logger log = LogManager.getLogger(Project.class);
  private static final String JAVA_HOME = "java-home";
  private static final String JAVA_VERSION = "java-version";
  private static final String COMPILE_SOURCE = "compile-source";
  private static final String COMPILE_TARGET = "compile-target";
  private static final String DEPENDENCIES = "dependencies";
  private static final String TEST_DEPENDENCIES = "test-dependencies";
  private static final String SOURCES = "sources";
  private static final String RESOURCES = "resources";
  private static final String TEST_SOURCES = "test-sources";
  private static final String TEST_RESOURCES = "test-resources";
  private static final String OUTPUT = "output";
  private static final String TEST_OUTPUT = "test-output";
  private static final String INCLUDE_FILE = "include-file";
  private static final String EXCLUDE_FILE = "exclude-file";
  private static final String FORMATTER_FILE = "meghanadaFormatter.properties";
  private static final String FORMATTER_FILE_XML = "meghanadaFormatter.xml";
  private static final Pattern SEP_COMPILE = Pattern.compile("/", Pattern.LITERAL);

  protected File projectRoot;
  protected Set<ProjectDependency> dependencies = new HashSet<>(16);
  protected String projectRootPath;
  protected Set<File> sources = new HashSet<>(2);
  protected Set<File> resources = new HashSet<>(2);
  protected File output;
  protected Set<File> testSources = new HashSet<>(2);
  protected Set<File> testResources = new HashSet<>(2);
  protected File testOutput;
  protected String compileSource = "1.8";
  protected String compileTarget = "1.8";
  protected Boolean isAndroidProject = false;
  protected String name;
  String id;
  private Map<String, Set<String>> callerMap = new ConcurrentHashMap<>(128);
  private String cachedClasspath;
  private String cachedAllClasspath;
  private transient JavaAnalyzer javaAnalyzer;
  private String[] prevTest;
  private transient Properties formatProperties;
  private boolean subProject;
  private transient Process runningProcess;

  public Project(final File projectRoot) throws IOException {
    this.projectRoot = projectRoot;
    this.name = projectRoot.getName();
    this.projectRootPath = this.projectRoot.getCanonicalPath();
    this.initialize();
  }

  private static CompileResult clearMemberCache(final CompileResult compileResult) {
    final Map<File, Source> sourceMap = compileResult.getSources();
    for (final Source source : sourceMap.values()) {
      source.invalidateCache();
    }
    return compileResult;
  }

  private static List<File> collectJavaFiles(final Set<File> sourceDirs) {
    return sourceDirs
        .parallelStream()
        .filter(File::exists)
        .map(root -> FileUtils.collectFiles(root, ".java"))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  public static Project loadProject(final String projectRoot) throws Exception {
    Project tempProject = ProjectDatabaseHelper.loadProject(projectRoot);
    if (tempProject != null) {
      tempProject.initialize();
    }
    return tempProject;
  }

  public void saveProject() {
    ProjectDatabaseHelper.saveProject(this, true);
  }

  protected void initialize() throws IOException {
    Config.setProjectRoot(this.projectRootPath);
    final File file = new File(projectRoot, FORMATTER_FILE);
    if (file.exists()) {
      System.setProperty(FORMATTER_FILE_KEY, file.getCanonicalPath());
    } else {
      final File xml = new File(projectRoot, FORMATTER_FILE_XML);
      if (xml.exists()) {
        System.setProperty(FORMATTER_FILE_KEY, xml.getCanonicalPath());
      }
    }
    loadCaller();
    final Config config = Config.load();
    final boolean clearCacheOnStart = config.clearCacheOnStart();
    if (clearCacheOnStart) {
      this.clearCache();
    }
  }

  public abstract Project parseProject() throws ProjectParseException;

  public Set<File> getAllSources() {
    final Set<File> temp = new HashSet<>(4);
    temp.addAll(this.sources);
    temp.addAll(this.resources);
    temp.addAll(this.testSources);
    temp.addAll(this.testResources);
    return temp;
  }

  private Set<File> getSourcesAndResources() {
    final Set<File> temp = new HashSet<>(2);
    temp.addAll(this.sources);
    temp.addAll(this.resources);
    return temp;
  }

  private Set<File> getTestSourcesAndResources() {
    final Set<File> temp = new HashSet<>(2);
    temp.addAll(this.testSources);
    temp.addAll(this.testResources);
    return temp;
  }

  public Set<File> getAllSourcesWithDependencies() {
    final Set<File> temp = getAllSources();
    this.dependencies.forEach(
        projectDependency -> temp.addAll(projectDependency.getProjectSources()));
    return temp;
  }

  public Set<ProjectDependency> getDependencies() {
    return this.dependencies;
  }

  private String getCompileSource() {
    return compileSource;
  }

  public void setCompileSource(String compileSource) {
    this.compileSource = compileSource;
  }

  public String getCompileTarget() {
    return compileTarget;
  }

  public void setCompileTarget(String compileTarget) {
    this.compileTarget = compileTarget;
  }

  private JavaAnalyzer getJavaAnalyzer() {
    if (this.javaAnalyzer == null) {
      this.javaAnalyzer = new JavaAnalyzer(this.compileSource, this.compileTarget);
    }
    return this.javaAnalyzer;
  }

  public String classpath() {
    if (this.cachedClasspath != null) {
      return this.cachedClasspath;
    }

    final Set<String> classpath = new HashSet<>(32);

    this.dependencies
        .stream()
        .filter(dependency -> !dependency.getScope().equals("TEST"))
        .map(ProjectDependency::getDependencyFilePath)
        .forEach(classpath::add);

    try {
      classpath.add(this.output.getCanonicalPath());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    this.cachedClasspath = String.join(File.pathSeparator, classpath);
    return this.cachedClasspath;
  }

  private String allClasspath() throws IOException {
    if (this.cachedAllClasspath != null) {
      return this.cachedAllClasspath;
    }

    final Set<String> classpath = new HashSet<>(32);
    this.dependencies
        .stream()
        .map(ProjectDependency::getDependencyFilePath)
        .forEach(classpath::add);

    classpath.add(this.output.getCanonicalPath());
    classpath.add(this.testOutput.getCanonicalPath());
    this.cachedAllClasspath = String.join(File.pathSeparator, classpath);
    return this.cachedAllClasspath;
  }

  public CompileResult compileJava() throws IOException {
    return compileJava(false);
  }

  public CompileResult compileJava(boolean force) throws IOException {

    final String origin = Config.getProjectRoot();
    try {
      Config.setProjectRoot(projectRootPath);

      List<File> files = Project.collectJavaFiles(sources);
      if (nonNull(files) && !files.isEmpty()) {

        if (this.callerMap.size() == 0) {
          force = true;
        }
        if (force) {
          this.callerMap.clear();
        }
        final Stopwatch stopwatch = Stopwatch.createStarted();

        files =
            force
                ? files
                : FileUtils.getModifiedSources(
                    this.projectRoot, files, this.getSourcesAndResources(), this.output);
        if (!force) {
          files = this.getRelatedSources(this.getSourcesAndResources(), files);
        }

        final String classpath = this.classpath();

        this.prepareCompile(files);

        final CompiledSourceHandler handler = new CompiledSourceHandler(this);
        final CompileResult compileResult =
            clearMemberCache(
                getJavaAnalyzer()
                    .analyzeAndCompile(files, classpath, output.getCanonicalPath(), true, handler));

        log.info(
            "project {} compile and analyze (java) {} files. force:{} problem:{} elapsed:{}",
            this.name,
            files.size(),
            force,
            compileResult.getDiagnostics().size(),
            stopwatch.stop());

        Config.setProjectRoot(projectRootPath);
        return compileResult;
      }
      return new CompileResult(true);
    } catch (Throwable t) {
      log.catching(t);
      final CompileResult result = new CompileResult(false);
      final Diagnostic<? extends JavaFileObject> diagnostic =
          CompileResult.getDiagnosticFromThrowable(t);
      if (diagnostic != null) {
        result.getDiagnostics().add(diagnostic);
      }
      return result;
    } finally {
      Config.setProjectRoot(origin);
    }
  }

  public CompileResult compileTestJava() throws IOException {
    return compileTestJava(false);
  }

  public CompileResult compileTestJava(boolean force) throws IOException {

    final String origin = Config.getProjectRoot();
    try {
      Config.setProjectRoot(projectRootPath);

      List<File> files = Project.collectJavaFiles(testSources);
      if (files != null && !files.isEmpty()) {
        if (this.callerMap.size() == 0) {
          force = true;
        }
        if (force) {
          this.callerMap.clear();
        }

        final Stopwatch stopwatch = Stopwatch.createStarted();

        files =
            force
                ? files
                : FileUtils.getModifiedSources(
                    projectRoot, files, this.getTestSourcesAndResources(), this.testOutput);
        if (!force) {
          files = this.getRelatedSources(this.getTestSourcesAndResources(), files);
        }

        final String classpath = this.allClasspath();
        this.prepareTestCompile(files);

        final CompiledSourceHandler handler = new CompiledSourceHandler(this);
        final CompileResult compileResult =
            clearMemberCache(
                getJavaAnalyzer()
                    .analyzeAndCompile(
                        files, classpath, testOutput.getCanonicalPath(), true, handler));

        log.info(
            "project {} compile and analyze (test) {} files. force:{} problem:{} elapsed:{}",
            this.name,
            files.size(),
            force,
            compileResult.getDiagnostics().size(),
            stopwatch.stop());

        Config.setProjectRoot(projectRootPath);
        return compileResult;
      }
      return new CompileResult(true);
    } catch (Throwable t) {
      log.catching(t);
      final CompileResult result = new CompileResult(false);
      final Diagnostic<? extends JavaFileObject> diagnostic =
          CompileResult.getDiagnosticFromThrowable(t);
      if (diagnostic != null) {
        result.getDiagnostics().add(diagnostic);
      }
      return result;
    } finally {
      Config.setProjectRoot(origin);
    }
  }

  protected void prepareCompile(final List<File> files) {}

  protected void prepareTestCompile(final List<File> files) {}

  public CompileResult parseFile(final File file) throws IOException {
    boolean isTest = false;

    final String filepath = file.getCanonicalPath();
    for (File source : this.testSources) {
      String testPath = source.getCanonicalPath();
      if (filepath.startsWith(testPath)) {
        isTest = true;
        break;
      }
    }

    String output;
    if (isTest) {
      output = this.testOutput.getCanonicalPath();
    } else {
      output = this.output.getCanonicalPath();
    }
    List<File> files = new ArrayList<>(2);
    files.add(file);
    return getJavaAnalyzer().analyzeAndCompile(files, this.allClasspath(), output, false);
  }

  public CompileResult compileFile(final File file, final boolean force, final boolean withRelated)
      throws IOException {
    boolean isTest = false;
    final String filepath = file.getCanonicalPath();

    for (final File source : this.testSources) {
      String testPath = source.getCanonicalPath();
      if (filepath.startsWith(testPath)) {
        isTest = true;
        break;
      }
    }

    String output;
    if (isTest) {
      output = this.testOutput.getCanonicalPath();
    } else {
      output = this.output.getCanonicalPath();
    }

    final Stopwatch stopwatch = Stopwatch.createStarted();
    List<File> files = new ArrayList<>(8);
    files.add(file);

    final Set<File> sources = isTest ? this.getAllSources() : this.getSourcesAndResources();
    files =
        force ? files : FileUtils.getModifiedSources(projectRoot, files, sources, new File(output));

    if (withRelated) {
      files = this.getRelatedSources(sources, files);
    }
    if (isTest) {
      this.prepareTestCompile(files);
    } else {
      this.prepareCompile(files);
    }

    final CompiledSourceHandler handler = new CompiledSourceHandler(this);
    final CompileResult compileResult =
        clearMemberCache(
            getJavaAnalyzer().analyzeAndCompile(files, this.allClasspath(), output, true, handler));

    log.info(
        "project {} compile and analyze {} files. force:{} problem:{} elapsed:{}",
        this.name,
        files.size(),
        force,
        compileResult.getDiagnostics().size(),
        stopwatch.stop());

    return compileResult;
  }

  public CompileResult compileFile(List<File> files, final boolean force) throws IOException {

    boolean isTest = false;
    // sampling
    String filepath = files.get(0).getCanonicalPath();
    for (File source : this.testSources) {
      String testPath = source.getCanonicalPath();
      if (filepath.startsWith(testPath)) {
        isTest = true;
        break;
      }
    }

    String output;
    if (isTest) {
      output = this.testOutput.getCanonicalPath();
    } else {
      output = this.output.getCanonicalPath();
    }

    final Set<File> sources = isTest ? this.getAllSources() : this.getSourcesAndResources();
    final Stopwatch stopwatch = Stopwatch.createStarted();

    files =
        force ? files : FileUtils.getModifiedSources(projectRoot, files, sources, new File(output));
    files = this.getRelatedSources(sources, files);

    if (isTest) {
      this.prepareTestCompile(files);
    } else {
      this.prepareCompile(files);
    }

    final CompiledSourceHandler handler = new CompiledSourceHandler(this);
    final CompileResult compileResult =
        clearMemberCache(
            getJavaAnalyzer().analyzeAndCompile(files, this.allClasspath(), output, true, handler));

    log.info(
        "project {} compile and analyze {} files. force:{} problem:{} elapsed:{}",
        this.name,
        files.size(),
        force,
        compileResult.getDiagnostics().size(),
        stopwatch.stop());

    return compileResult;
  }

  public File getProjectRoot() {
    return projectRoot;
  }

  public String getProjectRootPath() {
    return projectRootPath;
  }

  public File normalize(String src) {
    File file = new File(src);
    if (!file.isAbsolute()) {
      file = new File(this.projectRoot, src);
    }
    return file;
  }

  protected File normalizeFile(File file) {
    if (!file.isAbsolute()) {
      file = new File(this.projectRoot, file.getPath());
    }
    return file;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("projectRoot", projectRoot)
        .add("dependencies", dependencies.size())
        .add("sources", sources)
        .add("resources", resources)
        .add("output", output)
        .add("testSources", testSources)
        .add("testResources", testResources)
        .add("testOutput", testOutput)
        .add("compileSource", compileSource)
        .add("compileTarget", compileTarget)
        .toString();
  }

  protected InputStream runProcess(List<String> cmd) throws IOException {
    if (nonNull(this.runningProcess)) {
      if (this.runningProcess.isAlive()) {
        this.runningProcess.destroy();
      }
      this.runningProcess = null;
    }

    final ProcessBuilder processBuilder = new ProcessBuilder(cmd);
    processBuilder.directory(this.projectRoot);
    final String cmdString = String.join(" ", cmd);

    log.debug("RUN cmd: {}", cmdString);

    processBuilder.redirectErrorStream(true);
    final Process process = processBuilder.start();

    long pid = getPID(process);
    this.runningProcess = process;
    return process.getInputStream();
  }

  private long getPID(Process process) {
    long pid = -1;
    try {
      if (process.getClass().getName().equals("java.lang.UNIXProcess")) {
        Field field = process.getClass().getDeclaredField("pid");
        field.setAccessible(true);
        pid = field.getLong(process);
        field.setAccessible(false);
      }
    } catch (Exception e) {
      log.catching(e);
    }
    return pid;
  }

  public abstract InputStream runTask(List<String> args) throws IOException;

  public InputStream runJUnit(boolean debug, String test) throws IOException {
    try {
      return runUnitTest(debug, test);
    } finally {
      Config.setProjectRoot(this.projectRootPath);
    }
  }

  private InputStream runUnitTest(boolean debug, String... tests) throws IOException {
    if (tests[0].isEmpty()) {
      tests = this.prevTest;
    }
    log.debug("runUnit test:{} prevTest:{}", tests, prevTest);

    final Config config = Config.load();
    final List<String> cmd = new ArrayList<>(16);
    final String binJava =
        SEP_COMPILE.matcher("/bin/java").replaceAll(Matcher.quoteReplacement(File.separator));

    final String javaCmd = new File(config.getJavaHomeDir(), binJava).getCanonicalPath();
    cmd.add(javaCmd);

    String cp = this.allClasspath();

    final String jarPath = Config.getInstalledPath().getCanonicalPath();

    cp += File.pathSeparator + jarPath;
    cmd.add("-ea");
    cmd.add("-XX:+TieredCompilation");
    cmd.add("-XX:+UseConcMarkSweepGC");
    cmd.add("-XX:SoftRefLRUPolicyMSPerMB=50");
    cmd.add("-XX:ReservedCodeCacheSize=240m");
    cmd.add("-Dsun.io.useCanonCaches=false");
    cmd.add("-Xms128m");
    cmd.add("-Xmx750m");
    if (debug) {
      cmd.add("-Xdebug");
      cmd.add(
          "-Xrunjdwp:transport=dt_socket,address="
              + config.getDebuggerPort()
              + ",server=y,suspend=y");
    }
    cmd.add("-cp");
    cmd.add(cp);
    cmd.add(String.format("-Dproject.root=%s", this.projectRootPath));
    cmd.add(String.format("-Dmeghanada.output=%s", output.getCanonicalPath()));
    cmd.add(String.format("-Dmeghanada.test-output=%s", testOutput.getCanonicalPath()));
    cmd.add("meghanada.junit.TestRunner");
    Collections.addAll(cmd, tests);

    this.prevTest = tests;
    log.debug("run cmd {}", Joiner.on(" ").join(cmd));
    return this.runProcess(cmd);
  }

  public Project mergeFromProjectConfig() throws ProjectParseException {
    final File configFile = new File(this.projectRoot, Config.MEGHANADA_CONF_FILE);
    if (configFile.exists()) {
      final com.typesafe.config.Config config = ConfigFactory.parseFile(configFile);
      // java.home
      if (config.hasPath(JAVA_HOME)) {
        String o = config.getString(JAVA_HOME);
        System.setProperty("java.home", o);
      }
      // java.home
      if (config.hasPath(JAVA_VERSION)) {
        String o = config.getString(JAVA_VERSION);
        System.setProperty("java.specification.version", o);
      }

      // compile-source
      if (config.hasPath(COMPILE_SOURCE)) {
        this.compileSource = config.getString(COMPILE_SOURCE);
      }
      // compile-source
      if (config.hasPath(COMPILE_TARGET)) {
        this.compileTarget = config.getString(COMPILE_TARGET);
      }

      // dependencies
      if (config.hasPath(DEPENDENCIES)) {
        config
            .getStringList(DEPENDENCIES)
            .stream()
            .filter(path -> new File(path).exists())
            .map(
                path -> {
                  final File file = new File(path);
                  final ProjectDependency.Type type = ProjectDependency.getFileType(file);
                  return new ProjectDependency(file.getName(), "COMPILE", "1.0.0", file, type);
                })
            .forEach(this.dependencies::add);
      }
      // test-dependencies
      if (config.hasPath(TEST_DEPENDENCIES)) {
        config
            .getStringList(TEST_DEPENDENCIES)
            .stream()
            .filter(path -> new File(path).exists())
            .map(
                path -> {
                  final File file = new File(path);
                  final ProjectDependency.Type type = ProjectDependency.getFileType(file);
                  return new ProjectDependency(file.getName(), "TEST", "1.0.0", file, type);
                })
            .forEach(this.dependencies::add);
      }

      // sources
      if (config.hasPath(SOURCES)) {
        config
            .getStringList(SOURCES)
            .stream()
            .filter(path -> new File(path).exists())
            .map(File::new)
            .forEach(file -> this.sources.add(file));
      }
      // sources
      if (config.hasPath(RESOURCES)) {
        config
            .getStringList(RESOURCES)
            .stream()
            .filter(path -> new File(path).exists())
            .map(File::new)
            .forEach(file -> this.resources.add(file));
      }
      // test-sources
      if (config.hasPath(TEST_SOURCES)) {
        config
            .getStringList(TEST_SOURCES)
            .stream()
            .filter(path -> new File(path).exists())
            .map(File::new)
            .forEach(file -> this.testSources.add(file));
      }
      // test-resources
      if (config.hasPath(TEST_RESOURCES)) {
        config
            .getStringList(TEST_RESOURCES)
            .stream()
            .filter(path -> new File(path).exists())
            .map(File::new)
            .forEach(file -> this.testResources.add(file));
      }
      // output
      if (config.hasPath(OUTPUT)) {
        String o = config.getString(OUTPUT);
        this.output = new File(o);
      }
      // test-output
      if (config.hasPath(TEST_OUTPUT)) {
        String o = config.getString(TEST_OUTPUT);
        this.testOutput = new File(o);
      }

      final Config mainConfig = Config.load();
      if (config.hasPath(INCLUDE_FILE)) {
        final List<String> list = config.getStringList(INCLUDE_FILE);
        mainConfig.setIncludeList(list);
      }
      if (config.hasPath(EXCLUDE_FILE)) {
        final List<String> list = config.getStringList(INCLUDE_FILE);
        mainConfig.setExcludeList(list);
      }
    }

    // guard
    if (this.output == null) {
      throw new ProjectParseException("require output path");
    }

    if (this.testOutput == null) {
      throw new ProjectParseException("require test output path");
    }

    // freeze
    this.sources = new ImmutableSet.Builder<File>().addAll(this.sources).build();
    log.debug("sources {}", this.sources);
    this.resources = new ImmutableSet.Builder<File>().addAll(this.resources).build();
    log.debug("resources {}", this.resources);

    log.debug("output {}", this.output);

    this.testSources = new ImmutableSet.Builder<File>().addAll(this.testSources).build();
    log.debug("test sources {}", this.testSources);
    this.testResources = new ImmutableSet.Builder<File>().addAll(this.testResources).build();
    log.debug("test resources {}", this.testResources);

    log.debug("test output {}", this.testOutput);

    for (final ProjectDependency dependency : this.dependencies) {
      log.debug("dependency {}:{}", dependency.getId(), dependency.getVersion());
    }
    return this;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
    Project.loadedProject.put(id, this);
  }

  private List<File> getRelatedSources(final Set<File> sourceRoots, final List<File> files) {

    final Set<File> temp = Collections.newSetFromMap(new ConcurrentHashMap<File, Boolean>(16));
    temp.addAll(files);
    temp.addAll(FileUtils.getPackagePrivateSource(files));
    sourceRoots
        .parallelStream()
        .forEach(
            root -> {
              try {
                final String rootPath = root.getCanonicalPath();
                for (final File file : files) {
                  final String path = file.getCanonicalPath();
                  if (path.startsWith(rootPath)) {
                    final String p = path.substring(rootPath.length() + 1, path.length() - 5);
                    final String importClass = ClassNameUtils.replace(p, File.separator, ".");
                    if (this.callerMap.containsKey(importClass)) {
                      final Set<String> imports = this.callerMap.get(importClass);
                      for (final String dep : imports) {
                        FileUtils.getSourceFile(dep, sourceRoots).ifPresent(temp::add);
                      }
                    }
                  }
                }
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });

    return new ArrayList<>(temp);
  }

  public void resetCallerMap() {
    this.callerMap.clear();
  }

  private synchronized void writeCaller() throws IOException {
    ProjectDatabaseHelper.saveCallerMap(this.projectRootPath, this.callerMap);
  }

  private void loadCaller() throws IOException {

    @SuppressWarnings("unchecked")
    final Map<String, Set<String>> map = ProjectDatabaseHelper.getCallerMap(this.projectRootPath);
    this.callerMap = map;
  }

  @Override
  public String getStoreId() {
    return this.projectRootPath;
  }

  @Override
  public String getEntityType() {
    return ENTITY_TYPE;
  }

  @Override
  public void store(StoreTransaction txn, Entity entity) {
    entity.setProperty("name", name);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Project project = (Project) o;
    return Objects.equal(projectRoot, project.projectRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(projectRoot);
  }

  public void clearCache() throws IOException {
    ProjectDatabaseHelper.shutdown();
    Config.setProjectRoot(this.projectRootPath);
    final File projectSettingDir = new File(Config.load().getProjectSettingDir());
    log.info("clear cache {}", projectSettingDir);
    org.apache.commons.io.FileUtils.deleteDirectory(projectSettingDir);
  }

  private Optional<Properties> readFormatPropertiesFromFile() {
    final String val = System.getProperty(FORMATTER_FILE_KEY);
    if (val != null) {
      final File file = new File(val);
      log.info("load formatter rule from {}", val);
      if (val.endsWith(".xml")) {
        return Optional.of(JavaFormatter.getPropertiesFromXML(file));
      } else {
        try {
          final Properties prop = FileUtils.loadPropertiesFile(file);
          return Optional.ofNullable(prop);
        } catch (IOException e) {
          log.catching(e);
        }
      }
    }
    return Optional.empty();
  }

  public Optional<Properties> getFormatProperties() {
    if (this.formatProperties != null) {
      return Optional.of(this.formatProperties);
    }
    final Optional<Properties> properties = this.readFormatPropertiesFromFile();
    return properties.map(
        p -> {
          // merge
          p.setProperty(JavaCore.COMPILER_SOURCE, compileSource);
          p.setProperty(JavaCore.COMPILER_COMPLIANCE, compileSource);
          p.setProperty(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, compileSource);
          this.formatProperties = p;
          return this.formatProperties;
        });
  }

  public File getOutput() {
    return output;
  }

  public void setOutput(File output) {
    this.output = output;
  }

  public Set<File> getSources() {
    return sources;
  }

  public Set<File> getResources() {
    return resources;
  }

  public Set<File> getTestSources() {
    return testSources;
  }

  public Set<File> getTestResources() {
    return testResources;
  }

  public File getTestOutput() {
    return testOutput;
  }

  public void setTestOutput(File testOutput) {
    this.testOutput = testOutput;
  }

  public String getName() {
    return name;
  }

  public void resetCachedClasspath() {
    this.cachedClasspath = null;
    this.cachedAllClasspath = null;
  }

  public boolean isSubProject() {
    return subProject;
  }

  public void setSubProject(boolean subProject) {
    this.subProject = subProject;
  }

  public InputStream execMainClass(String mainClazz, boolean debug) throws IOException {
    log.debug("exec file:{}", mainClazz);

    final Config config = Config.load();
    final List<String> cmd = new ArrayList<>(16);
    final String binJava =
        SEP_COMPILE.matcher("/bin/java").replaceAll(Matcher.quoteReplacement(File.separator));

    final String javaCmd = new File(config.getJavaHomeDir(), binJava).getCanonicalPath();
    cmd.add(javaCmd);

    String cp = this.allClasspath();

    final String jarPath = Config.getInstalledPath().getCanonicalPath();

    cp += File.pathSeparator + jarPath;
    cmd.add("-ea");
    cmd.add("-XX:+TieredCompilation");
    cmd.add("-XX:+UseConcMarkSweepGC");
    cmd.add("-XX:SoftRefLRUPolicyMSPerMB=50");
    cmd.add("-XX:ReservedCodeCacheSize=240m");
    cmd.add("-Dsun.io.useCanonCaches=false");
    cmd.add("-Xms128m");
    cmd.add("-Xmx750m");
    if (debug) {
      cmd.add("-Xdebug");
      cmd.add(
          "-Xrunjdwp:transport=dt_socket,address="
              + config.getDebuggerPort()
              + ",server=y,suspend=y");
    }
    cmd.add("-cp");
    cmd.add(cp);
    cmd.add(String.format("-Dproject.root=%s", this.projectRootPath));
    cmd.add(String.format("-Dmeghanada.output=%s", output.getCanonicalPath()));
    cmd.add(String.format("-Dmeghanada.test-output=%s", testOutput.getCanonicalPath()));
    cmd.add(mainClazz);

    log.debug("run cmd {}", Joiner.on(" ").join(cmd));
    return this.runProcess(cmd);
  }

  public void killRunningProcess() {
    if (nonNull(this.runningProcess)) {
      if (this.runningProcess.isAlive()) {
        this.runningProcess.destroy();
      }
      this.runningProcess = null;
    }
  }

  public CompileResult compileString(final String sourceFile, final String sourceCode)
      throws IOException {

    boolean isTest = false;

    for (final File source : this.testSources) {
      String testPath = source.getCanonicalPath();
      if (sourceFile.startsWith(testPath)) {
        isTest = true;
        break;
      }
    }

    String output;
    if (isTest) {
      output = this.testOutput.getCanonicalPath();
    } else {
      output = this.output.getCanonicalPath();
    }
    final Stopwatch stopwatch = Stopwatch.createStarted();

    CompiledSourceHandler handler = new CompiledSourceHandler(this, true);
    CompileResult compileResult =
        clearMemberCache(
            getJavaAnalyzer()
                .runAnalyzeAndCompile(
                    this.allClasspath(), output, sourceFile, sourceCode, true, handler));

    log.info(
        "file {} compile and analyze problem:{} elapsed:{}",
        sourceFile,
        compileResult.getDiagnostics().size(),
        stopwatch.stop());
    return compileResult;
  }

  private static class CompiledSourceHandler implements JavaAnalyzer.SourceAnalyzedHandler {

    private final boolean useSourceCache;
    private final Map<String, Set<String>> callerMap;
    private final Map<String, String> checksumMap;
    private final Project project;
    private boolean diagnostics = false;

    CompiledSourceHandler(Project project) throws IOException {
      this.project = project;
      this.callerMap = project.callerMap;
      final Config config = Config.load();
      this.useSourceCache = config.useSourceCache();
      this.checksumMap = ProjectDatabaseHelper.getChecksumMap(project.projectRootPath);
    }

    CompiledSourceHandler(final Project project, boolean diagnostics) throws IOException {
      this(project);
      this.diagnostics = diagnostics;
    }

    @Override
    public void analyzed(final Source source) throws IOException {

      if (!useSourceCache) {
        return;
      }

      final GlobalCache globalCache = GlobalCache.getInstance();
      List<ClassScope> classScopes = source.getClassScopes();
      for (ClassScope cs : classScopes) {
        final String fqcn = cs.getFQCN();
        for (String clazz : source.usingClasses) {
          if (this.callerMap.containsKey(clazz)) {
            final Set<String> set = this.callerMap.get(clazz);
            set.add(fqcn);
            this.callerMap.put(clazz, set);
          } else {
            final Set<String> set = new HashSet<>(16);
            set.add(fqcn);
            this.callerMap.put(clazz, set);
          }
        }
        source.usingClasses.clear();
      }

      final File sourceFile = source.getFile();
      final String path = sourceFile.getCanonicalPath();
      source.invalidateCache();
      if (!source.hasCompileError) {
        final String md5sum = FileUtils.getChecksum(sourceFile);
        checksumMap.put(path, md5sum);
        globalCache.replaceSource(this.project, source);
        ProjectDatabaseHelper.saveSource(source);
      } else {
        // error
        checksumMap.remove(path);
        if (!this.diagnostics) {
          globalCache.replaceSource(this.project, source);
        } else {
          globalCache.invalidateSource(this.project, sourceFile);
        }
      }
    }

    @Override
    public void complete() throws IOException {
      ProjectDatabaseHelper.saveChecksumMap(this.project.projectRootPath, this.checksumMap);
      this.project.writeCaller();
    }
  }
}
