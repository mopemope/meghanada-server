package meghanada.completion;

import com.google.common.cache.LoadingCache;
import meghanada.config.Config;
import meghanada.parser.source.AccessSymbol;
import meghanada.parser.source.JavaSource;
import meghanada.parser.source.TypeScope;
import meghanada.parser.source.Variable;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassNameUtils;
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

        log.debug("line={} column={} prefix={}", line, column, prefix);
        try {
            final JavaSource source = this.sourceCache.get(file);
            // check type
            if (prefix.startsWith("*")) {
                // special command
                return this.specialCompletion(source, line, column, prefix);
            }
            if (prefix.startsWith("@")) {
                return this.annotationCompletion(source, line, column, prefix);
            }
            // search symbol
            return this.completionSymbols(source, line, prefix);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private Collection<? extends CandidateUnit> annotationCompletion(final JavaSource source, final int line, final int column, final String prefix) {
        final boolean useFuzzySearch = Config.load().useClassFuzzySearch();
        String classPrefix = prefix.substring(1);
        List<ClassIndex> result;
        if (useFuzzySearch) {
            result = CachedASMReflector
                    .getInstance()
                    .fuzzySearchAnnotations(classPrefix.toLowerCase());

        } else {
            result = CachedASMReflector
                    .getInstance()
                    .searchAnnotations(classPrefix.toLowerCase());
        }
        result.forEach(classIndex -> {
            classIndex.name = "@" + classIndex.name;
        });
        return result;
    }

    private List<MemberDescriptor> doReflect(String fqcn) {
        return CachedASMReflector.getInstance().reflect(fqcn);
    }

    private Collection<? extends CandidateUnit> completionThis(final JavaSource source, final int line, final String prefix) throws ExecutionException {
        final String fqcn = source.getTypeScope(line).getFQCN();
        return this.reflectSelf(source.getPkg(), fqcn, false, false, prefix);
    }

    private Collection<? extends CandidateUnit> completionSuper(final JavaSource source, final int line, final String prefix) throws ExecutionException {
        final TypeScope typeScope = source.getTypeScope(line);
        final String fqcn = typeScope.getFQCN();
        return doReflect(fqcn)
                .stream()
                .filter(md -> {
                    if (md.getDeclaringClass().equals(fqcn)) {
                        return false;
                    }
                    return !(prefix != null
                            && !prefix.isEmpty()
                            && !md.getName().toLowerCase().startsWith(prefix));
                })
                .collect(Collectors.toList());
    }

    private Collection<? extends CandidateUnit> specialCompletion(final JavaSource source, final int line, final int column, final String searchWord) throws ExecutionException {

        // special command
        final boolean useFuzzySearch = Config.load().useClassFuzzySearch();
        if (searchWord.startsWith("*import")) {
            return Collections.emptyList();
        } else if (searchWord.startsWith("*new")) {
            // list all classes
            int idx = searchWord.lastIndexOf(":");
            if (idx > 0) {
                String classPrefix = searchWord.substring(idx + 1, searchWord.length());
                if (useFuzzySearch) {
                    return CachedASMReflector.getInstance().fuzzySearchClasses(classPrefix.toLowerCase());
                }
                return CachedASMReflector.getInstance().searchClasses(classPrefix.toLowerCase());
            }
            return this.completionConstructors(source);
        } else if (searchWord.startsWith("*method")) {
            final int prefixIdx = searchWord.lastIndexOf("#");
            final int classIdx = searchWord.lastIndexOf(":");
            final String pkg = source.getPkg();

            if (classIdx > 0 && prefixIdx > 0) {
                final String prefix = searchWord.substring(prefixIdx + 1);
                // return methods of prefix class
                final String fqcn = searchWord.substring(classIdx + 1, prefixIdx);
                return reflect(pkg, fqcn, prefix);
            }
            // chained method completion

            if (classIdx > 0) {
                // return methods of prefix class
                final String fqcn = searchWord.substring(classIdx + 1, searchWord.length());
                return reflect(pkg, fqcn, "");

            } else {
                String prefix = "";
                if (prefixIdx > 0) {
                    prefix = searchWord.substring(prefixIdx + 1);
                }

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
                            return reflect(pkg, accessSymbol.getReturnType(), prefix);
                        }
                    }
                }

                return Collections.emptyList();
            }
        } else if (searchWord.startsWith("*package")) {
            // completion projects package
            return this.completionPackage();
        }
        // search fields or methods
        final int idx = searchWord.lastIndexOf("#");
        if (idx > 0) {
            final String var = searchWord.substring(1, idx);
            final String prefix = searchWord.substring(idx + 1);
            return this.completionFieldsOrMethods(source, line, var, prefix.toLowerCase());
        }

        return this.completionFieldsOrMethods(source, line, searchWord.substring(1), "");
    }

    private boolean publicFilter(final CandidateUnit cu, final boolean isStatic, final boolean withCONSTRUCTOR, final String target) {

        final String name = cu.getName().toLowerCase();
        if (target != null
                && !target.isEmpty()
                && !name.contains(target)) {
            return false;
        }

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

    private boolean packageFilter(final CandidateUnit cu, final boolean isStatic, final boolean withCONSTRUCTOR, final String target) {
        final String name = cu.getName().toLowerCase();

        if (target != null
                && !target.isEmpty()
                && !name.contains(target)) {
            return false;
        }
        final String declaration = cu.getDeclaration();
        if (declaration.contains("private")) {
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

    private boolean privateFilter(final CandidateUnit cu, final boolean isStatic, final boolean withCONSTRUCTOR, final String target) {

        final String name = cu.getName().toLowerCase();
        if (cu.getType().equals("FIELD") && name.startsWith("this$")) {
            return false;
        }

        if (target != null
                && !target.isEmpty()
                && !name.contains(target)) {
            return false;
        }

        final String declaration = cu.getDeclaration();
        if (!isStatic) {
            if (withCONSTRUCTOR) {
                return !declaration.contains("static");
            }
            return !declaration.contains("static") && !cu.getType().equals("CONSTRUCTOR");
        }
        return declaration.contains("static");
    }

    private Collection<? extends CandidateUnit> publicReflect(final String fqcn, final boolean isStatic, final boolean withCONSTRUCTOR, final String target) {
        return doReflect(fqcn)
                .stream()
                .filter(md -> this.publicFilter(md, isStatic, withCONSTRUCTOR, target))
                .collect(Collectors.toSet());
    }

    private Collection<? extends CandidateUnit> packageReflect(final String fqcn, final boolean noStatic, final boolean withCONSTRUCTOR, final String target) {
        return doReflect(fqcn)
                .stream()
                .filter(md -> this.packageFilter(md, noStatic, withCONSTRUCTOR, target))
                .collect(Collectors.toSet());
    }

    private Collection<? extends CandidateUnit> privateReflect(final String fqcn, final boolean noStatic, final boolean withCONSTRUCTOR, final String target) {
        return doReflect(fqcn)
                .stream()
                .filter(md -> this.privateFilter(md, noStatic, withCONSTRUCTOR, target))
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

    private Collection<? extends CandidateUnit> completionSymbols(final JavaSource source, final int line, final String prefix) throws ExecutionException {
        final List<CandidateUnit> result = new ArrayList<>(32);

        // prefix search
        log.debug("Search symbols Prefix:{} Line:{}", prefix, line);

        final String fqcn = source.getTypeScope(line).getFQCN();

        // add this scope member
        this.reflectSelf(source.getPkg(), fqcn, false, false, prefix)
                .stream()
                .filter(c -> c.getName().startsWith(prefix))
                .forEach(result::add);

        if (fqcn.contains(ClassNameUtils.INNER_MARK)) {
            // add parent
            String parentClass = fqcn;
            while (true) {
                int i = parentClass.lastIndexOf("$");
                if (i < 0) {
                    break;
                }
                parentClass = parentClass.substring(0, i);
                this.reflectSelf(source.getPkg(), parentClass, false, false, prefix)
                        .stream()
                        .filter(c -> c.getName().startsWith(prefix))
                        .forEach(result::add);
            }
        }

        log.debug("self fqcn:{}", fqcn);

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
//        for (CandidateUnit cu : source.getMemberDescriptors(line)) {
//            final String name = cu.getName();
//            if (name.startsWith(prefix)) {
//                result.add(cu);
//            }
//        }

        // Add class
        if (Character.isUpperCase(prefix.charAt(0))) {
            // completion
            final CachedASMReflector reflector = CachedASMReflector.getInstance();
            final boolean fuzzySearch = Config.load().useClassFuzzySearch();
            if (fuzzySearch) {
                result.addAll(reflector.fuzzySearchClasses(prefix.toLowerCase()));
            } else {
                result.addAll(reflector.searchClasses(prefix.toLowerCase()));
            }
        }

        return result;
    }

    private Collection<? extends CandidateUnit> completionFieldsOrMethods(final JavaSource source, final int line, final String var, final String target) throws ExecutionException {

        // completionAt methods or fields
        if (var.equals("this")) {
            return this.completionThis(source, line, target);
        }
        if (var.equals("super")) {
            return this.completionSuper(source, line, target);
        }

        log.debug("search {}'s field or method", var);

        final String ownPackage = source.getPkg();
        {
            // completion static method
            String fqcn = source.importClass.get(var);
            if (fqcn != null) {
                if (!fqcn.contains(".")
                        && ownPackage != null) {
                    fqcn = ownPackage + "." + fqcn;
                }

                final List<CandidateUnit> res = new ArrayList<>(32);
                final Collection<? extends CandidateUnit> result = this.reflect(ownPackage, fqcn, true, false, target);
                res.addAll(result);

                // add inner class
                final Collection<? extends CandidateUnit> inners = CachedASMReflector.getInstance().searchInnerClasses(fqcn);
                res.addAll(inners);

                if (!res.isEmpty()) {
                    return res;
                }
            }
        }

        {
            final Map<String, Variable> symbols = source.getDeclaratorMap(line);
            final Variable ns = symbols.get(var);
            if (ns != null) {
                // get data from reflector
                String fqcn = ns.getFQCN();
                if (!fqcn.contains(".")) {
                    fqcn = ownPackage + "." + fqcn;
                }
                return this.reflect(ownPackage, fqcn, target);
            }
        }
        {
            final Optional<MemberDescriptor> fieldResult = source.getAllMember()
                    .stream()
                    .filter(md -> md.matchType(CandidateUnit.MemberType.FIELD) && md.getName().equals(var))
                    .findFirst();

            if (fieldResult.isPresent()) {
                final MemberDescriptor memberDescriptor = fieldResult.orElse(null);
                final String returnType = memberDescriptor.getRawReturnType();
                return reflect(ownPackage, returnType, target);
            }
        }

        {
            // java.lang
            final String fqcn = "java.lang." + var;
            final Collection<? extends CandidateUnit> result = this.reflect(ownPackage, fqcn, true, false, target);
            if (!result.isEmpty()) {
                return result;
            }
        }

        return Collections.emptySet();
    }

    private Collection<? extends CandidateUnit> reflect(final String ownPackage, final String fqcn, final boolean isStatic, final boolean withConstructor, final String prefix) {
        if (fqcn.startsWith(ownPackage)) {
            // package
            return this.packageReflect(fqcn, isStatic, withConstructor, prefix);
        }
        return this.publicReflect(fqcn, isStatic, withConstructor, prefix);
    }

    private Collection<? extends CandidateUnit> reflectSelf(final String ownPackage, final String fqcn, final boolean isStatic, final boolean withConstructor, final String prefix) {
        return this.privateReflect(fqcn, isStatic, withConstructor, prefix);
    }

    private Collection<? extends CandidateUnit> reflect(final String ownPackage, final String fqcn, final String prefix) {
        return this.reflect(ownPackage, fqcn, false, false, prefix);
    }

}
