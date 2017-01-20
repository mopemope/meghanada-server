package meghanada.analyze;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class CompileResult {

    private final boolean success;
    private final Map<File, Source> sources;
    private List<Diagnostic<? extends JavaFileObject>> diagnostics = new ArrayList<>();
    private Set<File> errorFiles = new HashSet<>();

    public CompileResult(final boolean success) {
        this(success, new HashMap<>());
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
}
