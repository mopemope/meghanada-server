package meghanada.completion;

import static java.util.Objects.isNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import meghanada.analyze.MethodCall;
import meghanada.analyze.Source;
import meghanada.index.IndexDatabase;
import meghanada.project.Project;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.telemetry.TelemetryUtils;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JavaImportCompletion {

  private static final Logger log = LogManager.getLogger(JavaImportCompletion.class);

  private final List<SearchFunction> functions;

  public JavaImportCompletion(Supplier<Project> supplier) {
    this.functions = JavaImportCompletion.createFunctions();
  }

  private static List<SearchFunction> createFunctions() {
    List<SearchFunction> list = new ArrayList<>(4);
    list.add(JavaImportCompletion::searchMethodCall);
    list.add(JavaImportCompletion::searchClassOrInterface);
    list.add(JavaImportCompletion::searchFieldAndMethods);
    return list;
  }

  private static Optional<Map<String, List<String>>> searchFieldAndMethods(
      Source source, Integer line, Integer column, String name) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("JavaCompletion.searchFieldAndMethods")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("source", source.getFile().getPath())
              .put("line", line)
              .put("column", column)
              .put("name", name)
              .build("args"));

      List<String> results =
          IndexDatabase.getInstance()
              .searchMembers(
                  "", IndexDatabase.doubleQuote("public static"), "(\"METHOD\" OR \"FIELD\")", name)
              .stream()
              .filter(d -> d.getName().equals(name))
              .map(d -> d.getDeclaringClass() + "#" + d.getName())
              .distinct()
              .collect(Collectors.toList());
      if (isNull(results) || results.isEmpty()) {
        return Optional.empty();
      }

      Map<String, List<String>> m = new HashMap<>(1);
      m.putIfAbsent("method", results);
      return Optional.of(m);
    }
  }

  private static Optional<Map<String, List<String>>> searchMethodCall(
      Source source, Integer line, Integer column, String symbol) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("JavaCompletion.searchMethodCall")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("source", source.getFile().getPath())
              .put("line", line)
              .put("column", column)
              .put("symbol", symbol)
              .build("args"));

      Optional<MethodCall> methodCall = source.getMethodCall(line, column, true);
      Optional<List<String>> optionalList =
          methodCall.map(
              mc -> {
                String methodName = mc.name;
                List<String> arguments = mc.getArguments();
                return IndexDatabase.getInstance()
                    .searchMembers(
                        "",
                        IndexDatabase.doubleQuote("public static"),
                        IndexDatabase.doubleQuote("METHOD"),
                        methodName)
                    .stream()
                    .filter(
                        d ->
                            d.getName().equals(methodName)
                                && ClassNameUtils.compareArgumentType(
                                    arguments, d.getParameters(), false))
                    .map(d -> d.getDeclaringClass() + "#" + d.getName())
                    .distinct()
                    .collect(Collectors.toList());
              });
      if (optionalList.isPresent()) {
        List<String> results = optionalList.get();
        if (isNull(results) || results.isEmpty()) {
          return Optional.empty();
        }
        Map<String, List<String>> m = new HashMap<>(1);
        m.putIfAbsent("method", results);
        return Optional.of(m);
      }
      return Optional.empty();
    }
  }

  private static Optional<Map<String, List<String>>> searchClassOrInterface(
      final Source source, final Integer line, final Integer column, final String symbol) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("JavaCompletion.searchMethodCall")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("source", source.getFile().getPath())
              .put("line", line)
              .put("column", column)
              .put("symbol", symbol)
              .build("args"));

      CachedASMReflector reflector = CachedASMReflector.getInstance();
      String packageName = source.getPackageName();
      String fqcn = source.getImportedClassFQCN(symbol, null);
      if (isNull(fqcn)) {
        if (!packageName.isEmpty()) {
          String pkgFQCN = packageName + '.' + symbol;
          List<String> results =
              reflector
                  .containsClassIndex(pkgFQCN)
                  .map(
                      ci -> {
                        // same package. imported
                        return Collections.<String>emptyList();
                      })
                  .orElseGet(
                      () ->
                          reflector.searchClasses(symbol, true).stream()
                              .map(ClassIndex::getDeclaration)
                              .collect(Collectors.toList()));
          if (results.isEmpty()) {
            return Optional.empty();
          }
          Map<String, List<String>> m = new HashMap<>(1);
          m.putIfAbsent("class", results);
          return Optional.of(m);
        } else {
          List<String> results =
              reflector.searchClasses(symbol, true).stream()
                  .map(ClassIndex::getDeclaration)
                  .collect(Collectors.toList());
          if (results.isEmpty()) {
            return Optional.empty();
          }
          Map<String, List<String>> m = new HashMap<>(1);
          m.putIfAbsent("class", results);
          return Optional.of(m);
        }
      }
      return Optional.empty();
    }
  }

  private Optional<Map<String, List<String>>> execFunctions(
      final Source src, final Integer line, final Integer column, final String symbol) {
    for (final SearchFunction f : this.functions) {
      Optional<Map<String, List<String>>> result = f.apply(src, line, column, symbol);
      if (result.isPresent()) {
        return result;
      }
    }
    return Optional.empty();
  }

  public Optional<Map<String, List<String>>> importAtPoint(
      final File file, final int line, final int column, final String symbol)
      throws ExecutionException, IOException {
    if (!file.exists()) {
      return Optional.empty();
    }
    return FileUtils.getSource(file).flatMap(src -> execFunctions(src, line, column, symbol));
  }

  @FunctionalInterface
  interface SearchFunction {
    Optional<Map<String, List<String>>> apply(Source s, Integer l, Integer c, String symbol);
  }
}
