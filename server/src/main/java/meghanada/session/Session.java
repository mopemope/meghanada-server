package meghanada.session;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

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
import meghanada.completion.JavaImportCompletion;
import meghanada.completion.JavaVariableCompletion;
import meghanada.completion.LocalVariable;
import meghanada.config.Config;
import meghanada.docs.declaration.Declaration;
import meghanada.docs.declaration.DeclarationSearcher;
import meghanada.index.IndexDatabase;
import meghanada.index.SearchResults;
import meghanada.location.Location;
import meghanada.location.LocationSearcher;
import meghanada.module.ModuleHelper;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.eclipse.EclipseProject;
import meghanada.project.gradle.GradleProject;
import meghanada.project.maven.MavenProject;
import meghanada.project.meghanada.MeghanadaProject;
import meghanada.reference.Reference;
import meghanada.reference.ReferenceSearcher;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.typeinfo.TypeInfo;
import meghanada.typeinfo.TypeInfoSearcher;
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
  private JavaImportCompletion importCompletion;
  private LocationSearcher locationSearcher;
  private DeclarationSearcher declarationSearcher;
  private ReferenceSearcher referenceSearcher;
  private TypeInfoSearcher typeinfoSearcher;

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
    File current = base;
    while (true) {
      log.debug("finding project from '{}' ...", base);
      if (isNull(base.getParent())) {
        return Optional.empty();
      }

      // challenge
      final File gradle = new File(base, Project.GRADLE_PROJECT_FILE);
      final File mvn = new File(base, Project.MVN_PROJECT_FILE);
      final File eclipse = new File(base, Project.ECLIPSE_PROJECT_FILE);
      final File meghanada = new File(base, Config.MEGHANADA_CONF_FILE);

      if (gradle.exists()) {
        log.debug("find gradle project {}", gradle);
        return loadProject(base, Project.GRADLE_PROJECT_FILE, current);
      } else if (mvn.exists()) {
        log.debug("find mvn project {}", mvn);
        return loadProject(base, Project.MVN_PROJECT_FILE, current);
      } else if (eclipse.exists()) {
        log.debug("find eclipse project {}", eclipse);
        return loadProject(base, Project.ECLIPSE_PROJECT_FILE, current);
      } else if (meghanada.exists()) {
        log.debug("find meghanada project {}", meghanada, current);
        return loadProject(base, Config.MEGHANADA_CONF_FILE, current);
      }

      File parent = base.getParentFile();
      if (isNull(parent)) {
        return Optional.empty();
      }
      base = base.getParentFile();
    }
  }

  private static Optional<Project> loadProject(File projectRoot, String targetFile, File current)
      throws IOException {

    EntryMessage entryMessage =
        log.traceEntry("projectRoot={} targetFile={}", projectRoot, targetFile);

    String projectRootPath = projectRoot.getCanonicalPath();
    Config.setProjectRoot(projectRootPath);

    try {
      Config config = Config.load();

      String id = FileUtils.findProjectID(projectRoot, targetFile);
      if (Project.loadedProject.containsKey(id)) {
        // loaded skip
        Project project = Project.loadedProject.get(id);
        log.traceExit(entryMessage);
        Config.setProjectRoot(projectRootPath);
        return Optional.of(project);
      }

      log.trace("project projectID={} projectRoot={}", id, projectRoot);

      if (config.useFastBoot()) {
        try {
          Project tempProject = Project.loadProject(projectRootPath);
          if (nonNull(tempProject) && tempProject.getId().equals(id)) {
            tempProject.setId(id);
            log.debug("load from cache project={}", tempProject);
            log.info("load project from cache. projectRoot:{}", tempProject.getProjectRoot());
            log.traceExit(entryMessage);
            return Optional.of(tempProject.mergeFromProjectConfig());
          }
        } catch (Exception ex) {
          log.catching(ex);
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
        case Project.ECLIPSE_PROJECT_FILE:
          project = new EclipseProject(projectRoot);
          break;
        default:
          project = new MeghanadaProject(projectRoot);
          break;
      }

      project.setId(id);
      Stopwatch stopwatch = Stopwatch.createStarted();
      Project parsed = project.parseProject(projectRoot, current);
      if (config.useFastBoot()) {
        parsed.saveProject();
      }
      log.info("loaded project:{} elapsed:{}", project.getProjectRoot(), stopwatch.stop());

      log.traceExit(entryMessage);
      return Optional.of(parsed.mergeFromProjectConfig());
    } finally {
      Config.setProjectRoot(projectRootPath);
    }
  }

  private static List<File> getSystemJars() throws IOException {
    Config config = Config.load();

    if (config.isJava8()) {
      final String javaHome = Config.load().getJavaHomeDir();
      final File jvmDir = new File(javaHome);
      final String toolsJarPath = Joiner.on(File.separator).join("..", "lib", "tools.jar");
      final File toolsJar = new File(jvmDir, toolsJarPath);

      try (final Stream<Path> stream = Files.walk(jvmDir.toPath())) {
        final List<File> files =
            stream
                .map(Path::toFile)
                .filter(
                    f ->
                        f.getName().endsWith(FileUtils.JAR_EXT)
                            && !f.getName().endsWith("policy.jar"))
                .collect(Collectors.toList());
        files.add(toolsJar.getCanonicalFile());
        return files;
      }
    } else {
      List<File> result = new ArrayList<>(1);
      result.add(ModuleHelper.getJrtFsFile());
      return result;
    }
  }

  private static File findProjectRoot(File base) {
    while (true) {

      log.debug("finding project from '{}' ...", base);
      if (isNull(base.getParent())) {
        return null;
      }

      // challenge
      final File gradle = new File(base, Project.GRADLE_PROJECT_FILE);
      final File mvn = new File(base, Project.MVN_PROJECT_FILE);
      final File eclipse = new File(base, Project.ECLIPSE_PROJECT_FILE);
      final File meghanada = new File(base, Config.MEGHANADA_CONF_FILE);

      if (gradle.exists()) {
        log.debug("find gradle project {}", gradle);
        return base;
      } else if (mvn.exists()) {
        log.debug("find mvn project {}", mvn);
        return base;
      } else if (eclipse.exists()) {
        log.debug("find eclipse project {}", eclipse);
        return base;
      } else if (meghanada.exists()) {
        log.debug("find meghanada project {}", meghanada);
        return base;
      }

      File parent = base.getParentFile();
      if (isNull(parent)) {
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

    if (isNull(projectRoot) || this.currentProject.getProjectRoot().equals(projectRoot)) {
      // not change
      return false;
    }

    if (this.projects.containsKey(projectRoot)) {
      // loaded project
      this.currentProject = this.projects.get(projectRoot);
      log.info("change project {}", this.currentProject.getName());
      String projectRootPath = this.currentProject.getProjectRootPath();
      Config.setProjectRoot(projectRootPath);
      this.getLocationSearcher().setProject(currentProject);
      this.getDeclarationSearcher().setProject(this.currentProject);
      this.getVariableCompletion().setProject(this.currentProject);
      this.getImportCompletion().setProject(this.currentProject);
      this.getCompletion().setProject(this.currentProject);
      return true;
    }

    if (currentProject instanceof GradleProject) {
      return loadProject(projectRoot, Project.GRADLE_PROJECT_FILE, base)
          .map(project -> setProject(projectRoot, project))
          .orElse(false);
    } else if (currentProject instanceof MavenProject) {
      return loadProject(projectRoot, Project.MVN_PROJECT_FILE, base)
          .map(project -> setProject(projectRoot, project))
          .orElse(false);
    } else if (currentProject instanceof EclipseProject) {
      return loadProject(projectRoot, Project.ECLIPSE_PROJECT_FILE, base)
          .map(project -> setProject(projectRoot, project))
          .orElse(false);
    }
    return loadProject(projectRoot, Config.MEGHANADA_CONF_FILE, base)
        .map(project -> setProject(projectRoot, project))
        .orElse(false);
  }

  private boolean setProject(final File projectRoot, final Project project) {
    this.currentProject = project;
    log.info("change project {}", this.currentProject.getName());
    String projectRootPath = this.currentProject.getProjectRootPath();
    Config.setProjectRoot(projectRootPath);
    this.projects.put(projectRoot, this.currentProject);
    this.getLocationSearcher().setProject(currentProject);
    this.getDeclarationSearcher().setProject(this.currentProject);
    this.getVariableCompletion().setProject(currentProject);
    this.getImportCompletion().setProject(currentProject);
    this.getCompletion().setProject(currentProject);
    return true;
  }

  private void setupSubscribes() {
    // subscribe file watch
    this.sessionEventBus.subscribeFileWatch();
    this.sessionEventBus.subscribeParse();
    this.sessionEventBus.subscribeCache();
    // this.sessionEventBus.subscribeIdle();
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
    if (isNull(this.locationSearcher)) {
      this.locationSearcher = new LocationSearcher(currentProject);
    }
    return locationSearcher;
  }

  private JavaCompletion getCompletion() {
    if (isNull(this.completion)) {
      this.completion = new JavaCompletion(currentProject);
    }
    return this.completion;
  }

  private JavaVariableCompletion getVariableCompletion() {
    if (isNull(this.variableCompletion)) {
      this.variableCompletion = new JavaVariableCompletion(currentProject);
    }
    return variableCompletion;
  }

  private JavaImportCompletion getImportCompletion() {
    if (isNull(this.importCompletion)) {
      this.importCompletion = new JavaImportCompletion(this.currentProject);
    }
    return this.importCompletion;
  }

  public synchronized Collection<? extends CandidateUnit> completionAt(
      String path, int line, int column, String prefix) {
    // java file only
    File file = normalize(path);
    if (!FileUtils.isJavaFile(file)) {
      return Collections.emptyList();
    }
    boolean b = this.changeProject(path);
    return getCompletion().completionAt(file, line, column, prefix);
  }

  public synchronized boolean changeProject(final String path) {
    final File file = new File(path);

    if (this.started) {
      try {
        if (!file.exists()) {
          return false;
        }
        final boolean changed = this.searchAndChangeProject(file);
        if (changed) {
          this.sessionEventBus.requestCreateCache();
          return true;
        }
        return false;
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
    boolean b = this.changeProject(path);
    return getVariableCompletion().localVariable(file, line);
  }

  public synchronized boolean addImport(final String path, final String fqcn)
      throws ExecutionException {
    // java file only
    final File file = normalize(path);
    if (!FileUtils.isJavaFile(file)) {
      return false;
    }
    boolean b = this.changeProject(path);
    log.debug("addImport path={} fqcn={}", path, fqcn);
    return parseJavaSource(file).map(source -> source.addImportIfAbsent(fqcn)).orElse(false);
  }

  public synchronized void optimizeImport(String sourceFile, String tmpSourceFile, String code)
      throws IOException {
    // java file only
    final File file = normalize(sourceFile);
    if (!FileUtils.isJavaFile(file)) {
      return;
    }
    boolean b = this.changeProject(sourceFile);
    CompileResult result = currentProject.compileString(sourceFile, code);
    Source source = result.getSources().get(file.getCanonicalFile());
    if (nonNull(source)) {
      List<String> optimized;
      try {
        optimized = source.optimizeImports();
      } catch (IllegalStateException ex) {
        log.warn("it can not be optimized:{}", ex.getMessage());
        return;
      }
      boolean addLine = false;
      final StringBuilder sb = new StringBuilder(1024 * 4);

      if (!source.getPackageName().isEmpty()) {
        try {
          long end = source.getPackageStartLine();
          if (end > 0) {
            end = end - 1;
            FileUtils.readRangeLines(
                file,
                0,
                end,
                s -> {
                  sb.append(s).append("\n");
                });
          }
          sb.append("package ").append(source.getPackageName()).append(";\n");
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      if (source.staticImportClass.size() > 0) {
        sb.append('\n');
        addLine = true;
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
      if (optimized.size() > 0) {
        if (!addLine) {
          sb.append('\n');
        }
        for (final String fqcn : optimized) {
          sb.append("import ").append(fqcn).append(";\n");
        }
        sb.append('\n');
      }

      try (final Stream<String> stream = Files.lines(file.toPath(), StandardCharsets.UTF_8)) {
        long startLine = source.getClassStartLine();
        stream
            .skip(startLine)
            .forEach(
                s -> {
                  if (startLine > 0 || !s.contains("package ")) {
                    sb.append(s).append('\n');
                  }
                });
        Files.write(
            Paths.get(tmpSourceFile),
            sb.toString().getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  public synchronized Map<String, List<String>> searchMissingImport(final String path)
      throws ExecutionException {
    // java file only
    final File file = normalize(path);
    if (!FileUtils.isJavaFile(file)) {
      return Collections.emptyMap();
    }

    boolean b = this.changeProject(path);
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
    boolean b = this.changeProject(path);
    final GlobalCache globalCache = GlobalCache.getInstance();
    globalCache.invalidateSource(currentProject, file);
    Optional<Source> source = this.parseJavaSource(file);
    return source.isPresent();
  }

  public synchronized CompileResult compileFile(final String path) throws IOException {
    // java file only
    final File file = normalize(path);
    boolean b = this.changeProject(path);
    return currentProject.compileFile(file, true, true);
  }

  public synchronized CompileResult compileProject(final String path, final boolean force)
      throws IOException {

    final Project project = currentProject;
    final CompileResult result = project.compileJava(force);
    if (result.hasDiagnostics()) {
      log.warn("project {} compile report:{}", project.getName(), result.getDiagnosticsSummary());
    }

    final CompileResult testResult = project.compileTestJava(force);
    if (testResult.hasDiagnostics()) {
      for (final Diagnostic<? extends JavaFileObject> diagnostic : testResult.getDiagnostics()) {
        result.getDiagnostics().add(diagnostic);
      }
      log.warn(
          "peoject {} test compile report:{}",
          project.getName(),
          testResult.getDiagnosticsSummary());
    }

    return result;
  }

  public Collection<File> getDependentJars() {
    return currentProject
        .getDependencies()
        .stream()
        .filter(pd -> !pd.getType().equals(ProjectDependency.Type.PROJECT))
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

  public InputStream runJUnit(String path, String test, boolean debug) throws IOException {
    boolean b = this.changeProject(path);
    return currentProject.runJUnit(debug, test);
  }

  public Optional<String> switchTest(final String path) throws IOException {
    boolean b = this.changeProject(path);
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
    if (isNull(root)) {
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

    boolean b = this.changeProject(path);
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

  public synchronized Optional<Location> backDeclaration() {
    return Optional.ofNullable(this.jumpDecHistory.pollLast());
  }

  public InputStream runTask(List<String> args) throws Exception {
    return currentProject.runTask(args);
  }

  public void formatCode(final String path) throws IOException {
    boolean b = this.changeProject(path);
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
      loadProject(projectRoot, Project.GRADLE_PROJECT_FILE, projectRoot)
          .ifPresent(
              project -> {
                boolean ret = setProject(projectRoot, project);
              });
    } else if (currentProject instanceof MavenProject) {
      loadProject(projectRoot, Project.MVN_PROJECT_FILE, projectRoot)
          .ifPresent(
              project -> {
                boolean ret = setProject(projectRoot, project);
              });
    } else if (currentProject instanceof EclipseProject) {
      loadProject(projectRoot, Project.ECLIPSE_PROJECT_FILE, projectRoot)
          .ifPresent(
              project -> {
                boolean ret = setProject(projectRoot, project);
              });
    } else {
      loadProject(projectRoot, Config.MEGHANADA_CONF_FILE, projectRoot)
          .ifPresent(
              project -> {
                boolean ret = setProject(projectRoot, project);
              });
    }

    final Set<File> temp = new HashSet<>(this.currentProject.getSources());
    temp.addAll(this.currentProject.getTestSources());
    this.sessionEventBus.requestWatchFiles(new ArrayList<>(temp));
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    reflector.addClasspath(Session.getSystemJars());
    this.sessionEventBus.requestCreateCache();
    this.projects
        .values()
        .forEach(project -> this.sessionEventBus.requestWatchFile(project.getProjectRoot()));
  }

  public Optional<Declaration> showDeclaration(
      final String path, final int line, final int column, final String symbol)
      throws IOException, ExecutionException {
    boolean b = this.changeProject(path);
    final DeclarationSearcher searcher = this.getDeclarationSearcher();
    return searcher.searchDeclaration(new File(path), line, column, symbol);
  }

  private DeclarationSearcher getDeclarationSearcher() {
    if (isNull(this.declarationSearcher)) {
      this.declarationSearcher = new DeclarationSearcher(currentProject);
    }
    return declarationSearcher;
  }

  public InputStream execMain(String path, boolean debug) throws Exception {
    boolean b = this.changeProject(path);
    Optional<Source> source = this.parseJavaSource(new File(path));
    return source
        .map(
            src -> {
              try {
                String clazz = src.getFQCN();
                return currentProject.execMainClass(clazz, debug);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            })
        .orElse(null);
  }

  public CompileResult diagnosticString(String sourceFile, String sourceCode) throws IOException {

    boolean b = this.changeProject(sourceFile);
    CompileResult result = currentProject.compileString(sourceFile, sourceCode);
    if (result.isSuccess()) {}

    return result;
  }

  private ReferenceSearcher getReferenceSearcher() {
    if (isNull(this.referenceSearcher)) {
      this.referenceSearcher = new ReferenceSearcher(currentProject);
    }
    return referenceSearcher;
  }

  public List<Reference> reference(
      final String path, final int line, final int column, final String symbol)
      throws IOException, ExecutionException {
    boolean b = this.changeProject(path);
    final ReferenceSearcher searcher = this.getReferenceSearcher();
    return searcher.searchReference(new File(path), line, column, symbol);
  }

  private TypeInfoSearcher getTypeInfoSearcher() {
    if (isNull(this.typeinfoSearcher)) {
      this.typeinfoSearcher = new TypeInfoSearcher(currentProject);
    }
    return typeinfoSearcher;
  }

  public Optional<TypeInfo> typeInfo(
      final String path, final int line, final int column, final String symbol)
      throws IOException, ExecutionException {
    boolean b = this.changeProject(path);
    final TypeInfoSearcher searcher = this.getTypeInfoSearcher();
    return searcher.search(new File(path), line, column, symbol);
  }

  public void killRunningProcess() {
    getCurrentProject().killRunningProcess();
  }

  public Optional<SearchResults> searchEverywhere(final String q) {
    return IndexDatabase.getInstance().search(q);
  }

  public String showProject() {
    return getCurrentProject().toString();
  }

  @Override
  public String toString() {
    return "";
  }

  public SessionEventBus getSessionEventBus() {
    return sessionEventBus;
  }

  public boolean completionResolve(
      String path, int lineInt, int columnInt, String type, String item, String desc)
      throws ExecutionException {

    File file = normalize(path);
    if (!FileUtils.isJavaFile(file)) {
      return false;
    }
    getCompletion().resolve(file, type, desc);
    log.trace("path {} {} {} {}", path, type, item, desc);
    return true;
  }

  public synchronized Map<String, List<String>> searchImports(
      String path, int line, int column, String symbol) throws IOException, ExecutionException {
    // java file only
    final File file = normalize(path);
    if (!FileUtils.isJavaFile(file)) {
      return Collections.emptyMap();
    }

    boolean b = this.changeProject(path);
    return getImportCompletion()
        .importAtPoint(file, line, column, symbol)
        .orElse(Collections.emptyMap());
  }
}
