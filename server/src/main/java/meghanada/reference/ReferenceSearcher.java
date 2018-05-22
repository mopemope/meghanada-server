package meghanada.reference;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static meghanada.utils.FileUtils.getSource;

import com.google.common.base.MoreObjects;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import meghanada.analyze.BlockScope;
import meghanada.analyze.ClassScope;
import meghanada.analyze.FieldAccess;
import meghanada.analyze.MethodCall;
import meghanada.analyze.MethodScope;
import meghanada.analyze.Position;
import meghanada.analyze.Range;
import meghanada.analyze.Source;
import meghanada.analyze.Variable;
import meghanada.project.Project;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.MethodDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.store.ProjectDatabaseHelper;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

public class ReferenceSearcher {

  private static final Logger log = LogManager.getLogger(ReferenceSearcher.class);
  private final List<SearchFunction> functions;
  private Project project;

  public ReferenceSearcher(Project project) {
    this.project = project;
    this.functions = getSearchFunctions();
  }

  private static Optional<SearchCondition> searchMemberCondition(
      Source source, int line, int col, String symbol) {

    EntryMessage msg = log.traceEntry("line={} col={} symbol={}", line, col, symbol);

    for (ClassScope classScope : source.getClassScopes()) {
      for (Variable variable : classScope.getVariables()) {
        if (variable.isField) {
          Position pos = variable.range.begin;
          String name = variable.name;
          if (pos.line == line && name.equals(symbol)) {
            String clazz = classScope.getFQCN();
            SearchCondition condition =
                new SearchCondition(clazz, name, SearchCondition.Type.FIELD);
            return Optional.of(condition);
          }
        }
      }

      for (BlockScope blockScope : classScope.getScopes()) {
        if (blockScope instanceof MethodScope) {
          MethodScope ms = ((MethodScope) blockScope);
          Position pos = ms.getNameRange().begin;
          String name = ms.getName();
          if (pos.line == line && name.equals(symbol)) {
            String clazz = classScope.getFQCN();
            SearchCondition condition =
                new SearchCondition(
                    clazz, name, SearchCondition.Type.METHOD, ms.getParameters(), ms.vararg);
            return Optional.of(condition);
          }
        }
      }
    }

    log.traceExit(msg);
    return Optional.empty();
  }

  private static Optional<SearchCondition> searchFieldAccessCondition(
      Source source, int line, int col, String symbol) {

    EntryMessage msg = log.traceEntry("line={} col={} symbol={}", line, col, symbol);
    Optional<SearchCondition> result =
        source
            .searchFieldAccess(line, col, symbol)
            .map(fa -> new SearchCondition(fa.declaringClass, fa.name, SearchCondition.Type.FIELD));
    log.traceExit(msg);
    return result;
  }

  private static Optional<MemberDescriptor> searchMatchConstructor(
      String declaringClass, List<String> arguments) {
    CachedASMReflector reflector = CachedASMReflector.getInstance();
    for (MemberDescriptor desc : reflector.reflectConstructors(declaringClass)) {
      MethodDescriptor mDesc = (MethodDescriptor) desc;
      if (ClassNameUtils.compareArgumentType(arguments, desc.getParameters(), mDesc.hasVarargs)) {
        return Optional.of(desc);
      }
    }
    return Optional.empty();
  }

  private static Optional<MemberDescriptor> searchMatchMethod(
      String declaringClass, String methodName, List<String> arguments) {
    CachedASMReflector reflector = CachedASMReflector.getInstance();
    for (MemberDescriptor desc : reflector.reflectMethods(declaringClass, methodName)) {
      MethodDescriptor mDesc = (MethodDescriptor) desc;
      if (ClassNameUtils.compareArgumentType(arguments, desc.getParameters(), mDesc.hasVarargs)) {
        return Optional.of(desc);
      }
    }
    return Optional.empty();
  }

  private static Optional<SearchCondition> searchMethodCallCondition(
      Source source, int line, int col, String symbol) {

    EntryMessage msg = log.traceEntry("line={} col={} symbol={}", line, col, symbol);
    Optional<MethodCall> mcResult = source.getMethodCall(line, col, true);
    Optional<SearchCondition> result =
        mcResult.map(
            mc -> {
              String methodName = mc.name;
              List<String> arguments = mc.getArguments();
              String declaringClass = mc.declaringClass;
              if (isNull(declaringClass)) {
                return null;
              }
              MemberDescriptor method =
                  searchMatchMethod(declaringClass, methodName, arguments)
                      .orElseGet(
                          () -> searchMatchConstructor(declaringClass, arguments).orElse(null));
              if (isNull(method)) {
                // not match
                return null;
              }
              MethodDescriptor md = (MethodDescriptor) method;
              if (md.getMemberType().equals(CandidateUnit.MemberType.CONSTRUCTOR)) {
                return new SearchCondition(
                    mc.declaringClass,
                    mc.name,
                    SearchCondition.Type.CONSTRUCTOR,
                    mc.getArguments(),
                    md.hasVarargs);
              }
              return new SearchCondition(
                  mc.declaringClass,
                  mc.name,
                  SearchCondition.Type.METHOD,
                  mc.getArguments(),
                  md.hasVarargs);
            });
    log.traceExit(msg);
    return result;
  }

  private static List<SearchFunction> getSearchFunctions() {
    List<SearchFunction> list = new ArrayList<>(3);
    list.add(ReferenceSearcher::searchMemberCondition);
    list.add(ReferenceSearcher::searchFieldAccessCondition);
    list.add(ReferenceSearcher::searchMethodCallCondition);
    list.add(ReferenceSearcher::searchClassCondition);
    return list;
  }

  private static Optional<SearchCondition> searchClassCondition(
      Source source, int line, int col, String symbol) {
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    final EntryMessage entryMessage = log.traceEntry("line={} col={} symbol={}", line, col, symbol);
    Optional<SearchCondition> result;
    String fqcn = source.getImportedClassFQCN(symbol, null);
    if (isNull(fqcn)) {
      if (!source.getPackageName().isEmpty()) {
        fqcn = source.getPackageName() + '.' + symbol;
        result =
            reflector
                .containsClassIndex(fqcn)
                .map(
                    index -> {
                      SearchCondition sc =
                          new SearchCondition(
                              index.getRawDeclaration(),
                              index.getName(),
                              SearchCondition.Type.CLASS);
                      return Optional.of(sc);
                    })
                .orElseGet(
                    () -> {
                      final Set<String> parents = new HashSet<>(8);
                      for (final ClassScope classScope : source.getClassScopes()) {
                        final String className = classScope.getFQCN();
                        parents.add(className);
                      }
                      parents.addAll(source.importClasses);

                      for (final ClassIndex index : reflector.searchInnerClasses(parents)) {
                        final String returnType = index.getReturnType();
                        if (returnType.endsWith(symbol)) {
                          SearchCondition sc =
                              new SearchCondition(
                                  index.getRawDeclaration(),
                                  index.getName(),
                                  SearchCondition.Type.CLASS);
                          return Optional.of(sc);
                        }
                      }
                      return Optional.empty();
                    });
      } else {
        result = Optional.empty();
      }
    } else {
      SearchCondition sc =
          new SearchCondition(fqcn, ClassNameUtils.getSimpleName(fqcn), SearchCondition.Type.CLASS);
      result = Optional.of(sc);
    }
    log.traceExit(entryMessage);
    return result;
  }

  private static List<Reference> searchClassReferences(Source src, SearchCondition sc)
      throws IOException {
    List<String> lines = FileUtils.readLines(src.getFile());
    int size = lines.size();
    List<Reference> result = new ArrayList<>(size);

    Collection<MethodCall> methodCalls = src.getMethodCalls();
    for (MethodCall mc : methodCalls) {
      String declaringClass = mc.declaringClass;
      if (sc.declaringClass.equals(declaringClass) && mc.scope.contains(sc.name)) {
        Range range = mc.nameRange;
        long line = range.begin.line;
        long column = range.begin.column;
        String code = StringEscapeUtils.escapeJava(lines.get((int) line - 1));
        Reference ref = new Reference(src.filePath, line, column, code);
        result.add(ref);
      }
    }
    Collection<FieldAccess> fieldAccesses = src.getFieldAccesses();
    for (FieldAccess fa : fieldAccesses) {
      String declaringClass = fa.declaringClass;
      if (sc.declaringClass.equals(declaringClass) && fa.scope.contains(sc.name)) {
        Range range = fa.range;
        long line = range.begin.line;
        long column = range.begin.column;
        String code = StringEscapeUtils.escapeJava(lines.get((int) line - 1));
        Reference ref = new Reference(src.filePath, line, column, code);
        result.add(ref);
      }
    }

    return result;
  }

  private static List<Reference> searchMethodCallReferences(Source src, SearchCondition sc)
      throws IOException {
    List<String> lines = FileUtils.readLines(src.getFile());
    int size = lines.size();
    List<Reference> result = new ArrayList<>(size);
    Collection<MethodCall> methodCalls = src.getMethodCalls();
    for (MethodCall mc : methodCalls) {
      String declaringClass = mc.declaringClass;
      String methodName = mc.name;

      boolean compare =
          ClassNameUtils.compareArgumentType(mc.getArguments(), sc.arguments, sc.varargs);
      if (compare && sc.declaringClass.equals(declaringClass) && sc.name.equals(methodName)) {
        Range range = mc.nameRange;
        long line = range.begin.line;
        long column = range.begin.column;
        String code = StringEscapeUtils.escapeJava(lines.get((int) line - 1));
        Reference ref = new Reference(src.filePath, line, column, code);
        result.add(ref);
      }
    }
    return result;
  }

  private static List<Reference> searchFieldReferences(Source src, SearchCondition sc)
      throws IOException {
    List<String> lines = FileUtils.readLines(src.getFile());
    int size = lines.size();
    List<Reference> result = new ArrayList<>(size);

    Collection<FieldAccess> fieldAccesses = src.getFieldAccesses();
    for (FieldAccess fa : fieldAccesses) {
      String declaringClass = fa.declaringClass;
      String methodName = fa.name;
      if (sc.declaringClass.equals(declaringClass) && sc.name.equals(methodName)) {
        Range range = fa.range;
        long line = range.begin.line;
        long column = range.begin.column;
        String code = StringEscapeUtils.escapeJava(lines.get((int) line - 1));
        Reference ref = new Reference(src.filePath, line, column, code);
        result.add(ref);
      }
    }
    return result;
  }

  private static Comparator<? super Reference> comparing() {

    return (r1, r2) -> {
      String path1 = r1.getPath();
      String path2 = r2.getPath();

      int compareTo = path1.compareTo(path2);
      if (compareTo == 0) {
        Long line1 = r1.getLine();
        Long line2 = r2.getLine();
        int compareTo1 = line1.compareTo(line2);
        if (compareTo1 == 0) {
          Long column1 = r1.getColumn();
          Long column2 = r2.getColumn();
          return column1.compareTo(column2);
        }
        return compareTo1;
      }
      return compareTo;
    };
  }

  private static List<Reference> searchReferenceFromSource(Source src, SearchCondition sc)
      throws IOException {

    if (sc.type.equals(SearchCondition.Type.CLASS)) {
      return ReferenceSearcher.searchClassReferences(src, sc);
    }

    if (sc.type.equals(SearchCondition.Type.FIELD)) {
      return ReferenceSearcher.searchFieldReferences(src, sc);
    }

    if (sc.type.equals(SearchCondition.Type.CONSTRUCTOR)) {
      return ReferenceSearcher.searchMethodCallReferences(src, sc);
    }

    if (sc.type.equals(SearchCondition.Type.METHOD)) {
      return ReferenceSearcher.searchMethodCallReferences(src, sc);
    }

    return Collections.emptyList();
  }

  private static List<Reference> searchReferences(SearchCondition sc) {
    List<Source> sources = ProjectDatabaseHelper.getAllSources();
    return sources
        .parallelStream()
        .map(
            src -> {
              try {
                return searchReferenceFromSource(src, sc);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            })
        .flatMap(Collection::stream)
        .distinct()
        .sorted(comparing())
        .collect(Collectors.toList());
  }

  private Optional<SearchCondition> getSearchCondition(
      Source src, int line, int column, String symbol) {
    for (SearchFunction f : this.functions) {
      Optional<SearchCondition> result = f.apply(src, line, column, symbol);
      if (result.isPresent()) {
        return result;
      }
    }
    return Optional.empty();
  }

  public List<Reference> searchReference(File file, int line, int column, String symbol)
      throws ExecutionException, IOException {

    if (!file.exists()) {
      return Collections.emptyList();
    }
    log.trace("search symbol={}", symbol);

    Optional<SearchCondition> cond =
        getSource(this.project, file).flatMap(src -> getSearchCondition(src, line, column, symbol));

    if (!cond.isPresent()) {
      return Collections.emptyList();
    }

    if (cond.isPresent()) {
      SearchCondition sc = cond.get();
      List<Reference> list = ReferenceSearcher.searchReferences(sc);
      if (nonNull(list)) {
        return list;
      }
    }
    return Collections.emptyList();
  }

  @FunctionalInterface
  interface SearchFunction {
    Optional<SearchCondition> apply(Source javaSource, Integer line, Integer column, String symbol);
  }

  private static class SearchCondition {
    final String declaringClass;
    final String name;
    final Type type;
    final List<String> arguments;
    final boolean varargs;

    SearchCondition(
        String declaringClass, String name, Type type, List<String> arguments, boolean varargs) {
      this.declaringClass = declaringClass;
      this.name = name;
      this.type = type;
      this.arguments = arguments;
      this.varargs = varargs;
    }

    SearchCondition(String declaringClass, String name, Type type) {
      this(declaringClass, name, type, Collections.emptyList(), false);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("declaringClass", declaringClass)
          .add("name", name)
          .add("type", type)
          .toString();
    }

    enum Type {
      FIELD,
      METHOD,
      CONSTRUCTOR,
      CLASS,
      VAR,
      OTHER
    }
  }
}
