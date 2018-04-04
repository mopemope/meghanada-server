package meghanada.index;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import jetbrains.exodus.env.ContextualEnvironment;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.Environments;
import meghanada.store.ProjectDatabase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryParser.ParseException;

public class IndexDatabase {

  private static final Logger log = LogManager.getLogger(IndexDatabase.class);

  private DocumentSearcher searcher;
  private Environment environment = null;
  private File baseLocation = null;
  private final ExecutorService executorService;
  private final EventBus eventBus;

  private static IndexDatabase indexDatabase;
  public int maxHits = 100;
  private boolean textSearch;

  public static synchronized IndexDatabase getInstance() {
    if (nonNull(indexDatabase)) {
      return indexDatabase;
    }
    indexDatabase = new IndexDatabase();
    return indexDatabase;
  }

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

  private void indexObject(final SearchIndexable s) {
    this.open();
    this.searcher.executeInTransaction(
        () -> {
          try {
            final String id = s.getIndexGroupId();
            final List<Document> docs = s.getDocumentIndices();
            searcher.deleteDocuments(SearchIndexable.GROUP_ID, id);
            searcher.addDocuments(docs);
            log.debug("indexed :{}", id);
          } catch (IOException ex) {
            throw new UncheckedIOException(ex);
          }
        });
  }

  private void indexObjects(final List<SearchIndexable> list) {
    this.open();
    this.searcher.executeInTransaction(
        () -> {
          try {
            for (final SearchIndexable s : list) {
              final String id = s.getIndexGroupId();
              final List<Document> docs = s.getDocumentIndices();
              searcher.deleteDocuments(SearchIndexable.GROUP_ID, id);
              searcher.addDocuments(docs);
              log.debug("indexed :{}", id);
            }
          } catch (IOException ex) {
            throw new UncheckedIOException(ex);
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
  }

  public void requestIndex(final SearchIndexable i) {
    final IndexEvent event = new IndexEvent(i);
    this.eventBus.post(event);
  }

  public void requestIndex(final List<SearchIndexable> i) {
    final IndexEvent event = new IndexEvent(i);
    this.eventBus.post(event);
  }

  List<Document> search(final String fld, final String query) {
    this.open();
    return this.searcher.searchInTransaction(
        () -> {
          try {
            return searcher.search(fld, query, maxHits);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          } catch (ParseException e) {
            log.catching(e);
            return Collections.emptyList();
          }
        });
  }

  <T> List<T> search(final String fld, final String query, final DocumentConverter<T> converter) {
    this.open();
    return this.searcher.searchInTransaction(
        () -> {
          try {
            return searcher.search(fld, query, maxHits, converter);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          } catch (ParseException e) {
            log.catching(e);
            return Collections.emptyList();
          }
        });
  }

  public Optional<SearchResults> search(final String query) {
    this.open();
    return this.searcher.searchInTransaction(
        () -> {
          try {
            final SearchResults results = new SearchResults();
            {
              final List<SearchResult> result =
                  this.searcher.search(
                      SearchIndexable.CLASS_NAME,
                      query,
                      maxHits,
                      d -> {
                        final String filePath = d.get(SearchIndexable.GROUP_ID);
                        final String line = d.get(SearchIndexable.LINE_NUMBER);
                        final String contents = d.get(SearchIndexable.CODE);
                        final String cat = d.get(SearchIndexable.CATEGORY);
                        return new SearchResult(filePath, line, contents, cat);
                      });

              result.forEach(
                  r -> {
                    final String cat = r.category;
                    if (cat.equals(SearchIndexable.CLASS_NAME)) {
                      results.classes.add(r);
                    } else if (cat.equals(SearchIndexable.METHOD_NAME)) {
                      results.methods.add(r);
                    } else if (cat.equals(SearchIndexable.SYMBOL_NAME)) {
                      results.symbols.add(r);
                    } else {
                      results.codes.add(r);
                    }
                  });
            }
            return Optional.of(results);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          } catch (ParseException e) {
            log.catching(e);
            return Optional.empty();
          }
        });
  }

  public static class IndexEvent {

    SearchIndexable indexable = null;
    List<SearchIndexable> indexables = null;

    public IndexEvent(final SearchIndexable indexable) {
      this.indexable = indexable;
    }

    public IndexEvent(final List<SearchIndexable> indexables) {
      this.indexables = indexables;
    }
  }
}
