package meghanada.analyze;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

public class CompileResult {

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

    public CompileResult(final boolean success,
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
            return new JavaDiagnostic(fileObject, Diagnostic.Kind.ERROR, lineNumber, methodName, t.getMessage());
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
        return diagnostics != null && diagnostics.size() > 0;
    }

    public String getDiagnosticsSummary() {
        if (hasDiagnostics()) {
            return diagnostics.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("\n"));
        } else {
            return "is java file?";
        }
    }

    public Map<File, Source> getSources() {
        return sources;
    }

    public Set<File> getErrorFiles() {
        return errorFiles;
    }

    static class JavaDiagnostic implements Diagnostic<JavaFileObject> {

        private final JavaFileObject fileObject;
        private final Diagnostic.Kind kind;
        private final int line;
        private final String code;
        private final String message;

        JavaDiagnostic(final JavaFileObject fileObject,
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
