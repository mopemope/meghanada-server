package meghanada.project.gradle;

import java.util.List;

public interface Dependency {
  String getGroupId();

  String getArtifactId();

  String getVersion();

  String getClassifier();

  String getExtension();

  List<Dependency> getDependencies();

  String getError();

  String getWarning();

  String getPomFile();

  String getLocalPath();
}
