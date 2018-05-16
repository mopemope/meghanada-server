package meghanada.cache;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import meghanada.analyze.CompileResult;
import meghanada.analyze.Source;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.store.ProjectDatabaseHelper;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class JavaSourceLoader extends CacheLoader<File, Source> implements RemovalListener<File, Source> {

  private static final Logger log = LogManager.getLogger(JavaSourceLoader.class);

  private final Project project;

  public JavaSourceLoader(final Project project) {
    this.project = project;
  }

  private void deleteSource(final Source source) throws Exception {
    boolean b = ProjectDatabaseHelper.deleteSource(source.getStoreId());
  }

  private Optional<Source> loadSource(final File sourceFile) throws Exception {
    String filePath = sourceFile.getCanonicalPath();
    Source source = ProjectDatabaseHelper.loadSource(filePath);
    return Optional.ofNullable(source);
  }

  @Override
  public Source load(final File file) throws IOException {
    final Config config = Config.load();
    if (!file.exists()) {
      return new Source(file.getPath());
    }

    if (!config.useSourceCache()) {
      final CompileResult compileResult = project.parseFile(file);
      return compileResult.getSources().get(file);
    }

    final String projectRootPath = this.project.getProjectRootPath();
    final Map<String, String> checksumMap = ProjectDatabaseHelper.getChecksumMap(projectRootPath);

    final String path = file.getCanonicalPath();
    final String md5sum = FileUtils.getChecksum(file);
    if (checksumMap.containsKey(path)) {
      // compare checksum
      final String prevSum = checksumMap.get(path);
      if (md5sum.equals(prevSum)) {
        // not modify
        // load from cache
        try {
          final Optional<Source> source = loadSource(file);
          if (source.isPresent()) {
            log.debug("hit source cache {}", file);
            return source.get();
          }
        } catch (Exception e) {
          log.catching(e);
        }
      }
    }
    log.warn("source cache miss {}", file);
    final CompileResult compileResult = project.parseFile(file.getCanonicalFile());
    return compileResult.getSources().get(file.getCanonicalFile());
  }

  @Override
  public void onRemoval(final RemovalNotification<File, Source> notification) {
    final RemovalCause cause = notification.getCause();

    final Config config = Config.load();
    if (config.useSourceCache() && cause.equals(RemovalCause.EXPLICIT)) {
      final Source source = notification.getValue();
      try {
        deleteSource(source);
      } catch (Exception e) {
        log.catching(e);
      }
    }
  }
}
