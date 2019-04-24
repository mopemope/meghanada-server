package meghanada.index;

import static java.util.Objects.nonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jetbrains.exodus.env.ContextualEnvironment;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.StoreConfig;
import jetbrains.exodus.lucene.ExodusDirectory;
import jetbrains.exodus.lucene.codecs.Lucene70CodecWithNoFieldCompression;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;

public class DocumentSearcher implements AutoCloseable {

  private static final Logger log = LogManager.getLogger(DocumentSearcher.class);

  private final Environment environment;
  private final Directory directory;
  private final Analyzer analyzer;
  private IndexWriter indexWriter;
  private IndexSearcher indexSearcher;

  private DocumentSearcher(final ContextualEnvironment environment, final boolean withPrefixing)
      throws IOException {
    this.environment = environment;
    final StoreConfig config =
        withPrefixing
            ? StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING
            : StoreConfig.WITHOUT_DUPLICATES;
    this.directory = new ExodusDirectory(environment, config);
    this.analyzer = createAnalyzer();
  }

  DocumentSearcher(final ContextualEnvironment environment) throws IOException {
    this(environment, false);
  }

  private static Analyzer createAnalyzer() {
    return new StandardAnalyzer();
  }

  private static IndexWriterConfig createIndexConfig(Analyzer analyzer) {
    IndexWriterConfig config = new IndexWriterConfig(analyzer);
    config.setMergeScheduler(new ConcurrentMergeScheduler());
    config.setCodec(new Lucene70CodecWithNoFieldCompression());
    return config;
  }

  private synchronized IndexWriter createIndexWriter(
      Directory directory, IndexWriterConfig indexConfig) throws IOException {
    if (nonNull(this.indexWriter)) {
      return this.indexWriter;
    }
    this.indexWriter = new IndexWriter(directory, indexConfig);
    return indexWriter;
  }

  private static IndexReader createIndexReader(Directory directory) throws IOException {
    return DirectoryReader.open(directory);
  }

  private synchronized IndexSearcher createIndexSearcher(IndexReader indexReader) {
    if (nonNull(this.indexSearcher)) {
      return this.indexSearcher;
    }
    this.indexSearcher = new IndexSearcher(indexReader);
    return this.indexSearcher;
  }

  private synchronized void closeIndexWriter() {
    if (nonNull(this.indexWriter)) {
      try {
        this.indexWriter.close();
      } catch (IOException e) {
        log.catching(e);
      }
    }
    this.indexWriter = null;
  }

  void addDocuments(final Collection<Document> docs) throws IOException {
    indexWriter.addDocuments(docs);
  }

  void deleteDocuments(final String fld, final String idValue) throws IOException {
    indexWriter.deleteDocuments(new Term(fld, idValue));
  }

  private Query getQuery(final String field, final String query) throws ParseException {
    final QueryParser queryParser = new QueryParser(field, analyzer);
    queryParser.setAllowLeadingWildcard(true);
    queryParser.setDefaultOperator(QueryParser.Operator.OR);
    return queryParser.parse(query);
  }

  synchronized List<Document> search(final String field, final String query, final int cnt)
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
        .filter(r -> nonNull(r))
        .collect(Collectors.toList());
  }

  synchronized <T> List<T> search(
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
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  @SuppressWarnings("CheckReturnValue")
  synchronized void executeInTransaction(final Runnable runnable) {
    this.environment.executeInTransaction(
        txn -> {
          try {
            IndexWriter indexWriter =
                this.createIndexWriter(this.directory, createIndexConfig(this.analyzer));
            runnable.run();
          } catch (IOException ex) {
            txn.abort();
            throw new UncheckedIOException(ex);
          } finally {
            this.closeIndexWriter();
          }
        });
  }

  @SuppressWarnings("CheckReturnValue")
  synchronized <T> T computeInTransaction(final Supplier<T> fn) {
    return this.environment.computeInTransaction(
        txn -> {
          try {
            IndexWriter indexWriter =
                this.createIndexWriter(this.directory, createIndexConfig(this.analyzer));
            IndexReader indexReader = DocumentSearcher.createIndexReader(this.directory);
            IndexSearcher indexSearcher = this.createIndexSearcher(indexReader);
            return fn.get();
          } catch (IOException ex) {
            txn.abort();
            throw new UncheckedIOException(ex);
          } finally {
            this.closeIndexWriter();
          }
        });
  }

  @SuppressWarnings("CheckReturnValue")
  synchronized <T> T searchInTransaction(final Supplier<T> fn) {
    return this.environment.computeInReadonlyTransaction(
        txn -> {
          try {
            final IndexReader indexReader = DocumentSearcher.createIndexReader(this.directory);
            IndexSearcher searcher = this.createIndexSearcher(indexReader);
            return fn.get();
          } catch (IOException ex) {
            txn.abort();
            throw new UncheckedIOException(ex);
          }
        });
  }

  @Override
  public void close() {
    try {
      this.closeIndexWriter();
      this.directory.close();
    } catch (Throwable e) {
      log.catching(e);
    }
  }
}
