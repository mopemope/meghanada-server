package meghanada.project.gradle;

import com.android.builder.model.*;
import com.google.common.base.Joiner;
import meghanada.project.ProjectDependency;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static meghanada.utils.FunctionUtils.wrapIOConsumer;

class AndroidSupport {

    private static final Logger log = LogManager.getLogger(AndroidSupport.class);
    private static final String INTERMEDIATE_DIR = "intermediates";
    private static final String CLASSES_DIR = "classes";
    private static final String DEBUG_DIR = "debug";
    private static final String DEFAULT_SCOPE = "COMPILE";
    private static final String TEST_DIR = "test";
    private static final String DEFAULT_VERSION = "1.0.0";
    private static final String BUILD_DIR = "build";
    private static final String TEST_SOURCES_KEY = "testSources";
    private static final String SOURCES_KEY = "sources";
    private static final String TEST_RESOURCES_KEY = "testResources";
    private static final String RESOURCES_KEY = "resources";
    private static final String EXPLODED_DIR = "exploded-aar";
    private static final String EXT_JAR = ".jar";

    private final GradleProject project;

    private String genSourceTaskName = ":generateDebugSources";
    private String genUnitTestTaskName = ":prepareDebugUnitTestDependencies";
    private String genAndroidTestTaskName = ":generateDebugAndroidTestSources";

    AndroidSupport(final GradleProject project) {
        this.project = project;
    }

    static AndroidProject getAndroidProject(final File root, final org.gradle.tooling.model.GradleProject gradleProject) {
        final String path = gradleProject.getPath();
        final String name = path.substring(1);
        final File childDir = new File(root, name);
        final GradleConnector childConnector = GradleConnector.newConnector().forProjectDirectory(childDir);
        final ProjectConnection childConnection = childConnector.connect();
        try {
            return childConnection.getModel(AndroidProject.class);
        } catch (Exception e) {
            return null;
        } finally {
            childConnection.close();
        }
    }

    void parseAndroidProject(final org.gradle.tooling.model.GradleProject gradleProject, final AndroidProject androidProject) throws IOException {

        final JavaCompileOptions javaCompileOptions = androidProject.getJavaCompileOptions();
        this.project.setCompileSource(javaCompileOptions.getSourceCompatibility());
        this.project.setCompileTarget(javaCompileOptions.getTargetCompatibility());

        final ProductFlavorContainer defaultConfig = androidProject.getDefaultConfig();

        if (this.project.getOutput() == null) {
            final File buildDir = androidProject.getBuildFolder().getCanonicalFile();
            final String build = Joiner.on(File.separator).join(buildDir, INTERMEDIATE_DIR, CLASSES_DIR, DEBUG_DIR);
            this.project.setOutput(project.normalize(build));
        }
        if (this.project.getTestOutput() == null) {
            final File buildDir = androidProject.getBuildFolder().getCanonicalFile();
            final String build = Joiner.on(File.separator).join(buildDir, INTERMEDIATE_DIR, CLASSES_DIR, TEST_DIR, DEBUG_DIR);
            this.project.setTestOutput(this.project.normalize(build));
        }

        final Map<String, Set<File>> androidSources = this.getAndroidSources(defaultConfig);
        final Set<ProjectDependency> dependencies = this.getAndroidDependencies(androidProject);

        this.project.getSources().addAll(androidSources.get(SOURCES_KEY));
        this.project.getResources().addAll(androidSources.get(RESOURCES_KEY));
        this.project.getTestSources().addAll(androidSources.get(TEST_SOURCES_KEY));
        this.project.getTestResources().addAll(androidSources.get(AndroidSupport.TEST_RESOURCES_KEY));
        this.project.getDependencies().addAll(dependencies);

        // merge other project
        if (this.project.getSources().isEmpty()) {
            final File file = new File(Joiner.on(File.separator).join("src", "main", "java")).getCanonicalFile();
            this.project.getSources().add(file);
        }
        if (this.project.getTestSources().isEmpty()) {
            final File file = new File(Joiner.on(File.separator).join("src", "test", "java")).getCanonicalFile();
            this.project.getTestSources().add(file);
        }

        if (this.project.getOutput() == null) {
            final String buildDir = new File(this.project.getProjectRoot(), BUILD_DIR).getCanonicalPath();
            final String build = Joiner.on(File.separator).join(buildDir, INTERMEDIATE_DIR, CLASSES_DIR, DEBUG_DIR);
            this.project.setOutput(this.project.normalize(build));
        }
        if (this.project.getTestOutput() == null) {
            final String buildDir = new File(this.project.getProjectRoot(), BUILD_DIR).getCanonicalPath();
            final String build = Joiner.on(File.separator).join(buildDir, INTERMEDIATE_DIR, CLASSES_DIR, TEST_DIR, DEBUG_DIR);
            this.project.setTestOutput(this.project.normalize(build));
        }

        // load exists aar
        final String aar = Joiner.on(File.separator)
                .join(this.project.getProjectRoot(),
                        BUILD_DIR,
                        INTERMEDIATE_DIR,
                        EXPLODED_DIR);
        FileUtils.collectFiles(new File(aar), EXT_JAR)
                .forEach(wrapIOConsumer(this::addAAR));

        log.debug("sources {}", this.project.getSources());
        log.debug("resources {}", this.project.getResources());
        log.debug("output {}", this.project.getOutput());
        log.debug("test sources {}", this.project.getTestSources());
        log.debug("test resources {}", this.project.getTestResources());
        log.debug("test output {}", this.project.getTestOutput());

        for (final ProjectDependency projectDependency : this.project.getDependencies()) {
            log.debug("dependency {}", projectDependency);
        }
    }

    private void addAAR(final File jar) {
        final String pkg = jar.getParentFile().getParentFile().getParentFile().getParentFile().getName();
        final String name = jar.getParentFile().getParentFile().getParentFile().getName();
        final String id = pkg + '.' + name;
        final String version = jar.getParentFile().getParentFile().getName();
        final ProjectDependency projectDependency = new ProjectDependency(id, DEFAULT_SCOPE, version, jar);
        this.project.getDependencies().add(projectDependency);
    }

    private Set<ProjectDependency> getAndroidDependencies(final AndroidProject androidProject) {
        final Set<ProjectDependency> dependencies = new HashSet<>(16);
        final Collection<String> bootClasspath = androidProject.getBootClasspath();
        for (final String cp : bootClasspath) {
            final File file = new File(cp);
            final String id = file.getName();
            final String version = DEFAULT_VERSION;
            final String scope = DEFAULT_SCOPE;
            final ProjectDependency projectDependency = new ProjectDependency(id, scope, version, file);
            dependencies.add(projectDependency);
        }

        final Collection<Variant> variants = androidProject.getVariants();
        for (final Variant variant : variants) {

            final AndroidArtifact mainArtifact = variant.getMainArtifact();
            for (final File src : mainArtifact.getGeneratedSourceFolders()) {
                this.project.getSources().add(src);
            }
            for (final File src : mainArtifact.getGeneratedResourceFolders()) {
                this.project.getResources().add(src);
            }

            final Collection<AndroidArtifact> extraAndroidArtifacts = variant.getExtraAndroidArtifacts();
            for (final AndroidArtifact androidArtifact : extraAndroidArtifacts) {
                final Collection<File> generatedSourceFolders = androidArtifact.getGeneratedSourceFolders();
                for (final File src : generatedSourceFolders) {
                    this.project.getSources().add(src);
                }
                final Collection<File> generatedResourceFolders = androidArtifact.getGeneratedResourceFolders();
                for (final File src : generatedResourceFolders) {
                    this.project.getResources().add(src);
                }

                final Dependencies compileDependencies = androidArtifact.getCompileDependencies();
                // getLibraries
                final Collection<AndroidLibrary> libraries = compileDependencies.getLibraries();
                for (final AndroidLibrary androidLibrary : libraries) {
                    final String name = androidLibrary.getName();
                    final Collection<File> localJars = androidLibrary.getLocalJars();
                    if (localJars.isEmpty()) {
                        final File jar = androidLibrary.getJarFile();
                        final String id = jar.getName();
                        final String version = DEFAULT_VERSION;
                        final String scope = DEFAULT_SCOPE;
                        final ProjectDependency projectDependency = new ProjectDependency(id, scope, version, jar);
                        dependencies.add(projectDependency);
                    } else {
                        for (final File jar : localJars) {
                            final String id = jar.getName();
                            final String version = DEFAULT_VERSION;
                            final String scope = DEFAULT_SCOPE;
                            final ProjectDependency projectDependency = new ProjectDependency(id, scope, version, jar);
                            dependencies.add(projectDependency);
                        }
                    }
                }
                // getJavaLibraries
                final Collection<JavaLibrary> javaLibraries = compileDependencies.getJavaLibraries();
                for (final JavaLibrary javaLibrary : javaLibraries) {
                    final File file = javaLibrary.getJarFile();
                    final String id = file.getName();
                    final String version = DEFAULT_VERSION;
                    final String scope = DEFAULT_SCOPE;
                    final ProjectDependency projectDependency = new ProjectDependency(id, scope, version, file);
                    dependencies.add(projectDependency);
                }

            }

            final Collection<JavaArtifact> extraJavaArtifacts = variant.getExtraJavaArtifacts();
            for (final JavaArtifact javaArtifact : extraJavaArtifacts) {
                final Collection<File> generatedSourceFolders = javaArtifact.getGeneratedSourceFolders();
                for (final File src : generatedSourceFolders) {
                    this.project.getSources().add(src);
                }
                final Dependencies packageDependencies = javaArtifact.getPackageDependencies();
                final Collection<JavaLibrary> javaLibraries1 = packageDependencies.getJavaLibraries();
                final Collection<AndroidLibrary> libraries1 = packageDependencies.getLibraries();

                final Dependencies compileDependencies = javaArtifact.getCompileDependencies();
                final Collection<AndroidLibrary> libraries = compileDependencies.getLibraries();
                for (final AndroidLibrary androidLibrary : libraries) {
                    final Collection<File> localJars = androidLibrary.getLocalJars();
                    for (final File jar : localJars) {
                        final String id = jar.getName();
                        final String version = DEFAULT_VERSION;
                        final String scope = DEFAULT_SCOPE;
                        final ProjectDependency projectDependency = new ProjectDependency(id, scope, version, jar);
                        dependencies.add(projectDependency);
                    }
                }
                final Collection<JavaLibrary> javaLibraries = compileDependencies.getJavaLibraries();
                for (final JavaLibrary javaLibrary : javaLibraries) {
                    final File file = javaLibrary.getJarFile();
                    final String id = file.getName();
                    final String version = DEFAULT_VERSION;
                    final String scope = DEFAULT_SCOPE;
                    final ProjectDependency projectDependency = new ProjectDependency(id, scope, version, file);
                    dependencies.add(projectDependency);
                }
            }
        }
        return dependencies;
    }

    private void setAndroidSources(final String name, final Map<String, Set<File>> sources,
                                   final SourceProvider sourceProvider, boolean isTest) {
        // java
        final Collection<File> javaDirectories = sourceProvider.getJavaDirectories();
        for (final File f : javaDirectories) {
            if (isTest) {
                final Set<File> source = sources.getOrDefault(TEST_SOURCES_KEY, new HashSet<>());
                source.add(f);
                sources.put(TEST_SOURCES_KEY, source);
            } else {
                final Set<File> source = sources.getOrDefault(SOURCES_KEY, new HashSet<>());
                source.add(f);
                sources.put(SOURCES_KEY, source);
            }
        }

        // aidl
        final Collection<File> aidlDirectories = sourceProvider.getAidlDirectories();
        for (final File f : aidlDirectories) {
            if (isTest) {
                final Set<File> source = sources.getOrDefault(TEST_SOURCES_KEY, new HashSet<>());
                source.add(f);
                sources.put(TEST_SOURCES_KEY, source);
            } else {
                final Set<File> source = sources.getOrDefault(SOURCES_KEY, new HashSet<>());
                source.add(f);
                sources.put(SOURCES_KEY, source);
            }
        }

        // renderscript
        final Collection<File> renderscriptDirectories = sourceProvider.getRenderscriptDirectories();
        for (final File f : renderscriptDirectories) {
            if (isTest) {
                final Set<File> source = sources.getOrDefault(TEST_SOURCES_KEY, new HashSet<>());
                source.add(f);
                sources.put(TEST_SOURCES_KEY, source);
            } else {
                final Set<File> source = sources.getOrDefault(SOURCES_KEY, new HashSet<>());
                source.add(f);
                sources.put(SOURCES_KEY, source);
            }
        }

        // resource
        final Collection<File> resourcesDirectories = sourceProvider.getResourcesDirectories();
        for (final File f : resourcesDirectories) {
            if (isTest) {
                final Set<File> source = sources.getOrDefault(TEST_RESOURCES_KEY, new HashSet<>());
                source.add(f);
                sources.put(AndroidSupport.TEST_RESOURCES_KEY, source);
            } else {
                final Set<File> source = sources.getOrDefault(RESOURCES_KEY, new HashSet<>());
                source.add(f);
                sources.put(RESOURCES_KEY, source);
            }
        }

        // res
        final Collection<File> resDirectories = sourceProvider.getResDirectories();
        for (final File f : resDirectories) {
            if (isTest) {
                final Set<File> source = sources.getOrDefault(AndroidSupport.TEST_RESOURCES_KEY, new HashSet<>());
                source.add(f);
                sources.put(AndroidSupport.TEST_RESOURCES_KEY, source);
            } else {
                final Set<File> source = sources.getOrDefault(RESOURCES_KEY, new HashSet<>());
                source.add(f);
                sources.put(RESOURCES_KEY, source);
            }
        }

        // asset
        final Collection<File> assetsDirectories = sourceProvider.getAssetsDirectories();
        for (final File f : assetsDirectories) {
            if (isTest) {
                final Set<File> source = sources.getOrDefault(AndroidSupport.TEST_RESOURCES_KEY, new HashSet<>());
                source.add(f);
                sources.put(AndroidSupport.TEST_RESOURCES_KEY, source);
            } else {
                final Set<File> source = sources.getOrDefault(RESOURCES_KEY, new HashSet<>());
                source.add(f);
                sources.put(RESOURCES_KEY, source);
            }
        }

        // asset
        final Collection<File> jniLibsDirectories = sourceProvider.getJniLibsDirectories();
        for (final File f : jniLibsDirectories) {
            if (isTest) {
                final Set<File> source = sources.getOrDefault(AndroidSupport.TEST_RESOURCES_KEY, new HashSet<>());
                source.add(f);
                sources.put(AndroidSupport.TEST_RESOURCES_KEY, source);
            } else {
                final Set<File> source = sources.getOrDefault(RESOURCES_KEY, new HashSet<>());
                source.add(f);
                sources.put(RESOURCES_KEY, source);
            }
        }
    }

    private Map<String, Set<File>> getAndroidSources(final ProductFlavorContainer defaultConfig) {

        final Map<String, Set<File>> sources = new HashMap<>();
        final ProductFlavor productFlavor = defaultConfig.getProductFlavor();
        final String name = productFlavor.getName();

        final SourceProvider sourceProvider = defaultConfig.getSourceProvider();
        this.setAndroidSources(name, sources, sourceProvider, false);

        // extra
        final Collection<SourceProviderContainer> extraSourceProviders = defaultConfig.getExtraSourceProviders();
        extraSourceProviders.forEach(sourceProviderContainer -> {
            final String artifactName = sourceProviderContainer.getArtifactName();
            final SourceProvider provider = sourceProviderContainer.getSourceProvider();
            final boolean isTest = artifactName.contains("_test_");
            this.setAndroidSources(artifactName, sources, provider, isTest);
        });

        return sources;
    }

    void prepareCompileAndroidJava() throws IOException {
        final ProjectConnection connection = this.project.getProjectConnection();
        try {
            final BuildLauncher buildLauncher = connection.newBuild();
            final String genTask = this.project.getName() + this.genSourceTaskName;
            buildLauncher.forTasks(genTask).run();

            final int size = this.project.getDependencies().size();

            final String aar = Joiner.on(File.separator)
                    .join(this.project.getProjectRoot(),
                            BUILD_DIR,
                            INTERMEDIATE_DIR,
                            EXPLODED_DIR);
            final List<File> jars = FileUtils.collectFiles(new File(aar), EXT_JAR);
            for (final File jar : jars) {
                addAAR(jar);
            }

            final int after = this.project.getDependencies().size();
            if (size != after) {
                CachedASMReflector.getInstance().createClassIndexes(jars);
                this.project.resetCachedClasspath();
            }
        } finally {
            connection.close();
        }
    }

    void prepareCompileAndroidTestJava() throws IOException {
        final ProjectConnection connection = this.project.getProjectConnection();
        try {
            final BuildLauncher buildLauncher = connection.newBuild();
            final String genTestTask = this.project.getName() + genUnitTestTaskName;
            final String genAndroidTestTask = this.project.getName() + genAndroidTestTaskName;

            buildLauncher.forTasks(genTestTask, genAndroidTestTask).run();

            final int size = this.project.getDependencies().size();

            final String aar = Joiner.on(File.separator)
                    .join(this.project.getProjectRoot(),
                            BUILD_DIR,
                            INTERMEDIATE_DIR,
                            EXPLODED_DIR);
            final List<File> jars = FileUtils.collectFiles(new File(aar), EXT_JAR);
            for (final File jar : jars) {
                addAAR(jar);
            }

            final int after = this.project.getDependencies().size();
            if (size != after) {
                CachedASMReflector.getInstance().createClassIndexes(jars);
                this.project.resetCachedClasspath();
            }
        } finally {
            connection.close();
        }
    }

}
