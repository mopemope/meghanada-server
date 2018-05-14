package meghanada.index;

import static java.util.Objects.nonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jetbrains.exodus.env.ContextualEnvironment;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.StoreConfig;
import jetbrains.exodus.lucene.ExodusDirectory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NoLockFactory;
import org.apache.lucene.util.Version;

public class DocumentSearcher implements AutoCloseable {

  private static final Logger log = LogManager.getLogger(DocumentSearcher.class);

  private static final Version LUCENE_VERSION = Version.LUCENE_36;
  private final Environment environment;
  private final Directory directory;
  private final Analyzer analyzer;
  private IndexWriter indexWriter;
  private IndexSearcher indexSearcher;

  public DocumentSearcher(final ContextualEnvironment environment, final boolean withPrefixing)
      throws IOException {
    this.environment = environment;
    final StoreConfig config =
        withPrefixing
            ? StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING
            : StoreConfig.WITHOUT_DUPLICATES;
    this.directory = new ExodusDirectory(environment, config, NoLockFactory.getNoLockFactory());
    this.analyzer = createAnalyzer();
  }

  public DocumentSearcher(final ContextualEnvironment environment) throws IOException {
    this(environment, false);
  }

  private Analyzer createAnalyzer() {
    return new StandardAnalyzer(LUCENE_VERSION);
  }

  private IndexWriterConfig createIndexConfig(Analyzer analyzer) {
    IndexWriterConfig config = new IndexWriterConfig(LUCENE_VERSION, analyzer);
    config.setMergeScheduler(new SerialMergeScheduler());
    config.setMaxThreadStates(2);
    return config;
  }

  private IndexWriter createIndexWriter(Directory directory, IndexWriterConfig indexConfig)
      throws IOException {
    if (nonNull(this.indexWriter)) {
      return this.indexWriter;
    }
    this.indexWriter = new IndexWriter(directory, indexConfig);
    return indexWriter;
  }

  private IndexReader createIndexReader(Directory directory) throws IOException {
    return IndexReader.open(directory);
  }

  private IndexSearcher createIndexSearcher(IndexReader indexReader) {
    if (nonNull(this.indexSearcher)) {
      return this.indexSearcher;
    }
    this.indexSearcher = new IndexSearcher(indexReader);
    return this.indexSearcher;
  }

  public void closeIndexWriter() throws IOException {
    if (nonNull(this.indexWriter)) {
      this.indexWriter.close();
    }
    this.indexWriter = null;
  }

  public void closeIndexSearcher() throws IOException {
    if (nonNull(this.indexSearcher)) {
      this.indexSearcher.close();
    }
    this.indexSearcher = null;
  }

  public void addDocuments(final Collection<Document> docs) throws IOException {
    indexWriter.addDocuments(docs);
  }

  public void deleteDocuments(final String fld, final String idValue) throws IOException {
    indexWriter.deleteDocuments(new Term(fld, idValue));
  }

  private Query getQuery(final String field, final String query) throws ParseException {
    final QueryParser queryParser = new QueryParser(LUCENE_VERSION, field, analyzer);
    queryParser.setAllowLeadingWildcard(true);
    queryParser.setDefaultOperator(QueryParser.Operator.OR);
    return queryParser.parse(query);
  }

  List<Document> search(final String field, final String query, final int cnt)
      throws IOException, ParseException {
    final TopDocs results = indexSearcher.search(getQuery(field, query), cnt);
    return Arrays.stream(results.scoreDocs)
        .map(
            doc -> {
              try {
                return indexSearcher.doc(doc.doc);
              } catch (IOException ex) {
                throw new UncheckedIOException(ex);
              }
            })
        .collect(Collectors.toList());
  }

  <T> List<T> search(
      final String field, final String query, final int cnt, final DocumentConverter<T> converter)
      throws IOException, ParseException {
    final TopDocs results = indexSearcher.search(getQuery(field, query), cnt);
    return Arrays.stream(results.scoreDocs)
        .map(
            doc -> {
              try {
                return converter.convert(indexSearcher.doc(doc.doc));
              } catch (IOException ex) {
                throw new UncheckedIOException(ex);
              }
            })
        .collect(Collectors.toList());
  }

  @SuppressWarnings("CheckReturnValue")
  void executeInTransaction(final Runnable runnable) {
    this.environment.executeInTransaction(
        txn -> {
          try {
            this.createIndexWriter(this.directory, createIndexConfig(this.analyzer));
            runnable.run();
            this.closeIndexSearcher();
            this.closeIndexWriter();
          } catch (IOException ex) {
            txn.abort();
            throw new UncheckedIOException(ex);
          }
        });
  }

  @SuppressWarnings("CheckReturnValue")
  <T> T computeInTransaction(final Supplier<T> fn) {
    return this.environment.computeInTransaction(
        txn -> {
          try {
            this.createIndexWriter(this.directory, createIndexConfig(this.analyzer));
            final IndexReader indexReader = this.createIndexReader(this.directory);
            this.createIndexSearcher(indexReader);
            T t = fn.get();
            this.closeIndexSearcher();
            this.closeIndexWriter();
            return t;
          } catch (IOException ex) {
            txn.abort();
            throw new UncheckedIOException(ex);
          }
        });
  }

  @SuppressWarnings("CheckReturnValue")
  <T> T searchInTransaction(final Supplier<T> fn) {
    return this.environment.computeInReadonlyTransaction(
        txn -> {
          try {
            final IndexReader indexReader = this.createIndexReader(this.directory);
            this.createIndexSearcher(indexReader);
            T t = fn.get();
            this.closeIndexSearcher();
            return t;
          } catch (IOException ex) {
            txn.abort();
            throw new UncheckedIOException(ex);
          }
        });
  }

  @Override
  public void close() {
    try {
      this.closeIndexSearcher();
      this.closeIndexWriter();
      this.directory.close();
    } catch (Throwable e) {
      log.catching(e);
    }
  }
}
