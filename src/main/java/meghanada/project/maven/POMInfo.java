package meghanada.project.maven;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import meghanada.project.ProjectDependency;

class POMInfo {

  final Set<File> sourceDirectory = new HashSet<>(2);
  final Set<File> resourceDirectories = new HashSet<>(2);
  final Set<File> testSourceDirectory = new HashSet<>(2);
  final Set<File> testResourceDirectories = new HashSet<>(2);
  final Properties properties = new Properties();
  private final Map<String, ProjectDependency> dependencyHashMap = new HashMap<>(8);
  File outputDirectory;
  File testOutputDirectory;
  String groupId;
  String artifactId;
  String version;
  String compileSource = "1.8";
  String compileTarget = "1.8";

  POMInfo(String project) throws IOException {
    this.properties.setProperty("project.basedir", project);
    this.properties.setProperty(
        "project.build.directory", new File(project, "target").getCanonicalPath());
  }

  public Set<File> getSourceDirectory() {
    return sourceDirectory;
  }

  public Set<File> getResourceDirectories() {
    return resourceDirectories;
  }

  public File getOutputDirectory() {
    return outputDirectory;
  }

  public Set<File> getTestSourceDirectory() {
    return testSourceDirectory;
  }

  public Set<File> getTestResourceDirectories() {
    return testResourceDirectories;
  }

  public File getTestOutputDirectory() {
    return testOutputDirectory;
  }

  public Map<String, ProjectDependency> getDependencyHashMap() {
    return dependencyHashMap;
  }

  public String getCompileSource() {
    return compileSource;
  }

  public String getCompileTarget() {
    return compileTarget;
  }

  void putAll(POMInfo pomInfo) {
    this.groupId = pomInfo.groupId;
    this.artifactId = pomInfo.artifactId;
    this.version = pomInfo.version;
    this.properties.putAll(pomInfo.properties);

    this.dependencyHashMap.putAll(pomInfo.dependencyHashMap);
    this.compileSource = pomInfo.compileSource;
    this.compileTarget = pomInfo.compileTarget;
  }
}
