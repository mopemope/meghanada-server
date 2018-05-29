package meghanada.project;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
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
import meghanada.analyze.CompileResult;
import meghanada.analyze.JavaAnalyzer;
import meghanada.analyze.Source;
import meghanada.analyze.subscribe.IndexSubscriber;
import meghanada.analyze.subscribe.SourceCacheSubscriber;
import meghanada.config.Config;
import meghanada.formatter.JavaFormatter;
import meghanada.store.ProjectDatabaseHelper;
import meghanada.store.Storable;
import meghanada.utils.FileUtils;
import meghanada.utils.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jdt.core.JavaCore;

public abstract class Project implements Serializable, Storable {

  public static final String GRADLE_PROJECT_FILE = "build.gradle";
  public static final String MVN_PROJECT_FILE = "pom.xml";
  public static final String ECLIPSE_PROJECT_FILE = ".project";

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
  private static final String JAVA8_JAVAC_ARGS = "java8-javac-args";
  private static final String JAVA9_JAVAC_ARGS = "java9-javac-args";
  private static final String JAVA10_JAVAC_ARGS = "java10-javac-args";
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
  protected int androidApiVersion;
  protected String androidModelVersion;
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

  public abstract Project parseProject(File projectRoot, File current) throws ProjectParseException;

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
    if (isNull(this.javaAnalyzer)) {
      this.javaAnalyzer = new JavaAnalyzer(this.compileSource, this.compileTarget);
      this.javaAnalyzer.getEventBus().register(new SourceCacheSubscriber(this));
      if (Config.load().useFullTextSearch()) {
        this.javaAnalyzer.getEventBus().register(new IndexSubscriber(this));
      }
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

        final CompileResult compileResult =
            clearMemberCache(
                getJavaAnalyzer()
                    .analyzeAndCompile(files, classpath, output.getCanonicalPath(), true));

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

        final CompileResult compileResult =
            clearMemberCache(
                getJavaAnalyzer()
                    .analyzeAndCompile(files, classpath, testOutput.getCanonicalPath(), true));

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

    final CompileResult compileResult =
        clearMemberCache(
            getJavaAnalyzer().analyzeAndCompile(files, this.allClasspath(), output, true));

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

    final CompileResult compileResult =
        clearMemberCache(
            getJavaAnalyzer().analyzeAndCompile(files, this.allClasspath(), output, true));

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
    cmd.add("-XX:SoftRefLRUPolicyMSPerMB=50");
    cmd.add("-XX:ReservedCodeCacheSize=240m");
    cmd.add("-Dsun.io.useCanonCaches=false");
    cmd.add("-Xms128m");
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
            .map(
                path -> {
                  File file = new File(path);
                  if (!file.isAbsolute()) {
                    file = new File(this.projectRoot, path);
                  }
                  return file;
                })
            .filter(File::exists)
            .map(
                file -> {
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
            .map(
                path -> {
                  File file = new File(path);
                  if (!file.isAbsolute()) {
                    file = new File(this.projectRoot, path);
                  }
                  return file;
                })
            .filter(File::exists)
            .map(
                file -> {
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
            .map(
                path -> {
                  File file = new File(path);
                  if (!file.isAbsolute()) {
                    file = new File(this.projectRoot, path);
                  }
                  return file;
                })
            .filter(File::exists)
            .forEach(file -> this.sources.add(file));
      }
      // sources
      if (config.hasPath(RESOURCES)) {
        config
            .getStringList(RESOURCES)
            .stream()
            .map(
                path -> {
                  File file = new File(path);
                  if (!file.isAbsolute()) {
                    file = new File(this.projectRoot, path);
                  }
                  return file;
                })
            .filter(File::exists)
            .forEach(file -> this.resources.add(file));
      }
      // test-sources
      if (config.hasPath(TEST_SOURCES)) {
        config
            .getStringList(TEST_SOURCES)
            .stream()
            .map(
                path -> {
                  File file = new File(path);
                  if (!file.isAbsolute()) {
                    file = new File(this.projectRoot, path);
                  }
                  return file;
                })
            .filter(File::exists)
            .forEach(file -> this.testSources.add(file));
      }
      // test-resources
      if (config.hasPath(TEST_RESOURCES)) {
        config
            .getStringList(TEST_RESOURCES)
            .stream()
            .map(
                path -> {
                  File file = new File(path);
                  if (!file.isAbsolute()) {
                    file = new File(this.projectRoot, path);
                  }
                  return file;
                })
            .filter(File::exists)
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

      if (config.hasPath(JAVA8_JAVAC_ARGS) && mainConfig.isJava8()) {
        final List<String> list = config.getStringList(JAVA8_JAVAC_ARGS);
        mainConfig.setJava8JavacArgs(list);
      }
      if (config.hasPath(JAVA9_JAVAC_ARGS) && mainConfig.isJava9()) {
        final List<String> list = config.getStringList(JAVA9_JAVAC_ARGS);
        mainConfig.setJava9JavacArgs(list);
      }
      if (config.hasPath(JAVA10_JAVAC_ARGS) && mainConfig.isJava10()) {
        final List<String> list = config.getStringList(JAVA10_JAVAC_ARGS);
        mainConfig.setJava10JavacArgs(list);
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
                    final String importClass = StringUtils.replace(p, File.separator, ".");
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

  public Map<String, Set<String>> getCallerMap() {
    return this.callerMap;
  }

  public synchronized void writeCaller() throws IOException {
    boolean b = ProjectDatabaseHelper.saveCallerMap(this.projectRootPath, this.callerMap);
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
    if (nonNull(val)) {
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

    CompileResult compileResult =
        clearMemberCache(
            getJavaAnalyzer()
                .runAnalyzeAndCompile(
                    this.allClasspath(), output, sourceFile, sourceCode, true, true));

    log.info(
        "file {} compile and analyze problem:{} elapsed:{}",
        sourceFile,
        compileResult.getDiagnostics().size(),
        stopwatch.stop());
    return compileResult;
  }

  public int getAndroidApiVersion() {
    return androidApiVersion;
  }

  public void setAndroidApiVersion(int androidApiVersion) {
    this.androidApiVersion = androidApiVersion;
  }

  public String getAndroidModelVersion() {
    return androidModelVersion;
  }

  public void setAndroidModelVersion(String androidModelVersion) {
    this.androidModelVersion = androidModelVersion;
  }

  public abstract String getProjectType();

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(8096);
    Config config = Config.load();

    {
      sb.append("System:\n");
      sb.append(Strings.repeat("-", 80));
      sb.append("\n");
      sb.append(String.format("os: %s\n", System.getProperty("os.name")));
      sb.append(String.format("osVersion: %s\n", System.getProperty("os.version")));
      sb.append(String.format("osArch: %s\n", System.getProperty("os.arch")));
      sb.append(String.format("memory: %s\n", Config.showMemoryString()));
      sb.append("\n");
    }

    {
      sb.append("Meghanada:\n");
      sb.append(Strings.repeat("-", 80));
      sb.append("\n");
      //
      try {
        sb.append(String.format("meghanadaVersion: %s\n", meghanada.Main.getVersion()));
      } catch (IOException e) {
        log.catching(e);
      }
      sb.append(String.format("meghanadaPath: %s\n", Config.getInstalledPath()));
      sb.append(
          String.format("meghanadaServerPort: %s\n", System.getProperty("meghanada.server.port")));
      sb.append(String.format("home: %s\n", config.getHomeDir()));
      sb.append(String.format("userHome: %s\n", config.getUserHomeDir()));
      sb.append(String.format("useFastBoot: %s\n", config.useFastBoot()));
      sb.append(
          String.format(
              "classCompletionMatcher: %s\n", config.classCompletionMatcher().toString()));
      sb.append(String.format("completionMatcher: %s\n", config.completionMatcher().toString()));
      sb.append(
          String.format("useJavaVersion: %s\n", System.getProperty("java.specification.version")));
      sb.append(String.format("javacArg: %s\n", config.getJavacArg()));
      sb.append(String.format("useSourceCache: %s\n", config.useSourceCache()));
      sb.append(String.format("cacheInProject: %s\n", config.isCacheInProject()));
      sb.append(String.format("cacheRoot: %s\n", config.getCacheRoot()));
      sb.append(String.format("useExternalBuilder: %s\n", config.useExternalBuilder()));
      sb.append(String.format("clearCacheOnStart: %s\n", config.clearCacheOnStart()));
      sb.append(String.format("isSkipBuildSubProjects: %s\n", config.isSkipBuildSubProjects()));
      sb.append(String.format("useAOSPStyleFormat: %s\n", config.useAOSPStyle()));
      sb.append(String.format("mavenLocalRepository: %s\n", config.getMavenLocalRepository()));
      sb.append(String.format("useFullTextSearch: %s\n", config.useFullTextSearch()));
      sb.append("\n");
    }

    {
      sb.append("Java:\n");
      sb.append(Strings.repeat("-", 80));
      sb.append("\n");
      // java info
      sb.append(String.format("javaHome: %s\n", System.getProperty("java.home")));
      sb.append(String.format("javaVersion: %s\n", System.getProperty("java.version")));
      sb.append(String.format("compileSource: %s\n", compileSource));
      sb.append(String.format("compileTarget: %s\n", compileTarget));
      if (config.isJava8()) {
        sb.append(String.format("javac8Args: %s\n", config.getJava8JavacArgs()));
      } else if (config.isJava9()) {
        sb.append(String.format("javac9Args: %s\n", config.getJava9JavacArgs()));
      } else if (config.isJava10()) {
        sb.append(String.format("javac10Args: %s\n", config.getJava10JavacArgs()));
      }
      if (nonNull(this.cachedClasspath)) {
        List<String> cpList = Splitter.on(File.pathSeparator).splitToList(this.cachedClasspath);
        if (cpList.size() > 0) {
          sb.append("classpath:\n");
          cpList.forEach(
              s -> {
                sb.append(String.format("  %s\n", s));
              });
          sb.append("\n");
        }
      }
      if (nonNull(this.cachedAllClasspath)) {
        List<String> cpAllList =
            Splitter.on(File.pathSeparator).splitToList(this.cachedAllClasspath);
        if (cpAllList.size() > 0) {
          sb.append("allClasspath:\n");
          cpAllList.forEach(
              s -> {
                sb.append(String.format("  %s\n", s));
              });
          sb.append("\n");
        }
      }
      Properties sysProp = System.getProperties();
      sb.append("SystemProperties:\n");
      sysProp.forEach(
          (key, value) -> {
            sb.append(String.format("  %s: %s\n", key, value));
          });
      sb.append("\n");
    }

    {
      sb.append("Project:\n");
      sb.append(Strings.repeat("-", 80));
      sb.append("\n");
      // project info
      sb.append(String.format("project: %s\n", getProjectType()));
      sb.append(String.format("projectRoot: %s\n", projectRoot));
      sb.append(String.format("gradlePrepareCompileTask: %s\n", config.gradlePrepareCompileTask()));
      sb.append(
          String.format(
              "gradlePrepareTestCompileTask: %s\n", config.gradlePrepareTestCompileTask()));

      sb.append(
          String.format(
              "projectDatbase: %s\n",
              meghanada.store.ProjectDatabase.getInstance().getBaseLocation()));

      long longSize =
          org.apache.commons.io.FileUtils.sizeOfDirectory(
              meghanada.store.ProjectDatabase.getInstance().getBaseLocation());
      float size = longSize / 1024 / 1024;
      sb.append("projectDatabaseSize: ");
      sb.append(String.format("  %.2fMB\n", size));

      sb.append("source-formatter: ");
      Optional<Properties> op = getFormatProperties();
      if (op.isPresent()) {
        String settingFile = System.getProperty(FORMATTER_FILE_KEY);
        sb.append("eclipse ");
        sb.append(settingFile);
      } else {
        sb.append("google ");
        if (config.useAOSPStyle()) {
          sb.append("(AOSP)");
        }
      }
      sb.append("\n");
      sb.append("sources:\n");
      sources.forEach(
          s -> {
            sb.append(String.format("  %s\n", s));
          });
      sb.append("resources:\n");
      resources.forEach(
          s -> {
            sb.append(String.format("  %s\n", s));
          });
      sb.append("output:\n");
      sb.append(String.format("  %s\n", output));

      sb.append("testSources:\n");
      testSources.forEach(
          s -> {
            sb.append(String.format("  %s\n", s));
          });
      sb.append("testResources:\n");
      testResources.forEach(
          s -> {
            sb.append(String.format("  %s\n", s));
          });
      sb.append("testOutput:\n");
      sb.append(String.format("  %s\n", testOutput));

      sb.append("dependencies:\n");
      dependencies.forEach(
          d -> {
            sb.append(String.format("  id:%s \n", d.getId()));
            sb.append(String.format("  scope: %s\n", d.getScope()));
            sb.append(String.format("  file: %s\n\n", d.getFile()));
          });
    }

    return sb.toString();
  }
}
