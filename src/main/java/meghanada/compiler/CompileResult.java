package meghanada.compiler;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CompileResult {

    private boolean success;
    private List<Diagnostic<? extends JavaFileObject>> diagnostics = new ArrayList<>();

    public CompileResult(boolean success) {
        this.success = success;
    }

    public CompileResult(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        this.success = success;
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

}
