package meghanada.project.gradle;

import com.android.builder.model.AndroidProject;
import com.esotericsoftware.kryo.DefaultSerializer;
import com.google.common.base.Joiner;
import com.typesafe.config.ConfigFactory;
import meghanada.analyze.CompileResult;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.ProjectParseException;
import meghanada.project.ProjectSerializer;
import meghanada.utils.ClassNameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gradle.tooling.*;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.idea.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static meghanada.config.Config.debugTimeItF;
import static meghanada.utils.FunctionUtils.wrapIOConsumer;

@DefaultSerializer(ProjectSerializer.class)
public class GradleProject extends Project {

    private static final Logger log = LogManager.getLogger(GradleProject.class);
    private final File rootProject;
    private final Map<String, File> allModules = new ConcurrentHashMap<>(4);
    private final List<String> prepareCompileTask = new ArrayList<>(2);
    private final List<String> prepareTestCompileTask = new ArrayList<>(2);

    public GradleProject(final File projectRoot) throws IOException {
        super(projectRoot);
        this.rootProject = GradleProject.searchRootProject(projectRoot);
    }

    private static File searchRootProject(File dir) throws IOException {

        File result = dir;
        dir = dir.getParentFile();
        if (dir == null) {
            System.setProperty("user.dir", result.getCanonicalPath());
            return result;
        }
        while (true) {

            if (dir.getPath().equals("/")) {
                break;
            }

            final File gradle = new File(dir, Project.GRADLE_PROJECT_FILE);
            if (!gradle.exists()) {
                break;
            }
            result = dir;
            dir = dir.getParentFile();
            if (dir == null) {
                break;
            }
        }
        System.setProperty("user.dir", result.getCanonicalPath());
        return result;
    }

    private static String convertName(final String path) {
        final String replaced = ClassNameUtils.replace(path, ":", "-");
        if (replaced.startsWith("-")) {
            return replaced.substring(1);
        }
        return replaced;
    }

    @Override
    public Project parseProject() throws ProjectParseException {
        final ProjectConnection connection = getProjectConnection();
        log.info("loading gradle project:{}", new File(this.projectRoot, Project.GRADLE_PROJECT_FILE));
        try {
            final IdeaProject ideaProject = debugTimeItF("get idea project model elapsed={}", () -> connection.getModel(IdeaProject.class));
            this.setCompileTarget(ideaProject);

            log.trace("load root project path:{}", this.rootProject);
            final DomainObjectSet<? extends IdeaModule> modules = ideaProject.getModules();

            final List<? extends IdeaModule> mainModules = modules.parallelStream().filter(ideaModule -> {
                final org.gradle.tooling.model.GradleProject gradleProject = ideaModule.getGradleProject();
                final File moduleProjectRoot = gradleProject.getProjectDirectory();
                final String name = ideaModule.getName();
                log.trace("find sub-module name {}:{} path:{} ", name, moduleProjectRoot);
                this.allModules.putIfAbsent(name, moduleProjectRoot);
                return moduleProjectRoot.equals(this.getProjectRoot());
            }).collect(Collectors.toList());
            mainModules.forEach(wrapIOConsumer(this::parseIdeaModule));

            // set default output
            if (super.output == null) {
                final String build = Joiner.on(File.separator).join(this.projectRoot, "build", "classes", "main");
                super.output = this.normalize(build);
            }
            if (super.testOutput == null) {
                final String build = Joiner.on(File.separator).join(this.projectRoot, "build", "classes", "test");
                super.testOutput = this.normalize(build);
            }

            return this;
        } catch (Exception e) {
            throw new ProjectParseException(e);
        } finally {
            connection.close();
        }
    }

    private void parseIdeaModule(final IdeaModule ideaModule) throws IOException {
        final org.gradle.tooling.model.GradleProject gradleProject = ideaModule.getGradleProject();
        this.name = convertName(gradleProject.getPath());
        final AndroidProject androidProject = AndroidSupport.getAndroidProject(this.rootProject, gradleProject);
        if (androidProject != null) {
            // parse android
            this.isAndroidProject = true;
            final AndroidSupport androidSupport = new AndroidSupport(this);
            androidSupport.parseAndroidProject(androidProject);
        } else {
            // normal
            this.parseIdeaModule(gradleProject, ideaModule);
        }
    }

    private void setCompileTarget(final IdeaProject ideaProject) {
        final IdeaJavaLanguageSettings javaLanguageSettings = ideaProject.getJavaLanguageSettings();
        try {
            final String srcLevel = javaLanguageSettings.getLanguageLevel().toString();
            final String targetLevel = javaLanguageSettings.getTargetBytecodeVersion().toString();
            super.compileSource = srcLevel;
            super.compileTarget = targetLevel;
        } catch (UnsupportedMethodException e) {
            log.warn(e.getMessage());
        }
    }

    private void parseIdeaModule(final org.gradle.tooling.model.GradleProject gradleProject,
            final IdeaModule ideaModule) throws IOException {
        if (this.output == null) {
            final String buildDir = gradleProject.getBuildDirectory().getCanonicalPath();
            String build = Joiner.on(File.separator).join(buildDir, "classes", "main");
            this.output = this.normalize(build);
        }
        if (this.testOutput == null) {
            final String buildDir = gradleProject.getBuildDirectory().getCanonicalPath();
            String build = Joiner.on(File.separator).join(buildDir, "classes", "test");
            this.testOutput = this.normalize(build);
        }
        final Set<ProjectDependency> dependencies = this.analyzeDependencies(ideaModule);
        final Map<String, Set<File>> sources = this.searchProjectSources(ideaModule);

        this.sources.addAll(sources.get("sources"));
        this.resources.addAll(sources.get("resources"));
        this.testSources.addAll(sources.get("testSources"));
        this.testResources.addAll(sources.get("testResources"));
        this.dependencies.addAll(dependencies);

        // merge other project
        if (this.sources.isEmpty()) {
            final File file = new File(Joiner.on(File.separator).join("src", "main", "java")).getCanonicalFile();
            this.sources.add(file);
        }
        if (this.testSources.isEmpty()) {
            final File file = new File(Joiner.on(File.separator).join("src", "test", "java")).getCanonicalFile();
            this.testSources.add(file);
        }

        if (this.output == null) {
            final String buildDir = new File(this.getProjectRoot(), "build").getCanonicalPath();
            String build = Joiner.on(File.separator).join(buildDir, "classes", "main");
            this.output = this.normalize(build);
        }
        if (this.testOutput == null) {
            final String buildDir = new File(this.getProjectRoot(), "build").getCanonicalPath();
            String build = Joiner.on(File.separator).join(buildDir, "classes", "test");
            this.testOutput = this.normalize(build);
        }

        log.debug("sources {}", this.sources);
        log.debug("resources {}", this.resources);
        log.debug("output {}", this.output);
        log.debug("test sources {}", this.testSources);
        log.debug("test resources {}", this.testResources);
        log.debug("test output {}", this.testOutput);

        for (final ProjectDependency projectDependency : this.getDependencies()) {
            log.debug("{}", projectDependency.string());
        }

    }

    ProjectConnection getProjectConnection() {
        final String gradleVersion = Config.load().getGradleVersion();
        GradleConnector connector;
        if (gradleVersion.isEmpty()) {
            connector = GradleConnector.newConnector()
                    .forProjectDirectory(this.rootProject);
        } else {
            log.debug("use gradle version:'{}'", gradleVersion);
            connector = GradleConnector.newConnector()
                    .useGradleVersion(gradleVersion)
                    .forProjectDirectory(this.rootProject);
        }
        return connector.connect();
    }

    @Override
    public InputStream runTask(final List<String> args) throws IOException {
        try {
            final List<String> tasks = new ArrayList<>(4);
            final List<String> taskArgs = new ArrayList<>(4);
            for (final String temp : args) {
                for (final String arg : temp.split(" ")) {
                    if (arg.startsWith("-")) {
                        taskArgs.add(arg.trim());
                    } else {
                        tasks.add(arg.trim());
                    }
                }
            }

            log.debug("task:{}:{} args:{}:{}", tasks, tasks.size(), taskArgs, taskArgs.size());

            final ProjectConnection projectConnection = getProjectConnection();
            final BuildLauncher build = projectConnection.newBuild();
            build.forTasks(tasks.toArray(new String[tasks.size()]));
            if (taskArgs.size() > 0) {
                build.withArguments(taskArgs.toArray(new String[taskArgs.size()]));
            }

            final PipedOutputStream outputStream = new PipedOutputStream();
            PipedInputStream inputStream = new PipedInputStream(outputStream);
            build.setStandardError(outputStream);
            build.setStandardOutput(outputStream);

            final VoidResultHandler handler = new VoidResultHandler(outputStream, inputStream, projectConnection);
            build.run(handler);
            return inputStream;
        } finally {
            System.setProperty(PROJECT_ROOT_KEY, this.projectRoot.getCanonicalPath());
        }
    }

    private Map<String, Set<File>> searchProjectSources(final IdeaModule ideaModule) throws IOException {
        final Map<String, Set<File>> result = new HashMap<>(8);
        result.put("sources", new HashSet<>(2));
        result.put("resources", new HashSet<>(2));
        result.put("testSources", new HashSet<>(2));
        result.put("testResources", new HashSet<>(2));

        for (final IdeaContentRoot ideaContentRoot : ideaModule.getContentRoots().getAll()) {
            for (final IdeaSourceDirectory sourceDirectory : ideaContentRoot.getSourceDirectories().getAll()) {
                final File file = normalizeFile(sourceDirectory.getDirectory());
                final String path = file.getCanonicalPath();
                if (path.contains("resources")) {
                    result.get("resources").add(file);
                } else {
                    result.get("sources").add(file);
                }
            }
            for (final IdeaSourceDirectory sourceDirectory : ideaContentRoot.getTestDirectories().getAll()) {
                final File file = normalizeFile(sourceDirectory.getDirectory());
                final String path = file.getCanonicalPath();
                if (path.contains("resources")) {
                    result.get("testResources").add(file);
                } else {
                    result.get("testSources").add(file);
                }
            }
        }
        return result;
    }

    private Set<ProjectDependency> analyzeDependencies(final IdeaModule ideaModule) {
        final Set<ProjectDependency> dependencies = new HashSet<>(16);

        for (final IdeaDependency dependency : ideaModule.getDependencies().getAll()) {
            if (dependency instanceof IdeaSingleEntryLibraryDependency) {
                final IdeaSingleEntryLibraryDependency libraryDependency = (IdeaSingleEntryLibraryDependency) dependency;

                final File file = libraryDependency.getFile();
                final GradleModuleVersion gradleModuleVersion = libraryDependency.getGradleModuleVersion();
                String scope = libraryDependency.getScope().getScope();
                String id;
                String version;
                if (gradleModuleVersion == null) {
                    id = file.getName();
                    // dummy
                    version = "1.0.0";
                } else {
                    id = String.join(":",
                            gradleModuleVersion.getGroup(),
                            gradleModuleVersion.getName(),
                            gradleModuleVersion.getVersion());
                    version = gradleModuleVersion.getVersion();
                }
                if (scope == null) {
                    scope = "COMPILE";
                }
                final ProjectDependency.Type type = ProjectDependency.getFileType(file);
                final ProjectDependency projectDependency = new ProjectDependency(id, scope, version, file, type);
                dependencies.add(projectDependency);
            } else if (dependency instanceof IdeaModuleDependency) {
                final IdeaModuleDependency moduleDependency = (IdeaModuleDependency) dependency;
                final String scope = moduleDependency.getScope().getScope();
                final String moduleName = moduleDependency.getTargetModuleName();
                this.allModules.computeIfPresent(moduleName, (key, projectRoot) -> {
                    final ProjectDependency projectDependency = new ProjectDependency(key,
                            scope,
                            "1.0.0",
                            projectRoot,
                            ProjectDependency.Type.PROJECT);
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
            return super.compileJava(force);
        } else {
            return super.compileJava(force);
        }
    }

    private void runPrepareCompileTask() {
        if (!this.prepareCompileTask.isEmpty()) {
            final ProjectConnection connection = this.getProjectConnection();
            try {
                final String[] tasks = prepareCompileTask.toArray(new String[prepareCompileTask.size()]);
                final BuildLauncher buildLauncher = connection.newBuild();
                log.info("project {} run tasks:{}", this.name, (Object) tasks);
                buildLauncher.forTasks(tasks).run();
            } finally {
                connection.close();
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

    private void runPrepareTestCompileTask() {
        if (!this.prepareTestCompileTask.isEmpty()) {
            final ProjectConnection connection = this.getProjectConnection();
            try {
                final String[] tasks = prepareTestCompileTask.toArray(new String[prepareTestCompileTask.size()]);
                final BuildLauncher buildLauncher = connection.newBuild();
                log.info("project {} run tasks:{}", this.name, (Object) tasks);
                buildLauncher.forTasks(tasks).run();
            } finally {
                connection.close();
            }
        }
    }

    @Override
    public Project mergeFromProjectConfig() {
        final Config config1 = Config.load();
        this.prepareCompileTask.addAll(config1.gradlePrepareCompileTask());
        this.prepareTestCompileTask.addAll(config1.gradlePrepareTestCompileTask());

        final File configFile = new File(this.projectRoot, Config.MEGHANADA_CONF_FILE);
        if (configFile.exists()) {
            final com.typesafe.config.Config config = ConfigFactory.parseFile(configFile);
            if (config.hasPath(Config.GRADLE_PREPARE_COMPILE_TASK)) {
                final String taskConfig = config.getString(Config.GRADLE_PREPARE_COMPILE_TASK);
                final String[] tasks = StringUtils.split(taskConfig, ",");
                if (tasks != null) {
                    Collections.addAll(this.prepareCompileTask, tasks);
                }
            }
            if (config.hasPath(Config.GRADLE_PREPARE_TEST_COMPILE_TASK)) {
                final String taskConfig = config.getString(Config.GRADLE_PREPARE_TEST_COMPILE_TASK);
                final String[] tasks = StringUtils.split(taskConfig, ",");
                if (tasks != null) {
                    Collections.addAll(this.prepareTestCompileTask, tasks);
                }
            }
        }
        return super.mergeFromProjectConfig();
    }

    private static class VoidResultHandler implements ResultHandler<Void> {
        private final PipedOutputStream outputStream;
        private final PipedInputStream inputStream;
        private final ProjectConnection projectConnection;

        VoidResultHandler(final PipedOutputStream outputStream, final PipedInputStream inputStream, final ProjectConnection projectConnection) {
            this.outputStream = outputStream;
            this.inputStream = inputStream;
            this.projectConnection = projectConnection;
        }

        @Override
        public void onComplete(final Void result) {
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
        public void onFailure(final GradleConnectionException failure) {
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
}
