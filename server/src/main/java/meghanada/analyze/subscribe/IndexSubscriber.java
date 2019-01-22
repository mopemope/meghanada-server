package meghanada.analyze.subscribe;

import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import meghanada.analyze.JavaAnalyzer;
import meghanada.analyze.Source;
import meghanada.index.IndexDatabase;
import meghanada.index.SearchIndexable;
import meghanada.project.Project;
import meghanada.store.ProjectDatabaseHelper;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IndexSubscriber {

  private static final Logger log = LogManager.getLogger(IndexSubscriber.class);

  private final Map<String, String> checksumMap;

  public IndexSubscriber(final Project project) {
    this.checksumMap = ProjectDatabaseHelper.getChecksumMap(project.getProjectRootPath());
  }

  @Subscribe
  public void on(final JavaAnalyzer.AnalyzedEvent event) {
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
  }
}
