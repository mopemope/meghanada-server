package meghanada.session;

import com.google.common.base.Joiner;
import meghanada.analyze.ClassScope;
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
import meghanada.utils.ClassNameUtils;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        assert result != null;
        return result.map(Session::new)
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
                return null;
            }
            base = base.getParentFile();
        }
    }

    private static Optional<Project> loadProject(final File projectRoot, final String targetFile) throws IOException {
        final EntryMessage entryMessage = log.traceEntry("projectRoot={} targetFile={}", projectRoot, targetFile);
        final String projectRootPath = projectRoot.getCanonicalPath();
        System.setProperty(Project.PROJECT_ROOT_KEY, projectRootPath);

        try {
            final Config config = Config.load();
            final String projectSettingDir = config.getProjectSettingDir();

            final File settingFile = new File(projectSettingDir);
            if (!settingFile.exists()) {
                settingFile.mkdirs();
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

            final File projectCache = FileUtils.getProjectDataFile(GlobalCache.PROJECT_DATA);
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
                    projectCache.delete();
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

            final Project parsed = project.parseProject();
            if (config.useFastBoot()) {
                Session.writeProjectCache(projectCache, parsed);
            }
            log.info("load project projectRoot:{}", project.getProjectRoot());
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
        final List<File> files = Files.walk(jvmDir.toPath())
                .map(Path::toFile)
                .filter(f -> f.getName().endsWith(".jar") && !f.getName().endsWith("policy.jar"))
                .collect(Collectors.toList());
        files.add(toolsJar.getCanonicalFile());
        return files;
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
            this.getVariableCompletion().setProject(currentProject);
            return false;
        }

        if (currentProject instanceof GradleProject) {
            return loadProject(projectRoot, Project.GRADLE_PROJECT_FILE).map(project -> {
                return setProject(projectRoot, project);
            }).orElse(false);
        } else if (currentProject instanceof MavenProject) {
            return loadProject(projectRoot, Project.MVN_PROJECT_FILE).map(project -> {
                return setProject(projectRoot, project);
            }).orElse(false);
        }
        return loadProject(projectRoot, Config.MEGHANADA_CONF_FILE).map(project -> {
            return setProject(projectRoot, project);
        }).orElse(false);
    }

    private Boolean setProject(File projectRoot, Project project) {
        this.currentProject = project;
        this.projects.put(projectRoot, this.currentProject);
        this.getLocationSearcher().setProject(currentProject);
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

    public Session start() throws IOException {
        if (this.started) {
            return this;
        }

        this.setupSubscribes();
        log.debug("session start");

        final Set<File> temp = new HashSet<>(currentProject.getSourceDirectories());
        temp.addAll(currentProject.getTestSourceDirectories());
        this.sessionEventBus.requestWatchFiles(new ArrayList<>(temp));

        // load once
        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        reflector.addClasspath(Session.getSystemJars());
        this.sessionEventBus.requestCreateCache();

        this.projects.values().forEach(project -> {
            this.sessionEventBus.requestWatchFile(project.getProjectRoot());
        });

        log.debug("session started");
        this.started = true;
        return this;
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

    public synchronized Collection<? extends CandidateUnit> completionAt(String path, int line, int column, String prefix) {
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
                    globalCache.getSource(currentProject, file);
                }
                return true;
            } catch (Exception e) {
                log.catching(e);
                return false;
            }
        }

        return false;
    }

    public synchronized Optional<LocalVariable> localVariable(final String path, final int line) throws ExecutionException, IOException {
        // java file only
        final File file = normalize(path);
        if (!FileUtils.isJavaFile(file)) {
            return Optional.of(new LocalVariable("", Collections.emptyList()));
        }
        return getVariableCompletion().localVariable(file, line);
    }

    public synchronized boolean addImport(final String path, final String fqcn) throws ExecutionException {
        // java file only
        final File file = normalize(path);
        if (!FileUtils.isJavaFile(file)) {
            return false;
        }

        final Source source = parseJavaSource(file);
        for (final ClassScope classScope : source.getClassScopes()) {
            if (fqcn.equals(classScope.getFQCN())) {
                return false;
            }
        }

        source.importClass.put(ClassNameUtils.getSimpleName(fqcn), fqcn);
        return true;
    }

    public synchronized List<String> optimizeImport(String path) throws ExecutionException {
        // java file only
        File file = normalize(path);
        if (!FileUtils.isJavaFile(file)) {
            return Collections.emptyList();
        }

        final Source source = parseJavaSource(file);
        return source.optimizeImports();
    }

    public synchronized Map<String, List<String>> searchMissingImport(String path) throws ExecutionException {
        // java file only
        final File file = normalize(path);
        if (!FileUtils.isJavaFile(file)) {
            return Collections.emptyMap();
        }

        final Source source = parseJavaSource(file);
        return source.searchMissingImport();
    }

    public synchronized String getImplementTemplate(String fqcn) {
        return "";
    }

    private Source parseJavaSource(final File file) throws ExecutionException {
        final GlobalCache globalCache = GlobalCache.getInstance();
        return globalCache.getSource(currentProject, file);
    }

    public synchronized boolean parseFile(final String path) throws ExecutionException {
        // java file only
        final File file = normalize(path);
        if (!FileUtils.isJavaFile(file)) {
            return false;
        }
        final GlobalCache globalCache = GlobalCache.getInstance();
        globalCache.invalidateSource(currentProject, file);
        this.parseJavaSource(file);
        this.sessionEventBus.requestCreateCache(true);
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
        CompileResult result = project.compileJava(false);
        if (result.isSuccess()) {
            result = project.compileTestJava(false);
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

    public File getOutputDirectory() {
        return currentProject
                .getOutputDirectory();
    }

    public File getTestOutputDirectory() {
        return currentProject
                .getTestOutputDirectory();
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

    public String switchTest(final String path) throws IOException {
        Project project = currentProject;
        String root = null;
        Set<File> roots;
        boolean isTest;

        if (path.endsWith("Test.java")) {
            // test -> src
            roots = project.getTestSourceDirectories();
            isTest = true;
        } else {
            // src -> test
            roots = project.getSourceDirectories();
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
            return null;
        }

        String switchPath = path.substring(root.length());

        if (isTest) {
            switchPath = SWITCH_TEST_RE.matcher(switchPath).replaceAll(Matcher.quoteReplacement(".java"));
            // to src
            for (File srcRoot : project.getSourceDirectories()) {
                final File srcFile = new File(srcRoot, switchPath);
                if (srcFile.exists()) {
                    return srcFile.getCanonicalPath();
                }
            }

        } else {
            switchPath = SWITCH_JAVA_RE.matcher(switchPath).replaceAll(Matcher.quoteReplacement("Test.java"));
            // to test
            for (File srcRoot : project.getTestSourceDirectories()) {
                final File testFile = new File(srcRoot, switchPath);
                if (testFile.exists()) {
                    return testFile.getCanonicalPath();
                }
            }
        }

        return null;
    }

    String createJunitFile(String path) throws IOException, ExecutionException {
        Project project = currentProject;
        String root = null;
        Set<File> roots = project.getSourceDirectories();

        for (File file : roots) {
            String rootPath = file.getCanonicalPath();
            if (path.startsWith(rootPath)) {
                root = rootPath;
                break;
            }
        }
        if (root == null) {
            return null;
        }

        String switchPath = path.substring(root.length());
        switchPath = SWITCH_JAVA_RE.matcher(switchPath).replaceAll(Matcher.quoteReplacement("Test.java"));
        int srcSize = project.getTestSourceDirectories().size();
        // to test
        for (File srcRoot : project.getTestSourceDirectories()) {
            String srcRootPath = srcRoot.getCanonicalPath();
            if (srcSize > 1 && srcRootPath.contains(Project.DEFAULT_PATH)) {
                // skip default root
                continue;
            }
            File testFile = new File(srcRoot, switchPath);
            if (testFile.exists()) {
                return testFile.getCanonicalPath();
            } else {
                // create Junit
                this.createTestFile(path, testFile);
                return testFile.getCanonicalPath();
            }
        }
        return null;
    }

    private void createTestFile(String path, File testFile) throws IOException, ExecutionException {
        Source javaSource = this.parseJavaSource(new File(path));
        String pkg = javaSource.packageName;
        String testName = SWITCH_JAVA_RE.matcher(testFile.getName()).replaceAll(Matcher.quoteReplacement(""));

        String sb = "package " + pkg + ";\n"
                + "\n"
                + "import org.junit.After;\n"
                + "import org.junit.Before;\n"
                + "import org.junit.Test;\n"
                + "\n"
                + "import static org.junit.Assert.assertEquals;\n"
                + "\n"
                + "public class " + testName + " {\n"
                + "\n"
                + "    @Before\n"
                + "    public void setUp() throws Exception {\n"
                + "\n"
                + "    }\n"
                + "\n"
                + "    @After\n"
                + "    public void tearDown() throws Exception {\n"
                + "\n"
                + "    }\n"
                + "\n"
                + "    @Test\n"
                + "    public void test() throws Exception {\n"
                + "        assertEquals(1, 1);\n"
                + "    }\n"
                + "}\n";
        com.google.common.io.Files.write(sb, testFile, Charset.forName("UTF-8"));
    }

    public synchronized Location jumpDeclaration(final String path, final int line, final int column, final String symbol) throws ExecutionException, IOException {
        Location location = this.getLocationSearcher().searchDeclarationLocation(new File(path), line, column, symbol);
        if (location != null) {
            Location backLocation = new Location(path, line, column);
            this.jumpDecHistory.addLast(backLocation);
        } else {
            log.warn("missing location path={} line={} column={} symbol={}", path, line, column, symbol);
            location = new Location(path, line, column);
        }
        return location;
    }

    public synchronized Location backDeclaration() {
        return this.jumpDecHistory.pollLast();
    }

    public InputStream runTask(List<String> args) throws Exception {
        return currentProject.runTask(args);
    }

    public String formatCode(final String path) throws IOException {
        final Project project = currentProject;
        FileUtils.formatJavaFile(project.getFormatProperties(), path);
        return path;
    }

    public void reloadProject() throws IOException {
        final Project currentProject = this.currentProject;
        final File projectRoot = currentProject.getProjectRoot();
        this.projects.clear();

        if (currentProject instanceof GradleProject) {
            loadProject(projectRoot, Project.GRADLE_PROJECT_FILE).ifPresent(project -> {
                this.currentProject = project;
                this.projects.put(projectRoot, this.currentProject);
                this.getLocationSearcher().setProject(this.currentProject);
                this.getVariableCompletion().setProject(this.currentProject);
                this.getCompletion().setProject(this.currentProject);
            });
        } else if (currentProject instanceof MavenProject) {
            loadProject(projectRoot, Project.MVN_PROJECT_FILE).ifPresent(project -> {
                this.currentProject = project;
                this.projects.put(projectRoot, this.currentProject);
                this.getLocationSearcher().setProject(this.currentProject);
                this.getVariableCompletion().setProject(this.currentProject);
                this.getCompletion().setProject(this.currentProject);
            });
        } else {
            loadProject(projectRoot, Config.MEGHANADA_CONF_FILE).ifPresent(project -> {
                this.currentProject = project;
                this.projects.put(projectRoot, this.currentProject);
                this.getLocationSearcher().setProject(this.currentProject);
                this.getVariableCompletion().setProject(this.currentProject);
                this.getCompletion().setProject(this.currentProject);
            });
        }
        final Set<File> temp = new HashSet<>(this.currentProject.getSourceDirectories());
        temp.addAll(this.currentProject.getTestSourceDirectories());
        this.sessionEventBus.requestWatchFiles(new ArrayList<>(temp));
        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        reflector.resetClassFileMap();
        reflector.addClasspath(Session.getSystemJars());
        this.sessionEventBus.requestCreateCache();
        this.projects.values().forEach(project -> {
            this.sessionEventBus.requestWatchFile(project.getProjectRoot());
        });
    }

    public Optional<Declaration> showDeclaration(final String path,
                                                 final int line,
                                                 final int column,
                                                 final String symbol) throws IOException, ExecutionException {
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
