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
import java.util.stream.Collectors;
import meghanada.analyze.Source;
import meghanada.project.Project;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JavaImportCompletion {

  private static final Logger log = LogManager.getLogger(JavaImportCompletion.class);

  private final List<SearchFunction> functions;
  private Project project;

  public JavaImportCompletion(Project project) {
    this.project = project;
    this.functions = this.createFunctions();
  }

  private List<SearchFunction> createFunctions() {
    final List<SearchFunction> list = new ArrayList<>(4);

    // TODO support import static
    // list.add(searchField);
    // list.add(searchMethodCall);

    list.add(this::searchClassOrInterface);
    return list;
  }

  private Optional<Map<String, List<String>>> searchClassOrInterface(
      final Source source, final Integer line, final Integer col, final String symbol) {
    CachedASMReflector reflector = CachedASMReflector.getInstance();
    String packageName = source.getPackageName();
    String fqcn = source.getImportedClassFQCN(symbol, null);
    if (isNull(fqcn)) {
      if (!packageName.isEmpty()) {
        String pkgFQCN = packageName + '.' + symbol;
        List<String> result =
            reflector
                .containsClassIndex(pkgFQCN)
                .map(
                    ci -> {
                      // same package. imported
                      List<String> emptyList = Collections.emptyList();
                      return emptyList;
                    })
                .orElseGet(
                    () ->
                        reflector
                            .searchClasses(symbol, false, false)
                            .stream()
                            .map(ClassIndex::getDeclaration)
                            .collect(Collectors.toList()));
        Map<String, List<String>> m = new HashMap<>(1);
        m.putIfAbsent("class", result);
        return Optional.of(m);
      } else {
        List<String> result =
            reflector
                .searchClasses(symbol, false, false)
                .stream()
                .map(ClassIndex::getDeclaration)
                .collect(Collectors.toList());
        Map<String, List<String>> m = new HashMap<>(1);
        m.putIfAbsent("class", result);
        return Optional.of(m);
      }
    }
    return Optional.empty();
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
    return FileUtils.getSource(project, file)
        .flatMap(src -> execFunctions(src, line, column, symbol));
  }

  public void setProject(Project project) {
    this.project = project;
  }

  @FunctionalInterface
  interface SearchFunction {
    Optional<Map<String, List<String>>> apply(Source s, Integer l, Integer c, String symbol);
  }
}
