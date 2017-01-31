package meghanada.project;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.typesafe.config.ConfigFactory;
import meghanada.analyze.CompileResult;
import meghanada.analyze.JavaAnalyzer;
import meghanada.analyze.Source;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.JavaCore;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@DefaultSerializer(ProjectSerializer.class)
public abstract class Project {

    public static final String DEFAULT_PATH = File.separator + "src" + File.separator + "main" + File.separator;
    public static final String FORMETTER_FILE_KEY = "meghanada.formatter.file";
    public static final String PROJECT_ROOT_KEY = "project.root";
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
    public static Map<String, Project> loadedProject = new HashMap<>();

    protected File projectRoot;
    protected Set<ProjectDependency> dependencies = new HashSet<>();
    protected Set<File> sources = new HashSet<>();
    protected Set<File> resources = new HashSet<>();
    protected File output;
    protected Set<File> testSources = new HashSet<>();
    protected Set<File> testResources = new HashSet<>();
    protected File testOutput;
    protected String compileSource = "1.8";
    protected String compileTarget = "1.8";
    protected String id;
    protected Set<Project> dependencyProjects = new HashSet<>();
    protected Map<String, Set<String>> callerMap = new ConcurrentHashMap<>();

    private JavaAnalyzer javaAnalyzer;
    private String cachedClasspath;
    private String cachedAllClasspath;

    private String[] prevTest;
    private Properties formatProperties;

    public Project(File projectRoot) throws IOException {
        this.projectRoot = projectRoot;
        System.setProperty(PROJECT_ROOT_KEY, this.projectRoot.getCanonicalPath());
        final File file = new File(projectRoot, FORMATTER_FILE);
        if (file.exists()) {
            System.setProperty(FORMETTER_FILE_KEY, file.getCanonicalPath());
        }
    }

    public abstract Project parseProject() throws ProjectParseException;

    public Set<File> getAllSources() {
        Set<File> temp = new HashSet<>();
        temp.addAll(this.getSourceDirectories());
        temp.addAll(this.getResourceDirectories());
        temp.addAll(this.getTestSourceDirectories());
        temp.addAll(this.getTestResourceDirectories());
        return temp;
    }

    public Set<File> getSourceDirectories() {
        return this.sources;
    }

    Set<File> getResourceDirectories() {
        return this.resources;
    }

    public File getOutputDirectory() {
        return this.output;
    }

    public Set<File> getTestSourceDirectories() {
        return this.testSources;
    }

    Set<File> getTestResourceDirectories() {
        return this.testResources;
    }

    public File getTestOutputDirectory() {
        return this.testOutput;
    }

    public Set<ProjectDependency> getDependencies() {
        return this.dependencies;
    }

    public String getCompileSource() {
        return compileSource;
    }

    public String getCompileTarget() {
        return compileTarget;
    }

    private JavaAnalyzer getJavaAnalyzer() {
        if (this.javaAnalyzer == null) {
            this.javaAnalyzer = new JavaAnalyzer(this.compileSource, this.compileTarget, getAllSources());
        }
        return this.javaAnalyzer;
    }

    public String classpath() {
        if (this.cachedClasspath != null) {
            return this.cachedClasspath;
        }
        List<String> classpath = new ArrayList<>();

        this.dependencies.stream()
                .filter(input -> input.getScope().equals("COMPILE"))
                .map(this::getCanonicalPath)
                .forEach(classpath::add);

        try {
            classpath.add(this.output.getCanonicalPath());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        this.cachedClasspath = String.join(File.pathSeparator, classpath);
        return this.cachedClasspath;
    }

    private String allClasspath() {
        if (this.cachedAllClasspath != null) {
            return this.cachedAllClasspath;
        }

        List<String> classpath = new ArrayList<>();
        this.dependencies.stream()
                .map(this::getCanonicalPath)
                .forEach(classpath::add);

        classpath.add(getCanonicalPath(this.output));
        classpath.add(getCanonicalPath(this.testOutput));
//        if (log.isDebugEnabled()) {
//            classpath.stream().forEach(s -> {
//                log.debug("Classpath:{}", s);
//            });
//        }
        this.cachedAllClasspath = String.join(File.pathSeparator, classpath);
        return this.cachedAllClasspath;
    }

    private String getCanonicalPath(ProjectDependency d) {
        try {
            return d.getFile().getCanonicalPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getCanonicalPath(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<File> collectJavaFiles(Set<File> sourceDirs) throws IOException {
        return sourceDirs.parallelStream()
                .filter(File::exists)
                .map(this::collectJavaFiles)
                .flatMap(Collection::parallelStream)
                .filter(FileUtils::filterFile)
                .collect(Collectors.toList());
    }

    private List<File> collectJavaFiles(File root) {
        if (!root.exists()) {
            return Collections.emptyList();
        }
        try {
            return Files.walk(root.toPath())
                    .map(Path::toFile)
                    .filter(FileUtils::isJavaFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    public CompileResult compileJava(boolean force) throws IOException {
        return compileJava(force, false);
    }

    public CompileResult compileJava(boolean force, final boolean fullBuild) throws IOException {
        final Set<Project> lazyLoad = new HashSet<>();

        if (fullBuild) {
            for (final Project p : this.dependencyProjects) {
                if (p.equals(this)) {
                    // skip
                    continue;
                }
                final Set<Project> dependencyProjects = p.getDependencyProjects();
                if (dependencyProjects.contains(this)) {
                    lazyLoad.add(p);
                    continue;
                }
                System.setProperty(PROJECT_ROOT_KEY, p.getProjectRoot().getCanonicalPath());
                final CompileResult compileResult = p.compileJava(force, true);
                if (!compileResult.isSuccess()) {
                    log.warn("dependency module compile error {}", p.getProjectRoot());
                    log.warn("{}", compileResult.getDiagnosticsSummary());
                }
            }
        }

        System.setProperty(PROJECT_ROOT_KEY, this.getProjectRoot().getCanonicalPath());

        List<File> files = this.collectJavaFiles(this.getSourceDirectories());
        if (files != null && !files.isEmpty()) {
            if (callerMap.size() == 0) {
                force = true;
            }
            final File callerFile = FileUtils.getSettingFile(JavaAnalyzer.CALLER);
            if (force && callerFile.exists()) {
                callerFile.delete();
            }
            files = force ? files : FileUtils.getModifiedSources(JavaAnalyzer.COMPILE_CHECKSUM,
                    files,
                    this.getAllSources(),
                    this.output);

            files = addDepends(this.getAllSources(), files);
            final CompileResult compileResult = getJavaAnalyzer().analyzeAndCompile(files, this.allClasspath(), output.getCanonicalPath());

            for (final Project p : lazyLoad) {
                if (p.equals(this)) {
                    // skip
                    continue;
                }

                final Set<Project> dependencyProjects = p.getDependencyProjects();
                System.setProperty(PROJECT_ROOT_KEY, p.getProjectRoot().getCanonicalPath());
                if (dependencyProjects.contains(this)) {
                    dependencyProjects.remove(this);
                    final CompileResult tempCR = p.compileJava(force, fullBuild);
                    if (!tempCR.isSuccess()) {
                        log.warn("dependency module compile error {}", p.getProjectRoot());
                        log.warn("{}", tempCR.getDiagnosticsSummary());
                    }
                    dependencyProjects.add(this);
                } else {
                    final CompileResult tempCR = p.compileJava(force, fullBuild);
                    if (!tempCR.isSuccess()) {
                        log.warn("dependency module compile error {}", p.getProjectRoot());
                        log.warn("{}", tempCR.getDiagnosticsSummary());
                    }
                }
            }
            System.setProperty(PROJECT_ROOT_KEY, this.getProjectRoot().getCanonicalPath());
            return this.updateSourceCache(compileResult);
        }
        return new CompileResult(true);
    }

    public CompileResult compileTestJava(boolean force) throws IOException {
        return compileTestJava(force, false);
    }

    public CompileResult compileTestJava(boolean force, final boolean fullBuild) throws IOException {
        final Set<Project> lazyLoad = new HashSet<>();

        if (fullBuild) {
            for (final Project p : dependencyProjects) {
                if (p.equals(this)) {
                    // skip
                    continue;
                }
                final Set<Project> dependencyProjects = p.getDependencyProjects();
                if (dependencyProjects.contains(this)) {
                    lazyLoad.add(p);
                    continue;
                }
                System.setProperty(PROJECT_ROOT_KEY, p.getProjectRoot().getCanonicalPath());
                final CompileResult compileResult = p.compileTestJava(force, true);
                if (!compileResult.isSuccess()) {
                    log.warn("dependency module test compile error {}", p.getProjectRoot());
                    log.warn("{}", compileResult.getDiagnosticsSummary());
                }
            }
        }

        System.setProperty(PROJECT_ROOT_KEY, this.getProjectRoot().getCanonicalPath());
        List<File> files = this.collectJavaFiles(this.getTestSourceDirectories());
        if (files != null && !files.isEmpty()) {
            if (callerMap.size() == 0) {
                force = true;
            }
            final File callerFile = FileUtils.getSettingFile(JavaAnalyzer.CALLER);
            if (force && callerFile.exists()) {
                callerFile.delete();
            }

            files = force ? files : FileUtils.getModifiedSources(JavaAnalyzer.COMPILE_CHECKSUM,
                    files,
                    this.getAllSources(),
                    this.testOutput);
            files = addDepends(this.getAllSources(), files);
            final CompileResult compileResult = getJavaAnalyzer().analyzeAndCompile(files, this.allClasspath(), testOutput.getCanonicalPath());

            for (final Project p : lazyLoad) {
                if (p.equals(this)) {
                    // skip
                    continue;
                }
                final Set<Project> dependencyProjects = p.getDependencyProjects();
                System.setProperty(PROJECT_ROOT_KEY, p.getProjectRoot().getCanonicalPath());
                if (dependencyProjects.contains(this)) {
                    dependencyProjects.remove(this);
                    final CompileResult tempCR = p.compileTestJava(force, fullBuild);
                    if (!tempCR.isSuccess()) {
                        log.warn("dependency module test compile error {}", p.getProjectRoot());
                        log.warn("{}", tempCR.getDiagnosticsSummary());
                    }
                    dependencyProjects.add(this);
                } else {
                    final CompileResult tempCR = p.compileTestJava(force, fullBuild);
                    if (!tempCR.isSuccess()) {
                        log.warn("dependency module test compile error {}", p.getProjectRoot());
                        log.warn("{}", tempCR.getDiagnosticsSummary());
                    }
                }
            }
            System.setProperty(PROJECT_ROOT_KEY, this.getProjectRoot().getCanonicalPath());
            return this.updateSourceCache(compileResult);
        }
        return new CompileResult(true);
    }

    public CompileResult parseFile(final File file) throws IOException {
        boolean isTest = false;
        String filepath = file.getCanonicalPath();
        for (File source : this.getTestSourceDirectories()) {
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
        if (FileUtils.filterFile(file)) {
            List<File> files = new ArrayList<>();
            files.add(file);
            return getJavaAnalyzer().analyzeAndCompile(files, this.allClasspath(), output);
        }
        return new CompileResult(false);
    }

    public CompileResult compileFileNoCache(final File file, final boolean force) throws IOException {
        boolean isTest = false;
        String filepath = file.getCanonicalPath();
        for (File source : this.getTestSourceDirectories()) {
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
        if (FileUtils.filterFile(file)) {
            List<File> files = new ArrayList<>();
            files.add(file);

            files = force ? files : FileUtils.getModifiedSources(JavaAnalyzer.COMPILE_CHECKSUM,
                    files,
                    this.getAllSources(),
                    new File(output));
            files = addDepends(this.getAllSources(), files);
            return getJavaAnalyzer().analyzeAndCompile(files, this.allClasspath(), output);
        }
        return new CompileResult(false);
    }

    public CompileResult compileFileNoCache(final List<File> files, final boolean force) throws IOException {
        boolean isTest = false;
        // sampling
        String filepath = files.get(0).getCanonicalPath();
        for (File source : this.getTestSourceDirectories()) {
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

        List<File> filesList = files.stream()
                .filter(FileUtils::filterFile)
                .collect(Collectors.toList());

        filesList = force ? filesList : FileUtils.getModifiedSources(JavaAnalyzer.COMPILE_CHECKSUM,
                files,
                this.getAllSources(),
                new File(output));
        filesList = addDepends(this.getAllSources(), filesList);
        return getJavaAnalyzer().analyzeAndCompile(filesList, this.allClasspath(), output);
    }

    public File getProjectRoot() {
        return projectRoot;
    }

    protected File normalize(String src) {
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
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        processBuilder.directory(this.projectRoot);
        String cmdString = String.join(" ", cmd);

        log.debug("RUN cmd: {}", cmdString);

        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        return process.getInputStream();
    }

    public abstract InputStream runTask(List<String> args) throws IOException;

    public InputStream runJUnit(String test) throws IOException {
        try {
            return runUnitTest(test);
        } finally {
            System.setProperty(PROJECT_ROOT_KEY, this.projectRoot.getCanonicalPath());
        }
    }

    private InputStream runUnitTest(String... tests) throws IOException {
        if (tests[0].isEmpty()) {
            tests = this.prevTest;
        }
        log.debug("runUnit test:{} prevTest:{}", tests, prevTest);

        final Config config = Config.load();
        final List<String> cmd = new ArrayList<>();
        final String binJava = "/bin/java".replace("/", File.separator);
        final String javaCmd = new File(config.getJavaHomeDir(), binJava).getCanonicalPath();
        cmd.add(javaCmd);
        String cp = this.allClasspath();
        final String jarPath = Config.getInstalledPath().getCanonicalPath();
        cp += File.pathSeparator + jarPath;
        cmd.add("-ea");
        cmd.add("-XX:+TieredCompilation");
        cmd.add("-XX:+UseConcMarkSweepGC");
        cmd.add("-XX:SoftRefLRUPolicyMSPerMB=50");
        cmd.add("-Xverify:none");
        cmd.add("-Xms256m");
        cmd.add("-Xmx2G");
        cmd.add("-cp");
        cmd.add(cp);
        cmd.add(String.format("-Dproject.root=%s", this.projectRoot.getCanonicalPath()));
        cmd.add("meghanada.junit.TestRunner");
        Collections.addAll(cmd, tests);

        this.prevTest = tests;
        log.debug("run cmd {}", Joiner.on(" ").join(cmd));
        return this.runProcess(cmd);
    }

    public Project mergeFromProjectConfig() {
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
                config.getStringList(DEPENDENCIES).stream()
                        .filter(path -> new File(path).exists())
                        .map(path -> {
                            final File file = new File(path);
                            return new ProjectDependency(file.getName(), "COMPILE", "1.0.0", file);
                        }).forEach(p -> this.dependencies.add(p));
            }
            // test-dependencies
            if (config.hasPath(TEST_DEPENDENCIES)) {
                config.getStringList(TEST_DEPENDENCIES).stream()
                        .filter(path -> new File(path).exists())
                        .map(path -> {
                            final File file = new File(path);
                            return new ProjectDependency(file.getName(), "TEST", "1.0.0", file);
                        }).forEach(p -> this.dependencies.add(p));
            }

            // sources
            if (config.hasPath(SOURCES)) {
                config.getStringList(SOURCES)
                        .stream()
                        .filter(path -> new File(path).exists())
                        .map(File::new)
                        .forEach(file -> this.sources.add(file));
            }
            // sources
            if (config.hasPath(RESOURCES)) {
                config.getStringList(RESOURCES)
                        .stream()
                        .filter(path -> new File(path).exists())
                        .map(File::new)
                        .forEach(file -> this.resources.add(file));
            }
            // test-sources
            if (config.hasPath(TEST_SOURCES)) {
                config.getStringList(TEST_SOURCES)
                        .stream()
                        .filter(path -> new File(path).exists())
                        .map(File::new)
                        .forEach(file -> this.testSources.add(file));
            }
            // test-resources
            if (config.hasPath(TEST_RESOURCES)) {
                config.getStringList(TEST_RESOURCES)
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
        // log.debug("Merged Project:{}", this);

        // freeze
        this.sources = new ImmutableSet.Builder<File>().addAll(this.sources).build();
        log.debug("sources {}", this.getSourceDirectories());
        this.resources = new ImmutableSet.Builder<File>().addAll(this.resources).build();
        log.debug("resources {}", this.getResourceDirectories());

        log.debug("output {}", this.getOutputDirectory());

        this.testSources = new ImmutableSet.Builder<File>().addAll(this.testSources).build();
        log.debug("test sources {}", this.getTestSourceDirectories());
        this.testResources = new ImmutableSet.Builder<File>().addAll(this.testResources).build();
        log.debug("test resources {}", this.getTestResourceDirectories());

        log.debug("test output {}", this.getTestOutputDirectory());
        log.debug("dependencyProjects output {}", this.dependencyProjects);

        return this;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
        Project.loadedProject.put(id, this);
    }

    private CompileResult updateSourceCache(final CompileResult compileResult) throws IOException {
        final Config config = Config.load();
        final GlobalCache globalCache = GlobalCache.getInstance();
        if (config.useSourceCache()) {

            final Set<File> errorFiles = compileResult.getErrorFiles();
            final Map<File, Source> sourceMap = compileResult.getSources();
            final Map<File, Map<String, String>> checksum = config.getAllChecksumMap();

            final File checksumFile = FileUtils.getSettingFile(JavaAnalyzer.COMPILE_CHECKSUM);
            final Map<String, String> checksumMap = checksum.getOrDefault(checksumFile, new ConcurrentHashMap<>());

            for (final Source source : sourceMap.values()) {

                source.getClassScopes().forEach(cs -> {
                    final String fqcn = cs.getFQCN();
                    source.usingClasses.forEach(s -> {
                        if (this.callerMap.containsKey(s)) {
                            final Set<String> set = this.callerMap.get(s);
                            set.add(fqcn);
                            this.callerMap.put(s, set);
                        } else {
                            final Set<String> set = new HashSet<>();
                            set.add(fqcn);
                            this.callerMap.put(s, set);
                        }
                    });
                });

                final File sourceFile = source.getFile();
                final String path = sourceFile.getCanonicalPath();
                if (!errorFiles.contains(sourceFile)) {
                    final String md5sum = FileUtils.md5sum(sourceFile);
                    checksumMap.put(path, md5sum);
                    try {
                        globalCache.replaceSource(this, source);
                    } catch (Exception e) {
                        throw new UncheckedExecutionException(e);
                    }
                } else {
                    // error
                    checksumMap.remove(path);
                    try {
                        globalCache.invalidateSource(this, sourceFile);
                    } catch (Exception e) {
                        throw new UncheckedExecutionException(e);
                    }
                }
            }
            FileUtils.writeMapSetting(checksumMap, checksumFile);
            checksum.put(checksumFile, checksumMap);
        }
        this.writeCaller();
        return compileResult;
    }

    private List<File> addDepends(final Set<File> sourceRoots, final List<File> files) {
        final Set<File> temp = Collections.newSetFromMap(new ConcurrentHashMap<File, Boolean>());
        temp.addAll(files);
        temp.addAll(FileUtils.getPackagePrivateSource(files));

        sourceRoots.parallelStream().forEach(root -> {
            try {
                final String rootPath = root.getCanonicalPath();
                files.parallelStream().forEach(file -> {
                    try {
                        final String path = file.getCanonicalPath();
                        if (path.startsWith(rootPath)) {
                            final String p = path.substring(rootPath.length() + 1, path.length() - 5);
                            final String importClass = ClassNameUtils.replace(p, File.separator, ".");
                            if (callerMap.containsKey(importClass)) {
                                FileUtils.getSourceFile(importClass, sourceRoots).ifPresent(temp::add);
                            }
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        return new ArrayList<>(temp);
    }

    private synchronized void writeCaller() {
        final File callerFile = FileUtils.getSettingFile(JavaAnalyzer.CALLER);
        GlobalCache.getInstance().asyncWriteCache(callerFile, callerMap);
    }

    synchronized void loadCaller() {
        final File callerFile = FileUtils.getSettingFile(JavaAnalyzer.CALLER);
        if (!callerFile.exists()) {
            return;
        }

        @SuppressWarnings("unchecked")
        final Map<String, Set<String>> map =
                GlobalCache.getInstance().readCacheFromFile(callerFile, ConcurrentHashMap.class);
        this.callerMap = map;
    }

    public Set<Project> getDependencyProjects() {
        return this.dependencyProjects;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return com.google.common.base.Objects.equal(projectRoot, project.projectRoot);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(projectRoot);
    }

    public void clearCache() throws IOException {
        for (final Project p : this.dependencyProjects) {
            p.clearCache();
        }
        System.setProperty(PROJECT_ROOT_KEY, this.projectRoot.getCanonicalPath());
        final Config config = Config.load();
        final String projectSettingDir = config.getProjectSettingDir();
        FileUtils.deleteFile(new File(projectSettingDir), false);
    }

    public Properties readFormatProperties() {
        final Properties fileProperties = readFormatPropertiesFromFile();
        if (fileProperties != null) {
            return fileProperties;
        }
        // default
        final Properties properties = new Properties();
        // Default
        properties.setProperty(DefaultCodeFormatterConstants.FORMATTER_BLANK_LINES_AFTER_IMPORTS, "1");
        properties.setProperty(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, "120");
        properties.setProperty(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, "space");
        properties.setProperty(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
        return properties;
    }

    private Properties readFormatPropertiesFromFile() {
        final String val = System.getProperty(FORMETTER_FILE_KEY);
        if (val != null) {
            final File file = new File(val);
            try {
                final Properties prop = FileUtils.loadPropertiesFile(file);
                log.info("load formatter rule from {}", val);
                return prop;
            } catch (IOException e) {
                log.catching(e);
            }
        }
        return null;
    }

    public Properties getFormatProperties() {
        if (this.formatProperties != null) {
            return this.formatProperties;
        }
        Properties properties = readFormatProperties();
        // merge
        properties.setProperty(JavaCore.COMPILER_SOURCE, this.getCompileSource());
        properties.setProperty(JavaCore.COMPILER_COMPLIANCE, this.getCompileSource());
        properties.setProperty(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, this.getCompileSource());

        this.formatProperties = properties;
        return this.formatProperties;
    }
}
