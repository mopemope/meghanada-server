package meghanada.index;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import jetbrains.exodus.env.ContextualEnvironment;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.Environments;
import meghanada.reflect.MemberDescriptor;
import meghanada.store.ProjectDatabase;
import meghanada.store.Serializer;
import meghanada.system.Executor;
import meghanada.telemetry.TelemetryUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.util.BytesRef;

public class IndexDatabase {

  private static final Logger log = LogManager.getLogger(IndexDatabase.class);
  private static final String QUOTE = "\"";
  private static IndexDatabase indexDatabase;
  public final int maxHits = Integer.MAX_VALUE;
  private DocumentSearcher searcher;
  private Environment environment = null;
  private final File baseLocation = null;

  private IndexDatabase() {
    Executor.getInstance().getEventBus().register(this);
  }

  public static synchronized IndexDatabase getInstance() {
    if (nonNull(indexDatabase)) {
      return indexDatabase;
    }
    indexDatabase = new IndexDatabase();
    return indexDatabase;
  }

  public static String doubleQuote(@Nullable final String s) {
    if (isNull(s)) {
      return QUOTE + QUOTE;
    }
    return QUOTE + s + QUOTE;
  }

  public static String paren(@Nullable final String s) {
    if (isNull(s)) {
      return s;
    }
    return "(" + s + ")";
  }

  private synchronized void open() {
    final File loc = ProjectDatabase.getInstance().getBaseLocation();
    if (nonNull(this.baseLocation) && !this.baseLocation.equals(loc)) {
      // change database
      if (nonNull(this.searcher)) {
        this.searcher.close();
        this.searcher = null;
      }
      if (nonNull(this.environment)) {
        this.environment.close();
        this.environment = null;
      }
    }
    if (isNull(this.environment) || isNull(this.searcher)) {
      File indexDir = new File(loc, "index");
      Environment env = Environments.newContextualInstance(indexDir);
      String location = env.getLocation();
      log.debug("open index database {}", location);
      try {
        this.searcher = new DocumentSearcher((ContextualEnvironment) env);
        this.environment = env;
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  synchronized void indexObject(final SearchIndexable s) {
    this.open();
    this.searcher.executeInTransaction(
        () -> {
          try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            String id = s.getIndexGroupId();
            List<Document> docs = s.getDocumentIndices();
            this.searcher.deleteDocuments(SearchIndexable.GROUP_ID, id);
            this.searcher.addDocuments(docs);
            log.debug("indexed :{} elapsed:{}", id, stopwatch.stop());
          } catch (Throwable e) {
            log.catching(e);
          }
        });
  }

  synchronized void indexObjects(final List<SearchIndexable> list) {
    this.open();
    this.searcher.executeInTransaction(
        () -> {
          try {
            for (final SearchIndexable s : list) {
              if (nonNull(s) && nonNull(s.getIndexGroupId())) {
                String id = s.getIndexGroupId();
                List<Document> docs = s.getDocumentIndices();
                this.searcher.deleteDocuments(SearchIndexable.GROUP_ID, id);
                this.searcher.addDocuments(docs);
                log.debug("indexed :{}", id);
              }
            }
          } catch (Throwable e) {
            log.catching(e);
          }
        });
  }

  @Subscribe
  public void on(final IndexEvent event) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("IndexDatabase/on");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {

      if (isNull(this.searcher)) {
        return;
      }
      if (nonNull(event.indexables)) {
        this.indexObjects(event.indexables);
      } else {
        this.indexObject(event.indexable);
      }
      if (nonNull(event.onSuccess)) {
        event.onSuccess.accept(event);
      }
    }
  }

  public static void requestIndex(final SearchIndexable i) {
    final IndexEvent event = new IndexEvent(i);
    Executor.getInstance().getEventBus().post(event);
  }

  public static void requestIndex(final SearchIndexable i, final Consumer<IndexEvent> c) {
    final IndexEvent event = new IndexEvent(i, c);
    Executor.getInstance().getEventBus().post(event);
  }

  public static void requestIndex(final List<SearchIndexable> i) {
    final IndexEvent event = new IndexEvent(i);
    Executor.getInstance().getEventBus().post(event);
  }

  public synchronized Optional<SearchResults> search(final String query) {
    this.open();
    return this.searcher.searchInTransaction(
        () -> {
          try {
            final SearchResults results = new SearchResults();
            {
              String codeField = IndexableWord.Field.CODE.getName();
              final List<SearchResult> result =
                  this.searcher.search(
                      codeField,
                      query,
                      maxHits,
                      d -> {
                        final String filePath = d.get(SearchIndexable.GROUP_ID);
                        final String line = d.get(SearchIndexable.LINE_NUMBER);
                        final String contents = d.get(codeField);
                        final String cat = d.get(SearchIndexable.CATEGORY);
                        return Optional.of(new SearchResult(filePath, line, contents, cat));
                      });

              result.forEach(
                  r -> {
                    final String cat = r.category;
                    if (cat.equals(IndexableWord.Field.CLASS_NAME.getName())) {
                      results.classes.add(r);
                    } else if (cat.equals(IndexableWord.Field.METHOD_NAME.getName())) {
                      results.methods.add(r);
                    } else if (cat.equals(IndexableWord.Field.PACKAGE_NAME.getName())) {
                      results.classes.add(r);
                    } else if (cat.equals(IndexableWord.Field.USAGE.getName())) {
                      results.usages.add(r);
                    } else if (cat.equals(IndexableWord.Field.SYMBOL_NAME.getName())) {
                      results.symbols.add(r);
                    } else {
                      results.codes.add(r);
                    }
                  });
            }
            return Optional.of(results);
          } catch (Throwable e) {
            log.warn(e);
            return Optional.empty();
          }
        });
  }

  public synchronized List<MemberDescriptor> searchMembers(
      final String classQuery,
      final String modifierQuery,
      final String memberTypeQuery,
      final String nameQuery) {
    this.open();
    try {
      return this.searcher.searchInTransaction(
          () -> {
            try {
              String codeField = IndexableWord.Field.CODE.getName();
              List<String> queryList = new ArrayList<>(4);
              if (!isNullOrEmpty(classQuery)) {
                queryList.add("cdc:" + classQuery);
              }
              if (!isNullOrEmpty(modifierQuery)) {
                queryList.add("modifier:" + modifierQuery);
              }
              if (!isNullOrEmpty(memberTypeQuery)) {
                queryList.add("memberType:" + memberTypeQuery);
              }
              if (!isNullOrEmpty(nameQuery)) {
                queryList.add("completion:" + nameQuery);
              }
              final String query = Joiner.on(" AND ").join(queryList);
              log.debug("query: {}", query);
              return this.searcher.search(
                  codeField,
                  query,
                  maxHits,
                  d -> {
                    BytesRef value = d.getBinaryValue("binary");
                    if (isNull(value)) {
                      return Optional.empty();
                    }
                    byte[] b = value.bytes;
                    if (isNull(b)) {
                      return Optional.empty();
                    }
                    return Optional.ofNullable(Serializer.asObject(b, MemberDescriptor.class));
                  });
            } catch (Throwable e) {
              log.warn(e);
              return Collections.emptyList();
            }
          });
    } catch (UncheckedIOException e) {
      IOException cause = e.getCause();
      if (cause instanceof IndexNotFoundException) {
        return Collections.emptyList();
      }
      throw e;
    }
  }

  public static class IndexEvent {

    Consumer<IndexEvent> onSuccess;
    SearchIndexable indexable = null;
    List<SearchIndexable> indexables = null;

    IndexEvent(final SearchIndexable indexable) {
      this.indexable = indexable;
    }

    IndexEvent(final List<SearchIndexable> indexables) {
      this.indexables = indexables;
    }

    IndexEvent(final SearchIndexable indexable, final Consumer<IndexEvent> consumer) {
      this.indexable = indexable;
      this.onSuccess = consumer;
    }
  }
}
