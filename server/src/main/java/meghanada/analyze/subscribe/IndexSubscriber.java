package meghanada.analyze.subscribe;

import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import meghanada.analyze.JavaAnalyzer;
import meghanada.analyze.Source;
import meghanada.index.IndexDatabase;
import meghanada.index.SearchIndexable;
import meghanada.project.Project;
import meghanada.store.ProjectDatabaseHelper;
import meghanada.telemetry.TelemetryUtils;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IndexSubscriber {

  private static final Logger log = LogManager.getLogger(IndexSubscriber.class);

  private final Map<Project, Map<String, String>> checksums;
  private final Supplier<Project> projectSupplier;

  public IndexSubscriber(final Supplier<Project> projectSupplier) {
    this.projectSupplier = projectSupplier;
    this.checksums = new HashMap<>(2);
  }

  private Map<String, String> getChecksumMap() {
    Project project = this.projectSupplier.get();
    if (this.checksums.containsKey(project)) {
      return this.checksums.get(project);
    }
    Map<String, String> checksumMap =
        ProjectDatabaseHelper.getChecksumMap(project.getProjectRootPath());
    this.checksums.put(project, checksumMap);
    return checksumMap;
  }

  @Subscribe
  @SuppressWarnings("try")
  public void on(final JavaAnalyzer.AnalyzedEvent event) {

    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("IndexSubscriber/on");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {

      Map<String, String> checksumMap = getChecksumMap();
      final Map<File, Source> analyzedMap = event.analyzedMap;
      List<SearchIndexable> sources =
          analyzedMap.values().stream()
              .filter(
                  source -> {
                    try {
                      final File sourceFile = source.getFile();
                      final String path = sourceFile.getCanonicalPath();
                      final String oldChecksum = checksumMap.getOrDefault(path, "");
                      final String md5sum = FileUtils.getChecksum(sourceFile);
                      return !oldChecksum.equals(md5sum);
                    } catch (Exception e) {
                      log.catching(e);
                    }
                    return false;
                  })
              .collect(Collectors.toList());

      IndexDatabase.requestIndex(sources);
      span.setStatusOK();
    }
  }
}
