package meghanada.server.formatter;

import meghanada.compiler.CompileResult;
import meghanada.completion.LocalVariable;
import meghanada.location.Location;
import meghanada.reflect.CandidateUnit;
import meghanada.server.OutputFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SexpOutputFormatter implements OutputFormatter {

    private static final Logger log = LogManager.getLogger(SexpOutputFormatter.class);

    private static final String LPAREN = "(";
    private static final String RPAREN = ")";
    private static final String LIST_SEP = " ";
    private static final String QUOTE = "\"";

    private String doubleQuote(String s) {
        return QUOTE + s + QUOTE;
    }

    @Override
    public String changeProject(final boolean result) {
        if (result) {
            return LPAREN + "success" + RPAREN;
        }
        return LPAREN + "error" + RPAREN;
    }

    @Override
    public String compile(CompileResult compileResult, String path) {

        if (compileResult.isSuccess()) {
            return LPAREN + "success " + doubleQuote(path) + RPAREN;
        }
        return LPAREN + "error " + doubleQuote(compileResult.getDiagnosticsSummary()) + RPAREN;
    }

    @Override
    public String compileProject(CompileResult compileResult) {

        if (compileResult.isSuccess()) {
            return LPAREN + "success true" + RPAREN;
        }
        return LPAREN + "error " + doubleQuote(compileResult.getDiagnosticsSummary()) + RPAREN;
    }

    @Override
    public String diagnostics(CompileResult compileResult, String path) {
        if (compileResult.isSuccess()) {
            return LPAREN + "success" + RPAREN;
        }
        final List<Diagnostic<? extends JavaFileObject>> list = compileResult.getDiagnostics();
        StringBuilder sb = new StringBuilder();
        sb.append(LPAREN);
        sb.append("error ");

        final String s = list.stream()
                .map(d -> LPAREN + String.join(LIST_SEP,
                        Long.toString(d.getLineNumber()),
                        Long.toString(d.getColumnNumber()),
                        doubleQuote(d.getKind().toString()),
                        doubleQuote(d.getMessage(null))) + RPAREN)
                .collect(Collectors.joining(LIST_SEP));
        sb.append(s);

        sb.append(RPAREN);
        return sb.toString();
    }

    @Override
    public String autocomplete(Collection<? extends CandidateUnit> units) {
        StringBuilder sb = new StringBuilder(LPAREN);

        final String s = units.stream()
                .map(d -> LPAREN + String.join(LIST_SEP,
                        doubleQuote(d.getType()),
                        doubleQuote(toSimpleName(d.getName())),
                        doubleQuote(d.getDisplayDeclaration()),
                        doubleQuote(d.getDeclaration()),
                        doubleQuote(d.getReturnType())) + RPAREN)
                .collect(Collectors.joining(LIST_SEP));
        sb.append(s);

        sb.append(")");
        return sb.toString();
    }

    private String toSimpleName(final String name) {
        final int i = name.lastIndexOf("$");
        if (i > 0) {
            return name.substring(i + 1);
        }
        return name;
    }

    @Override
    public String parse(boolean result) {
        if (result) {
            return LPAREN + "success" + RPAREN;
        }
        return LPAREN + "error" + RPAREN;
    }

    @Override
    public String addImport(boolean result) {
        if (result) {
            return LPAREN + "success" + RPAREN;
        }
        return LPAREN + "error" + RPAREN;
    }

    @Override
    public String optimizeImport(List<String> result) {
        StringBuilder sb = new StringBuilder(LPAREN);

        final String collect = result.stream()
                .map(this::doubleQuote)
                .collect(Collectors.joining(LIST_SEP));
        sb.append(collect);

        sb.append(RPAREN);
        return sb.toString();
    }

    @Override
    public String importAll(Map<String, List<String>> result) {
        StringBuilder sb = new StringBuilder();
        sb.append(LPAREN);

        final String str = result.values()
                .stream()
                .map(strings -> {
                    if (strings != null && strings.size() > 0) {
                        return LPAREN
                                + strings.stream()
                                .map(this::doubleQuote)
                                .collect(Collectors.joining(LIST_SEP))
                                + RPAREN;
                    }
                    return null;
                })
                .filter(s -> s != null)
                .collect(Collectors.joining(LIST_SEP));
        sb.append(str);

        sb.append(RPAREN);
        return sb.toString();
    }

    @Override
    public String switchTest(String openPath) {
        return doubleQuote(openPath);
    }

    @Override
    public String jumpDeclaration(Location loc) {
        return LPAREN
                + String.join(LIST_SEP,
                doubleQuote(loc.getPath()),
                Integer.toString(loc.getLine()),
                Integer.toString(loc.getColumn()))
                + RPAREN;
    }

    @Override
    public String clearCache(boolean result) {
        return Boolean.toString(result);
    }

    @Override
    public String localVariable(LocalVariable lv) {
        StringBuilder sb = new StringBuilder();
        sb.append(LPAREN);
        sb.append(this.doubleQuote(lv.getReturnType()));
        if (!lv.getCandidates().isEmpty()) {
            sb.append(LIST_SEP);
            sb.append(LPAREN);

            final String values = String.join(LIST_SEP,
                    lv.getCandidates()
                            .stream()
                            .map(this::doubleQuote)
                            .collect(Collectors.toList()));

            sb.append(values);
            sb.append(RPAREN);
        }
        sb.append(RPAREN);

        return sb.toString();
    }
}
