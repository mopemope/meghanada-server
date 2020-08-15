package meghanada.project.gradle;

import java.util.List;

public interface Configuration {
  String getName();

  List<Dependency> getDependencies();
}
