package meghanada.completion;

import com.google.common.cache.LoadingCache;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import meghanada.analyze.AccessSymbol;
import meghanada.analyze.ClassScope;
import meghanada.analyze.Source;
import meghanada.analyze.TypeScope;
import meghanada.analyze.Variable;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.FieldDescriptor;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JavaCompletion {

  private static final Logger log = LogManager.getLogger(JavaCompletion.class);
  private static final String STATIC = "static ";
  private Project project;

  public JavaCompletion(final Project project) {
    this.project = project;
  }

  private static Collection<? extends CandidateUnit> annotationCompletion(
      final Source source, final int line, final int column, final String prefix) {
    final boolean useFuzzySearch = Config.load().useClassFuzzySearch();
    String classPrefix = prefix.substring(1);
    List<ClassIndex> result;
    if (useFuzzySearch) {
      result = CachedASMReflector.getInstance().fuzzySearchAnnotations(classPrefix.toLowerCase());

    } else {
      result = CachedASMReflector.getInstance().searchAnnotations(classPrefix.toLowerCase());
    }
    return result
        .stream()
        .sorted(comparing(prefix))
        .map(
            classIndex -> {
              final String name =
                  ClassNameUtils.getSimpleName(
                      ClassNameUtils.replaceInnerMark(classIndex.getName()));
              classIndex.setName('@' + name);
              return classIndex;
            })
        .collect(Collectors.toList());
  }

  private static List<MemberDescriptor> doReflect(String fqcn) {
    return CachedASMReflector.getInstance().reflect(fqcn);
  }

  private static Collection<? extends CandidateUnit> completionSuper(
      final Source source, final int line, final String prefix) {
    return source
        .getTypeScope(line)
        .map(
            typeScope -> {
              final String fqcn = typeScope.getFQCN();
              return doReflect(fqcn)
                  .stream()
                  .filter(
                      md ->
                          !md.getDeclaringClass().equals(fqcn)
                              && !(!prefix.isEmpty()
                                  && !md.getName().toLowerCase().startsWith(prefix)))
                  .collect(Collectors.toList());
            })
        .orElse(Collections.emptyList());
  }

  private static boolean publicFilter(
      final CandidateUnit cu,
      final boolean isStatic,
      final boolean withCONSTRUCTOR,
      final String target) {

    final String name = cu.getName().toLowerCase();
    if (!target.isEmpty() && !name.contains(target)) {
      return false;
    }

    final String declaration = cu.getDeclaration();
    if (!declaration.contains("public")) {
      return false;
    }
    if (!isStatic) {
      if (withCONSTRUCTOR) {
        return !declaration.contains(STATIC);
      }
      return !declaration.contains(STATIC) && !cu.getType().equals("CONSTRUCTOR");
    }
    return declaration.contains(STATIC);
  }

  private static boolean publicFilter(final CandidateUnit cu, final String target) {

    final String name = cu.getName().toLowerCase();
    if (!target.isEmpty() && !name.contains(target)) {
      return false;
    }
    if (cu.getType().equals("CONSTRUCTOR")) {
      return false;
    }

    final String declaration = cu.getDeclaration();
    return declaration.contains("public");
  }

  private static boolean packageFilter(
      final CandidateUnit cu,
      final boolean isStatic,
      final boolean withCONSTRUCTOR,
      final String target) {
    final String name = cu.getName().toLowerCase();

    if (!target.isEmpty() && !name.contains(target)) {
      return false;
    }
    final String declaration = cu.getDeclaration();
    if (declaration.contains("private")) {
      return false;
    }
    if (!isStatic) {
      if (withCONSTRUCTOR) {
        return !declaration.contains(STATIC);
      }
      return !declaration.contains(STATIC) && !cu.getType().equals("CONSTRUCTOR");
    }
    return declaration.contains(STATIC);
  }

  private static boolean privateFilter(
      final CandidateUnit cu,
      final boolean isStatic,
      final boolean withCONSTRUCTOR,
      final String target) {

    final String name = cu.getName().toLowerCase();
    if (cu.getType().equals("FIELD") && name.startsWith("this$")) {
      return false;
    }

    if (!target.isEmpty() && !name.contains(target)) {
      return false;
    }

    final String declaration = cu.getDeclaration();
    if (!isStatic) {
      if (withCONSTRUCTOR) {
        return !declaration.contains(STATIC);
      }
      return !declaration.contains(STATIC) && !cu.getType().equals("CONSTRUCTOR");
    }
    return declaration.contains(STATIC);
  }

  private static boolean privateFilter(
      final CandidateUnit cu, final boolean withCONSTRUCTOR, final String target) {

    final String name = cu.getName().toLowerCase();
    return !(cu.getType().equals("FIELD") && name.startsWith("this$"))
        && !(!target.isEmpty() && !name.contains(target))
        && (withCONSTRUCTOR || !cu.getType().equals("CONSTRUCTOR"));
  }

  private static Collection<? extends CandidateUnit> publicReflect(
      final String fqcn,
      final boolean isStatic,
      final boolean withCONSTRUCTOR,
      final String target) {
    return doReflect(fqcn)
        .stream()
        .filter(md -> JavaCompletion.publicFilter(md, isStatic, withCONSTRUCTOR, target))
        .collect(Collectors.toSet());
  }

  private static Collection<? extends CandidateUnit> packageReflect(
      final String fqcn,
      final boolean noStatic,
      final boolean withCONSTRUCTOR,
      final String target) {
    return doReflect(fqcn)
        .stream()
        .filter(md -> JavaCompletion.packageFilter(md, noStatic, withCONSTRUCTOR, target))
        .collect(Collectors.toSet());
  }

  private static Collection<? extends CandidateUnit> completionConstructors(final Source source) {
    return source
        .importClasses
        .parallelStream()
        .map(JavaCompletion::doReflect)
        .flatMap(Collection::parallelStream)
        .filter(md -> md.getType().equals(CandidateUnit.MemberType.CONSTRUCTOR.name()))
        .collect(Collectors.toSet());
  }

  private static Collection<? extends CandidateUnit> completionThis(
      final Source source, final int line, final String prefix) {
    return source
        .getTypeScope(line)
        .map(
            typeScope -> {
              final String fqcn = typeScope.getFQCN();
              return JavaCompletion.reflectSelf(fqcn, false, prefix);
            })
        .orElse(Collections.emptyList());
  }

  private static Collection<? extends CandidateUnit> completionSymbols(
      Source source, int line, String prefix) {
    Set<CandidateUnit> result = new HashSet<>(32);

    // prefix search
    log.debug("Search variables prefix:{} line:{}", prefix, line);

    Optional<TypeScope> typeScope = source.getTypeScope(line);
    if (!typeScope.isPresent()) {
      return result;
    }
    String fqcn = typeScope.get().getFQCN();

    // add this member
    for (MemberDescriptor c : JavaCompletion.reflectSelf(fqcn, true, prefix)) {
      if (c.getName().startsWith(prefix)) {
        result.add(c);
      }
    }

    if (fqcn.contains(ClassNameUtils.INNER_MARK)) {
      // add parent
      String parentClass = fqcn;
      while (true) {
        int i = parentClass.lastIndexOf('$');
        if (i < 0) {
          break;
        }
        parentClass = parentClass.substring(0, i);
        for (MemberDescriptor c : JavaCompletion.reflectSelf(parentClass, true, prefix)) {
          if (c.getName().startsWith(prefix)) {
            result.add(c);
          }
        }
      }
    }

    log.debug("self fqcn:{}", fqcn);

    Map<String, Variable> symbols = source.getDeclaratorMap(line);
    log.debug("search variables size:{} result:{}", symbols.size(), symbols);

    for (Map.Entry<String, Variable> e : symbols.entrySet()) {
      String k = e.getKey();
      Variable v = e.getValue();
      log.debug("check variable name:{}", k);
      if (k.startsWith(prefix)) {
        log.debug("match variable name:{}", k);
        if (!v.isField) {
          result.add(v.toCandidateUnit());
        }
      }
    }

    // import
    for (Map.Entry<String, String> e : source.getImportedClassMap().entrySet()) {
      String k = e.getKey();
      String v = e.getValue();
      if (k.startsWith(prefix)) {
        result.add(ClassIndex.createClass(v));
      }
    }

    // Add class
    if (Character.isUpperCase(prefix.charAt(0))) {
      // completion
      CachedASMReflector reflector = CachedASMReflector.getInstance();
      boolean fuzzySearch = Config.load().useClassFuzzySearch();
      if (fuzzySearch) {
        result.addAll(reflector.fuzzySearchClasses(prefix.toLowerCase()));
      } else {
        result.addAll(reflector.searchClasses(prefix.toLowerCase()));
      }
    }

    List<CandidateUnit> list = new ArrayList<>(result);
    list.sort(comparing(source, prefix));
    return list;
  }

  private static Collection<? extends CandidateUnit> reflect(
      final String ownPackage,
      final String fqcn,
      final boolean isStatic,
      final boolean withConstructor,
      final String prefix) {
    if (fqcn.startsWith(ownPackage)) {
      // package
      return JavaCompletion.packageReflect(fqcn, isStatic, withConstructor, prefix);
    }
    return JavaCompletion.publicReflect(fqcn, isStatic, withConstructor, prefix);
  }

  private static Collection<MemberDescriptor> reflectSelf(
      final String fqcn, final boolean withConstructor, final String prefix) {
    final String target = prefix.toLowerCase();
    return doReflect(fqcn)
        .stream()
        .filter(md -> JavaCompletion.privateFilter(md, withConstructor, target))
        .collect(Collectors.toSet());
  }

  private static Collection<MemberDescriptor> reflectWithFQCN(
      final String fqcn, final String prefix) {
    final String target = prefix.toLowerCase();
    return doReflect(fqcn)
        .stream()
        .filter(md -> JavaCompletion.publicFilter(md, target))
        .collect(Collectors.toSet());
  }

  private static Collection<? extends CandidateUnit> reflect(
      final String ownPackage, final String fqcn, final String prefix) {
    return JavaCompletion.reflect(ownPackage, fqcn, false, false, prefix);
  }

  private static Collection<? extends CandidateUnit> completionFieldsOrMethods(
      final Source source, final int line, final String var, final String target) {

    // completionAt methods or fields
    if (var.equals("this")) {
      return JavaCompletion.completionThis(source, line, target);
    }
    if (var.equals("super")) {
      return JavaCompletion.completionSuper(source, line, target);
    }

    log.debug("search '{}' field or method", var);

    String ownPackage = source.getPackageName();
    final Set<CandidateUnit> res = new HashSet<>(32);

    {
      // completion static method
      String fqcn = source.getImportedClassFQCN(var, null);
      if (fqcn != null) {
        if (!fqcn.contains(".") && !ownPackage.isEmpty()) {
          fqcn = ownPackage + '.' + fqcn;
        }

        final Collection<? extends CandidateUnit> result =
            JavaCompletion.reflect(ownPackage, fqcn, true, false, target);
        res.addAll(result);

        // add inner class
        final Collection<? extends CandidateUnit> inners =
            CachedASMReflector.getInstance().searchInnerClasses(fqcn);
        res.addAll(inners);

        if (!res.isEmpty()) {
          return res;
        }
      }
    }

    {
      final Map<String, Variable> symbols = source.getDeclaratorMap(line);
      final Variable variable = symbols.get(var);
      if (variable != null) {
        // get data from reflector
        String fqcn = variable.fqcn;
        if (!fqcn.contains(".")) {
          fqcn = ownPackage + '.' + fqcn;
        }
        final Collection<? extends CandidateUnit> reflect =
            JavaCompletion.reflect(ownPackage, fqcn, target);
        res.addAll(reflect);
      }
    }

    {
      for (final ClassScope cs : source.getClassScopes()) {
        final String fqcn = cs.getFQCN();
        final Optional<MemberDescriptor> fieldResult =
            JavaCompletion.reflectSelf(fqcn, true, target)
                .stream()
                .filter(c -> c instanceof FieldDescriptor && c.getName().equals(var))
                .findFirst();
        if (fieldResult.isPresent()) {
          final MemberDescriptor memberDescriptor = fieldResult.orElse(null);
          final String returnType = memberDescriptor.getRawReturnType();
          final Collection<? extends CandidateUnit> reflect =
              reflect(ownPackage, returnType, target);
          res.addAll(reflect);
        }
      }
    }

    {
      // java.lang
      final String fqcn = "java.lang." + var;
      final Collection<? extends CandidateUnit> result =
          JavaCompletion.reflect(ownPackage, fqcn, true, false, target);
      res.addAll(result);
    }

    {
      String fqcn = var;
      if (!ownPackage.isEmpty()) {
        fqcn = ownPackage + '.' + var;
      }
      final Collection<? extends CandidateUnit> reflectResults =
          JavaCompletion.reflect(ownPackage, fqcn, true, false, target);
      res.addAll(reflectResults);
      final CachedASMReflector reflector = CachedASMReflector.getInstance();
      final Collection<? extends CandidateUnit> inners = reflector.searchInnerClasses(fqcn);
      res.addAll(inners);
    }
    return res;
  }

  private static Comparator<? super CandidateUnit> comparing(
      final Source src, final String keyword) {

    final Set<String> imps = new HashSet<>(src.getImportedClassMap().values());

    return (c1, c2) -> {
      final String n1 = c1.getName();
      final String n2 = c2.getName();
      final String d1 = c1.getDeclaration();
      final String d2 = c2.getDeclaration();

      if (n1.startsWith(keyword) && n2.startsWith(keyword)) {
        if (imps.contains(d1) && imps.contains(d2)) {
          return Integer.compare(n1.length(), n2.length());
        }

        if (imps.contains(d1)) {
          return -1;
        }
        if (imps.contains(d2)) {
          return 1;
        }

        return Integer.compare(n1.length(), n2.length());
      }

      if (n1.startsWith(keyword)) {
        return -1;
      }
      if (n2.startsWith(keyword)) {
        return 1;
      }
      return n1.compareTo(n2);
    };
  }

  private static Comparator<? super CandidateUnit> comparing(final String keyword) {
    return (c1, c2) -> {
      final String o1 = c1.getName();
      final String o2 = c2.getName();

      if (o1.startsWith(keyword) && o2.startsWith(keyword)) {
        return Integer.compare(o1.length(), o2.length());
      }
      if (o1.startsWith(keyword)) {
        return -1;
      }
      if (o2.startsWith(keyword)) {
        return 1;
      }
      return o1.compareTo(o2);
    };
  }

  private static Comparator<? super CandidateUnit> defaultComparing() {
    return (c1, c2) -> {
      final String o1 = c1.getName();
      final String o2 = c2.getName();
      final int i = o1.compareTo(o2);
      if (i == 0) {
        final String d1 = c1.getDisplayDeclaration();
        final String d2 = c2.getDisplayDeclaration();
        return Integer.compare(d1.length(), d2.length());
      }
      return i;
    };
  }

  private static Comparator<? super CandidateUnit> methodComparing(final String keyword) {
    if (keyword.isEmpty()) {
      return defaultComparing();
    }
    return (c1, c2) -> {
      final String o1 = c1.getName();
      final String o2 = c2.getName();

      if (o1.startsWith(keyword) && o2.startsWith(keyword)) {
        final String d1 = c1.getDisplayDeclaration();
        final String d2 = c2.getDisplayDeclaration();
        return Integer.compare(d1.length(), d2.length());
      }

      if (o1.startsWith(keyword)) {
        return -1;
      }
      if (o2.startsWith(keyword)) {
        return 1;
      }
      return o1.compareTo(o2);
    };
  }

  private static List<ClassIndex> completionImport(final String searchWord) {

    final Config config = Config.load();
    final boolean useFuzzySearch = config.useClassFuzzySearch();
    final int idx = searchWord.lastIndexOf(':');
    final Stream<ClassIndex> stream;
    final CachedASMReflector reflector = CachedASMReflector.getInstance();

    if (idx > 0) {
      final String classPrefix = searchWord.substring(idx + 1, searchWord.length());
      if (useFuzzySearch) {
        stream = reflector.fuzzySearchClassesStream(classPrefix.toLowerCase(), false);
      } else {
        stream = reflector.searchClassesStream(classPrefix.toLowerCase(), true, false);
      }
      return stream
          .map(
              cl -> {
                cl.setMemberType(CandidateUnit.MemberType.IMPORT);
                return cl;
              })
          .sorted(comparing(classPrefix))
          .collect(Collectors.toList());
    }

    return Collections.emptyList();
  }

  public void setProject(Project project) {
    this.project = project;
  }

  private Source getSource(final File file) throws IOException, ExecutionException {
    final GlobalCache globalCache = GlobalCache.getInstance();
    return globalCache.getSource(project, file.getCanonicalFile());
  }

  public Collection<? extends CandidateUnit> completionAt(
      final File file, int line, int column, String prefix) {

    log.debug("line={} column={} prefix={}", line, column, prefix);
    try {
      if (!file.exists()) {
        return Collections.emptyList();
      }
      final Source source = this.getSource(file);
      // check type
      if (prefix.startsWith("*")) {
        // special command
        return this.specialCompletion(source, line, column, prefix);
      }
      if (prefix.startsWith("@")) {
        return JavaCompletion.annotationCompletion(source, line, column, prefix);
      }
      // search symbol
      return JavaCompletion.completionSymbols(source, line, prefix);
    } catch (Throwable t) {
      log.catching(t);
      return Collections.emptyList();
    }
  }

  private Collection<? extends CandidateUnit> specialCompletion(
      final Source source, final int line, final int column, final String searchWord) {

    // special command
    final Config config = Config.load();
    final boolean useFuzzySearch = config.useClassFuzzySearch();

    if (searchWord.startsWith("*import")) {

      return JavaCompletion.completionImport(searchWord);

    } else if (searchWord.startsWith("*new")) {

      // list all classes
      final int idx = searchWord.lastIndexOf(':');
      if (idx > 0) {
        final List<ClassIndex> result;
        final String classPrefix = searchWord.substring(idx + 1, searchWord.length());
        if (useFuzzySearch) {
          result = CachedASMReflector.getInstance().fuzzySearchClasses(classPrefix.toLowerCase());
        } else {
          result = CachedASMReflector.getInstance().searchClasses(classPrefix.toLowerCase());
        }
        result.sort(comparing(source, classPrefix));
        return result;
      }

      return JavaCompletion.completionConstructors(source)
          .stream()
          .sorted(comparing(source, ""))
          .collect(Collectors.toList());

    } else if (searchWord.startsWith("*method")) {

      final int prefixIdx = searchWord.lastIndexOf('#');
      final int classIdx = searchWord.lastIndexOf(':');
      final String pkg = source.getPackageName();

      if (classIdx > 0 && prefixIdx > 0) {
        final String prefix = searchWord.substring(prefixIdx + 1);
        // return methods of prefix class
        String fqcn = searchWord.substring(classIdx + 1, prefixIdx);
        fqcn = ClassNameUtils.replace(fqcn, ClassNameUtils.CAPTURE_OF, "");
        return reflectWithFQCN(fqcn, prefix)
            .stream()
            .sorted(methodComparing(prefix))
            .collect(Collectors.toList());
      }

      // chained method completion
      if (classIdx > 0) {
        // return methods of prefix class
        String fqcn = searchWord.substring(classIdx + 1, searchWord.length());
        fqcn = ClassNameUtils.replace(fqcn, ClassNameUtils.CAPTURE_OF, "");
        return reflect(pkg, fqcn, "")
            .stream()
            .sorted(defaultComparing())
            .collect(Collectors.toList());

      } else {
        String prefix = "";
        if (prefixIdx > 0) {
          prefix = searchWord.substring(prefixIdx + 1);
        }

        // search near method call and return methods of prefix class
        final List<AccessSymbol> targets = new ArrayList<>(8);
        targets.addAll(source.getMethodCall(line));
        targets.addAll(source.getFieldAccess(line));
        log.debug("targets:{}", targets);

        int size = targets.size();
        int startColumn = column;

        while (size > 0 && startColumn-- > 0) {
          for (AccessSymbol accessSymbol : targets) {
            if (accessSymbol.match(line, startColumn) && accessSymbol.returnType != null) {
              final String fqcn =
                  ClassNameUtils.replace(accessSymbol.returnType, ClassNameUtils.CAPTURE_OF, "");
              return reflect(pkg, fqcn, prefix)
                  .stream()
                  .sorted(methodComparing(prefix))
                  .collect(Collectors.toList());
            }
          }
        }

        return Collections.emptyList();
      }
    } else if (searchWord.startsWith("*package")) {
      // completion projects package
      return this.completionPackage()
          .stream()
          .sorted(Comparator.comparing(CandidateUnit::getName))
          .collect(Collectors.toList());
    }
    // search fields or methods
    final int idx = searchWord.lastIndexOf('#');
    if (idx > 0) {
      final String var = searchWord.substring(1, idx);
      final String prefix = searchWord.substring(idx + 1);
      return JavaCompletion.completionFieldsOrMethods(source, line, var, prefix.toLowerCase())
          .stream()
          .sorted(methodComparing(prefix))
          .collect(Collectors.toList());
    }

    return JavaCompletion.completionFieldsOrMethods(source, line, searchWord.substring(1), "")
        .stream()
        .sorted(defaultComparing())
        .collect(Collectors.toList());
  }

  private Collection<? extends CandidateUnit> completionPackage() {
    final GlobalCache globalCache = GlobalCache.getInstance();
    final LoadingCache<File, Source> sourceCache = globalCache.getSourceCache(project);
    return sourceCache
        .asMap()
        .values()
        .stream()
        .map(source -> ClassIndex.createPackage(source.getPackageName()))
        .collect(Collectors.toSet());
  }
}
