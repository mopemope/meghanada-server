package meghanada.completion;

import com.google.common.cache.LoadingCache;
import meghanada.parser.AccessSymbol;
import meghanada.parser.JavaSource;
import meghanada.parser.TypeScope;
import meghanada.parser.Variable;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class JavaCompletion {

    private static Logger log = LogManager.getLogger(JavaCompletion.class);

    private LoadingCache<File, JavaSource> sourceCache;

    public JavaCompletion(LoadingCache<File, JavaSource> sourceCache) {
        this.sourceCache = sourceCache;
    }

    public Collection<? extends CandidateUnit> completionAt(File file, int line, int column, String prefix) {
        try {
            final JavaSource source = this.sourceCache.get(file);
            // check type
            if (prefix.startsWith("*")) {
                // special command
                return this.specialCompletion(source, line, column, prefix);
            }
            // search symbol
            return this.completionSymbols(source, line, prefix);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<MemberDescriptor> doReflect(String fqcn) {
        return CachedASMReflector.getInstance().reflect(fqcn);
    }

    private Collection<? extends CandidateUnit> completionThis(JavaSource source) throws ExecutionException {
        final String fqcn = source.getTypeScopes().get(0).getFQCN();
        return reflect(source.getPkg(), fqcn);
    }

    private Collection<? extends CandidateUnit> completionSuper(JavaSource source, int line) throws ExecutionException {
        final TypeScope typeScope = source.getTypeScope(line);
        final String fqcn = typeScope.getFQCN();
        return doReflect(fqcn)
                .stream()
                .filter(md -> !md.getDeclaringClass().equals(fqcn))
                .collect(Collectors.toList());
    }

    private Collection<? extends CandidateUnit> specialCompletion(final JavaSource source, final int line, final int column, String prefix) throws ExecutionException {

        // special command
        if (prefix.equals("*import")) {
            // TODO
            return Collections.emptyList();
        } else if (prefix.contains("*new")) {
            // list all classes
            int idx = prefix.lastIndexOf(":");
            if (idx > 0) {
                String classPrefix = prefix.substring(idx + 1, prefix.length());
                return CachedASMReflector.getInstance().searchClasses(classPrefix.toLowerCase());
            }
            return this.completionConstructors(source);
        } else if (prefix.contains("*method")) {
            // chained method completion
            int idx = prefix.lastIndexOf(":");
            final String pkg = source.getPkg();

            if (idx > 0) {
                // return methods of prefix class
                String fqcn = prefix.substring(idx + 1, prefix.length());
                return reflect(pkg, fqcn);
            } else {
                // search near method call and return methods of prefix class
                List<AccessSymbol> targets = new ArrayList<>();
                targets.addAll(source.getMethodCallSymbols(line));
                targets.addAll(source.getFieldAccessSymbols(line));
                log.debug("targets:{}", targets);

                int size = targets.size();
                int startColumn = column;

                while (size > 0 && startColumn-- > 0) {
                    for (AccessSymbol accessSymbol : targets) {
                        if (accessSymbol.match(line, startColumn)) {
                            return reflect(pkg, accessSymbol.getReturnType());
                        }
                    }
                }

                return Collections.emptyList();
            }
        } else if (prefix.equals("*package")) {
            // completion projects package
            return this.completionPackage();
        }
        // search fields or methods
        prefix = prefix.substring(1);
        return this.completionFieldsOrMethods(source, line, prefix);
    }

    private boolean publicFilter(CandidateUnit cu, boolean isStatic, boolean withCONSTRUCTOR) {
        final String declaration = cu.getDeclaration();
        if (!declaration.contains("public")) {
            return false;
        }
        if (!isStatic) {
            if (withCONSTRUCTOR) {
                return !declaration.contains("static");
            }
            return !declaration.contains("static") && !cu.getType().equals("CONSTRUCTOR");
        }
        return declaration.contains("static");
    }

    private boolean packageFilter(CandidateUnit cu, boolean isStatic, boolean withCONSTRUCTOR) {
        final String declaration = cu.getDeclaration();
        if (!isStatic) {
            if (withCONSTRUCTOR) {
                return !declaration.contains("static");
            }
            return !declaration.contains("static") && !cu.getType().equals("CONSTRUCTOR");
        }
        return declaration.contains("static");
    }

    private Collection<? extends CandidateUnit> publicReflect(String fqcn, boolean isStatic, boolean withCONSTRUCTOR) {
        return doReflect(fqcn)
                .stream()
                .filter(md -> this.publicFilter(md, isStatic, withCONSTRUCTOR))
                .collect(Collectors.toSet());
    }

    private Collection<? extends CandidateUnit> packageReflect(String fqcn, boolean noStatic, boolean withCONSTRUCTOR) {
        return doReflect(fqcn)
                .stream()
                .filter(md -> this.packageFilter(md, noStatic, withCONSTRUCTOR))
                .collect(Collectors.toSet());
    }

    private Collection<? extends CandidateUnit> completionConstructors(final JavaSource source) throws ExecutionException {
        return source.importClass
                .values()
                .parallelStream()
                .map(this::doReflect)
                .flatMap(Collection::parallelStream)
                .filter(md -> md.getType().equals(CandidateUnit.MemberType.CONSTRUCTOR.name()))
                .collect(Collectors.toSet());
    }

    private Collection<? extends CandidateUnit> completionPackage() {
        log.debug("sourceCache:{}", this.sourceCache.asMap());
        return this.sourceCache.asMap()
                .values()
                .stream()
                .map(source -> ClassIndex.createPackage(source.getPkg()))
                .collect(Collectors.toSet());
    }

    private Collection<? extends CandidateUnit> completionSymbols(final JavaSource source, int line, String prefix) throws ExecutionException {
        final List<CandidateUnit> result = new ArrayList<>(16);

        // prefix search
        log.debug("Search symbols Prefix:{} Line:{}", prefix, line);
        // Map<String, Variable> symbols = source.getNameSymbol(line);
        final Map<String, Variable> symbols = source.getDeclaratorMap(line);
        log.debug("Search symbols Size:{} Result:{} Size:{}", symbols.size(), symbols);

        symbols.entrySet().forEach(entry -> {
            final String key = entry.getKey();
            log.debug("Variable Name:{}", key);
            if (key.startsWith(prefix)) {
                result.add(entry.getValue().toCandidateUnit());
            }
        });

        source.importClass.entrySet().forEach(entry -> {
            final String key = entry.getKey();
            if (key.startsWith(prefix)) {
                result.add(ClassIndex.createClass(entry.getValue()));
            }
        });

        // search this
        for (CandidateUnit cu : source.getMemberDescriptors(line)) {
            final String name = cu.getName();
            if (name.startsWith(prefix)) {
                result.add(cu);
            }
        }

        if (Character.isUpperCase(prefix.charAt(0))) {
            // completion
            CachedASMReflector reflector = CachedASMReflector.getInstance();
            return reflector.searchClasses(prefix.toLowerCase());
        }
        return result;
    }

    private Collection<? extends CandidateUnit> completionFieldsOrMethods(final JavaSource source, int line, String prefix) throws ExecutionException {
        // completionAt methods or fields
        if (prefix.equals("this")) {
            return this.completionThis(source);
        }
        if (prefix.equals("super")) {
            return this.completionSuper(source, line);
        }

        log.debug("search {}'s field or method", prefix);
        if (Character.isUpperCase(prefix.charAt(0))) {
            // completion static method
            String fqcn = source.importClass.get(prefix);
            if (fqcn != null) {
                if (!fqcn.contains(".")) {
                    // try same pkg
                    String pkg = source.getPkg();
                    if (pkg != null) {
                        fqcn = pkg + "." + fqcn;
                    }
                }
                final String pkg = source.getPkg();
                return reflect(pkg, fqcn, true, false);
            }
        } else {
            final Map<String, Variable> symbols = source.getDeclaratorMap(line);
            final Variable ns = symbols.get(prefix);
            final String pkg = source.getPkg();
            if (ns != null) {
                // get data from reflector
                String fqcn = ns.getFQCN();
                log.debug("FQCN {}", fqcn);
                if (!fqcn.contains(".")) {
                    fqcn = pkg + "." + fqcn;
                }
                return this.reflect(pkg, fqcn);
            }
            final Optional<MemberDescriptor> fieldResult = source.getAllMember()
                    .stream()
                    .filter(md -> md.matchType(CandidateUnit.MemberType.FIELD) && md.getName().equals(prefix))
                    .findFirst();
            if (fieldResult.isPresent()) {
                final MemberDescriptor memberDescriptor = fieldResult.orElse(null);
                final String returnType = memberDescriptor.getRawReturnType();
                return reflect(pkg, returnType);
            }
        }
        return Collections.emptySet();
    }

    private Collection<? extends CandidateUnit> reflect(String pkg, String fqcn, boolean isStatic, boolean withConstructor) {
        if (fqcn.startsWith(pkg)) {
            // package
            return packageReflect(fqcn, isStatic, withConstructor);
        }
        return publicReflect(fqcn, isStatic, withConstructor);
    }

    private Collection<? extends CandidateUnit> reflect(String pkg, String fqcn) {
        return this.reflect(pkg, fqcn, false, false);
    }

}
