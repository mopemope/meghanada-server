package meghanada.docs.declaration;

import static meghanada.utils.FileUtils.getSource;

import com.google.common.base.Joiner;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import meghanada.analyze.ClassScope;
import meghanada.analyze.MethodCall;
import meghanada.analyze.Source;
import meghanada.analyze.Variable;
import meghanada.project.Project;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.MethodDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.telemetry.TelemetryUtils;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DeclarationSearcher {
  private static final Logger log = LogManager.getLogger(DeclarationSearcher.class);
  private final List<DeclarationSearchFunction> functions;
  private final Supplier<Project> projectSupplier;

  public DeclarationSearcher(final Supplier<Project> supplier) {
    this.functions = DeclarationSearcher.getFunctions();
    this.projectSupplier = supplier;
  }

  @SuppressWarnings("try")
  private static Optional<Declaration> searchFieldVar(Source source, Integer line, String symbol) {
    try (TelemetryUtils.ScopedSpan ss =
        TelemetryUtils.startScopedSpan("DeclarationSearcher.searchFieldVar")) {
      return source
          .getTypeScope(line)
          .flatMap(
              ts ->
                  ts.getField(symbol)
                      .map(
                          fv ->
                              new Declaration(
                                  symbol, fv.fqcn, Declaration.Type.VAR, fv.argumentIndex)));
    }
  }

  private static Optional<Declaration> searchLocalVariable(
      Source source, Integer line, Integer column, String symbol) {

    try (TelemetryUtils.ScopedSpan ss =
        TelemetryUtils.startScopedSpan("DeclarationSearcher.searchLocalVariable")) {

      ss.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("source", source.getFile().getPath())
              .put("line", line)
              .put("column", column)
              .build("args"));

      Optional<Variable> variable = source.getVariable(line, column);

      Optional<Declaration> result =
          variable
              .map(
                  var -> {
                    Declaration declaration =
                        new Declaration(symbol, var.fqcn, Declaration.Type.VAR, var.argumentIndex);
                    return Optional.of(declaration);
                  })
              .orElseGet(() -> searchFieldVar(source, line, symbol));
      return result;
    }
  }

  private static List<DeclarationSearchFunction> getFunctions() {
    List<DeclarationSearchFunction> list = new ArrayList<>(4);
    list.add(DeclarationSearcher::searchReserved);
    list.add(DeclarationSearcher::searchField);
    list.add(DeclarationSearcher::searchMethodCall);
    list.add(DeclarationSearcher::searchClassOrInterface);
    list.add(DeclarationSearcher::searchLocalVariable);
    return list;
  }

  private static Optional<Declaration> searchReserved(
      @SuppressWarnings("unused") Source source, Integer line, Integer column, String symbol) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("DeclarationSearcher.searchReserved")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("source", source.getFile().getPath())
              .put("line", line)
              .put("column", column)
              .build("args"));

      Optional<Declaration> result = Optional.empty();
      if (symbol.equals("package")
          || symbol.equals("import")
          || symbol.equals("new")
          || symbol.equals("try")
          || symbol.equals("throw")
          || symbol.equals("finally")
          || symbol.equals("public")
          || symbol.equals("private")
          || symbol.equals("protected")
          || symbol.equals("return")
          || symbol.equals("static")
          || symbol.equals("final")) {
        result = Optional.of(new Declaration(symbol, "", Declaration.Type.OTHER, 0));
      }

      return result;
    }
  }

  private static Optional<Declaration> searchField(
      final Source source, Integer line, Integer column, String symbol) {

    try (TelemetryUtils.ScopedSpan ss =
        TelemetryUtils.startScopedSpan("DeclarationSearcher.searchField")) {

      ss.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("source", source.getFile().getPath())
              .put("line", line)
              .put("column", column)
              .build("args"));

      Optional<Declaration> result =
          source
              .searchFieldAccess(line, column, symbol)
              .map(
                  fa -> {
                    String scope = fa.scope;
                    if (scope != null && !scope.isEmpty()) {
                      scope = scope + '.' + symbol;
                    } else {
                      scope = symbol;
                    }
                    return new Declaration(
                        scope.trim(), fa.returnType, Declaration.Type.FIELD, fa.argumentIndex);
                  });
      return result;
    }
  }

  @SuppressWarnings("try")
  private static Optional<MemberDescriptor> searchMethod(
      String declaringClass, String methodName, List<String> arguments) {

    try (TelemetryUtils.ScopedSpan ss =
        TelemetryUtils.startScopedSpan("DeclarationSearcher.searchMethod")) {

      CachedASMReflector reflector = CachedASMReflector.getInstance();
      for (MemberDescriptor desc : reflector.reflectMethods(declaringClass, methodName)) {
        MethodDescriptor mDesc = (MethodDescriptor) desc;
        if (ClassNameUtils.compareArgumentType(arguments, desc.getParameters(), mDesc.hasVarargs)) {
          return Optional.of(desc);
        }
      }
      return Optional.empty();
    }
  }

  @SuppressWarnings("try")
  private static Optional<MemberDescriptor> searchConstructor(
      String declaringClass, List<String> arguments) {

    try (TelemetryUtils.ScopedSpan ss =
        TelemetryUtils.startScopedSpan("DeclarationSearcher.searchConstructor")) {

      CachedASMReflector reflector = CachedASMReflector.getInstance();
      for (MemberDescriptor desc : reflector.reflectConstructors(declaringClass)) {
        MethodDescriptor mDesc = (MethodDescriptor) desc;
        if (ClassNameUtils.compareArgumentType(arguments, desc.getParameters(), mDesc.hasVarargs)) {
          return Optional.of(desc);
        }
      }
      return Optional.empty();
    }
  }

  private static Optional<Declaration> searchMethodCall(
      Source source, Integer line, Integer column, String symbol) {

    try (TelemetryUtils.ScopedSpan ss =
        TelemetryUtils.startScopedSpan("DeclarationSearcher.searchMethodCall")) {

      ss.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("source", source.getFile().getPath())
              .put("line", line)
              .put("column", column)
              .build("args"));

      Optional<MethodCall> methodCall = source.getMethodCall(line, column, true);

      Optional<Declaration> result =
          methodCall.map(
              mc -> {
                String methodName = mc.name;
                List<String> arguments = mc.getArguments();
                String declaringClass = mc.declaringClass;
                if (declaringClass == null) {
                  return null;
                }
                CachedASMReflector reflector = CachedASMReflector.getInstance();

                MemberDescriptor method =
                    searchMethod(declaringClass, methodName, arguments)
                        .orElseGet(() -> searchConstructor(declaringClass, arguments).orElse(null));
                String declaration;
                if (method != null) {
                  declaration = method.getDeclaration();
                } else {
                  String args = Joiner.on(", ").join(arguments);
                  declaration = mc.returnType + ' ' + methodName + '(' + args + ')';
                }

                String scope = mc.scope;
                if (scope != null && !scope.isEmpty()) {
                  scope = scope + '.' + symbol;
                } else {
                  scope = symbol;
                }
                return new Declaration(
                    scope.trim(), declaration, Declaration.Type.METHOD, mc.argumentIndex);
              });
      return result;
    }
  }

  private static Optional<Declaration> searchClassOrInterface(
      Source source, Integer line, Integer column, String symbol) {

    try (TelemetryUtils.ScopedSpan ss =
        TelemetryUtils.startScopedSpan("DeclarationSearcher.searchClassOrInterface")) {

      ss.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("source", source.getFile().getPath())
              .put("line", line)
              .put("column", column)
              .build("args"));

      // TODO need tune

      CachedASMReflector reflector = CachedASMReflector.getInstance();
      Optional<Declaration> result;
      String fqcn = source.getImportedClassFQCN(symbol, null);
      if (fqcn == null) {
        if (!source.getPackageName().isEmpty()) {
          fqcn = source.getPackageName() + '.' + symbol;
          result =
              reflector
                  .containsClassIndex(fqcn)
                  .map(
                      classIndex -> {
                        Declaration declaration =
                            new Declaration(
                                symbol, classIndex.getReturnType(), Declaration.Type.CLASS, 0);
                        return Optional.of(declaration);
                      })
                  .orElseGet(
                      () -> {
                        Set<String> parents = new HashSet<>(8);
                        for (ClassScope classScope : source.getClassScopes()) {
                          String className = classScope.getFQCN();
                          parents.add(className);
                        }
                        parents.addAll(source.importClasses);

                        for (ClassIndex ci : reflector.searchInnerClasses(parents)) {
                          String returnType = ci.getReturnType();
                          if (returnType.endsWith(symbol)) {
                            Declaration d =
                                new Declaration(symbol, returnType, Declaration.Type.CLASS, 0);
                            return Optional.of(d);
                          }
                        }
                        return Optional.empty();
                      });
        } else {
          result = Optional.empty();
        }
      } else {
        Declaration declaration = new Declaration(symbol, fqcn, Declaration.Type.CLASS, 0);
        result = Optional.of(declaration);
      }
      return result;
    }
  }

  private Optional<Declaration> execFunctions(
      Source src, Integer line, Integer column, String symbol) {

    for (DeclarationSearchFunction f : this.functions) {
      Optional<Declaration> result = f.apply(src, line, column, symbol);
      if (result.isPresent()) {
        return result;
      }
    }

    return Optional.empty();
  }

  public Optional<Declaration> searchDeclaration(File file, int line, int column, String symbol)
      throws ExecutionException, IOException {
    if (!file.exists()) {
      return Optional.empty();
    }
    log.trace("search symbol={}", symbol);
    return getSource(file).flatMap(src -> execFunctions(src, line, column, symbol));
  }

  @FunctionalInterface
  interface DeclarationSearchFunction {

    Optional<Declaration> apply(Source javaSource, Integer line, Integer column, String symbol);
  }
}
