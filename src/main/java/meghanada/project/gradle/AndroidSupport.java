package meghanada.project.gradle;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static meghanada.utils.FunctionUtils.wrapIOConsumer;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.Variant;
import com.google.common.base.Joiner;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meghanada.project.ProjectDependency;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

class AndroidSupport {

  private static final Logger log = LogManager.getLogger(AndroidSupport.class);
  private static final String INTERMEDIATE_DIR = "intermediates";
  private static final String CLASSES_DIR = "classes";
  private static final String DEBUG_DIR = "debug";
  private static final String DEFAULT_SCOPE = "COMPILE";
  private static final String SRC_DIR = "src";
  private static final String MAIN_DIR = "main";
  private static final String TEST_DIR = "test";
  private static final String JAVA_DIR = "java";
  private static final String DEBUG_BUILD = "debug";
  private static final String DEFAULT_VERSION = "1.0.0";
  private static final String BUILD_DIR = "build";
  private static final String TEST_SOURCES_KEY = "testSources";
  private static final String SOURCES_KEY = "sources";
  private static final String TEST_RESOURCES_KEY = "testResources";
  private static final String RESOURCES_KEY = "resources";
  private static final String EXPLODED_DIR = "exploded-aar";
  private static final String EXT_JAR = ".jar";
  private static final String TEST_SUFFIX = "_test_";

  private final GradleProject project;

  private String genSourceTaskName = ":generateDebugSources";
  private String genUnitTestTaskName = ":prepareDebugUnitTestDependencies";
  private String genAndroidTestTaskName = ":generateDebugAndroidTestSources";

  AndroidSupport(final GradleProject project) {
    this.project = project;
  }

  static AndroidProject getAndroidProject(
      final File root, final org.gradle.tooling.model.GradleProject gradleProject) {
    String path = gradleProject.getPath();
    String name = path.substring(1);
    File childDir = new File(root, name);
    GradleConnector childConnector = GradleConnector.newConnector().forProjectDirectory(childDir);
    ProjectConnection childConnection = childConnector.connect();
    try {
      return childConnection.getModel(AndroidProject.class);
    } catch (Exception e) {
      return null;
    } finally {
      childConnection.close();
    }
  }

  private static void setAndroidSources(
      Map<String, Set<File>> sources, SourceProvider sourceProvider, boolean isTest) {
    // java
    Collection<File> javaDirectories = sourceProvider.getJavaDirectories();
    for (File f : javaDirectories) {
      addSource(sources, isTest, f);
    }

    // aidl
    Collection<File> aidlDirectories = sourceProvider.getAidlDirectories();
    for (File f : aidlDirectories) {
      addSource(sources, isTest, f);
    }

    // renderscript
    Collection<File> renderscriptDirectories = sourceProvider.getRenderscriptDirectories();
    for (File f : renderscriptDirectories) {
      addSource(sources, isTest, f);
    }

    // resource
    Collection<File> resourcesDirectories = sourceProvider.getResourcesDirectories();
    for (File f : resourcesDirectories) {
      addSource(sources, isTest, f);
    }

    // res
    Collection<File> resDirectories = sourceProvider.getResDirectories();
    for (File f : resDirectories) {
      addSource(sources, isTest, f);
    }

    // asset
    Collection<File> assetsDirectories = sourceProvider.getAssetsDirectories();
    for (File f : assetsDirectories) {
      addSource(sources, isTest, f);
    }

    // jni
    Collection<File> jniLibsDirectories = sourceProvider.getJniLibsDirectories();
    for (File f : jniLibsDirectories) {
      addSource(sources, isTest, f);
    }
  }

  private static void addSource(Map<String, Set<File>> sources, boolean isTest, File f) {
    if (isTest) {
      Set<File> source = sources.getOrDefault(TEST_SOURCES_KEY, new HashSet<>(2));
      source.add(f);
      sources.put(TEST_SOURCES_KEY, source);
    } else {
      Set<File> source = sources.getOrDefault(SOURCES_KEY, new HashSet<>(2));
      source.add(f);
      sources.put(SOURCES_KEY, source);
    }
  }

  void parseAndroidProject(AndroidProject androidProject) throws IOException {

    JavaCompileOptions javaCompileOptions = androidProject.getJavaCompileOptions();
    this.project.setCompileSource(javaCompileOptions.getSourceCompatibility());
    this.project.setCompileTarget(javaCompileOptions.getTargetCompatibility());

    ProductFlavorContainer defaultConfig = androidProject.getDefaultConfig();

    if (isNull(this.project.getOutput())) {
      File buildDir = androidProject.getBuildFolder().getCanonicalFile();
      String build =
          Joiner.on(File.separator).join(buildDir, INTERMEDIATE_DIR, CLASSES_DIR, DEBUG_DIR);
      this.project.setOutput(project.normalize(build));
    }

    if (isNull(this.project.getTestOutput())) {
      File buildDir = androidProject.getBuildFolder().getCanonicalFile();
      String build =
          Joiner.on(File.separator)
              .join(buildDir, INTERMEDIATE_DIR, CLASSES_DIR, TEST_DIR, DEBUG_DIR);
      this.project.setTestOutput(this.project.normalize(build));
    }

    Map<String, Set<File>> androidSources = this.getAndroidSources(defaultConfig);
    Set<ProjectDependency> dependencies = this.getAndroidDependencies(androidProject);

    this.project.getSources().addAll(androidSources.getOrDefault(SOURCES_KEY, new HashSet<>()));
    this.project.getResources().addAll(androidSources.getOrDefault(RESOURCES_KEY, new HashSet<>()));
    this.project
        .getTestSources()
        .addAll(androidSources.getOrDefault(TEST_SOURCES_KEY, new HashSet<>()));
    this.project
        .getTestResources()
        .addAll(androidSources.getOrDefault(AndroidSupport.TEST_RESOURCES_KEY, new HashSet<>()));
    this.project.getDependencies().addAll(dependencies);

    // merge other project
    if (this.project.getSources().isEmpty()) {
      File file =
          new File(Joiner.on(File.separator).join(SRC_DIR, MAIN_DIR, JAVA_DIR)).getCanonicalFile();
      this.project.getSources().add(file);
    }
    if (this.project.getTestSources().isEmpty()) {
      File file =
          new File(Joiner.on(File.separator).join(SRC_DIR, TEST_DIR, JAVA_DIR)).getCanonicalFile();
      this.project.getTestSources().add(file);
    }

    if (isNull(this.project.getOutput())) {
      String buildDir = new File(this.project.getProjectRoot(), BUILD_DIR).getCanonicalPath();
      String build =
          Joiner.on(File.separator).join(buildDir, INTERMEDIATE_DIR, CLASSES_DIR, DEBUG_DIR);
      this.project.setOutput(this.project.normalize(build));
    }
    if (isNull(this.project.getTestOutput())) {
      String buildDir = new File(this.project.getProjectRoot(), BUILD_DIR).getCanonicalPath();
      String build =
          Joiner.on(File.separator)
              .join(buildDir, INTERMEDIATE_DIR, CLASSES_DIR, TEST_DIR, DEBUG_DIR);
      this.project.setTestOutput(this.project.normalize(build));
    }

    // load exists aar
    String aar =
        Joiner.on(File.separator)
            .join(this.project.getProjectRoot(), BUILD_DIR, INTERMEDIATE_DIR, EXPLODED_DIR);
    FileUtils.collectFiles(new File(aar), EXT_JAR).forEach(wrapIOConsumer(this::addAAR));

    log.debug("sources {}", this.project.getSources());
    log.debug("resources {}", this.project.getResources());
    log.debug("output {}", this.project.getOutput());
    log.debug("test sources {}", this.project.getTestSources());
    log.debug("test resources {}", this.project.getTestResources());
    log.debug("test output {}", this.project.getTestOutput());

    for (ProjectDependency projectDependency : this.project.getDependencies()) {
      log.debug("dependency {}", projectDependency);
    }
  }

  private void addAAR(File jar) {
    String pkg = jar.getParentFile().getParentFile().getParentFile().getParentFile().getName();
    String name = jar.getParentFile().getParentFile().getParentFile().getName();
    String id = pkg + '.' + name;
    String version = jar.getParentFile().getParentFile().getName();
    ProjectDependency projectDependency =
        new ProjectDependency(id, DEFAULT_SCOPE, version, jar, ProjectDependency.Type.JAR);
    this.project.getDependencies().add(projectDependency);
  }

  private Set<ProjectDependency> getAndroidDependencies(AndroidProject androidProject) {
    Set<ProjectDependency> dependencies = new HashSet<>(16);

    Collection<String> bootClasspath = androidProject.getBootClasspath();
    for (String cp : bootClasspath) {
      File file = new File(cp);
      addDependencies(dependencies, file);
    }

    Collection<Variant> variants = androidProject.getVariants();
    for (Variant variant : variants) {

      String buildType = variant.getBuildType();
      boolean debugBuild = buildType.equals(DEBUG_BUILD);
      AndroidArtifact mainArtifact = variant.getMainArtifact();

      if (debugBuild) {
        Collection<File> generatedSourceFolders = mainArtifact.getGeneratedSourceFolders();
        for (File src : generatedSourceFolders) {
          this.project.getSources().add(src);
        }

        Collection<File> generatedResourceFolders = mainArtifact.getGeneratedResourceFolders();
        for (File src : generatedResourceFolders) {
          this.project.getResources().add(src);
        }
      }

      Dependencies compileDependencies = mainArtifact.getCompileDependencies();
      Collection<AndroidLibrary> libraries = compileDependencies.getLibraries();
      for (AndroidLibrary androidLibrary : libraries) {
        String project = androidLibrary.getProject();
        if (nonNull(project)) {
          if (project.startsWith(":")) {
            project = project.substring(1);
          }
          if (this.project.allModules.containsKey(project)) {
            File root = this.project.allModules.get(project);
            final ProjectDependency projectDependency =
                new ProjectDependency(
                    project, "COMPILE", "1.0.0", root, ProjectDependency.Type.PROJECT);
            dependencies.add(projectDependency);
          }
        }
        Collection<File> localJars = androidLibrary.getLocalJars();
        for (File jar : localJars) {
          addDependencies(dependencies, jar);
        }
      }
      Collection<JavaLibrary> javaLibraries = compileDependencies.getJavaLibraries();
      for (JavaLibrary javaLibrary : javaLibraries) {
        File file = javaLibrary.getJarFile();
        addDependencies(dependencies, file);
      }

      parseExtraAndroidArtifacts(dependencies, variant);
      parseExtraJavaArtifacts(dependencies, variant);
    }
    return dependencies;
  }

  private void parseExtraJavaArtifacts(Set<ProjectDependency> dependencies, Variant variant) {
    String buildType = variant.getBuildType();
    boolean debugBuild = buildType.equals(DEBUG_BUILD);
    Collection<JavaArtifact> extraJavaArtifacts = variant.getExtraJavaArtifacts();
    for (JavaArtifact javaArtifact : extraJavaArtifacts) {
      if (debugBuild) {
        Collection<File> generatedSourceFolders = javaArtifact.getGeneratedSourceFolders();
        for (File src : generatedSourceFolders) {
          this.project.getSources().add(src);
        }
      }

      Dependencies compileDependencies = javaArtifact.getCompileDependencies();
      Collection<AndroidLibrary> libraries = compileDependencies.getLibraries();
      for (AndroidLibrary androidLibrary : libraries) {
        Collection<File> localJars = androidLibrary.getLocalJars();
        for (File jar : localJars) {
          addDependencies(dependencies, jar);
        }
      }
      Collection<JavaLibrary> javaLibraries = compileDependencies.getJavaLibraries();
      for (JavaLibrary javaLibrary : javaLibraries) {
        File file = javaLibrary.getJarFile();
        addDependencies(dependencies, file);
      }
    }
  }

  private void parseExtraAndroidArtifacts(Set<ProjectDependency> dependencies, Variant variant) {

    String buildType = variant.getBuildType();
    boolean debugBuild = buildType.equals(DEBUG_BUILD);

    Collection<AndroidArtifact> extraAndroidArtifacts = variant.getExtraAndroidArtifacts();
    for (AndroidArtifact androidArtifact : extraAndroidArtifacts) {
      String name = androidArtifact.getName();
      boolean isTest = name.contains(TEST_SUFFIX);

      if (debugBuild) {
        Collection<File> generatedSourceFolders = androidArtifact.getGeneratedSourceFolders();
        for (File src : generatedSourceFolders) {
          if (isTest) {
            this.project.getTestSources().add(src);
          } else {
            this.project.getSources().add(src);
          }
        }
        Collection<File> generatedResourceFolders = androidArtifact.getGeneratedResourceFolders();
        for (File src : generatedResourceFolders) {
          if (isTest) {
            this.project.getTestResources().add(src);
          } else {
            this.project.getResources().add(src);
          }
        }
      }

      Dependencies compileDependencies = androidArtifact.getCompileDependencies();
      // getLibraries
      Collection<AndroidLibrary> libraries = compileDependencies.getLibraries();
      for (AndroidLibrary androidLibrary : libraries) {
        Collection<File> localJars = androidLibrary.getLocalJars();
        if (localJars.isEmpty()) {
          File jar = androidLibrary.getJarFile();
          addDependencies(dependencies, jar);
        } else {
          for (File jar : localJars) {
            addDependencies(dependencies, jar);
          }
        }
      }
      // getJavaLibraries
      Collection<JavaLibrary> javaLibraries = compileDependencies.getJavaLibraries();
      for (JavaLibrary javaLibrary : javaLibraries) {
        File file = javaLibrary.getJarFile();
        addDependencies(dependencies, file);
      }
    }
  }

  private void addDependencies(Set<ProjectDependency> dependencies, File jar) {
    String id = jar.getName();
    String version = DEFAULT_VERSION;
    String scope = DEFAULT_SCOPE;
    ProjectDependency projectDependency =
        new ProjectDependency(id, scope, version, jar, ProjectDependency.Type.JAR);
    dependencies.add(projectDependency);
  }

  private Map<String, Set<File>> getAndroidSources(ProductFlavorContainer defaultConfig) {

    Map<String, Set<File>> sources = new HashMap<>();
    ProductFlavor productFlavor = defaultConfig.getProductFlavor();
    String name = productFlavor.getName();

    SourceProvider sourceProvider = defaultConfig.getSourceProvider();
    AndroidSupport.setAndroidSources(sources, sourceProvider, false);

    // extra
    Collection<SourceProviderContainer> extraSourceProviders =
        defaultConfig.getExtraSourceProviders();
    extraSourceProviders.forEach(
        sourceProviderContainer -> {
          String artifactName = sourceProviderContainer.getArtifactName();
          SourceProvider provider = sourceProviderContainer.getSourceProvider();
          boolean isTest = artifactName.contains(TEST_SUFFIX);
          AndroidSupport.setAndroidSources(sources, provider, isTest);
        });

    return sources;
  }

  void prepareCompileAndroidJava() {
    ProjectConnection connection = this.project.getProjectConnection();
    try {
      BuildLauncher buildLauncher = connection.newBuild();
      String genTask = this.project.getName() + this.genSourceTaskName;
      buildLauncher.forTasks(genTask).run();

      int size = this.project.getDependencies().size();

      String aar =
          Joiner.on(File.separator)
              .join(this.project.getProjectRoot(), BUILD_DIR, INTERMEDIATE_DIR, EXPLODED_DIR);
      List<File> jars = FileUtils.collectFiles(new File(aar), EXT_JAR);
      for (File jar : jars) {
        addAAR(jar);
      }

      int after = this.project.getDependencies().size();
      if (size != after) {
        CachedASMReflector.getInstance().createClassIndexes(jars);
        this.project.resetCachedClasspath();
      }
    } finally {
      connection.close();
    }
  }

  void prepareCompileAndroidTestJava() {
    ProjectConnection connection = this.project.getProjectConnection();
    try {
      BuildLauncher buildLauncher = connection.newBuild();
      String genTestTask = this.project.getName() + genUnitTestTaskName;
      String genAndroidTestTask = this.project.getName() + genAndroidTestTaskName;

      buildLauncher.forTasks(genTestTask, genAndroidTestTask).run();

      int size = this.project.getDependencies().size();

      String aar =
          Joiner.on(File.separator)
              .join(this.project.getProjectRoot(), BUILD_DIR, INTERMEDIATE_DIR, EXPLODED_DIR);
      List<File> jars = FileUtils.collectFiles(new File(aar), EXT_JAR);
      for (File jar : jars) {
        addAAR(jar);
      }

      int after = this.project.getDependencies().size();
      if (size != after) {
        CachedASMReflector.getInstance().createClassIndexes(jars);
        this.project.resetCachedClasspath();
      }
    } finally {
      connection.close();
    }
  }
}
