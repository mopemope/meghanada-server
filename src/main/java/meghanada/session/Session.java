package meghanada.session;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import meghanada.analyze.CompileResult;
import meghanada.analyze.Source;
import meghanada.cache.GlobalCache;
import meghanada.completion.JavaCompletion;
import meghanada.completion.JavaVariableCompletion;
import meghanada.completion.LocalVariable;
import meghanada.config.Config;
import meghanada.docs.declaration.Declaration;
import meghanada.docs.declaration.DeclarationSearcher;
import meghanada.location.Location;
import meghanada.location.LocationSearcher;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.gradle.GradleProject;
import meghanada.project.maven.MavenProject;
import meghanada.project.meghanada.MeghanadaProject;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

public class Session {

  private static final Logger log = LogManager.getLogger(Session.class);

  private static final Pattern SWITCH_TEST_RE = Pattern.compile("Test.java", Pattern.LITERAL);
  private static final Pattern SWITCH_JAVA_RE = Pattern.compile(".java", Pattern.LITERAL);
  private final SessionEventBus sessionEventBus;
  private final Deque<Location> jumpDecHistory = new ArrayDeque<>(16);
  private final HashMap<File, Project> projects = new HashMap<>(2);

  private Project currentProject;
  private JavaCompletion completion;
  private JavaVariableCompletion variableCompletion;
  private LocationSearcher locationSearcher;
  private DeclarationSearcher declarationSearcher;

  private boolean started;

  private Session(final Project currentProject) {
    this.currentProject = currentProject;
    this.sessionEventBus = new SessionEventBus(this);
    this.started = false;
    this.projects.put(currentProject.getProjectRoot(), currentProject);
  }

  public static Session createSession(String root) throws IOException {
    return createSession(new File(root));
  }

  private static Session createSession(File root) throws IOException {
    root = root.getCanonicalFile();
    final Optional<Project> result = findProject(root);
    return result
        .map(Session::new)
        .orElseThrow(() -> new IllegalArgumentException("Project Not Found"));
  }

  public static Optional<Project> findProject(File base) throws IOException {
    while (true) {

      log.debug("finding project from '{}' ...", base);
      if (base.getPath().equals("/")) {
        return Optional.empty();
      }

      // challenge
      final File gradle = new File(base, Project.GRADLE_PROJECT_FILE);
      final File mvn = new File(base, Project.MVN_PROJECT_FILE);
      final File meghanada = new File(base, Config.MEGHANADA_CONF_FILE);

      if (gradle.exists()) {
        log.debug("find gradle project {}", gradle);
        return loadProject(base, Project.GRADLE_PROJECT_FILE);
      } else if (mvn.exists()) {
        log.debug("find mvn project {}", mvn);
        return loadProject(base, Project.MVN_PROJECT_FILE);
      } else if (meghanada.exists()) {
        log.debug("find meghanada project {}", meghanada);
        return loadProject(base, Config.MEGHANADA_CONF_FILE);
      }

      File parent = base.getParentFile();
      if (parent == null) {
        return Optional.empty();
      }
      base = base.getParentFile();
    }
  }

  private static Optional<Project> loadProject(final File projectRoot, final String targetFile)
      throws IOException {
    final EntryMessage entryMessage =
        log.traceEntry("projectRoot={} targetFile={}", projectRoot, targetFile);
    final String projectRootPath = projectRoot.getCanonicalPath();
    System.setProperty(Project.PROJECT_ROOT_KEY, projectRootPath);

    try {
      final Config config = Config.load();
      final File settingFile = new File(projectRoot, Config.MEGHANADA_DIR);
      if (!settingFile.exists() && !settingFile.mkdirs()) {
        log.warn("{} mkdirs fail", settingFile);
      }
      final String id = FileUtils.findProjectID(projectRoot, targetFile);
      if (Project.loadedProject.containsKey(id)) {
        // loaded skip
        final Project project = Project.loadedProject.get(id);
        log.traceExit(entryMessage);
        System.setProperty(Project.PROJECT_ROOT_KEY, projectRootPath);
        return Optional.of(project);
      }

      log.trace("project projectID={} projectRoot={}", id, projectRoot);

      final File projectCache = FileUtils.getProjectDataFile(projectRoot, GlobalCache.PROJECT_DATA);

      if (config.useFastBoot() && projectCache.exists()) {
        try {
          final Project tempProject = Session.readProjectCache(projectCache);
          if (tempProject != null && tempProject.getId().equals(id)) {
            tempProject.setId(id);
            log.debug("load from cache project={}", tempProject);
            log.info("load project from cache. projectRoot:{}", tempProject.getProjectRoot());
            log.traceExit(entryMessage);
            return Optional.of(tempProject.mergeFromProjectConfig());
          }
        } catch (Exception ex) {
          // delete broken cache
          if (!projectCache.delete()) {
            log.warn("{} delete fail", projectCache);
          }
        }
      }

      Project project;
      switch (targetFile) {
        case Project.GRADLE_PROJECT_FILE:
          project = new GradleProject(projectRoot);
          break;
        case Project.MVN_PROJECT_FILE:
          project = new MavenProject(projectRoot);
          break;
        default:
          project = new MeghanadaProject(projectRoot);
          break;
      }
      project.setId(id);
      final Stopwatch stopwatch = Stopwatch.createStarted();
      final Project parsed = project.parseProject();
      if (config.useFastBoot()) {
        Session.writeProjectCache(projectCache, parsed);
      }
      log.info("loaded project:{} elapsed:{}", project.getProjectRoot(), stopwatch.stop());
      log.traceExit(entryMessage);
      return Optional.of(parsed.mergeFromProjectConfig());
    } finally {
      System.setProperty(Project.PROJECT_ROOT_KEY, projectRootPath);
    }
  }

  private static List<File> getSystemJars() throws IOException {
    final String javaHome = Config.load().getJavaHomeDir();
    final File jvmDir = new File(javaHome);
    final String toolsJarPath = Joiner.on(File.separator).join("..", "lib", "tools.jar");
    final File toolsJar = new File(jvmDir, toolsJarPath);

    try (final Stream<Path> stream = Files.walk(jvmDir.toPath())) {
      final List<File> files =
          stream
              .map(Path::toFile)
              .filter(f -> f.getName().endsWith(".jar") && !f.getName().endsWith("policy.jar"))
              .collect(Collectors.toList());
      files.add(toolsJar.getCanonicalFile());
      return files;
    }
  }

  private static void writeProjectCache(final File cacheFile, final Project project) {
    final GlobalCache globalCache = GlobalCache.getInstance();
    globalCache.asyncWriteCache(cacheFile, project);
  }

  private static Project readProjectCache(final File cacheFile) {
    final GlobalCache globalCache = GlobalCache.getInstance();
    return globalCache.readCacheFromFile(cacheFile, Project.class);
  }

  private static File findProjectRoot(File base) {
    while (true) {

      log.debug("finding project from '{}' ...", base);
      if (base.getPath().equals("/")) {
        return null;
      }

      // challenge
      final File gradle = new File(base, Project.GRADLE_PROJECT_FILE);
      final File mvn = new File(base, Project.MVN_PROJECT_FILE);
      final File meghanada = new File(base, Config.MEGHANADA_CONF_FILE);

      if (gradle.exists()) {
        log.debug("find gradle project {}", gradle);
        return base;
      } else if (mvn.exists()) {
        log.debug("find mvn project {}", mvn);
        return base;
      } else if (meghanada.exists()) {
        log.debug("find meghanada project {}", meghanada);
        return base;
      }

      File parent = base.getParentFile();
      if (parent == null) {
        return null;
      }
      base = base.getParentFile();
    }
  }

  public boolean clearCache() throws IOException {
    this.currentProject.clearCache();
    return true;
  }

  private boolean searchAndChangeProject(final File base) throws IOException {
    final File projectRoot = Session.findProjectRoot(base);

    if (projectRoot == null || this.currentProject.getProjectRoot().equals(projectRoot)) {
      // not change
      return false;
    }

    if (this.projects.containsKey(projectRoot)) {
      // loaded project
      this.currentProject = this.projects.get(projectRoot);
      this.getLocationSearcher().setProject(currentProject);
      this.getDeclarationSearcher().setProject(this.currentProject);
      this.getVariableCompletion().setProject(currentProject);
      return false;
    }

    if (currentProject instanceof GradleProject) {
      return loadProject(projectRoot, Project.GRADLE_PROJECT_FILE)
          .map(project -> setProject(projectRoot, project))
          .orElse(false);
    } else if (currentProject instanceof MavenProject) {
      return loadProject(projectRoot, Project.MVN_PROJECT_FILE)
          .map(project -> setProject(projectRoot, project))
          .orElse(false);
    }
    return loadProject(projectRoot, Config.MEGHANADA_CONF_FILE)
        .map(project -> setProject(projectRoot, project))
        .orElse(false);
  }

  private Boolean setProject(final File projectRoot, final Project project) {
    this.currentProject = project;
    this.projects.put(projectRoot, this.currentProject);
    this.getLocationSearcher().setProject(currentProject);
    this.getDeclarationSearcher().setProject(this.currentProject);
    this.getVariableCompletion().setProject(currentProject);
    this.getCompletion().setProject(currentProject);
    return true;
  }

  private void setupSubscribes() {
    // subscribe file watch
    this.sessionEventBus.subscribeFileWatch();
    this.sessionEventBus.subscribeParse();
    this.sessionEventBus.subscribeCache();
  }

  public void start() throws IOException {
    if (this.started) {
      return;
    }

    this.setupSubscribes();
    log.debug("session start");

    final Set<File> temp = new HashSet<>(currentProject.getSources());
    temp.addAll(currentProject.getTestSources());
    this.sessionEventBus.requestWatchFiles(new ArrayList<>(temp));

    // load once
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    reflector.addClasspath(Session.getSystemJars());
    this.sessionEventBus.requestCreateCache();

    this.projects
        .values()
        .forEach(project -> this.sessionEventBus.requestWatchFile(project.getProjectRoot()));

    log.debug("session started");
    this.started = true;
  }

  public void shutdown(int timeout) {
    log.debug("session shutdown ...");
    this.sessionEventBus.shutdown(timeout);
    log.debug("session shutdown done");
  }

  public Project getCurrentProject() {
    return currentProject;
  }

  private LocationSearcher getLocationSearcher() {
    if (this.locationSearcher == null) {
      this.locationSearcher = new LocationSearcher(currentProject);
    }
    return locationSearcher;
  }

  private JavaCompletion getCompletion() {
    if (this.completion == null) {
      this.completion = new JavaCompletion(currentProject);
    }
    return this.completion;
  }

  private JavaVariableCompletion getVariableCompletion() {
    if (this.variableCompletion == null) {
      this.variableCompletion = new JavaVariableCompletion(currentProject);
    }
    return variableCompletion;
  }

  public synchronized Collection<? extends CandidateUnit> completionAt(
      String path, int line, int column, String prefix) {
    // java file only
    File file = normalize(path);
    if (!FileUtils.isJavaFile(file)) {
      return Collections.emptyList();
    }
    return getCompletion().completionAt(file, line, column, prefix);
  }

  public synchronized boolean changeProject(final String path) {
    final File file = new File(path);

    if (this.started) {
      try {
        if (!file.exists()) {
          return true;
        }
        final boolean changed = this.searchAndChangeProject(file);
        if (changed) {
          this.sessionEventBus.requestCreateCache();
        } else {
          // load source
          final GlobalCache globalCache = GlobalCache.getInstance();
          final Source source = globalCache.getSource(currentProject, file);
        }
        return true;
      } catch (Exception e) {
        log.catching(e);
        return false;
      }
    }

    return false;
  }

  public synchronized Optional<LocalVariable> localVariable(final String path, final int line)
      throws ExecutionException, IOException {
    // java file only
    final File file = normalize(path);
    if (!FileUtils.isJavaFile(file)) {
      return Optional.of(new LocalVariable("void", Collections.emptyList()));
    }
    return getVariableCompletion().localVariable(file, line);
  }

  public synchronized boolean addImport(final String path, final String fqcn)
      throws ExecutionException {
    // java file only
    final File file = normalize(path);
    if (!FileUtils.isJavaFile(file)) {
      return false;
    }
    log.debug("addImport path={} fqcn={}", path, fqcn);
    return parseJavaSource(file).map(source -> source.addImportIfAbsent(fqcn)).orElse(false);
  }

  public synchronized void optimizeImport(final String path) throws ExecutionException {
    // java file only
    final File file = normalize(path);
    if (!FileUtils.isJavaFile(file)) {
      return;
    }

    parseJavaSource(file)
        .ifPresent(
            source -> {
              final List<String> optimized = source.optimizeImports();
              final StringBuilder sb = new StringBuilder(1024 * 4);
              if (source.packageName != null && !source.packageName.isEmpty()) {
                sb.append("package ").append(source.packageName).append(";\n");
              }

              if (source.staticImportClass.size() > 0) {
                sb.append('\n');
                source
                    .staticImportClass
                    .entrySet()
                    .stream()
                    .map(
                        e -> {
                          final String method = e.getKey();
                          final String fqcn = e.getValue();
                          return fqcn + '.' + method;
                        })
                    .sorted(Comparator.naturalOrder())
                    .forEach(s -> sb.append("import static ").append(s).append(";\n"));
                sb.append('\n');
              }
              for (final String fqcn : optimized) {
                sb.append("import ").append(fqcn).append(";\n");
              }

              try (final Stream<String> stream =
                  Files.lines(file.toPath(), StandardCharsets.UTF_8)) {
                stream.skip(source.classStartLine).forEach(s -> sb.append(s).append('\n'));
                Files.write(
                    Paths.get(path),
                    sb.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  public synchronized Map<String, List<String>> searchMissingImport(String path)
      throws ExecutionException {
    // java file only
    final File file = normalize(path);
    if (!FileUtils.isJavaFile(file)) {
      return Collections.emptyMap();
    }

    return parseJavaSource(file).map(Source::searchMissingImport).orElse(Collections.emptyMap());
  }

  private Optional<Source> parseJavaSource(final File file) throws ExecutionException {
    if (!FileUtils.isJavaFile(file)) {
      return Optional.empty();
    }
    final GlobalCache globalCache = GlobalCache.getInstance();
    return Optional.of(globalCache.getSource(currentProject, file));
  }

  public synchronized boolean parseFile(final String path) throws ExecutionException {
    // java file only
    final File file = normalize(path);
    if (!FileUtils.isJavaFile(file)) {
      return false;
    }
    final GlobalCache globalCache = GlobalCache.getInstance();
    globalCache.invalidateSource(currentProject, file);
    this.parseJavaSource(file).ifPresent(source -> this.sessionEventBus.requestCreateCache(true));
    return true;
  }

  public synchronized CompileResult compileFile(final String path) throws IOException {
    // java file only
    final File file = normalize(path);
    final CompileResult compileResult = currentProject.compileFileNoCache(file, true);
    this.sessionEventBus.requestCreateCache(true);
    return compileResult;
  }

  public synchronized CompileResult compileProject() throws IOException {
    final Project project = currentProject;
    final CompileResult result = project.compileJava(false);
    if (result.hasDiagnostics()) {
      log.warn("compileProject report:{}", result.getDiagnosticsSummary());
    }
    if (result.isSuccess()) {

      final CompileResult testResult = project.compileTestJava(false);
      if (testResult.hasDiagnostics()) {
        for (final Diagnostic<? extends JavaFileObject> diagnostic : testResult.getDiagnostics()) {
          result.getDiagnostics().add(diagnostic);
        }
        log.warn("compileProject test report:{}", testResult.getDiagnosticsSummary());
      }
    }

    this.sessionEventBus.requestCreateCache(true);

    return result;
  }

  public Collection<File> getDependentJars() {
    return currentProject
        .getDependencies()
        .stream()
        .map(ProjectDependency::getFile)
        .collect(Collectors.toList());
  }

  private File normalize(String src) {
    File file = new File(src);
    if (!file.isAbsolute()) {
      file = new File(currentProject.getProjectRoot(), src);
    }
    return file;
  }

  public InputStream runJUnit(String test) throws IOException {
    return currentProject.runJUnit(test);
  }

  public Optional<String> switchTest(final String path) throws IOException {
    Project project = currentProject;
    String root = null;
    Set<File> roots;
    boolean isTest;

    if (path.endsWith("Test.java")) {
      // test -> src
      roots = project.getTestSources();
      isTest = true;
    } else {
      // src -> test
      roots = project.getSources();
      isTest = false;
    }

    for (final File file : roots) {
      final String rootPath = file.getCanonicalPath();
      if (path.startsWith(rootPath)) {
        root = rootPath;
        break;
      }
    }
    if (root == null) {
      return Optional.empty();
    }

    String switchPath = path.substring(root.length());

    if (isTest) {
      switchPath = SWITCH_TEST_RE.matcher(switchPath).replaceAll(Matcher.quoteReplacement(".java"));
      // to src
      for (File srcRoot : project.getSources()) {
        final File srcFile = new File(srcRoot, switchPath);
        if (srcFile.exists()) {
          return Optional.of(srcFile.getCanonicalPath());
        }
      }

    } else {
      switchPath =
          SWITCH_JAVA_RE.matcher(switchPath).replaceAll(Matcher.quoteReplacement("Test.java"));
      // to test
      for (File srcRoot : project.getTestSources()) {
        final File testFile = new File(srcRoot, switchPath);
        if (testFile.exists()) {
          return Optional.of(testFile.getCanonicalPath());
        }
      }
    }

    return Optional.empty();
  }

  public synchronized Optional<Location> jumpDeclaration(
      final String path, final int line, final int column, final String symbol)
      throws ExecutionException, IOException {
    final Optional<Location> location =
        this.getLocationSearcher().searchDeclarationLocation(new File(path), line, column, symbol);

    location.ifPresent(
        a -> {
          Location backLocation = new Location(path, line, column);
          this.jumpDecHistory.addLast(backLocation);
        });

    if (!location.isPresent()) {
      log.warn("missing location path={} line={} column={} symbol={}", path, line, column, symbol);
    }
    return location;
  }

  public synchronized Location backDeclaration() {
    return this.jumpDecHistory.pollLast();
  }

  public InputStream runTask(List<String> args) throws Exception {
    return currentProject.runTask(args);
  }

  public void formatCode(final String path) throws IOException {
    final Project project = currentProject;
    final Optional<Properties> formatProperties = project.getFormatProperties();
    if (!formatProperties.isPresent()) {
      FileUtils.formatJavaFile(path);
    } else {
      FileUtils.formatJavaFile(formatProperties.get(), path);
    }
  }

  public void reloadProject() throws IOException {
    final Project currentProject = this.currentProject;
    final File projectRoot = currentProject.getProjectRoot();
    this.projects.clear();
    if (currentProject instanceof GradleProject) {
      loadProject(projectRoot, Project.GRADLE_PROJECT_FILE)
          .ifPresent(project -> setProject(projectRoot, project));
    } else if (currentProject instanceof MavenProject) {
      loadProject(projectRoot, Project.MVN_PROJECT_FILE)
          .ifPresent(project -> setProject(projectRoot, project));
    } else {
      loadProject(projectRoot, Config.MEGHANADA_CONF_FILE)
          .ifPresent(project -> setProject(projectRoot, project));
    }

    final Set<File> temp = new HashSet<>(this.currentProject.getSources());
    temp.addAll(this.currentProject.getTestSources());
    this.sessionEventBus.requestWatchFiles(new ArrayList<>(temp));
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    reflector.resetClassFileMap();
    reflector.addClasspath(Session.getSystemJars());
    this.sessionEventBus.requestCreateCache();
    this.projects
        .values()
        .forEach(project -> this.sessionEventBus.requestWatchFile(project.getProjectRoot()));
  }

  public Optional<Declaration> showDeclaration(
      final String path, final int line, final int column, final String symbol)
      throws IOException, ExecutionException {
    final DeclarationSearcher searcher = this.getDeclarationSearcher();
    return searcher.searchDeclaration(new File(path), line, column, symbol);
  }

  private DeclarationSearcher getDeclarationSearcher() {
    if (this.declarationSearcher == null) {
      this.declarationSearcher = new DeclarationSearcher(currentProject);
    }
    return declarationSearcher;
  }
}
