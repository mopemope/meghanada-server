package meghanada.server.formatter;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static meghanada.utils.FunctionUtils.wrapIOConsumer;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import meghanada.analyze.CompileResult;
import meghanada.completion.LocalVariable;
import meghanada.docs.declaration.Declaration;
import meghanada.index.SearchResults;
import meghanada.location.Location;
import meghanada.reference.Reference;
import meghanada.reflect.CandidateUnit;
import meghanada.server.OutputFormatter;
import meghanada.typeinfo.TypeInfo;
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

  private static String doubleQuote(@Nullable String s) {
    if (isNull(s)) {
      return QUOTE + QUOTE;
    }
    return QUOTE + s + QUOTE;
  }

  private static String success(@Nullable String s) {
    if (isNull(s)) {
      return LPAREN + SUCCESS + RPAREN;
    }
    return LPAREN + SUCCESS + LIST_SEP + s + RPAREN;
  }

  private static String error(@Nullable String s) {
    if (isNull(s)) {
      return LPAREN + ERROR + RPAREN;
    }
    return LPAREN + ERROR + LIST_SEP + s + RPAREN;
  }

  private static String toSimpleName(String name) {
    int i = name.lastIndexOf('$');
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
    sb.append("error");
    sb.append(LIST_SEP);

    final Map<String, Set<Diagnostic<? extends JavaFileObject>>> res = new HashMap<>();

    list.forEach(
        wrapIOConsumer(
            d -> {
              final JavaFileObject fileObject = d.getSource();
              String key = path;
              if (nonNull(fileObject) && fileObject.getKind().equals(JavaFileObject.Kind.SOURCE)) {
                final URI uri = fileObject.toUri();
                final File file = new File(uri);
                key = file.getCanonicalPath();
              }
              if (res.containsKey(key)) {
                final Set<Diagnostic<? extends JavaFileObject>> set = res.get(key);
                set.add(d);
                res.put(key, set);
              } else {
                final Set<Diagnostic<? extends JavaFileObject>> set = new HashSet<>();
                set.add(d);
                res.put(key, set);
              }
            }));

    sb.append(LPAREN);
    res.forEach(
        (k, v) -> {
          sb.append(LPAREN);
          sb.append(doubleQuote(k));
          sb.append(LIST_SEP);
          sb.append(LPAREN);
          v.forEach(
              d -> {
                sb.append(LPAREN);
                sb.append(
                    String.join(
                        LIST_SEP,
                        Long.toString(d.getLineNumber()),
                        Long.toString(d.getColumnNumber()),
                        doubleQuote(d.getKind().toString()),
                        doubleQuote(d.getMessage(null))));
                sb.append(RPAREN);
                sb.append(LIST_SEP);
              });
          sb.append(RPAREN);
          sb.append(RPAREN);
        });
    sb.append(RPAREN);
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
                            doubleQuote(d.getReturnType()),
                            doubleQuote(d.getExtra()))
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
            .filter(strings -> nonNull(strings) && strings.size() > 0)
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
                Long.toString(loc.getLine()),
                Long.toString(loc.getColumn()))
            + RPAREN;
    return success(result);
  }

  @Override
  public String clearCache(final long id, final boolean result) {
    return success(doubleQuote(Boolean.toString(result)));
  }

  @Override
  public String ping(final long id, final String ping) {
    return success(doubleQuote(ping));
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

  @Override
  public String references(long id, List<Reference> references) {
    StringBuilder sb = new StringBuilder(1024);
    sb.append(LPAREN);
    references.forEach(
        r -> {
          String s = r.getPath() + ':' + r.getLine() + ':' + r.getCode();
          sb.append(doubleQuote(s));
          sb.append(LIST_SEP);
        });
    sb.append(RPAREN);
    return success(sb.toString());
  }

  @Override
  public String typeInfo(long id, TypeInfo typeInfo) {
    StringBuilder sb = new StringBuilder(1024);
    sb.append(LPAREN);

    sb.append(doubleQuote(typeInfo.getFqcn()));

    sb.append(LIST_SEP);

    sb.append(LPAREN);
    typeInfo
        .getHierarchy()
        .forEach(
            c -> {
              sb.append(doubleQuote(c));
              sb.append(LIST_SEP);
            });
    sb.append(RPAREN);

    sb.append(LIST_SEP);

    sb.append(LPAREN);
    typeInfo
        .getInterfaces()
        .forEach(
            c -> {
              sb.append(doubleQuote(c));
              sb.append(LIST_SEP);
            });
    sb.append(RPAREN);

    sb.append(LIST_SEP);

    sb.append(LPAREN);
    typeInfo
        .getMembers()
        .forEach(
            c -> {
              sb.append(doubleQuote(c));
              sb.append(LIST_SEP);
            });
    sb.append(RPAREN);

    sb.append(RPAREN);
    return success(sb.toString());
  }

  @Override
  public String killRunningProcess(long id) {
    return success(LPAREN + "success" + RPAREN);
  }

  @Override
  public String searchEverywhere(long id, SearchResults results) {
    final StringBuilder sb = new StringBuilder(8192);
    if (results.size() == 0) {
      sb.append(LPAREN);
      sb.append(RPAREN);
      return success(sb.toString());
    }

    sb.append(LPAREN);

    sb.append(LIST_SEP);
    {
      sb.append(LPAREN);
      results.classes.forEach(
          r -> {
            sb.append(doubleQuote(r.toString()));
            sb.append(LIST_SEP);
          });
      sb.append(RPAREN);
    }

    sb.append(LIST_SEP);
    {
      sb.append(LPAREN);
      results.methods.forEach(
          r -> {
            sb.append(doubleQuote(r.toString()));
            sb.append(LIST_SEP);
          });
      sb.append(RPAREN);
    }

    sb.append(LIST_SEP);
    {
      sb.append(LPAREN);
      results.symbols.forEach(
          r -> {
            sb.append(doubleQuote(r.toString()));
            sb.append(LIST_SEP);
          });
      sb.append(RPAREN);
    }

    sb.append(LIST_SEP);
    {
      sb.append(LPAREN);
      results.usages.forEach(
          r -> {
            sb.append(doubleQuote(r.toString()));
            sb.append(LIST_SEP);
          });
      sb.append(RPAREN);
    }

    sb.append(LIST_SEP);
    {
      sb.append(LPAREN);
      results.codes.forEach(
          r -> {
            sb.append(doubleQuote(r.toString()));
            sb.append(LIST_SEP);
          });
      sb.append(RPAREN);
    }

    sb.append(RPAREN);
    return success(sb.toString());
  }

  @Override
  public String showProject(long id, String s) {
    return success(doubleQuote(s));
  }

  @Override
  public String completionResolve(long id, boolean b) {
    return success(doubleQuote(Boolean.toString(b)));
  }

  @Override
  public String importAtPoint(long id, Map<String, List<String>> result) {
    final StringBuilder sb = new StringBuilder(128);
    sb.append(LPAREN);

    final String str =
        result
            .entrySet()
            .stream()
            .filter(entry -> nonNull(entry.getValue()) && entry.getValue().size() > 0)
            .map(
                entry ->
                    LPAREN
                        + entry.getKey()
                        + LIST_SEP
                        + entry
                            .getValue()
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
}
