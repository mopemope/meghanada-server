package meghanada.server.formatter;

import static meghanada.utils.FunctionUtils.wrapIO;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import meghanada.analyze.CompileResult;
import meghanada.completion.LocalVariable;
import meghanada.docs.declaration.Declaration;
import meghanada.location.Location;
import meghanada.reflect.CandidateUnit;
import meghanada.server.OutputFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SexpOutputFormatter implements OutputFormatter {

  private static final Logger log = LogManager.getLogger(SexpOutputFormatter.class);

  private static final String LPAREN = "(";
  private static final String RPAREN = ")";
  private static final String LIST_SEP = " ";
  private static final String QUOTE = "\"";
  private static final String SUCCESS = "success";
  private static final String ERROR = "error";

  private static String doubleQuote(final String s) {
    if (s == null) {
      return QUOTE + QUOTE;
    }
    return QUOTE + s + QUOTE;
  }

  private static String success(final String s) {
    if (s == null) {
      return LPAREN + SUCCESS + RPAREN;
    }
    return LPAREN + SUCCESS + LIST_SEP + s + RPAREN;
  }

  private static String error(final String s) {
    if (s == null) {
      return LPAREN + ERROR + RPAREN;
    }
    return LPAREN + ERROR + LIST_SEP + s + RPAREN;
  }

  private static String toSimpleName(final String name) {
    final int i = name.lastIndexOf('$');
    if (i > 0) {
      return name.substring(i + 1);
    }
    return name;
  }

  @Override
  public String changeProject(final long id, final boolean result) {
    if (result) {
      return success(LPAREN + "success" + RPAREN);
    }
    return success(LPAREN + "error" + RPAREN);
  }

  @Override
  public String compile(final long id, CompileResult compileResult, String path) {

    if (compileResult.isSuccess() && !compileResult.hasDiagnostics()) {
      return success(LPAREN + "success " + doubleQuote(path) + RPAREN);
    }
    return success(LPAREN + "error " + doubleQuote(compileResult.getDiagnosticsSummary()) + RPAREN);
  }

  @Override
  public String compileProject(final long id, CompileResult compileResult) {
    if (compileResult.isSuccess() && !compileResult.hasDiagnostics()) {
      return success(LPAREN + "success true" + RPAREN);
    }
    return success(LPAREN + "error " + doubleQuote(compileResult.getDiagnosticsSummary()) + RPAREN);
  }

  @Override
  public String diagnostics(final long id, final CompileResult compileResult, final String path) {
    if (compileResult.isSuccess() && !compileResult.hasDiagnostics()) {
      return success(LPAREN + "success" + RPAREN);
    }
    final List<Diagnostic<? extends JavaFileObject>> list = compileResult.getDiagnostics();
    final StringBuilder sb = new StringBuilder(256);
    sb.append(LPAREN);
    sb.append("error ");
    final String s =
        list.stream()
            .map(
                wrapIO(
                    d -> {
                      final URI uri = d.getSource().toUri();
                      final File file = new File(uri);
                      final String buffer = file.getCanonicalPath();
                      return LPAREN
                          + String.join(
                              LIST_SEP,
                              Long.toString(d.getLineNumber()),
                              Long.toString(d.getColumnNumber()),
                              doubleQuote(d.getKind().toString()),
                              doubleQuote(d.getMessage(null)),
                              doubleQuote(buffer))
                          + RPAREN;
                    }))
            .collect(Collectors.joining(LIST_SEP));
    sb.append(s);
    sb.append(RPAREN);
    return success(sb.toString());
  }

  @Override
  public String autocomplete(final long id, Collection<? extends CandidateUnit> units) {
    final StringBuilder sb = new StringBuilder(LPAREN);

    final String s =
        units
            .stream()
            .map(
                d ->
                    LPAREN
                        + String.join(
                            LIST_SEP,
                            doubleQuote(d.getType()),
                            doubleQuote(toSimpleName(d.getName())),
                            doubleQuote(d.getDisplayDeclaration()),
                            doubleQuote(d.getDeclaration()),
                            doubleQuote(d.getReturnType()))
                        + RPAREN)
            .collect(Collectors.joining(LIST_SEP));
    sb.append(s);
    sb.append(')');
    return success(sb.toString());
  }

  @Override
  public String parse(final long id, boolean result) {
    if (result) {
      return success(LPAREN + "success" + RPAREN);
    }
    return success(LPAREN + "error" + RPAREN);
  }

  @Override
  public String addImport(final long id, boolean result, final String fqcn) {
    if (result) {
      return success(LPAREN + "success " + doubleQuote(fqcn) + RPAREN);
    }
    return success(LPAREN + "error" + RPAREN);
  }

  @Override
  public String optimizeImport(final long id, final String path) {
    return success(doubleQuote(path));
  }

  @Override
  public String importAll(final long id, final Map<String, List<String>> result) {
    final StringBuilder sb = new StringBuilder(128);
    sb.append(LPAREN);

    final String str =
        result
            .values()
            .stream()
            .filter(strings -> strings != null && strings.size() > 0)
            .map(
                strings ->
                    LPAREN
                        + strings
                            .stream()
                            .map(SexpOutputFormatter::doubleQuote)
                            .collect(Collectors.joining(LIST_SEP))
                        + RPAREN)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(LIST_SEP));
    sb.append(str);
    sb.append(RPAREN);
    return success(sb.toString());
  }

  @Override
  public String switchTest(final long id, final String openPath) {
    return success(doubleQuote(openPath));
  }

  @Override
  public String jumpDeclaration(final long id, final Location loc) {
    final String result =
        LPAREN
            + String.join(
                LIST_SEP,
                doubleQuote(loc.getPath()),
                Integer.toString(loc.getLine()),
                Integer.toString(loc.getColumn()))
            + RPAREN;
    return success(result);
  }

  @Override
  public String clearCache(final long id, final boolean result) {
    return Boolean.toString(result);
  }

  @Override
  public String localVariable(final long id, final LocalVariable lv) {
    final StringBuilder sb = new StringBuilder(1024);
    sb.append(LPAREN);
    sb.append(SexpOutputFormatter.doubleQuote(lv.getReturnType()));
    if (!lv.getCandidates().isEmpty()) {
      sb.append(LIST_SEP);
      sb.append(LPAREN);

      final String values =
          String.join(
              LIST_SEP,
              lv.getCandidates()
                  .stream()
                  .map(SexpOutputFormatter::doubleQuote)
                  .collect(Collectors.toList()));

      sb.append(values);
      sb.append(RPAREN);
    }
    sb.append(RPAREN);

    return success(sb.toString());
  }

  @Override
  public String formatCode(final long id, final String path) {
    return success(doubleQuote(path));
  }

  @Override
  public String showDeclaration(final long id, final Declaration declaration) {
    final String result =
        LPAREN
            + String.join(
                LIST_SEP,
                declaration.type.name().toLowerCase(),
                doubleQuote(declaration.scopeInfo),
                doubleQuote(declaration.signature),
                Integer.toString(declaration.argumentIndex))
            + RPAREN;
    return success(result);
  }

  @Override
  public String error(final long id, final Throwable t) {
    return error(t.getMessage());
  }
}
