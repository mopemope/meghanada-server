package meghanada.project.gradle;

import java.util.List;

public interface DependencyTreeModel {
  String getGroup();

  String getName();

  String getVersion();

  List<Configuration> getConfigurations();

  List<String> getRepositories();

  List<String> getErrors();

  List<String> getWarnings();
}
