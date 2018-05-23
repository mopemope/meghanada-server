package meghanada.index;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import jetbrains.exodus.env.ContextualEnvironment;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.Environments;
import meghanada.reflect.MemberDescriptor;
import meghanada.store.ProjectDatabase;
import meghanada.store.Serializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;

public class IndexDatabase {

  private static final Logger log = LogManager.getLogger(IndexDatabase.class);
  private static final String QUOTE = "\"";
  private static IndexDatabase indexDatabase;
  private final ExecutorService executorService;
  private final EventBus eventBus;
  public int maxHits = Integer.MAX_VALUE;
  private DocumentSearcher searcher;
  private Environment environment = null;
  private File baseLocation = null;

  private IndexDatabase() {
    this.executorService = Executors.newSingleThreadExecutor();
    this.eventBus =
        new AsyncEventBus(
            executorService,
            (throwable, subscriberExceptionContext) -> {
              if (!(throwable instanceof RejectedExecutionException)) {
                log.error(throwable.getMessage(), throwable);
              }
            });

    this.eventBus.register(this);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    shutdown();
                  } catch (Throwable t) {
                    log.catching(t);
                  }
                }));
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

  public void shutdown() {
    this.executorService.shutdown();
    try {
      this.executorService.awaitTermination(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      log.catching(e);
    }
  }

  private void open() {
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

    if (isNull(this.environment)) {
      File env = new File(loc, "index");
      this.environment = Environments.newContextualInstance(env);
      String location = this.environment.getLocation();
      log.debug("open index database {}", location);
      try {
        this.searcher = new DocumentSearcher((ContextualEnvironment) this.environment);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private synchronized void indexObject(final SearchIndexable s) {
    this.open();
    this.searcher.executeInTransaction(
        () -> {
          try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            String id = s.getIndexGroupId();
            List<Document> docs = s.getDocumentIndices();
            searcher.deleteDocuments(SearchIndexable.GROUP_ID, id);
            searcher.addDocuments(docs);
            log.debug("indexed :{} elapsed:{}", id, stopwatch.stop());
          } catch (Throwable e) {
            log.catching(e);
          }
        });
  }

  private synchronized void indexObjects(final List<SearchIndexable> list) {
    this.open();
    this.searcher.executeInTransaction(
        () -> {
          try {
            for (final SearchIndexable s : list) {
              if (nonNull(s) && nonNull(s.getIndexGroupId())) {
                String id = s.getIndexGroupId();
                List<Document> docs = s.getDocumentIndices();
                searcher.deleteDocuments(SearchIndexable.GROUP_ID, id);
                searcher.addDocuments(docs);
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

    if (nonNull(event.indexables)) {
      this.indexObjects(event.indexables);
    } else {
      this.indexObject(event.indexable);
    }
    if (nonNull(event.onSuccess)) {
      event.onSuccess.accept(event);
    }
  }

  public void requestIndex(final SearchIndexable i) {
    final IndexEvent event = new IndexEvent(i);
    this.eventBus.post(event);
  }

  public void requestIndex(final SearchIndexable i, final Consumer<IndexEvent> c) {
    final IndexEvent event = new IndexEvent(i, c);
    this.eventBus.post(event);
  }

  public void requestIndex(final List<SearchIndexable> i) {
    final IndexEvent event = new IndexEvent(i);
    this.eventBus.post(event);
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
                        return new SearchResult(filePath, line, contents, cat);
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
            log.catching(e);
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
                  byte[] b = d.getBinaryValue("binary");
                  return Serializer.asObject(b, MemberDescriptor.class);
                });
          } catch (Throwable e) {
            log.catching(e);
            return Collections.emptyList();
          }
        });
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
