package meghanada.completion;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.base.Joiner;
import com.google.common.collect.TreeBasedTable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import meghanada.analyze.AccessSymbol;
import meghanada.analyze.ClassScope;
import meghanada.analyze.MethodCall;
import meghanada.analyze.Source;
import meghanada.analyze.TypeScope;
import meghanada.analyze.Variable;
import meghanada.cache.GlobalCache;
import meghanada.completion.matcher.CompletionMatcher;
import meghanada.completion.matcher.DefaultMatcher;
import meghanada.completion.matcher.FuzzyMatcher;
import meghanada.completion.matcher.CamelCaseMatcher;
import meghanada.config.Config;
import meghanada.index.IndexDatabase;
import meghanada.project.Project;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.FieldDescriptor;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.FileUtils;
import meghanada.utils.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JavaCompletion {

  private static final Logger log = LogManager.getLogger(JavaCompletion.class);
  private static final String STATIC = "static ";
  private final TreeBasedTable<File, CandidateUnit, Integer> statisticsTable;

  private Project project;
  private Collection<? extends CandidateUnit> hits;

  public JavaCompletion(final Project project) {
    this.project = project;
    this.statisticsTable = createStatisticsTable();
  }

  @Nonnull
  private static Collection<? extends CandidateUnit> annotationCompletion(
      final Source source, final int line, final int column, final String prefix) {
    String classPrefix = prefix.substring(1);
    CompletionMatcher matcher = getClassCompletionMatcher(classPrefix);
    CachedASMReflector reflector = CachedASMReflector.getInstance();
    return reflector
        .allClassStream()
        .filter(
            c -> {
              if (!c.isAnnotation()) {
                return false;
              }
              return matcher.match(c);
            })
        .map(c -> cloneClassIndex(c))
        .sorted(matcher.comparator())
        .peek(
            c -> {
              String name =
                  ClassNameUtils.getSimpleName(ClassNameUtils.replaceInnerMark(c.getName()));
              c.setName('@' + name);
            })
        .collect(Collectors.toList());
  }

  private static ClassIndex cloneClassIndex(final ClassIndex c) {
    ClassIndex clone = c.clone();
    if (clone.isInnerClass()) {
      String p = clone.getPackage();
      if (!p.isEmpty()) {
        String declaration = clone.getDisplayDeclaration();
        String clazzName = declaration.substring(p.length() + 1);
        clone.setName(clazzName);
      }
    }
    return clone;
  }

  private static CompletionMatcher getClassCompletionMatcher(final String prefix) {
    CompletionMatcher matcher;
    boolean useFuzzySearch = Config.load().useClassFuzzySearch();
    boolean useCamelCaseCompletion = Config.load().useCamelCaseCompletion();
    if (useFuzzySearch) {
      matcher = new FuzzyMatcher(prefix);
    } else if (useCamelCaseCompletion) {
      matcher = new CamelCaseMatcher(prefix);
    } else {
      matcher = new DefaultMatcher(prefix.toLowerCase(), true);
    }
    return matcher;
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

  private static Collection<? extends CandidateUnit> publicReflect(
      final String fqcn,
      final boolean isStatic,
      final boolean withCONSTRUCTOR,
      final String target) {
    return doReflect(fqcn)
        .stream()
        .filter(md -> CompletionFilters.publicMemberFilter(md, isStatic, withCONSTRUCTOR, target))
        .collect(Collectors.toSet());
  }

  private static Collection<? extends CandidateUnit> packageReflect(
      final String fqcn,
      final boolean noStatic,
      final boolean withCONSTRUCTOR,
      final String target) {
    return doReflect(fqcn)
        .stream()
        .filter(md -> CompletionFilters.packageMemberFilter(md, noStatic, withCONSTRUCTOR, target))
        .collect(Collectors.toSet());
  }

  private static Collection<? extends CandidateUnit> completionNewKeyword(final Source source) {
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
    boolean useCamelCaseCompletion = Config.load().useCamelCaseCompletion();
    for (MemberDescriptor c : JavaCompletion.reflectSelf(fqcn, true, prefix)) {
      String name = c.getName();
      final boolean matched =
          useCamelCaseCompletion
              ? StringUtils.isMatchCamelCase(name, prefix)
              : name.startsWith(prefix);
      if (matched) {
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
          String name = c.getName();
          boolean matched =
              useCamelCaseCompletion
                  ? StringUtils.isMatchCamelCase(name, prefix)
                  : StringUtils.contains(name, prefix);
          if (matched) {
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
      boolean matched =
          useCamelCaseCompletion
              ? StringUtils.isMatchCamelCase(k, prefix)
              : k.startsWith(prefix);
      if (matched) {
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
    // static import
    for (Map.Entry<String, String> e : source.staticImportClass.entrySet()) {
      String methodName = e.getKey();
      String clazz = e.getValue();
      for (MemberDescriptor md : JavaCompletion.reflectWithFQCN(clazz, methodName)) {
        if (md.getName().equals(methodName)) {
          result.add(md);
        }
      }
    }

    // Add class
    if (Character.isUpperCase(prefix.charAt(0))) {
      // completion
      CachedASMReflector reflector = CachedASMReflector.getInstance();
      CompletionMatcher matcher = getClassCompletionMatcher(prefix);
      List<ClassIndex> classes =
          reflector
              .allClassStream()
              .filter(
                  c -> {
                    if (prefix.isEmpty()) {
                      return true;
                    }
                    if (c.isAnnotation()) {
                      return false;
                    }
                    return matcher.match(c);
                  })
              .map(
                  c -> {
                    return cloneClassIndex(c);
                  })
              .collect(Collectors.toList());
      result.addAll(classes);
    }
    result.addAll(searchStaticMembers(result, prefix));
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
    return doReflect(fqcn)
        .stream()
        .filter(md -> CompletionFilters.privateMemberFilter(md, withConstructor, prefix))
        .collect(Collectors.toSet());
  }

  private static Collection<MemberDescriptor> reflectWithFQCN(String fqcn, String prefix) {
    return doReflect(fqcn)
        .stream()
        .filter(md -> CompletionFilters.publicMemberFilter(md, prefix))
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
      if (nonNull(fqcn)) {
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
      if (nonNull(variable)) {
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
            JavaCompletion.reflectSelf(fqcn, true, var)
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
      if (reflector.containsFQCN(fqcn)) {
        final Collection<? extends CandidateUnit> inners = reflector.searchInnerClasses(fqcn);
        res.addAll(inners);
      }
    }

    if (line > 0 && res.isEmpty()) {
      List<MethodCall> calls = source.getMethodCall(line - 1);

      long lastCol = 0;
      String lastFQCN = null;
      for (MethodCall call : calls) {
        long col = call.nameRange.begin.column;
        String name = ClassNameUtils.getSimpleName(call.name);
        if (name.equals(var) && col > lastCol) {
          lastFQCN = call.returnType;
          lastCol = col;
        }
      }

      if (nonNull(lastFQCN)) {
        res.addAll(reflectWithFQCN(lastFQCN, ""));
      }
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
    final int idx = searchWord.lastIndexOf(':');
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    if (idx > 0) {
      final String classPrefix = searchWord.substring(idx + 1, searchWord.length());
      CompletionMatcher matcher = getClassCompletionMatcher(classPrefix);
      return reflector
          .allClassStream()
          .filter(c -> matcher.match(c))
          .map(
              c -> {
                ClassIndex ci = cloneClassIndex(c);
                ci.setMemberType(CandidateUnit.MemberType.IMPORT);
                return ci;
              })
          .sorted(matcher.comparator())
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  private static List<MemberDescriptor> searchStaticMembers(
      Set<CandidateUnit> result, final String name) {
    List<MemberDescriptor> members = new ArrayList<>();
    List<String> classes = Config.load().searchStaticMethodClasses();
    if (classes.isEmpty()) {
      return Collections.emptyList();
    }
    String s = Joiner.on(" OR ").join(classes);
    try {
      members =
          IndexDatabase.getInstance()
              .searchMembers(
                  IndexDatabase.paren(s),
                  IndexDatabase.doubleQuote("public static"),
                  "(\"METHOD\" OR \"FIELD\")",
                  name + "*")
              .stream()
              .filter(m -> !result.contains(m))
              .peek(
                  m -> {
                    m.setExtra("static-import " + m.getDeclaringClass());
                    m.showStaticClassName = true;
                  })
              .collect(Collectors.toList());
    } catch (Exception ex) {
      log.error("Error getting static method for {}", name);
    }
    return members;
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
    Collection<? extends CandidateUnit> collection =
        this.completionAtInternal(file, line, column, prefix);
    if (nonNull(collection)) {
      this.hits = collection;
    }
    return collection;
  }

  private Collection<? extends CandidateUnit> completionAtInternal(
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

    if (searchWord.startsWith("*import")) {

      return JavaCompletion.completionImport(searchWord);

    } else if (searchWord.startsWith("*new")) {

      return completionNewKeyword(source, searchWord);

    } else if (searchWord.startsWith("*method")) {

      return completionMethods(source, line, column, searchWord);

    } else if (searchWord.startsWith("*package")) {
      // completion projects package
      return this.completionPackage(source.getFile());
      //      return this.completionPackage()
      //          .stream()
      //          .sorted(Comparator.comparing(CandidateUnit::getName))
      //          .collect(Collectors.toList());
    }

    // search fields or methods
    final int idx = searchWord.lastIndexOf('#');
    if (idx > 0) {
      final String var = searchWord.substring(1, idx);
      final String prefix = searchWord.substring(idx + 1);
      return JavaCompletion.completionFieldsOrMethods(source, line, var, prefix)
          .stream()
          .sorted(methodComparing(prefix))
          .collect(Collectors.toList());
    }

    return JavaCompletion.completionFieldsOrMethods(source, line, searchWord.substring(1), "")
        .stream()
        .sorted(defaultComparing())
        .collect(Collectors.toList());
  }

  private Collection<? extends CandidateUnit> completionMethods(
      Source source, int line, int column, String searchWord) {
    final int prefixIdx = searchWord.lastIndexOf('#');
    final int classIdx = searchWord.lastIndexOf(':');
    final String pkg = source.getPackageName();

    if (classIdx > 0 && prefixIdx > 0) {
      final String prefix = searchWord.substring(prefixIdx + 1);
      // return methods of prefix class
      String fqcn = searchWord.substring(classIdx + 1, prefixIdx);
      fqcn = StringUtils.replace(fqcn, ClassNameUtils.CAPTURE_OF, "");
      return reflectWithFQCN(fqcn, prefix)
          .stream()
          .sorted(methodComparing(prefix))
          .collect(Collectors.toList());
    }

    // chained method completion
    if (classIdx > 0) {
      // return methods of prefix class
      String fqcn = searchWord.substring(classIdx + 1, searchWord.length());
      fqcn = StringUtils.replace(fqcn, ClassNameUtils.CAPTURE_OF, "");
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
        for (AccessSymbol as : targets) {
          if (as.match(line, startColumn) && nonNull(as.returnType)) {
            final String fqcn = StringUtils.replace(as.returnType, ClassNameUtils.CAPTURE_OF, "");
            return reflect(pkg, fqcn, prefix)
                .stream()
                .sorted(methodComparing(prefix))
                .collect(Collectors.toList());
          }
        }
      }

      return Collections.emptyList();
    }
  }

  private Collection<? extends CandidateUnit> completionNewKeyword(
      Source source, String searchWord) {
    // list all classes
    final int idx = searchWord.lastIndexOf(':');
    if (idx > 0) {
      final String classPrefix = searchWord.substring(idx + 1, searchWord.length());
      CachedASMReflector reflector = CachedASMReflector.getInstance();
      CompletionMatcher matcher = getClassCompletionMatcher(classPrefix);
      return reflector
          .allClassStream()
          .filter(c -> matcher.match(c))
          .map(c -> cloneClassIndex(c))
          .sorted(matcher.comparator())
          .collect(Collectors.toList());
    }

    return JavaCompletion.completionNewKeyword(source)
        .stream()
        .sorted(comparing(source, ""))
        .collect(Collectors.toList());
  }

  private Collection<? extends CandidateUnit> completionPackage(File f) {
    Set<File> allSources = this.project.getAllSources();
    try {
      Optional<String> s = FileUtils.convertPathToClass(allSources, f);
      if (s.isPresent()) {
        String className = s.get();
        String pkg = ClassNameUtils.getPackage(className);
        CandidateUnit unit =
            new CandidateUnit() {
              @Override
              public String getName() {
                return pkg;
              }

              @Override
              public String getType() {
                return MemberType.PACKAGE.name();
              }

              @Override
              public String getDeclaration() {
                return pkg;
              }

              @Override
              public String getDisplayDeclaration() {
                return pkg;
              }

              @Override
              public String getReturnType() {
                return pkg;
              }

              @Override
              public String getExtra() {
                return "";
              }
            };
        return Collections.singletonList(unit);
      }
      return Collections.emptyList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public synchronized void resolve(File file, String type, String desc) {
    if (nonNull(hits)) {
      hits.forEach(
          c -> {
            if (c.getType().equals(type) && c.getDisplayDeclaration().equals(desc)) {
              // match
              Integer count = this.statisticsTable.get(file, c);
              if (isNull(count)) {
                count = 0;
              }
              count++;
              this.statisticsTable.put(file, c, count);
            }
          });
    }
  }

  private TreeBasedTable<File, CandidateUnit, Integer> createStatisticsTable() {
    TreeBasedTable<File, CandidateUnit, Integer> table =
        TreeBasedTable.create(
            Comparator.naturalOrder(),
            (o1, o2) -> {
              String name1 = o1.getName();
              String name2 = o2.getName();
              return name1.compareTo(name2);
            });
    return table;
  }

  public void dumpStatsTable() {
    this.statisticsTable
        .rowKeySet()
        .forEach(
            f -> {
              SortedMap<CandidateUnit, Integer> map = this.statisticsTable.row(f);
              map.forEach(
                  (c, i) -> {
                    log.info("{} {} {}", f.getName(), c.getDisplayDeclaration(), i);
                  });
            });
  }
}
