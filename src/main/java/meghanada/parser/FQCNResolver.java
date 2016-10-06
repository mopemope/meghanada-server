package meghanada.parser;

import com.google.common.collect.BiMap;
import meghanada.parser.source.*;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassName;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

class FQCNResolver {

    private static final Pattern VALID_FQCN = Pattern
            .compile("(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)+\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*");

    private static final Logger log = LogManager.getLogger(FQCNResolver.class);

    private static FQCNResolver fqcnResolver;
    private final Map<String, String> globalClassSymbol;
    private final List<BiFunction<String, JavaSource, Optional<String>>> searchFunctions;

    private FQCNResolver(Map<String, String> globalClassSymbol) {
        this.globalClassSymbol = globalClassSymbol;
        this.searchFunctions = this.getSearchFunctions();

    }

    public static FQCNResolver getInstance() {
        if (fqcnResolver == null) {
            final Map<String, String> packageClasses = CachedASMReflector.getInstance().getPackageClasses("java.lang");
            fqcnResolver = new FQCNResolver(packageClasses);
        }

        return fqcnResolver;
    }

    private Optional<String> tryClassToFQCN(final String ownPkg, final String name, final BiMap<String, String> classes) {
        final EntryMessage entryMessage = log.traceEntry("ownPkg={} name={} classes={}", ownPkg, name, classes);
        final ClassName className = new ClassName(name);
        final Optional<String> result = Optional.ofNullable(className.toFQCN(ownPkg, classes));
        return log.traceExit(entryMessage, result);
    }

    Optional<String> resolveThisScope(final String name, final JavaSource source) {
        final String searchName = ClassNameUtils.removeCapture(name);

        // Check FQCN
        log.traceEntry("searchName={} name=name{}", searchName, name);

        {
            final Optional<String> typeParam = this.isTypeParameter(name, source);
            if (typeParam.isPresent()) {
                return log.traceExit(typeParam);
            }
            if (name.startsWith(ClassNameUtils.CAPTURE_OF)) {
                return log.traceExit(Optional.of(name));
            }
        }
        final Optional<String> result = source.getCurrentType().map(typeScope -> typeScope.getFieldSymbol(searchName)).map(Variable::getFQCN);
        return log.traceExit(result);

    }

    Optional<String> resolveSymbolFQCN(final String name, final JavaSource source, final int line) {
        final Optional<BlockScope> currentBlock = source.getCurrentBlock();

        return currentBlock.map(bs -> {

            // search current
            final Map<String, Variable> declaratorMap = bs.getDeclaratorMap();
            log.trace("declaratorMap={}", declaratorMap);
            if (declaratorMap.containsKey(name)) {
                final Variable variable = declaratorMap.get(name);
                return variable.getFQCN();
            }

            // search parent
            BlockScope parent = bs.getParent();
            while (parent != null) {
                final Map<String, Variable> parentDeclaratorMap = parent.getDeclaratorMap();
                if (parentDeclaratorMap.containsKey(name)) {
                    final Variable variable = parentDeclaratorMap.get(name);
                    return variable.getFQCN();
                }
                parent = parent.getParent();
            }

            // search field nam
            return source.getCurrentType().map(ts -> {
                final Variable fieldSymbol = ts.getFieldSymbol(name);
                if (fieldSymbol != null) {
                    return fieldSymbol.getFQCN();
                }
                return null;
            }).orElseGet(() -> resolveFQCN(name, source).orElse(null));
        });
    }

    Optional<String> resolveFQCN(final String name, final JavaSource source) {

        final String searchName = ClassNameUtils.removeCapture(name);

        // Check FQCN
        log.traceEntry("searchName={} name={}", searchName, name);

        {
            final Optional<String> typeParam = this.isTypeParameter(name, source);
            if (typeParam.isPresent()) {
                return log.traceExit(typeParam);
            }
            if (name.startsWith(ClassNameUtils.CAPTURE_OF)) {
                return log.traceExit(Optional.of(name));
            }
        }


        final Optional<String> result = this.searchFunctions
                .stream()
                .map(fn -> fn.apply(searchName, source))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        if (!result.isPresent()) {
            // final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            log.debug("can not resolve name:{} file:{}", name, source.getFile());
        } else {
            log.debug("resolved: {} -> FQCN:{}", searchName, result.get());
        }
        return log.traceExit(result);
    }

    private List<BiFunction<String, JavaSource, Optional<String>>> getSearchFunctions() {
        List<BiFunction<String, JavaSource, Optional<String>>> searchFunctions = new ArrayList<>(6);

        searchFunctions.add(this::resolveThis);
        searchFunctions.add(this::resolveSuper);
        searchFunctions.add(this::resolveClassName);
        searchFunctions.add(this::resolveSymbolName);
        searchFunctions.add(this::tryClassToFQCN);

        return searchFunctions;
    }

    private Optional<String> tryClassToFQCN(final String name, final JavaSource source) {
        return this.tryClassToFQCN(source.getPkg(), name, source.importClass);
    }


    private Optional<String> resolveSuper(final String name, final JavaSource source) {
        log.traceEntry("name={}", name);
        final Optional<TypeScope> currentType = source.getCurrentType();
        if (name.equals("super")
            && (currentType.isPresent() && currentType.get() instanceof ClassScope)) {

            final ClassScope classScope = (ClassScope) currentType.get();
            final List<String> supers = classScope.getExtendsClasses();
            if (supers.size() > 0) {
                final String s = supers.get(0);
                return Optional.ofNullable(s);
            }
        }
        final Optional<String> empty = Optional.empty();
        return log.traceExit(empty);
    }

    private Optional<String> resolveThis(final String name, final JavaSource source) {
        log.traceEntry("name={}", name);
        if (name.equals("this")) {
            final Optional<String> result = source.getCurrentType().map(TypeScope::getFQCN);
            if (result.isPresent()) {
                return log.traceExit(result);
            }
            final String fqcn = result.orElseGet(() -> {
                List<TypeScope> typeScopes = source.getTypeScopes();
                if (typeScopes != null && typeScopes.size() > 0) {
                    // return first FQCN
                    return typeScopes.get(0).getFQCN();
                }
                return null;
            });
            final Optional<String> result1 = Optional.ofNullable(fqcn);
            return log.traceExit(result1);
        }
        final Optional<String> result = Optional.empty();
        return log.traceExit(result);
    }

    private Optional<String> resolveClassName(final String name, final JavaSource source) {

        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        final ClassName className = new ClassName(name);
        String searchName = className.getName();
        final EntryMessage entryMessage = log.traceEntry("searchName={} name={}", searchName, name);

        String fqcn;

        // 1. check primitive
        if (ClassNameUtils.isPrimitive(searchName)) {
            final Optional<String> result = Optional.ofNullable(className.addTypeParameters(searchName));

            log.trace("resolved primitive name={} result={}", searchName, result);
            return log.traceExit(entryMessage, result);
        }

        // 2. resolve from imports
        fqcn = source.importClass.get(searchName);
        if (fqcn != null) {
            final Optional<String> result = Optional.ofNullable(className.addTypeParameters(fqcn));
            log.trace("resolved import class name={} result={}", searchName, result);
            return log.traceExit(entryMessage, result);
        }

        // 3. resolve from package scope
        final Optional<String> packageResult = source.getCurrentType().map(typeScope -> {
            String s = searchName;
            final String typeScopePackage = typeScope.getPackage();

            if (typeScopePackage != null) {
                s = typeScopePackage + '.' + searchName;
            }

            final String result = reflector.containsClassIndex(s)
                    .map(classIndex -> className.addTypeParameters(classIndex.getDeclaration()))
                    .orElse(null);
            return result;
        });

        if (packageResult.isPresent()) {
            log.trace("resolved package or global scope class name={} result={}", searchName, packageResult);
            return log.traceExit(entryMessage, packageResult);
        }

        // 4. resolve from globals (java.lang.*)
        fqcn = this.globalClassSymbol.get(searchName);
        if (fqcn != null) {
            final Optional<String> result = Optional.ofNullable(className.addTypeParameters(fqcn));
            log.trace("resolved default package name={} result={}", searchName, packageResult);
            return log.traceExit(entryMessage, result);
        }

        // 5. resolve current source
        fqcn = source.getCurrentType().map(ts -> {
            // current?
            String type = ts.getType();
            if (type.equals(searchName)) {
                return ts.getFQCN();
            }
            return null;
        }).orElseGet(() -> {
            // resolve from parsed source
            for (TypeScope ts : source.getTypeScopes()) {
                String type = ts.getType();
                if (type.equals(searchName)) {
                    return ts.getFQCN();
                }
            }
            return null;
        });

        if (fqcn != null && ClassNameUtils.getSimpleName(fqcn)
                .equals(ClassNameUtils.getSimpleName(searchName))) {
            final Optional<String> result = Optional.ofNullable(className.addTypeParameters(fqcn));
            log.trace("resolved current class name={} result={}", searchName, result);
            return log.traceExit(entryMessage, result);
        }

        // 6. search our class and inner class
        final Optional<String> result = this.searchInnerClass(source, className, searchName);

        return log.traceExit(entryMessage, result);
    }

    private Optional<String> searchInnerClass(final JavaSource source, final ClassName className, final String searchName) {

        final Optional<String> fqcn = source.getCurrentType().map(ts -> {
            String parentClass = ts.getFQCN();

            String result = searchInnerClassInternal(ts, className, parentClass, searchName);
            log.trace("search parentClass:{} searchName:{}", parentClass, searchName);

            while (result == null && parentClass.contains(ClassNameUtils.INNER_MARK)) {
                final int idx = parentClass.lastIndexOf("$");
                parentClass = parentClass.substring(0, idx);
                log.trace("search parentClass:{} searchName:{}", parentClass, searchName);
                result = searchInnerClassInternal(ts, className, parentClass, searchName);
                log.trace("result:{} parentClass:{}", result, parentClass);
            }
            return result;
        });

        return fqcn;
    }

    private String searchInnerClassInternal(final TypeScope ts, final ClassName className, final String parentClass, final String searchName) {
        final EntryMessage entryMessage = log.traceEntry("className={} parentClass={} searchName={}", className, parentClass, searchName);

        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        if (ts.getType().equals(searchName) && reflector.containsFQCN(parentClass)) {
            final String resolved = className.addTypeParameters(parentClass);
            return log.traceExit(entryMessage, resolved);
        }

        final String innerName = parentClass + '$' + searchName;
        if (reflector.containsFQCN(innerName) && innerName.endsWith(searchName)) {
            final String resolved = className.addTypeParameters(innerName);
            return log.traceExit(entryMessage, resolved);
        }

        if (reflector.getGlobalClassIndex().containsKey(parentClass)) {
            final Optional<String> result = reflector.getSuperClassStream(parentClass)
                    .map(superClass -> {
                        ClassName sc = new ClassName(superClass);
                        final String innerFQCN = sc.getName() + '$' + searchName;
                        log.trace("search inner name={}", innerFQCN);

                        if (reflector.containsFQCN(innerFQCN)) {
                            return className.addTypeParameters(innerFQCN);
                        }
                        return null;
                    })
                    .filter(s -> s != null)
                    .findFirst();
            if (result.isPresent()) {
                final String resolved = result.get();
                return log.traceExit(entryMessage, resolved);
            }
        }
        log.traceExit(entryMessage);
        return null;
    }

    private Optional<String> isTypeParameter(final String name, final JavaSource source) {
        return source.getCurrentType().map(typeScope -> {
            if (typeScope instanceof ClassScope) {
                final ClassScope cs = (ClassScope) typeScope;
                final Map<String, String> typeParameterMap = cs.getTypeParameterMap();
                if (typeParameterMap != null && typeParameterMap.containsKey(name)) {
                    final String fqcn = typeParameterMap.get(name);
                    log.trace("match typeParameter name={} fqcn={}", name, fqcn);
                    return fqcn;
                }
            }
            return null;
        });
    }

    private Optional<String> resolveSymbolName(final String name, final JavaSource source) {
        final ClassName className = new ClassName(name);
        final String symbolName = className.getName();
        final EntryMessage entryMessage = log.traceEntry("name={} symbolName={}", name, symbolName);

        {
            // check primitive
            if (ClassNameUtils.isPrimitive(symbolName)) {
                final Optional<String> result = Optional.ofNullable(className.addTypeParameters(symbolName));
                return log.traceExit(entryMessage, result);
            }
        }

        // search from field
        final String resultFQCN = source.getCurrentType()
                .map(ts -> this.resolveFromField(name, source, symbolName, ts))
                .orElseGet(() -> this.resolveFromSource(name, source));

        if (resultFQCN != null) {
            final Optional<String> result = Optional.ofNullable(className.addTypeParameters(resultFQCN));
            return log.traceExit(entryMessage, result);
        }
        final Optional<String> result = Optional.empty();
        return log.traceExit(entryMessage, result);
    }

    private String resolveFromSource(final String name, final JavaSource source) {
        final EntryMessage em = log.traceEntry("name={}", name);
        final String res = source.getTypeScopes()
                .stream()
                .map(TypeScope::getFieldSymbols)
                .filter(map -> map != null && map.containsKey(name))
                .map(map -> {
                    Variable ns = map.get(name);
                    if (ns != null) {
                        return ns.getFQCN();
                    }
                    return null;
                })
                .filter(s -> s != null)
                .findFirst()
                .orElse(null);
        log.traceExit(em, res);
        return res;
    }

    private String resolveFromField(final String name, final JavaSource source, final String symbolName, final TypeScope ts) {
        final EntryMessage em = log.traceEntry("name={} symbolName={}", name, symbolName);

        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        if (!symbolName.startsWith("this.")) {
            // search from current block symbol
            BlockScope blockScope = this.getCurrentBlockFromTS(ts);
            if (blockScope != null) {
                while (blockScope != null) {
                    log.trace("block={}", blockScope);
                    final Variable resolveNS = blockScope.getDeclaratorMap().get(name);
                    if (resolveNS != null) {
                        final String fqcn = resolveNS.getFQCN();
                        return log.traceExit(em, fqcn);
                    }
                    // from field
                    if (blockScope instanceof ClassScope) {
                        ClassScope classScope = (ClassScope) blockScope;
                        final Map<String, Variable> fieldSymbols = classScope.fieldSymbols;
                        final Variable variable = fieldSymbols.get(name);
                        if (variable != null) {
                            final String fqcn = variable.getFQCN();
                            return log.traceExit(em, fqcn);
                        }
                    }
                    blockScope = blockScope.getParent();
                }
            }
        }
        final String currentClass = ts.getFQCN();
        final String parentClass = ClassNameUtils.getParentClass(currentClass);

        final String searchField = symbolName.startsWith("this.") ? ClassNameUtils.replace(symbolName, "this.", "") : symbolName;

        for (TypeScope typeScope : source.getTypeScopes()) {
            final String fqcn = typeScope.getFQCN();
            if (fqcn.equals(currentClass) || fqcn.equals(parentClass)) {
                // search from class field
                Map<String, Variable> fieldMap = typeScope.getFieldSymbols();
                if (fieldMap != null && fieldMap.containsKey(searchField)) {
                    Variable ns = fieldMap.get(searchField);
                    if (ns != null) {
                        final String fqcn1 = ns.getFQCN();
                        return log.traceExit(em, fqcn1);
                    }
                }
            }
        }

        // search from class field
        final String res = reflector.reflectFieldStream(parentClass, searchField)
                .map(MemberDescriptor::getRawReturnType)
                .findFirst()
                .orElseGet(() -> reflector.reflectFieldStream(currentClass, searchField)
                        .map(MemberDescriptor::getRawReturnType)
                        .findFirst()
                        .orElse(null));
        log.traceExit(em, res);
        return res;
    }

    private BlockScope getCurrentBlockFromTS(TypeScope typeScope) {
        BlockScope blockScope = typeScope.currentBlock();
        if (blockScope == null) {
            return null;
        }
        return getCurrentBlock(blockScope);
    }

    private BlockScope getCurrentBlock(BlockScope blockScope) {
        if (blockScope.currentBlock() == null) {
            return blockScope;
        }
        blockScope = blockScope.currentBlock();
        return getCurrentBlock(blockScope);
    }

}
