package meghanada.analyze;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.StoreTransaction;
import meghanada.store.Storable;

public class CompileResult implements Storable {

  public static final String DIAGNOSTIC_ENTITY_TYPE = "Diagnostic";
  public static final String ENTITY_TYPE = "CompileResult";

  private final boolean success;
  private final Map<File, Source> sources;
  private List<Diagnostic<? extends JavaFileObject>> diagnostics = new ArrayList<>(0);
  private Set<File> errorFiles = new HashSet<>(0);

  public CompileResult(final boolean success) {
    this(success, new HashMap<>(0));
  }

  public CompileResult(final boolean success, final Map<File, Source> sources) {
    this.success = success;
    this.sources = sources;
  }

  public CompileResult(
      final boolean success,
      final Map<File, Source> sources,
      final List<Diagnostic<? extends JavaFileObject>> diagnostics,
      final Set<File> errorFiles) {

    this(success, sources);
    this.diagnostics = new ArrayList<>(diagnostics);
    this.errorFiles = errorFiles;
  }

  public static Diagnostic<? extends JavaFileObject> getDiagnosticFromThrowable(final Throwable t) {

    final int length = t.getStackTrace().length;
    if (length > 0) {
      final StackTraceElement st = t.getStackTrace()[0];
      final File file = new File(st.getFileName());
      final URI furi = file.toURI();
      final ErrorJavaFileObject fileObject = new ErrorJavaFileObject(furi);
      final int lineNumber = st.getLineNumber();
      final String methodName = st.getMethodName();
      return new JavaDiagnostic(
          fileObject, Diagnostic.Kind.ERROR, lineNumber, methodName, t.getMessage());
    }
    return null;
  }

  public boolean isSuccess() {
    return success;
  }

  public List<Diagnostic<? extends JavaFileObject>> getDiagnostics() {
    return diagnostics;
  }

  public boolean hasDiagnostics() {
    return nonNull(diagnostics) && !diagnostics.isEmpty();
  }

  public String getDiagnosticsSummary() {
    if (hasDiagnostics()) {
      return diagnostics.stream().map(Object::toString).collect(Collectors.joining("\n"));
    } else {
      return "not compiled.";
    }
  }

  public Map<File, Source> getSources() {
    return sources;
  }

  public Set<File> getErrorFiles() {
    return errorFiles;
  }

  @Override
  public String getStoreId() {
    long now = Instant.now().getEpochSecond();
    return Long.toString(now);
  }

  @Override
  public String getEntityType() {
    return ENTITY_TYPE;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Map<String, Comparable> getSaveProperties() {

    Map<String, Comparable> prop = new HashMap<>(3);
    long now = Instant.now().getEpochSecond();
    prop.put("createdAt", now);
    prop.put("result", this.success);
    prop.put("problems", this.diagnostics.size());
    return prop;
  }

  @Override
  public void storeExtraData(StoreTransaction txn, Entity mainEntity) {

    for (Diagnostic<? extends JavaFileObject> diagnostic : this.diagnostics) {
      String kind = diagnostic.getKind().toString();
      long line = diagnostic.getLineNumber();
      long column = diagnostic.getColumnNumber();

      String message = diagnostic.getMessage(null);
      if (isNull(message)) {
        message = "";
      }
      JavaFileObject fileObject = diagnostic.getSource();
      String path = null;
      if (fileObject != null) {
        final URI uri = fileObject.toUri();
        final File file = new File(uri);
        try {
          path = file.getCanonicalPath();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      String code = diagnostic.getCode();
      Entity entity = txn.newEntity(CompileResult.DIAGNOSTIC_ENTITY_TYPE);
      entity.setProperty("kind", kind);
      entity.setProperty("line", line);
      entity.setProperty("column", column);
      entity.setProperty("message", message);
      if (nonNull(path)) {
        entity.setProperty("path", path);
      }
      if (nonNull(code)) {
        entity.setProperty("code", code);
      }

      // txn.saveEntity(entity);
      mainEntity.addLink("diagnostic", entity);
    }
  }

  static class JavaDiagnostic implements Diagnostic<JavaFileObject> {

    private final JavaFileObject fileObject;
    private final Diagnostic.Kind kind;
    private final int line;
    private final String code;
    private final String message;

    JavaDiagnostic(
        final JavaFileObject fileObject,
        final Diagnostic.Kind kind,
        final int line,
        final String code,
        final String message) {
      this.fileObject = fileObject;
      this.kind = kind;
      this.line = line;
      this.code = code;
      this.message = message;
    }

    @Override
    public Kind getKind() {
      return this.kind;
    }

    @Override
    public JavaFileObject getSource() {
      return this.fileObject;
    }

    @Override
    public long getPosition() {
      return 0;
    }

    @Override
    public long getStartPosition() {
      return 0;
    }

    @Override
    public long getEndPosition() {
      return 0;
    }

    @Override
    public long getLineNumber() {
      return this.line;
    }

    @Override
    public long getColumnNumber() {
      return 0;
    }

    @Override
    public String getCode() {
      return this.code;
    }

    @Override
    public String getMessage(Locale locale) {
      return this.message;
    }

    @Override
    public String toString() {
      return this.message;
    }
  }

  static class ErrorJavaFileObject extends SimpleJavaFileObject {
    ErrorJavaFileObject(final URI uri) {
      super(uri, Kind.SOURCE);
    }
  }
}
