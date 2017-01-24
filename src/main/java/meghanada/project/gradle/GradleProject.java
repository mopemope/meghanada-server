package meghanada.project.gradle;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.google.common.base.Joiner;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.ProjectParseException;
import meghanada.project.ProjectSerializer;
import meghanada.session.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gradle.tooling.*;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.idea.*;

import java.io.*;
import java.util.*;

@DefaultSerializer(ProjectSerializer.class)
public class GradleProject extends Project {

    private static final Logger log = LogManager.getLogger(GradleProject.class);
    private final File rootProject;
    private final Map<String, File> projects = new HashMap<>();
    private List<String> lazyLoadModule = new ArrayList<>();

    public GradleProject(File projectRoot) throws IOException {
        super(projectRoot);
        this.rootProject = this.setRoot(projectRoot);
    }

    private File setRoot(File dir) throws IOException {

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

            final File gradle = new File(dir, Session.GRADLE_PROJECT_FILE);
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

    @Override
    public Project parseProject() throws ProjectParseException {
        final ProjectConnection projectConnection = getProjectConnection();
        try {
            final ModelBuilder<IdeaProject> ideaProjectModelBuilder = projectConnection.model(IdeaProject.class);
            final IdeaProject ideaProject = ideaProjectModelBuilder.get();

            final IdeaJavaLanguageSettings javaLanguageSettings = ideaProject.getJavaLanguageSettings();
            try {
                final String srcLevel = javaLanguageSettings.getLanguageLevel().toString();
                final String targetLevel = javaLanguageSettings.getTargetBytecodeVersion().toString();
                super.compileSource = srcLevel;
                super.compileTarget = targetLevel;
            } catch (UnsupportedMethodException e) {
                // through
                log.warn(e.getMessage());
            }
            log.trace("load project main module name:{} projectRoot:{}", ideaProject.getName(), this.projectRoot);

            for (final IdeaModule ideaModule : ideaProject.getModules().getAll()) {

                org.gradle.tooling.model.GradleProject gradleProject = ideaModule.getGradleProject();
                final String projectName = gradleProject.getName();
                final File moduleProjectRoot = gradleProject.getProjectDirectory();
                log.trace("find project module name:{} projectRoot:{}", projectName, moduleProjectRoot);

                if (moduleProjectRoot.equals(this.getProjectRoot())) {
                    log.debug("find target project name:{} projectRoot:{}", projectName, projectRoot);

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
                    final Set<ProjectDependency> dependency = this.getDependency(ideaModule);
                    final Map<String, Set<File>> sources = this.searchProjectSources(ideaModule);
                    log.debug("{} sources {}", projectName, sources);

                    this.sources.addAll(sources.get("sources"));
                    this.resources.addAll(sources.get("resources"));
                    this.testSources.addAll(sources.get("testSources"));
                    this.testResources.addAll(sources.get("testResources"));
                    this.dependencies.addAll(dependency);

                    // merge other project
                    if (this.sources.isEmpty()) {
                        this.sources.add(new File("src/main/java"));
                    }
                    if (this.testSources.isEmpty()) {
                        this.testSources.add(new File("src/test/java"));
                    }

                    if (this.output == null) {
                        final String buildDir = new File(this.projectRoot, "build").getCanonicalPath();
                        String build = Joiner.on(File.separator).join(buildDir, "classes", "main");
                        this.output = this.normalize(build);
                    }
                    if (this.testOutput == null) {
                        final String buildDir = new File(this.projectRoot, "build").getCanonicalPath();
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
                        log.debug("dependency {}", projectDependency);
                    }

                    if (this.sources.isEmpty()) {
                        log.warn("target source is empty. please change working directory to sub project");
                    }

                } else {
                    log.trace("load sub module. name:{} projectRoot:{}", projectName, moduleProjectRoot);
                    this.projects.putIfAbsent(projectName, moduleProjectRoot);
                }
            }

            for (final String name : this.lazyLoadModule) {
                if (projects.containsKey(name)) {
                    this.loadModule(name);
                } else {
                    final String[] split = name.split("-");
                    if (split.length > 0) {
                        final String nm = split[split.length - 1];
                        if (projects.containsKey(nm)) {
                            this.loadModule(nm);
                        } else {
                            log.warn("fail load module={}", name);
                        }
                    }
                }
            }
            return this;
        } catch (IOException e) {
            throw new ProjectParseException(e);
        } finally {
            projectConnection.close();
        }
    }

    private void loadModule(final String name) throws IOException {
        final File root = projects.get(name);
        final Optional<Project> p = Session.findProject(root);
        p.ifPresent(project -> {

            final File outputFile = project.getOutputDirectory();
            final ProjectDependency output = new ProjectDependency(name, "COMPILE", "", outputFile);
            dependencies.add(output);

            final File testOutputFile = project.getTestOutputDirectory();
            final ProjectDependency testOutput = new ProjectDependency(name + "Test", "COMPILE", "", testOutputFile);
            dependencies.add(testOutput);

            this.dependencyProjects.add(project);
            log.debug("load module dependency name={}", name);
        });
    }

    private ProjectConnection getProjectConnection() {
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
            List<String> tasks = new ArrayList<>();
            List<String> taskArgs = new ArrayList<>();
            for (String temp : args) {
                for (String arg : temp.split(" ")) {
                    if (arg.startsWith("-")) {
                        taskArgs.add(arg.trim());
                    } else {
                        tasks.add(arg.trim());
                    }
                }
            }

            log.debug("task:{}:{} args:{}:{}", tasks, tasks.size(), taskArgs, taskArgs.size());

            final ProjectConnection projectConnection = getProjectConnection();
            BuildLauncher build = projectConnection.newBuild();
            build.forTasks(tasks.toArray(new String[tasks.size()]));
            if (taskArgs.size() > 0) {
                build.withArguments(taskArgs.toArray(new String[taskArgs.size()]));
            }


            final PipedOutputStream outputStream = new PipedOutputStream();
            PipedInputStream inputStream = new PipedInputStream(outputStream);
            build.setStandardError(outputStream);
            build.setStandardOutput(outputStream);

            build.run(new ResultHandler<Void>() {
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
                        outputStream.close();
                        inputStream.close();
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    } finally {
                        projectConnection.close();
                    }
                }
            });
            return inputStream;
        } finally {
            System.setProperty("project.root", this.projectRoot.getCanonicalPath());
        }
    }


    private Map<String, Set<File>> searchProjectSources(final IdeaModule ideaModule) throws IOException {
        final Map<String, Set<File>> result = new HashMap<>();
        result.put("sources", new HashSet<>());
        result.put("resources", new HashSet<>());
        result.put("testSources", new HashSet<>());
        result.put("testResources", new HashSet<>());

        for (final IdeaContentRoot ideaContentRoot : ideaModule.getContentRoots().getAll()) {
            // log.debug("{}", ideaContentRoot.getExcludeDirectories());
            // log.debug("IdeaContentRoot {}", ideaContentRoot.getSourceDirectories());
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
                // log.debug("IdeaSourceDirectory {}", ideaContentRoot.getSourceDirectories());
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

    private Set<ProjectDependency> getDependency(final IdeaModule ideaModule) throws IOException {
        final Set<ProjectDependency> dependencies = new HashSet<>();

        for (final IdeaDependency dependency : ideaModule.getDependencies().getAll()) {
            if (dependency instanceof IdeaSingleEntryLibraryDependency) {
                final IdeaSingleEntryLibraryDependency libraryDependency = (IdeaSingleEntryLibraryDependency) dependency;

                final String scope = libraryDependency.getScope().getScope();
                final File file = libraryDependency.getFile();
                final GradleModuleVersion gradleModuleVersion = libraryDependency.getGradleModuleVersion();
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

                final ProjectDependency projectDependency = new ProjectDependency(id, scope, version, file);
                dependencies.add(projectDependency);
            } else if (dependency instanceof IdeaModuleDependency) {
                final IdeaModuleDependency moduleDependency = (IdeaModuleDependency) dependency;
                final String targetModuleName = moduleDependency.getTargetModuleName();
                log.debug("find module dependency name={}", targetModuleName);

                if (projects.containsKey(targetModuleName)) {
                    this.loadModule(targetModuleName);
                } else {
                    this.lazyLoadModule.add(targetModuleName);
                }
            } else {
                log.warn("dep ??? class={}", dependency.getClass());
            }
        }

        return dependencies;
    }

}
