package meghanada.parser.source;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.google.common.base.MoreObjects;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassName;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@DefaultSerializer(JavaSourceSerializer.class)
public class JavaSource {

    private static final int IMPORT_LIMIT = 5;
    private static Logger log = LogManager.getLogger(JavaSource.class);

    final File file;

    // K: className V: FQCN
    public BiMap<String, String> importClass = HashBiMap.create();
    public Map<String, String> staticImp = new HashMap<>(8);
    public List<TypeScope> typeScopes = new ArrayList<>(8);
    public String pkg;
    public Map<String, String> unusedClass = new HashMap<>(32);
    public Set<String> unknownClass = new HashSet<>(16);
    public Deque<TypeScope> currentType = new ArrayDeque<>(8);
    public TypeHint typeHint = new TypeHint();

    public JavaSource(final File file) {
        this.file = file;
    }

    public static boolean isJavaFile(File file) {
        return file.getName().endsWith(".java") && file.exists();
    }

    public Set<Variable> getNameSymbol(final int line) {
        Scope scope = Scope.getInnerScope(line, this.typeScopes);
        if (scope != null) {
            return scope.getNameSymbol(line);
        }
        return Collections.emptySet();
    }

    public Map<String, Variable> getDeclaratorMap(final int line) {
        Scope scope = Scope.getInnerScope(line, this.typeScopes);
        if (scope != null) {
            return scope.getDeclaratorMap();
        }
        return Collections.emptyMap();
    }

    public TypeScope getTypeScope(int line) {
        Scope scope = Scope.getScope(line, this.typeScopes);
        if (scope != null) {
            return (TypeScope) scope;
        }
        return null;
    }

    public FieldAccessSymbol searchFieldAccessSymbol(final int line, final String name) {
        Scope scope = Scope.getScope(line, this.typeScopes);
        if (scope != null && (scope instanceof TypeScope)) {
            final TypeScope typeScope = (TypeScope) scope;
            final Collection<FieldAccessSymbol> accessSymbols = typeScope.getFieldAccessSymbols(line);
            for (FieldAccessSymbol accessSymbol : accessSymbols) {
                if (accessSymbol.name.equals(name)) {
                    return accessSymbol;
                }
            }
        }
        return null;
    }

    public Optional<FieldAccessSymbol> getFieldAccessSymbol(int line, int column) {
        final Scope scope = Scope.getScope(line, this.typeScopes);
        if (scope != null && (scope instanceof TypeScope)) {
            final TypeScope typeScope = (TypeScope) scope;
            Collection<FieldAccessSymbol> symbols = typeScope.getFieldAccessSymbols(line);
            int size = symbols.size();
            if (size == 0) {
                symbols = scope.getFieldAccessSymbols(line);
            }
            while (size > 0 && column-- > 0) {
                for (FieldAccessSymbol fieldAccessSymbol : symbols) {
                    if (fieldAccessSymbol.contains(column)) {
                        return Optional.of(fieldAccessSymbol);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public List<FieldAccessSymbol> getFieldAccessSymbols(final int line) {
        Scope scope = Scope.getScope(line, this.typeScopes);
        if (scope != null) {
            if (scope instanceof TypeScope) {
                TypeScope typeScope = (TypeScope) scope;
                List<FieldAccessSymbol> symbols = typeScope.getFieldAccessSymbols(line);
                if (symbols.size() > 0) {
                    return symbols;
                }
            }
            return scope.getFieldAccessSymbols(line);
        }
        return Collections.emptyList();
    }

    public Optional<MethodCallSymbol> getMethodCallSymbol(final int line, final int column, final boolean onlyName) {
        final EntryMessage entryMessage = log.traceEntry("line={} column={}", line, column);
        int col = column;
        Scope scope = Scope.getInnerScope(line, this.typeScopes);
        if (scope != null) {
            Collection<MethodCallSymbol> symbols = scope.getMethodCallSymbols(line);
            int size = symbols.size();
            log.trace("symbols:{}", symbols);
            if (onlyName) {
                for (MethodCallSymbol methodCallSymbol : symbols) {
                    if (methodCallSymbol.nameContains(col)) {
                        final Optional<MethodCallSymbol> result = Optional.of(methodCallSymbol);
                        return log.traceExit(entryMessage, result);
                    }
                }
            } else {
                while (size > 0 && col-- > 0) {
                    for (MethodCallSymbol methodCallSymbol : symbols) {
                        if (methodCallSymbol.contains(col)) {
                            final Optional<MethodCallSymbol> result = Optional.of(methodCallSymbol);
                            return log.traceExit(entryMessage, result);
                        }
                    }
                }
            }
        }
        final Optional<MethodCallSymbol> empty = Optional.empty();
        return log.traceExit(entryMessage, empty);
    }

    public List<MethodCallSymbol> getMethodCallSymbols(final int line) {
        log.traceEntry("line={}", line);
        Scope scope = Scope.getScope(line, this.typeScopes);
        if (scope != null) {
            if (scope instanceof TypeScope) {
                TypeScope typeScope = (TypeScope) scope;
                List<MethodCallSymbol> symbols = typeScope.getMethodCallSymbols(line);
                if (symbols.size() > 0) {
                    return log.traceExit(symbols);
                }
            }
            final List<MethodCallSymbol> callSymbols = scope.getMethodCallSymbols(line);
            return log.traceExit(callSymbols);
        }
        return log.traceExit(Collections.emptyList());
    }

    public AccessSymbol getExpressionReturn(final int line) {
        final Scope scope = Scope.getScope(line, this.typeScopes);
        if (scope != null && (scope instanceof TypeScope)) {
            final TypeScope typeScope = (TypeScope) scope;
            return typeScope.getExpressionReturn(line);
        }
        return null;
    }

    public List<MemberDescriptor> getMemberDescriptors(final int line) {
        Scope scope = Scope.getScope(line, this.typeScopes);
        if (scope != null) {
            return ((TypeScope) scope).getMemberDescriptors();
        }
        return Collections.emptyList();
    }

    public List<MemberDescriptor> getAllMember() {
        List<MemberDescriptor> memberDescriptors = new ArrayList<>();
        for (TypeScope typeScope : this.typeScopes) {
            List<MemberDescriptor> result = typeScope.getMemberDescriptors();
            if (result != null) {
                memberDescriptors.addAll(result);
            }
        }
        return memberDescriptors;
    }

    public String getPkg() {
        return pkg;
    }

    public List<TypeScope> getTypeScopes() {
        return typeScopes;
    }

    public List<String> optimizeImports() {
        // shallow copy
        Map<String, String> importMap = new HashMap<>(this.importClass);

        log.debug("Unused:{}", this.unusedClass);
        // remove unused
        this.unusedClass.keySet().forEach(importMap::remove);
        log.debug("importMap:{}", importMap);

        final Map<String, List<String>> missingImport = this.searchMissingImport(importMap, false);
        log.debug("missingImport:{}", missingImport);

        if (missingImport.size() > 0) {
            // fail
            return Collections.emptyList();
        }

        // create optimize import
        // 1. count import pkg
        // 2. sort
        final Map<String, List<String>> optimizeMap = new HashMap<>();
        importMap.values()
                .stream()
                .forEach(fqcn -> {
                    String pkg1 = ClassNameUtils.getPackage(fqcn);
                    if (pkg1.startsWith("java.lang")) {
                        return;
                    }
                    if (optimizeMap.containsKey(pkg1)) {
                        List<String> list = optimizeMap.get(pkg1);
                        list.add(fqcn);
                    } else {
                        List<String> list = new ArrayList<>();
                        list.add(fqcn);
                        optimizeMap.put(pkg1, list);
                    }
                });

        final List<String> imports = optimizeMap.values().stream()
                .map(strings -> {
                    if (strings.size() >= IMPORT_LIMIT) {
                        String sample = strings.get(0);
                        String pkg = ClassNameUtils.getPackage(sample);
                        List<String> result = new ArrayList<>();
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

    public boolean isImported(String type) {
        log.traceEntry("className={}", type);
        if (type.equals("void")) {
            return log.traceExit(true);
        }
        type = ClassNameUtils.boxing(type);
        final ClassName className = new ClassName(type);
        final String name = className.getName();
        if (this.importClass.containsKey(name)) {
            return log.traceExit(true);
        }

        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        final Map<String, String> standardClasses = reflector.getStandardClasses();
        if (standardClasses.containsKey(name)) {
            return log.traceExit(true);
        }

        {
            final String pkg = this.pkg;
            if (pkg != null) {
                String pkgClassName = pkg + "." + name;
                Optional<ClassIndex> classIndex = reflector.containsClassIndex(pkgClassName);
                if (classIndex.isPresent()) {
                    return log.traceExit(true);
                }
            }
        }

        {
            // className is FQCN
            Optional<ClassIndex> classIndex = reflector.containsClassIndex(name);
            if (classIndex.isPresent()) {
                return log.traceExit(true);
            }

        }
        return log.traceExit(false);
    }

    public Map<String, List<String>> searchMissingImport() {
        Map<String, String> importMap = new HashMap<>(this.importClass);
        return searchMissingImport(importMap, true);
    }

    private Map<String, List<String>> searchMissingImport(Map<String, String> importMap, boolean addAll) {
        CachedASMReflector reflector = CachedASMReflector.getInstance();
        // search missing imports
        Map<String, List<String>> ask = new HashMap<>();

        log.debug("unknown class:{} ", this.unknownClass);
        for (String clazzName : this.unknownClass) {
            log.debug("search unknown {} ...", clazzName);
            Collection<? extends CandidateUnit> findUnits = reflector.searchClasses(clazzName, false, false);
            log.debug("find CandidateUnit {}", findUnits);

            if (findUnits.size() == 0) {
                continue;
            }
            if (findUnits.size() == 1) {

                CandidateUnit[] candidateUnits = findUnits.toArray(new CandidateUnit[1]);
                String declaration = candidateUnits[0].getDeclaration();
                if (declaration.startsWith("java.lang")) {
                    continue;
                }
                if (addAll) {
                    ask.put(clazzName, Collections.singletonList(candidateUnits[0].getDeclaration()));
                }
            } else {
                List<String> imports = findUnits.stream()
                        .map(CandidateUnit::getDeclaration)
                        .collect(Collectors.toList());
                ask.put(clazzName, imports);
            }
        }
        return ask;
    }

    public File getFile() {
        return file;
    }

    public Optional<TypeScope> getCurrentType() {
        return Optional.ofNullable(this.currentType.peek());
    }

    public void addUnknownClass(String className) {
        final ClassName cn = new ClassName(className);
        this.unknownClass.add(cn.getName());
    }

    public void addUnusedClass(String className, String fqcn) {
        final ClassName cn = new ClassName(className);
        this.unusedClass.put(ClassNameUtils.getSimpleName(cn.getName()), fqcn);
    }

    public void removeUnusedClass(String className) {
        ClassName cn = new ClassName(className);
        this.unusedClass.remove(ClassNameUtils.getSimpleName(cn.getName()));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("file", file)
                .add("pkg", pkg)
                .add("typeScopes", typeScopes)
                .add("importClass", importClass)
                .toString();
    }

    public Optional<BlockScope> getCurrentBlock() {
        return this.getCurrentType().flatMap(this::getCurrentBlock);
    }

    public Optional<BlockScope> getCurrentBlock(TypeScope typeScope) {
        BlockScope blockScope = typeScope.currentBlock();
        if (blockScope == null) {
            return Optional.empty();
        }
        return getCurrentBlock(blockScope);
    }

    Optional<BlockScope> getCurrentBlock(BlockScope blockScope) {
        if (blockScope.currentBlock() == null) {
            return Optional.of(blockScope);
        }
        return getCurrentBlock(blockScope.currentBlock());
    }

    public boolean hasType(final String fqcn) {
        for (TypeScope ts : this.typeScopes) {
            if (fqcn.equals(ts.getFQCN())) {
                return true;
            }
        }
        return false;
    }

    public boolean imported(final String fqcn) {
        for (String impFqcn : this.importClass.values()) {
            if (fqcn.equals(impFqcn)) {
                return true;
            }
        }
        return false;
    }

    public boolean imported(final Set<String> fqcns) {
        for (String impFqcn : this.importClass.values()) {
            if (fqcns.contains(impFqcn)) {
                return true;
            }
        }
        return false;
    }
}
