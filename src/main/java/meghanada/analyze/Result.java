package meghanada.analyze;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Result {

    private final boolean success;
    private final Map<File, Source> sources;
    private List<Diagnostic<? extends JavaFileObject>> diagnostics = new ArrayList<>();

    public Result(final boolean success, final Map<File, Source> sources) {
        this.success = success;
        this.sources = sources;
    }

    public Result(final boolean success, final Map<File, Source> sources, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        this(success, sources);
        this.diagnostics = diagnostics;
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
}
