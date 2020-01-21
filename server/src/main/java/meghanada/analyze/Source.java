package meghanada.analyze;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static meghanada.index.IndexableWord.Field.CODE;
import static org.apache.lucene.document.Field.Store.NO;
import static org.apache.lucene.document.Field.Store.YES;

import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.sun.source.tree.LineMap;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityId;
import jetbrains.exodus.entitystore.StoreTransaction;
import meghanada.cache.GlobalCache;
import meghanada.index.IndexableWord;
import meghanada.index.SearchIndexable;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.store.Storable;
import meghanada.telemetry.TelemetryUtils;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;

public class Source implements Serializable, Storable, SearchIndexable {

  public static final String REPORT_UNKNOWN_TREE = "report-unknown-tree";
  public static final String ENTITY_TYPE = "Source";
  public static final String LINK_CLASS_REFERENCES = "references";
  public static final String LINK_REV_CLASS_REFERENCES = "_references";
  public static final String LINK_CLASS = "_class";
  public static final String LINK_VARIABLE = "variable";
  public static final String LINK_FIELD_ACCESS = "fieldAccess";
  public static final String LINK_METHOD_CALL = "methodCall";
  public static final String LINK_SOURCE = "source";
  private static final long serialVersionUID = -4115484075118150793L;
  private static final Logger log = LogManager.getLogger(Source.class);

  public final Set<String> importClasses = new HashSet<>(16);
  public final Map<String, String> staticImportClass = new HashMap<>(8);
  public final Set<String> usingClasses = new HashSet<>(8);
  public final String filePath;
  public final Map<Long, Annotation> annotationMap = new HashMap<>(8);
  public final Set<String> unused = new HashSet<>(8);
  public final List<ClassScope> classScopes = new ArrayList<>(1);
  public final transient Map<Long, List<IndexableWord>> indexWords = new HashMap<>(4);
  public final Set<String> unknown = new HashSet<>(8);
  public final Deque<ClassScope> currentClassScope = new ArrayDeque<>(1);

  // temp flag
  public boolean hasCompileError;
  private String packageName = "";
  private long classStartLine;
  private long pkgStartLine;
  private Map<String, String> importMap;
  private BloomFilter<String> methodCallsBF;

  private transient List<LineRange> lineRange;
  private transient LineMap lineMap;

  public Source(String filePath) {
    this.filePath = filePath;
    this.methodCallsBF =
        BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 100000, 0.01);
  }

  public Source(String filePath, LineMap lineMap) {
    this(filePath);
    this.lineMap = lineMap;
  }

  private static boolean includeInnerClass(final ClassScope cs, final String fqcn) {
    final String classScopeFQCN = cs.getFQCN();
    if (fqcn.equals(classScopeFQCN)) {
      return true;
    }

    final String replaced = ClassNameUtils.replaceInnerMark(classScopeFQCN);
    if (fqcn.equals(replaced)) {
      return true;
    }

    final List<ClassScope> children = cs.classScopes;
    for (final ClassScope child : children) {
      if (includeInnerClass(child, fqcn)) {
        return true;
      }
    }

    return false;
  }

  private static void addClassReference(
      StoreTransaction txn,
      Entity mainEntity,
      Map<String, ClassIndex> classIndex,
      Entity entity,
      String fqcn) {

    if (isNull(fqcn)) {
      return;
    }

    classIndex.computeIfPresent(
        fqcn,
        (key, index) -> {
          try {
            EntityId entityId = index.getEntityId();
            if (nonNull(entityId)) {
              Entity classEntity = txn.getEntity(entityId);
              classEntity.addLink(LINK_CLASS_REFERENCES, entity);

              entity.addLink(LINK_CLASS, classEntity);
              mainEntity.addLink(LINK_REV_CLASS_REFERENCES, entity);
            }
          } catch (Exception e) {
            log.warn(e.getMessage());
          }
          return index;
        });
  }

  private static void deleteLinks(StoreTransaction txn, Entity mainEntity) {
    Set<String> names = new HashSet<>(3);
    names.add(LINK_VARIABLE);
    names.add(LINK_FIELD_ACCESS);
    names.add(LINK_METHOD_CALL);

    // for (Entity entity : mainEntity.getLinks(LINK_REV_CLASS_REFERENCES)) {
    //   Entity classEntity = entity.getLink(LINK_CLASS);
    //   if (nonNull(classEntity)) {
    //     // reload
    //     classEntity.deleteLink(LINK_CLASS_REFERENCES, entity);
    //   }
    // }

    for (Entity entity : mainEntity.getLinks(names)) {
      entity.delete();
    }

    for (String name : names) {
      mainEntity.deleteLinks(name);
    }

    // mainEntity.deleteLinks(LINK_REV_CLASS_REFERENCES);
  }

  public void addImport(final String fqcn) {
    this.importClasses.add(fqcn);
    this.unused.add(fqcn);
    log.trace("unused class {}", fqcn);
  }

  public void addStaticImport(final String method, final String clazz) {
    this.staticImportClass.putIfAbsent(method, clazz);
    log.trace("static unused class {} {}", clazz, method);
  }

  public void startClass(final ClassScope classScope) {
    this.currentClassScope.push(classScope);
  }

  public Optional<ClassScope> getCurrentClass() {
    ClassScope classScope = this.currentClassScope.peek();
    if (nonNull(classScope)) {
      return classScope.getCurrentClass();
    }
    return Optional.empty();
  }

  public Optional<ClassScope> endClass() {
    return this.getCurrentClass()
        .map(
            classScope -> {
              this.classScopes.add(classScope);
              return this.currentClassScope.remove();
            });
  }

  public Optional<BlockScope> getCurrentBlock() {
    return this.getCurrentClass().flatMap(TypeScope::getCurrentBlock);
  }

  public Optional<? extends Scope> getCurrentScope() {
    return this.getCurrentClass().flatMap(BlockScope::getCurrentScope);
  }

  public void addClassScope(final ClassScope classScope) {
    this.classScopes.add(classScope);
  }

  private List<LineRange> getRanges(final File file) throws IOException {

    if (nonNull(this.lineRange)) {
      return this.lineRange;
    }

    int last = 1;
    List<LineRange> list = new ArrayList<>(256);
    try (BufferedReader br =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
      String s;
      while ((s = br.readLine()) != null) {
        int length = s.length();
        LineRange range = new LineRange(last, last + length);
        list.add(range);
      }
    }
    this.lineRange = list;
    return this.lineRange;
  }

  Position getPos(int pos) {
    if (pos == -1) {
      return new Position(-1, -1);
    }
    if (nonNull(this.lineMap)) {
      long lineNumber = this.lineMap.getLineNumber(pos);
      long columnNumber = this.lineMap.getColumnNumber(pos);
      return new Position(lineNumber, columnNumber);
    }

    int line = 1;
    try {
      List<LineRange> ranges = getRanges(this.getFile());
      for (LineRange r : ranges) {
        if (r.contains(pos)) {
          return new Position(line, pos + 1);
        }
        int last = r.getEndPos();
        pos -= last;
        line++;
      }
      return new Position(-1, -1);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public File getFile() {
    return new File(this.filePath);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("file", this.filePath).toString();
  }

  public Optional<Variable> findVariable(final int pos) {
    for (ClassScope cs : classScopes) {
      Optional<Variable> variable = cs.findVariable(pos);
      if (variable.isPresent()) {
        return variable;
      }
    }
    log.warn("Missing element pos={}", pos);
    return Optional.empty();
  }

  public void dumpVariable() {
    EntryMessage entryMessage = log.traceEntry("{}", Strings.repeat("*", 80));
    for (ClassScope cs : classScopes) {
      cs.dumpVariable();
    }
    log.traceExit(entryMessage);
  }

  public void dumpFieldAccess() {
    EntryMessage entryMessage = log.traceEntry("{}", Strings.repeat("*", 80));
    for (ClassScope cs : classScopes) {
      cs.dumpFieldAccess();
    }
    log.traceExit(entryMessage);
  }

  public void dump() {
    EntryMessage entryMessage = log.traceEntry("{}", Strings.repeat("*", 80));
    for (ClassScope cs : classScopes) {
      cs.dump();
    }
    log.trace("unused={}", this.unused);
    log.trace("unknown={}", this.unknown);
    log.traceExit(entryMessage);
  }

  public Optional<AccessSymbol> getExpressionReturn(final int line) {
    Scope scope = Scope.getScope(line, this.classScopes);
    if (nonNull(scope) && (scope instanceof TypeScope)) {
      TypeScope typeScope = (TypeScope) scope;
      return Optional.ofNullable(typeScope.getExpressionReturn(line));
    }
    return Optional.empty();
  }

  public Optional<ExpressionScope> getExpression(final int line) {
    Scope scope = Scope.getScope(line, this.classScopes);
    if (nonNull(scope) && (scope instanceof TypeScope)) {
      TypeScope typeScope = (TypeScope) scope;
      return typeScope.getExpression(line);
    }
    return Optional.empty();
  }

  public List<ClassScope> getClassScopes() {
    return this.classScopes;
  }

  public Optional<TypeScope> getTypeScope(final int line) {
    TypeScope scope = getTypeScope(line, this.classScopes);
    if (nonNull(scope)) {
      return Optional.of(scope);
    }
    return Optional.empty();
  }

  private static TypeScope getTypeScope(int line, List<ClassScope> classScopes) {
    for (ClassScope cs : classScopes) {
      if (cs.contains(line)) {
        if (cs.classScopes.size() > 0) {
          TypeScope ts = getTypeScope(line, cs.classScopes);
          if (nonNull(ts)) {
            return ts;
          }
        }
        return cs;
      }
    }
    return null;
  }

  @SuppressWarnings("try")
  public Optional<MethodCall> getMethodCall(
      final int line, final int column, final boolean onlyName) {
    try (TelemetryUtils.ScopedSpan ss =
        TelemetryUtils.startScopedSpan("LocationSearcher.searchMethodCall")) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("line", line)
              .put("column", column)
              .put("onlyName", onlyName)
              .build("args"));
      int col = column;
      Scope scope = Scope.getInnerScope(line, this.classScopes);
      if (nonNull(scope)) {
        Collection<MethodCall> symbols = scope.getMethodCall(line);
        int size = symbols.size();
        log.trace("variables:{}", symbols);
        if (onlyName) {
          for (MethodCall methodCall : symbols) {
            if (methodCall.nameContains(col)) {
              return Optional.of(methodCall);
            }
          }
        } else {
          while (size > 0 && col-- > 0) {
            for (MethodCall methodCallSymbol : symbols) {
              if (methodCallSymbol.containsColumn(col)) {
                return Optional.of(methodCallSymbol);
              }
            }
          }
        }
      }
      return Optional.empty();
    }
  }

  public List<MethodCall> getMethodCall(final int line) {
    log.traceEntry("line={}", line);
    Scope scope = Scope.getScope(line, this.classScopes);
    if (nonNull(scope)) {
      if (scope instanceof TypeScope) {
        TypeScope typeScope = (TypeScope) scope;
        List<MethodCall> symbols = typeScope.getMethodCall(line);
        if (symbols.size() > 0) {
          return log.traceExit(symbols);
        }
      }
      List<MethodCall> callSymbols = scope.getMethodCall(line);
      return log.traceExit(callSymbols);
    }
    return log.traceExit(Collections.emptyList());
  }

  public List<FieldAccess> getFieldAccess(final int line) {
    Scope scope = Scope.getScope(line, this.classScopes);
    if (nonNull(scope)) {
      if (scope instanceof TypeScope) {
        TypeScope typeScope = (TypeScope) scope;
        List<FieldAccess> symbols = typeScope.getFieldAccess(line);
        if (symbols.size() > 0) {
          return symbols;
        }
      }
      return scope.getFieldAccess(line);
    }
    return Collections.emptyList();
  }

  public Map<String, Variable> getDeclaratorMap(final int line) {
    Scope scope = Scope.getInnerScope(line, this.classScopes);
    if (nonNull(scope)) {
      return scope.getDeclaratorMap();
    }
    return Collections.emptyMap();
  }

  public Map<String, Variable> getVariableMap(final int line) {
    Scope scope = Scope.getInnerScope(line, this.classScopes);
    if (nonNull(scope)) {
      return scope.getVariableMap();
    }
    return Collections.emptyMap();
  }

  public Optional<Variable> getVariable(final int line, final int col) {
    Scope scope = Scope.getInnerScope(line, this.classScopes);
    if (nonNull(scope)) {
      return scope.getVariables().stream()
          .filter(
              variable -> variable.range.begin.line == line && variable.range.containsColumn(col))
          .findFirst();
    }
    return Optional.empty();
  }

  public List<MemberDescriptor> getAllMember() {
    List<MemberDescriptor> memberDescriptors = new ArrayList<>(8);
    for (TypeScope typeScope : this.classScopes) {
      List<MemberDescriptor> result = typeScope.getMemberDescriptors();
      if (nonNull(result)) {
        memberDescriptors.addAll(result);
      }
    }
    return memberDescriptors;
  }

  @SuppressWarnings("try")
  public Optional<FieldAccess> searchFieldAccess(
      final int line, final int column, final String name) {
    try (TelemetryUtils.ScopedSpan ss =
        TelemetryUtils.startScopedSpan("Source.searchFieldAccess")) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("line", line)
              .put("column", column)
              .put("symbol", name)
              .build("args"));
      Scope scope = Scope.getScope(line, this.classScopes);
      if (nonNull(scope) && (scope instanceof TypeScope)) {
        TypeScope ts = (TypeScope) scope;
        Collection<FieldAccess> fieldAccesses = ts.getFieldAccess(line);
        for (FieldAccess fa : fieldAccesses) {
          Range range = fa.range;
          Position begin = fa.range.begin;
          if (range.begin.line == line && range.containsColumn(column) && fa.name.equals(name)) {
            return Optional.of(fa);
          }
        }
      }
      return Optional.empty();
    }
  }

  public Map<String, List<String>> searchMissingImport() {
    return this.searchMissingImport(true);
  }

  private Map<String, List<String>> searchMissingImport(boolean addAll) {
    CachedASMReflector reflector = CachedASMReflector.getInstance();

    // search missing imports
    Map<String, List<String>> ask = new HashMap<>(4);

    log.debug("unknown class size:{} classes:{}", this.unknown.size(), this.unknown);
    Map<String, String> importedClassMap = this.getImportedClassMap();
    for (String clazzName : this.unknown) {
      String searchWord = ClassNameUtils.removeTypeAndArray(clazzName);
      int i = searchWord.indexOf('.');
      if (i > 0) {
        searchWord = searchWord.substring(0, i);
      }

      if (searchWord.isEmpty()) {
        continue;
      }

      if (importedClassMap.containsKey(searchWord)) {
        continue;
      }

      log.debug("search unknown class : '{}' ...", searchWord);
      Collection<? extends CandidateUnit> findUnits = reflector.searchClasses(searchWord, true);
      log.debug("find candidate units : {}", findUnits);

      if (findUnits.size() == 0) {
        continue;
      }
      if (findUnits.size() == 1) {

        CandidateUnit[] candidateUnits = findUnits.toArray(new CandidateUnit[1]);
        String declaration = candidateUnits[0].getDeclaration();
        String pa = ClassNameUtils.getPackage(declaration);
        if (pa.equals("java.lang")) {
          continue;
        }
        if (nonNull(this.packageName) && pa.equals(this.packageName)) {
          // remove same package
          continue;
        }

        if (addAll) {
          ask.put(clazzName, Collections.singletonList(candidateUnits[0].getDeclaration()));
        }
      } else {
        List<String> imports =
            findUnits.stream().map(CandidateUnit::getDeclaration).collect(Collectors.toList());
        if (!imports.isEmpty()) {
          ask.put(clazzName, imports);
        }
      }
    }
    return ask;
  }

  public void invalidateCache() {
    this.invalidateCache(this.classScopes);
  }

  private void invalidateCache(List<ClassScope> classScopes) {
    GlobalCache globalCache = GlobalCache.getInstance();
    for (ClassScope classScope : classScopes) {
      globalCache.invalidateMemberDescriptors(classScope.getFQCN());
      this.invalidateCache(classScope.classScopes);
    }
  }

  public List<String> optimizeImports() {
    if (hasCompileError) {
      // fail
      throw new IllegalStateException("A source has compile error.please fix it.");
    }

    // shallow copy
    Map<String, String> importMap = new HashMap<>(this.getImportedClassMap());

    log.debug("unused:{}", this.unused);
    // remove unused
    this.unused.forEach(k -> importMap.values().remove(k));
    log.debug("importMap:{}", importMap);

    Map<String, List<String>> missingImport = this.searchMissingImport(false);
    log.debug("missingImport:{}", missingImport);

    if (missingImport.size() > 0) {
      // fail
      throw new IllegalStateException("It can not be resolve missing import.");
    }

    // create optimize import
    // 1. count import pkg
    // 2. sort
    Map<String, List<String>> optimizeMap = new HashMap<>(32);
    importMap
        .values()
        .forEach(
            fqcn -> {
              String packageName = ClassNameUtils.getPackage(fqcn);
              if (packageName.equals("java.lang")) {
                return;
              }
              if (optimizeMap.containsKey(packageName)) {
                List<String> list = optimizeMap.get(packageName);
                list.add(fqcn);
              } else {
                List<String> list = new ArrayList<>(1);
                list.add(fqcn);
                optimizeMap.put(packageName, list);
              }
            });

    List<String> optimized =
        optimizeMap.values().stream()
            .map(
                imports -> {
                  if (imports.size() >= 100000) {
                    String sample = imports.get(0);
                    String pkg = ClassNameUtils.getPackage(sample);
                    List<String> result = new ArrayList<>(4);
                    result.add(pkg + ".*");
                    return result;
                  }
                  return imports;
                })
            .flatMap(Collection::stream)
            .sorted(Comparator.naturalOrder())
            .collect(Collectors.toList());

    log.debug("optimize imports:{}", optimized);

    return optimized;
  }

  public boolean isReportUnknown() {
    if (this.hasCompileError) {
      return false;
    }
    String key = System.getProperty(REPORT_UNKNOWN_TREE);
    return nonNull(key) && key.equals("true");
  }

  public boolean addImportIfAbsent(final String fqcn) {

    int i = fqcn.indexOf('#');
    if (i > 0) {
      // static import
      List<String> imps = Splitter.on("#").splitToList(fqcn);
      this.staticImportClass.putIfAbsent(imps.get(1), imps.get(0));
      return true;
    }

    if (this.importClasses.contains(fqcn)) {
      return false;
    }

    for (ClassScope classScope : this.classScopes) {
      if (Source.includeInnerClass(classScope, fqcn)) {
        return false;
      }
    }

    CachedASMReflector reflector = CachedASMReflector.getInstance();
    if (fqcn.startsWith(this.packageName)) {
      Map<String, String> packageClasses = reflector.getPackageClasses(this.packageName);
      boolean find =
          packageClasses.values().stream()
              .filter(s -> s.equals(fqcn))
              .map(s -> true)
              .findFirst()
              .orElseGet(
                  () -> {
                    // TODO search inner class
                    return false;
                  });

      if (find) {
        return false;
      }
    }
    this.importClasses.add(fqcn);
    return true;
  }

  public void resetLineRange() {
    this.lineRange = null;
  }

  public String getImportedClassFQCN(final String shortName, @Nullable final String defaultValue) {
    return this.importClasses.stream()
        .filter(
            s -> {
              if (s.indexOf('.') > 0) {
                return s.endsWith('.' + shortName);
              }
              return s.endsWith(shortName);
            })
        .findFirst()
        .orElseGet(
            () ->
                CachedASMReflector.getInstance()
                    .getStandardClasses()
                    .getOrDefault(shortName, defaultValue));
  }

  public Map<String, String> getImportedClassMap() {

    if (nonNull(this.importMap)) {
      return this.importMap;
    }

    Map<String, String> map = new HashMap<>(32);
    for (String s : this.importClasses) {
      String key = ClassNameUtils.getSimpleName(s);
      map.putIfAbsent(key, s);
    }

    Map<String, String> standardClasses = CachedASMReflector.getInstance().getStandardClasses();
    map.putAll(standardClasses);

    this.importMap = map;
    return map;
  }

  public void addUnknown(@Nullable final String unknown) {
    if (isNull(unknown)) {
      return;
    }
    String trimed = unknown.trim();
    if (!trimed.isEmpty()) {
      this.unknown.add(trimed);
    }
  }

  @Override
  public String getStoreId() {
    return this.filePath;
  }

  @Override
  public String getEntityType() {
    return ENTITY_TYPE;
  }

  @Override
  public void store(StoreTransaction txn, Entity entity) {
    entity.setProperty("filePath", this.filePath);
    if (isNull(this.packageName)) {
      entity.setProperty("packageName", "");
    } else {
      entity.setProperty("packageName", this.packageName);
    }
    entity.setProperty("fqcn", this.getFQCN());
  }

  private static Set<ClassScope> getAllClassScopes(ClassScope classScope) {
    Set<ClassScope> set = new LinkedHashSet<>(8);
    for (ClassScope cs : classScope.getClassScopes()) {
      set.add(cs);
      set.addAll(getAllClassScopes(cs));
    }
    return set;
  }

  private Set<ClassScope> getAllClassScopes() {
    Set<ClassScope> set = new LinkedHashSet<>(8);
    for (ClassScope cs : this.classScopes) {
      set.addAll(getAllClassScopes(cs));
    }
    return set;
  }

  public String getFQCN() {
    if (this.classScopes.isEmpty()) {
      return "";
    }
    return this.classScopes.get(0).getFQCN();
  }

  public Collection<Variable> getVariables() {

    Set<Variable> result = new HashSet<>(8);
    for (ClassScope c : this.classScopes) {
      result.addAll(c.getVariables());
    }

    return result;
  }

  public Collection<FieldAccess> getFieldAccesses() {

    List<FieldAccess> result = new ArrayList<>(8);
    for (ClassScope c : this.classScopes) {
      result.addAll(c.getFieldAccesses());
    }

    return result;
  }

  public Collection<MethodCall> getMethodCalls() {
    List<MethodCall> result = new ArrayList<>(8);
    for (ClassScope c : this.classScopes) {
      result.addAll(c.getMethodCalls());
    }
    return result;
  }

  public void buildMethodCallsBF() {
    for (ClassScope c : this.classScopes) {
      for (MethodCall call : c.getMethodCalls()) {
        String declaringClass = call.declaringClass;
        this.methodCallsBF.put(declaringClass + "#" + call.name);
      }
    }
  }

  public boolean mightContainMethodCall(String methodCall) {
    return this.methodCallsBF.mightContain(methodCall);
  }

  public Collection<AccessSymbol> getAccessSymbols() {
    List<AccessSymbol> result = new ArrayList<>(8);
    for (ClassScope c : this.classScopes) {
      result.addAll(c.getAccessSymbols());
    }
    return result;
  }

  @Nonnull
  public String getPackageName() {
    if (isNull(packageName)) {
      return "";
    }
    return packageName;
  }

  public void setPackageName(String packageName) {
    this.packageName = packageName;
  }

  public long getClassStartLine() {
    return classStartLine;
  }

  public void setClassStartLine(long line) {
    this.classStartLine = line;
  }

  public long getPackageStartLine() {
    return pkgStartLine;
  }

  public void setPackageStartLine(long line) {
    this.pkgStartLine = line;
  }

  @Override
  public String getIndexGroupId() {
    return this.filePath;
  }

  @Override
  public List<Document> getDocumentIndices() {
    return new ArrayList<>(createSourceTextIndices());
  }

  private Collection<? extends Document> createSourceTextIndices() {
    List<Document> documents = new ArrayList<>(128);
    try (BufferedReader br =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(this.filePath), StandardCharsets.UTF_8))) {
      long lineNumber = 1;
      String s;
      while ((s = br.readLine()) != null) {
        Document doc = getBaseDocument(lineNumber);
        doc.add(new TextField(CODE.getName(), s, YES));

        if (this.indexWords.containsKey(lineNumber)) {

          List<IndexableWord> words = this.indexWords.get(lineNumber);
          words.sort(
              (w1, w2) -> {
                Integer sortNo1 = w1.field.getSortNo();
                Integer sortNo2 = w2.field.getSortNo();
                return sortNo1.compareTo(sortNo2);
              });
          for (IndexableWord word : words) {
            IndexableWord.Field field = word.field;
            String val = word.word;
            String name = field.getName();
            if (field.isCategorize()) {
              if (nonNull(name)) {
                doc.add(new StringField(SearchIndexable.CATEGORY, name, YES));
              }
            }
            if (nonNull(val) && nonNull(name)) {
              doc.add(new TextField(name, val, NO));
            }
          }
        }

        String cat = doc.get(SearchIndexable.CATEGORY);
        if (Strings.isNullOrEmpty(cat)) {
          doc.add(new StringField(SearchIndexable.CATEGORY, CODE.getName(), YES));
        }
        documents.add(doc);
        lineNumber++;
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }

    return documents;
  }

  private Document getBaseDocument(long lineNumber) {
    Document doc = new Document();
    doc.add(new StringField(SearchIndexable.GROUP_ID, this.filePath, YES));
    doc.add(new StringField(SearchIndexable.LINE_NUMBER, Long.toString(lineNumber), YES));
    return doc;
  }

  public void addIndexWord(IndexableWord.Field field, long line, long column, String word) {
    IndexableWord indexableWord = new IndexableWord(field, line, column, word);
    if (this.indexWords.containsKey(line)) {
      List<IndexableWord> words = this.indexWords.get(line);
      words.add(indexableWord);
      this.indexWords.put(line, words);
    } else {
      List<IndexableWord> words = new ArrayList<>(128);
      words.add(indexableWord);
      this.indexWords.put(line, words);
    }
  }

  public Map<String, ClassScope> getAllClasses() {
    Map<String, ClassScope> classMap = new HashMap<>(1024);
    this.getAllClassScopes()
        .forEach(
            cs -> {
              String fqcn = cs.getFQCN();
              classMap.put(fqcn, cs);
            });
    return classMap;
  }
}
