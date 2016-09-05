package meghanada.project.gradle;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.google.common.base.Joiner;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.ProjectParseException;
import meghanada.project.ProjectSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gradle.tooling.*;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.idea.*;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@DefaultSerializer(ProjectSerializer.class)
public class GradleProject extends Project {

    private static final Logger log = LogManager.getLogger(GradleProject.class);

    public GradleProject(File projectRoot) throws IOException {
        super(projectRoot);
    }

    public Project parseProject() throws ProjectParseException {
        final ProjectConnection projectConnection = getProjectConnection();
        try {
            final ModelBuilder<IdeaProject> builder = projectConnection.model(IdeaProject.class);
            final IdeaProject ideaProject = builder.get();

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
            File buildDir = null;
            for (IdeaModule ideaModule : ideaProject.getModules().getAll()) {
                org.gradle.tooling.model.GradleProject gradleProject = ideaModule.getGradleProject();

                if (buildDir == null) {
                    buildDir = new File(this.projectRoot, "build");
                }

                File out = ideaModule.getCompilerOutput().getOutputDir();
                if (out != null) {
                    if (out.getCanonicalPath().startsWith(this.projectRoot.getCanonicalPath())) {
                        super.output = normalizeFile(out);
                    }
                }
                File testOut = ideaModule.getCompilerOutput().getTestOutputDir();
                if (testOut != null) {
                    if (testOut.getCanonicalPath().startsWith(this.projectRoot.getCanonicalPath())) {
                        this.testOutput = normalizeFile(testOut);
                    }
                }

                dependencies.addAll(getDependency(ideaModule));
                searchSources(ideaModule);
            }
            // add default
            if (this.output == null) {
                if (buildDir != null) {
                    String build = Joiner.on(File.separator).join(buildDir, "classes", "main");
                    output = normalize(build);
                } else {
                    String build = Joiner.on(File.separator).join("build", "classes", "main");
                    output = normalize(build);
                }
            }
            if (this.testOutput == null) {
                if (buildDir != null) {
                    String build = Joiner.on(File.separator).join(buildDir, "classes", "test");
                    testOutput = normalize(build);
                } else {
                    String build = Joiner.on(File.separator).join("build", "classes", "test");
                    testOutput = normalize(build);
                }
            }

            log.debug("buildDir:{}", buildDir);
            log.debug("sources {}", this.sources);
            log.debug("resources {}", this.resources);
            log.debug("output {}", this.output);
            log.debug("test sources {}", this.testSources);
            log.debug("test resources {}", this.testResources);
            log.debug("test output {}", this.testOutput);

            for (ProjectDependency projectDependency : this.getDependencies()) {
                log.debug("dependency {}", projectDependency);
            }

            super.mergeFromProjectConfig();
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
                    .forProjectDirectory(this.projectRoot);
        } else {
            log.debug("use gradle version:'{}'", gradleVersion);
            connector = GradleConnector.newConnector()
                    .useGradleVersion(gradleVersion)
                    .forProjectDirectory(this.projectRoot);
        }

        if (connector instanceof DefaultGradleConnector) {
            ((DefaultGradleConnector) connector).embedded(true);
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

    private void searchSources(IdeaModule ideaModule) throws IOException {

        for (IdeaContentRoot ideaContentRoot : ideaModule.getContentRoots().getAll()) {
            // log.debug("{}", ideaContentRoot.getExcludeDirectories());
            // log.debug("{}", ideaContentRoot.getSourceDirectories());
            for (IdeaSourceDirectory sourceDirectory : ideaContentRoot.getSourceDirectories().getAll()) {
                File file = normalizeFile(sourceDirectory.getDirectory());
                String path = file.getCanonicalPath();
                if (path.startsWith(this.projectRoot.getCanonicalPath())) {
                    if (path.contains("resources")) {
                        this.resources.add(file);
                    } else {
                        this.sources.add(file);
                    }
                }
            }
            for (IdeaSourceDirectory sourceDirectory : ideaContentRoot.getTestDirectories().getAll()) {
                File file = normalizeFile(sourceDirectory.getDirectory());
                String path = file.getCanonicalPath();
                if (path.startsWith(this.projectRoot.getCanonicalPath())) {
                    if (path.contains("resources")) {
                        this.testResources.add(file);
                    } else {
                        this.testSources.add(file);
                    }
                }
            }
        }
    }

    private Set<ProjectDependency> getDependency(IdeaModule ideaModule) {
        Set<ProjectDependency> dependencyList = new HashSet<>();

        for (IdeaDependency dependency : ideaModule.getDependencies().getAll()) {
            if (dependency instanceof IdeaSingleEntryLibraryDependency) {
                IdeaSingleEntryLibraryDependency libraryDependency = (IdeaSingleEntryLibraryDependency) dependency;

                String scope = libraryDependency.getScope().getScope();
                File file = libraryDependency.getFile();
                GradleModuleVersion gradleModuleVersion = libraryDependency.getGradleModuleVersion();
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
                dependencyList.add(projectDependency);
            } else {
//                log.warn("dependency broken?:{}", dependency);
//                IdeaModuleDependency ideaModuleDependency = (IdeaModuleDependency) dependency;
//                IdeaModule tmpIdeaModule = ideaModuleDependency.getDependencyModule();
//                Set<ProjectDependency> tmp = getDependency(tmpIdeaModule);
//                log.warn("add dependencies:{}", tmp);
//                dependencyList.addAll(tmp);
            }
        }
        return dependencyList;
    }

}
