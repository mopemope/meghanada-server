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

    public Project parseProject() throws ProjectParseException {
        final ProjectConnection projectConnection = getProjectConnection();
        try {
            final ModelBuilder<IdeaProject> ideaProjectModelBuilder = projectConnection.model(IdeaProject.class);
            final IdeaProject ideaProject = ideaProjectModelBuilder.get();
            String targetProjectName = ideaProject.getName();

            final IdeaJavaLanguageSettings javaLanguageSettings = ideaProject.getJavaLanguageSettings();
            try {
                final String srcLevel = javaLanguageSettings.getLanguageLevel().toString();
                final String targetLevel = javaLanguageSettings.getTargetBytecodeVersion().toString();
                super.compileSource = srcLevel;
                super.compileTarget = targetLevel;
            } catch (UnsupportedMethodException e) {
                // through
                log.error(e);
            }

            // collect deps
            final Map<String, Set<ProjectDependency>> dependMap = new HashMap<>(4);
            final Map<String, Map<String, Set<File>>> sourceMap = new HashMap<>(4);
            final Map<String, Set<String>> depProjectMap = new HashMap<>(4);

            for (final IdeaModule ideaModule : ideaProject.getModules().getAll()) {

                org.gradle.tooling.model.GradleProject gradleProject = ideaModule.getGradleProject();
                final String projectName = gradleProject.getName();
                final File moduleProjectRoot = gradleProject.getProjectDirectory();
                final Set<String> depProjects = new HashSet<>(2);

                log.debug("module project name:{} projectRoot:{}", projectName, moduleProjectRoot);
                if (moduleProjectRoot.equals(this.getProjectRoot())) {
                    targetProjectName = projectName;
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
                }
                final Set<ProjectDependency> dependency = this.getDependency(ideaModule, depProjects);
                // log.debug("{} dependency {}", projectName, dependency);
                dependMap.putIfAbsent(projectName, dependency);

                depProjectMap.putIfAbsent(projectName, depProjects);

                final Map<String, Set<File>> sources = this.searchProjectSources(ideaModule);
                log.debug("{} sources {}", projectName, sources);
                sourceMap.putIfAbsent(projectName, sources);
            }

            // build

            // targetProject
            this.sources.addAll(sourceMap.get(targetProjectName).get("sources"));
            this.resources.addAll(sourceMap.get(targetProjectName).get("resources"));
            this.testSources.addAll(sourceMap.get(targetProjectName).get("testSources"));
            this.testResources.addAll(sourceMap.get(targetProjectName).get("testResources"));
            this.dependencies.addAll(dependMap.get(targetProjectName));

            // merge other project
            final Set<String> deps = depProjectMap.get(targetProjectName);
            for (final String projectName : deps) {
                log.debug("{} depend {}", targetProjectName, projectName);
                log.debug("{}", sourceMap.get(projectName));

                this.sources.addAll(sourceMap.get(projectName).get("sources"));
                this.resources.addAll(sourceMap.get(projectName).get("resources"));
                this.testSources.addAll(sourceMap.get(projectName).get("testSources"));
                this.testResources.addAll(sourceMap.get(projectName).get("testResources"));
                this.dependencies.addAll(dependMap.get(projectName));
            }

            if (this.sources.isEmpty()) {
                this.sources.add(new File("src/main/java"));
            }
            if (this.testSources.isEmpty()) {
                this.testSources.add(new File("src/test/java"));
            }

            log.debug("sources {}", this.sources);
            log.debug("resources {}", this.resources);
            log.debug("output {}", this.output);
            log.debug("test sources {}", this.testSources);
            log.debug("test resources {}", this.testResources);
            log.debug("test output {}", this.testOutput);

            for (ProjectDependency projectDependency : this.getDependencies()) {
                log.debug("dependency {}", projectDependency);
            }

            if (this.sources.isEmpty()) {
                log.warn("target source is empty. please change working directory to sub project");
            }

            return this;
        } catch (IOException e) {
            throw new ProjectParseException(e);
        } finally {
            projectConnection.close();
        }
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
    public InputStream runTask(List<String> args) throws IOException {
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

    private Set<ProjectDependency> getDependency(final IdeaModule ideaModule, final Set<String> depProjects) {
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
            } else {
                final String name = ((IdeaModuleDependency) dependency).getTargetModuleName();
                log.debug("depend project name:{}", name);
                depProjects.add(name);
            }
        }
        return dependencies;
    }

}
