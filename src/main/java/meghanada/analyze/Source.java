package meghanada.analyze;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import meghanada.cache.GlobalCache;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

public class Source {

    private static final String REPORT_UNKNOWN_TREE = "report-unknown-tree";
    private static final Logger log = LogManager.getLogger(Source.class);

    public final Set<String> importClasses = new HashSet<>(16);
    public final Map<String, String> staticImportClass = new HashMap<>(8);
    public final Set<String> unused = new HashSet<>(8);
    public final Set<String> unknown = new HashSet<>(8);
    public final List<ClassScope> classScopes = new ArrayList<>(1);
    public final Deque<ClassScope> currentClassScope = new ArrayDeque<>(1);
    public final Set<String> usingClasses = new HashSet<>(8);
    public String filePath;
    public String packageName;
    public List<LineRange> lineRange;

    // temp flag
    public boolean hasCompileError;

    public Source() {
    }

    public Source(final String filePath) {
        this.filePath = filePath;
    }

    public void addImport(final String fqcn) {
        this.importClasses.add(fqcn);
        this.unused.add(fqcn);
        log.trace("unused class {}", fqcn);
    }

    public void addStaticImport(final String method, final String clazz) {
        this.importClasses.add(clazz);
        this.staticImportClass.putIfAbsent(method, clazz);
        log.trace("static unused class {} {}", clazz, method);
    }

    public void startClass(final ClassScope classScope) {
        this.currentClassScope.push(classScope);
    }

    public Optional<ClassScope> getCurrentClass() {
        final ClassScope classScope = this.currentClassScope.peek();
        if (classScope != null) {
            return classScope.getCurrentClass();
        }
        return Optional.empty();
    }

    public Optional<ClassScope> endClass() {
        return this.getCurrentClass().map(classScope -> {
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

    private List<LineRange> getRange(final File file) throws IOException {
        if (this.lineRange != null) {
            return this.lineRange;
        }
        int last = 1;
        final List<LineRange> list = new ArrayList<>(32);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charset.forName("UTF-8")))) {
            String s;
            while ((s = br.readLine()) != null) {
                final int length = s.length();
                final LineRange range = new LineRange(last, last + length);
                list.add(range);
            }
        }
        this.lineRange = list;
        return this.lineRange;
    }

    Position getPos(int pos) throws IOException {
        int line = 1;
        for (final LineRange r : getRange(this.getFile())) {
            if (r.contains(pos)) {
                return new Position(line, pos + 1);
            }
            final Integer last = r.getEndPos();
            pos -= last;
            line++;
        }
        return new Position(-1, -1);
    }

    public File getFile() {
        return new File(this.filePath);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("file", this.filePath)
                .toString();
    }

    public Optional<Variable> findVariable(final int pos) {
        for (final ClassScope cs : classScopes) {
            final Optional<Variable> variable = cs.findVariable(pos);
            if (variable.isPresent()) {
                return variable;
            }
        }
        log.warn("Missing element pos={}", pos);
        return Optional.empty();
    }

    public void dumpVariable() {
        final EntryMessage entryMessage = log.traceEntry("{}", Strings.repeat("*", 100));
        for (final ClassScope cs : classScopes) {
            cs.dumpVariable();
        }
        log.traceExit(entryMessage);
    }

    public void dumpFieldAccess() {
        final EntryMessage entryMessage = log.traceEntry("{}", Strings.repeat("*", 100));
        for (final ClassScope cs : classScopes) {
            cs.dumpFieldAccess();
        }
        log.traceExit(entryMessage);
    }

    public void dump() {
        final EntryMessage entryMessage = log.traceEntry("{}", Strings.repeat("*", 100));
        for (final ClassScope cs : classScopes) {
            cs.dump();
        }
        log.trace("unused={}", this.unused);
        log.trace("unknown={}", this.unknown);
        log.traceExit(entryMessage);
    }

    public Optional<AccessSymbol> getExpressionReturn(final int line) {
        final Scope scope = Scope.getScope(line, this.classScopes);
        if (scope != null && (scope instanceof TypeScope)) {
            final TypeScope typeScope = (TypeScope) scope;
            return Optional.ofNullable(typeScope.getExpressionReturn(line));
        }
        return Optional.empty();
    }

    public List<ClassScope> getClassScopes() {
        return this.classScopes;
    }

    public Optional<TypeScope> getTypeScope(final int line) {
        final Scope scope = Scope.getScope(line, this.classScopes);
        if (scope != null) {
            return Optional.of((TypeScope) scope);
        }
        return Optional.empty();
    }

    public Optional<MethodCall> getMethodCall(final int line, final int column, final boolean onlyName) {
        final EntryMessage entryMessage = log.traceEntry("line={} column={}", line, column);
        int col = column;
        Scope scope = Scope.getInnerScope(line, this.classScopes);
        if (scope != null) {
            final Collection<MethodCall> symbols = scope.getMethodCall(line);
            final int size = symbols.size();
            log.trace("variables:{}", symbols);
            if (onlyName) {
                for (final MethodCall methodCall : symbols) {
                    if (methodCall.nameContains(col)) {
                        final Optional<MethodCall> result = Optional.of(methodCall);
                        return log.traceExit(entryMessage, result);
                    }
                }
            } else {
                while (size > 0 && col-- > 0) {
                    for (final MethodCall methodCallSymbol : symbols) {
                        if (methodCallSymbol.containsColumn(col)) {
                            final Optional<MethodCall> result = Optional.of(methodCallSymbol);
                            return log.traceExit(entryMessage, result);
                        }
                    }
                }
            }
        }
        final Optional<MethodCall> empty = Optional.empty();
        return log.traceExit(entryMessage, empty);
    }

    public List<MethodCall> getMethodCall(final int line) {
        log.traceEntry("line={}", line);
        Scope scope = Scope.getScope(line, this.classScopes);
        if (scope != null) {
            if (scope instanceof TypeScope) {
                TypeScope typeScope = (TypeScope) scope;
                List<MethodCall> symbols = typeScope.getMethodCall(line);
                if (symbols.size() > 0) {
                    return log.traceExit(symbols);
                }
            }
            final List<MethodCall> callSymbols = scope.getMethodCall(line);
            return log.traceExit(callSymbols);
        }
        return log.traceExit(Collections.emptyList());
    }

    public List<FieldAccess> getFieldAccess(final int line) {
        Scope scope = Scope.getScope(line, this.classScopes);
        if (scope != null) {
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
        final Scope scope = Scope.getInnerScope(line, this.classScopes);
        if (scope != null) {
            return scope.getDeclaratorMap();
        }
        return Collections.emptyMap();
    }

    public Optional<Variable> getVariable(final int line, final int col) {
        final Scope scope = Scope.getInnerScope(line, this.classScopes);
        if (scope != null) {
            return scope.getVariables()
                    .stream()
                    .filter(variable -> variable.range.begin.line == line &&
                            variable.range.containsColumn(col))
                    .findFirst();
        }
        return Optional.empty();
    }

    public List<MemberDescriptor> getAllMember() {
        final List<MemberDescriptor> memberDescriptors = new ArrayList<>(8);
        for (final TypeScope typeScope : this.classScopes) {
            final List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            if (result != null) {
                memberDescriptors.addAll(result);
            }
        }
        return memberDescriptors;
    }

    public Optional<FieldAccess> searchFieldAccess(final int line,
                                                   final int col,
                                                   final String name) {
        final Scope scope = Scope.getScope(line, this.classScopes);
        if (scope != null && (scope instanceof TypeScope)) {
            final TypeScope ts = (TypeScope) scope;
            final Collection<FieldAccess> fieldAccesses = ts.getFieldAccess(line);
            return fieldAccesses
                    .stream()
                    .filter(fa -> {
                        final Range range = fa.range;
                        final Position begin = fa.range.begin;
                        return range.begin.line == line &&
                                range.containsColumn(col) &&
                                fa.name.equals(name);
                    })
                    .findFirst();
        }
        return Optional.empty();
    }

    public Map<String, List<String>> searchMissingImport() {
        return this.searchMissingImport(true);
    }

    private Map<String, List<String>> searchMissingImport(boolean addAll) {
        final CachedASMReflector reflector = CachedASMReflector.getInstance();

        // search missing imports
        final Map<String, List<String>> ask = new HashMap<>(4);

        log.debug("unknown class size:{} classes:{}", this.unknown.size(), this.unknown);
        for (final String clazzName : this.unknown) {
            final String searchWord = ClassNameUtils.removeTypeAndArray(clazzName);
            log.debug("search unknown class : {} ...", searchWord);
            final Collection<? extends CandidateUnit> findUnits = reflector.searchClasses(searchWord, false, false);
            log.debug("find candidate units : {}", findUnits);

            if (findUnits.size() == 0) {
                continue;
            }
            if (findUnits.size() == 1) {

                final CandidateUnit[] candidateUnits = findUnits.toArray(new CandidateUnit[1]);
                final String declaration = candidateUnits[0].getDeclaration();
                if (declaration.startsWith("java.lang")) {
                    continue;
                }
                if (addAll) {
                    ask.put(clazzName, Collections.singletonList(candidateUnits[0].getDeclaration()));
                }
            } else {
                final List<String> imports = findUnits.stream()
                        .map(CandidateUnit::getDeclaration)
                        .collect(Collectors.toList());
                ask.put(clazzName, imports);
            }
        }
        return ask;
    }

    public void invalidateCache() {
        this.invalidateCache(this.classScopes);
    }

    private void invalidateCache(final List<ClassScope> classScopes) {
        final GlobalCache globalCache = GlobalCache.getInstance();
        for (final ClassScope classScope : classScopes) {
            globalCache.invalidateMemberDescriptors(classScope.getFQCN());
            this.invalidateCache(classScope.classScopes);
        }
    }

    public List<String> optimizeImports() {
        // shallow copy
        Map<String, String> importMap = new HashMap<>(this.getImportedClassMap());

        log.debug("unused:{}", this.unused);
        // remove unused
        this.unused.forEach(k -> importMap.values().remove(k));
        log.debug("importMap:{}", importMap);

        final Map<String, List<String>> missingImport = this.searchMissingImport(false);
        log.debug("missingImport:{}", missingImport);

        if (missingImport.size() > 0) {
            // fail
            return Collections.emptyList();
        }

        // create optimize import
        // 1. count import pkg
        // 2. sort
        final Map<String, List<String>> optimizeMap = new HashMap<>(4);
        importMap.values()
                .forEach(fqcn -> {
                    String pkg1 = ClassNameUtils.getPackage(fqcn);
                    if (pkg1.startsWith("java.lang")) {
                        return;
                    }
                    if (optimizeMap.containsKey(pkg1)) {
                        List<String> list = optimizeMap.get(pkg1);
                        list.add(fqcn);
                    } else {
                        List<String> list = new ArrayList<>(1);
                        list.add(fqcn);
                        optimizeMap.put(pkg1, list);
                    }
                });

        final List<String> imports = optimizeMap.values().stream()
                .map(strings -> {
                    if (strings.size() >= 5) {
                        final String sample = strings.get(0);
                        final String pkg = ClassNameUtils.getPackage(sample);
                        final List<String> result = new ArrayList<>(4);
                        result.add(pkg + ".*");
                        return result;
                    }
                    return strings;
                }).flatMap(Collection::stream)
                .sorted((o1, o2) -> {
                    if (o1.startsWith("java.") && o2.startsWith("java.")) {
                        return o1.compareTo(o2);
                    } else if (o1.startsWith("java.")) {
                        return 1;
                    } else if (o2.startsWith("java.")) {
                        return -1;
                    } else {
                        return o1.compareTo(o2);
                    }
                }).collect(Collectors.toList());

        log.debug("optimize imports:{}", imports);
        return imports;
    }

    public boolean isReportUnknown() {
        if (this.hasCompileError) {
            return false;
        }
        final String key = System.getProperty(REPORT_UNKNOWN_TREE);
        return key != null && key.equals("true");
    }

    public boolean addImportIfAbsent(final String fqcn) {
        if (this.importClasses.contains(fqcn)) {
            return false;
        }
        for (final ClassScope classScope : this.classScopes) {
            final String classScopeFQCN = classScope.getFQCN();
            if (fqcn.equals(classScopeFQCN)) {
                return false;
            }
        }
        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        if (fqcn.startsWith(this.packageName)) {
            final Map<String, String> packageClasses = reflector.getPackageClasses(this.packageName);
            final boolean find = packageClasses
                    .values()
                    .stream()
                    .filter(s -> s.equals(fqcn))
                    .map(s -> true)
                    .findFirst()
                    .orElseGet(() -> {
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

    public String getImportedClassFQCN(final String shortName,
                                       @Nullable final String defaultValue) {
        return this.importClasses.stream()
                .filter(s -> {
                    if (s.indexOf('.') > 0) {
                        return s.endsWith('.' + shortName);
                    }
                    return s.endsWith(shortName);
                })
                .findFirst()
                .orElseGet(() -> CachedASMReflector.getInstance()
                        .getStandardClasses()
                        .getOrDefault(shortName, defaultValue));
    }

    public Map<String, String> getImportedClassMap() {
        final Map<String, String> map = new HashMap<>(32);
        this.importClasses.forEach(s -> {
            final String key = ClassNameUtils.getSimpleName(s);
            map.putIfAbsent(key, s);
        });
        final Map<String, String> standardClasses = CachedASMReflector.getInstance().getStandardClasses();
        map.putAll(standardClasses);
        return map;
    }
}
