package meghanada.typeinfo;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static meghanada.utils.FileUtils.getSource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import meghanada.analyze.ClassScope;
import meghanada.analyze.Source;
import meghanada.project.Project;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.telemetry.TelemetryUtils;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TypeInfoSearcher {

  private static final Logger log = LogManager.getLogger(TypeInfoSearcher.class);
  private final Supplier<Project> projectSupplier;

  public TypeInfoSearcher(Supplier<Project> supplier) {
    this.projectSupplier = supplier;
  }

  @SuppressWarnings("try")
  private static List<MemberDescriptor> getMembers(String fqcn, String clsName) {

    try (TelemetryUtils.ScopedSpan ss =
        TelemetryUtils.startScopedSpan("TypeInfoSearcher.createTypeInfo")) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("fqcn", fqcn)
              .put("className", clsName)
              .build("args"));

      List<MemberDescriptor> results = new ArrayList<>(32);
      CachedASMReflector reflector = CachedASMReflector.getInstance();
      for (MemberDescriptor md : reflector.reflect(fqcn)) {
        CandidateUnit.MemberType type = md.getMemberType();
        String nm = ClassNameUtils.getSimpleName(md.getDeclaringClass());
        boolean own = clsName.equals(nm);
        if (type == CandidateUnit.MemberType.CONSTRUCTOR) {
          if (own) {
            results.add(md);
          }
        } else {
          if (md.isPrivate()) {
            if (own) {
              results.add(md);
            }
          } else {
            results.add(md);
          }
        }
      }
      return results;
    }
  }

  @SuppressWarnings("try")
  private static TypeInfo createTypeInfo(String fqcn, String clsName) {

    try (TelemetryUtils.ScopedSpan ss =
        TelemetryUtils.startScopedSpan("TypeInfoSearcher.createTypeInfo")) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("fqcn", fqcn)
              .put("className", clsName)
              .build("args"));

      CachedASMReflector reflector = CachedASMReflector.getInstance();
      List<String> interfaces = new ArrayList<>(1);
      Collection<String> superClass = reflector.getSuperClass(fqcn);
      List<String> hierarchy = new ArrayList<>(superClass);
      Collections.reverse(hierarchy);

      List<String> res = new ArrayList<>(hierarchy.size() + 1);
      final Map<String, MemberDescriptor> members = new HashMap<>(32);

      for (String cls : hierarchy) {
        final String className = ClassNameUtils.removeTypeParameter(cls);
        final String name = StringUtils.replace(cls, "%%", "");

        reflector
            .containsClassIndex(className)
            .ifPresent(
                ci -> {
                  if (ci.isInterface()) {
                    interfaces.add(name);
                  } else {
                    res.add(name);
                  }
                  for (MemberDescriptor md : getMembers(fqcn, clsName)) {
                    members.put(md.getDisplayDeclaration(), md);
                  }
                });
      }

      res.add(fqcn);
      TypeInfo ti = new TypeInfo(fqcn, res, interfaces);
      List<MemberDescriptor> ms = new ArrayList<>(members.values());
      for (MemberDescriptor md : ms) {
        String desc = md.getDeclaration();
        ti.addMember(md.name, desc.trim());
      }
      return ti;
    }
  }

  private static TypeInfo createTypeInfo(String fqcn) {
    CachedASMReflector reflector = CachedASMReflector.getInstance();
    final String clsName = ClassNameUtils.getSimpleName(fqcn);
    Optional<TypeInfo> typeInfo =
        reflector.containsClassIndex(fqcn).map(index -> createTypeInfo(fqcn, clsName));
    return typeInfo.orElse(new TypeInfo(fqcn));
  }

  @SuppressWarnings("try")
  private static Optional<TypeInfo> searchClass(
      Source source, int line, int column, String symbol) {

    try (TelemetryUtils.ScopedSpan ss =
        TelemetryUtils.startScopedSpan("TypeInfoSearcher.createTypeInfo")) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("source", source.getFile().getPath())
              .put("line", line)
              .put("column", column)
              .build("args"));

      String defaultFqcn = source.getFQCN();

      for (ClassScope scope : source.getClassScopes()) {
        Optional<TypeInfo> typeInfo =
            searchClass(scope, line).map(cs -> createTypeInfo(cs.getFQCN()));
        if (typeInfo.isPresent()) {
          return typeInfo;
        }
      }

      if (nonNull(defaultFqcn)) {
        return Optional.of(createTypeInfo(defaultFqcn));
      }
      return Optional.empty();
    }
  }

  private static Optional<ClassScope> searchClass(ClassScope parent, int line) {
    searchClass:
    while (true) {
      long begin = parent.range.begin.line;
      long end = parent.range.end.line;
      if (begin <= line && line <= end) {
        // find
        if (parent.classScopes.isEmpty()) {
          return Optional.of(parent);
        }
        for (ClassScope classScope : parent.classScopes) {
          parent = classScope;
          continue searchClass;
        }
        return Optional.empty();
      }
      return Optional.empty();
    }
  }

  @SuppressWarnings("try")
  private static Optional<String> searchClassCondition(
      Source source, int line, int column, String symbol) {

    try (TelemetryUtils.ScopedSpan ss = TelemetryUtils.startScopedSpan("TypeInfoSearcher.search")) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("source", source.getFile().getPath())
              .put("line", line)
              .put("column", column)
              .build("args"));

      final CachedASMReflector reflector = CachedASMReflector.getInstance();

      Optional<String> result;
      String fqcn = source.getImportedClassFQCN(symbol, null);

      if (isNull(fqcn)) {
        if (!source.getPackageName().isEmpty() && !symbol.isEmpty()) {
          fqcn = source.getPackageName() + '.' + symbol;
          result =
              reflector
                  .containsClassIndex(fqcn)
                  .map(
                      index -> {
                        return Optional.of(index.getDeclaration());
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
                            return Optional.of(returnType);
                          }
                        }
                        return Optional.empty();
                      });

        } else {
          result = Optional.empty();
        }
      } else {
        result = Optional.of(fqcn);
      }
      return result;
    }
  }

  @SuppressWarnings("try")
  public static Optional<TypeInfo> search(File file, int line, int column, String symbol)
      throws ExecutionException, IOException {

    try (TelemetryUtils.ScopedSpan ss = TelemetryUtils.startScopedSpan("TypeInfoSearcher.search")) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("file", file.getPath())
              .put("line", line)
              .put("column", column)
              .build("args"));

      return getSource(file)
          .flatMap(
              src -> {
                Optional<String> cond = searchClassCondition(src, line, column, symbol);
                if (cond.isPresent()) {
                  String fqcn = cond.get();
                  return Optional.of(createTypeInfo(fqcn));
                }
                return searchClass(src, line, column, symbol);
              });
    }
  }
}
