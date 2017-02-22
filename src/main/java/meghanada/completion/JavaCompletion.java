package meghanada.completion;

import com.google.common.cache.LoadingCache;
import meghanada.analyze.*;
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

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class JavaCompletion {

    private static final Logger log = LogManager.getLogger(JavaCompletion.class);

    private Project project;

    public JavaCompletion(final Project project) {
        this.project = project;
    }

    private static Collection<? extends CandidateUnit> annotationCompletion(final Source source, final int line,
                                                                            final int column, final String prefix) {
        final boolean useFuzzySearch = Config.load().useClassFuzzySearch();
        String classPrefix = prefix.substring(1);
        List<ClassIndex> result;
        if (useFuzzySearch) {
            result = CachedASMReflector.getInstance().fuzzySearchAnnotations(classPrefix.toLowerCase());

        } else {
            result = CachedASMReflector.getInstance().searchAnnotations(classPrefix.toLowerCase());
        }
        result.forEach(classIndex -> {
            final String name = ClassNameUtils.getSimpleName(ClassNameUtils.replaceInnerMark(classIndex.name));
            classIndex.name = '@' + name;
        });
        return result;
    }

    private static List<MemberDescriptor> doReflect(String fqcn) {
        return CachedASMReflector.getInstance().reflect(fqcn);
    }

    private static Collection<? extends CandidateUnit> completionSuper(final Source source, final int line, final String prefix) {
        final TypeScope typeScope = source.getTypeScope(line);
        final String fqcn = typeScope.getFQCN();
        return doReflect(fqcn).stream().filter(md -> {
            return !md.getDeclaringClass().equals(fqcn) && !(prefix != null && !prefix.isEmpty() && !md.getName().toLowerCase().startsWith(prefix));
        }).collect(Collectors.toList());
    }

    private static boolean publicFilter(final CandidateUnit cu, final boolean isStatic, final boolean withCONSTRUCTOR,
                                        final String target) {

        final String name = cu.getName().toLowerCase();
        if (target != null && !target.isEmpty() && !name.contains(target)) {
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

    private static boolean publicFilter(final CandidateUnit cu, final String target) {

        final String name = cu.getName().toLowerCase();
        if (target != null && !target.isEmpty() && !name.contains(target)) {
            return false;
        }
        if (cu.getType().equals("CONSTRUCTOR")) {
            return false;
        }

        final String declaration = cu.getDeclaration();
        return declaration.contains("public");
    }

    private static boolean packageFilter(final CandidateUnit cu, final boolean isStatic, final boolean withCONSTRUCTOR,
                                         final String target) {
        final String name = cu.getName().toLowerCase();

        if (target != null && !target.isEmpty() && !name.contains(target)) {
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

    private static boolean privateFilter(final CandidateUnit cu, final boolean isStatic, final boolean withCONSTRUCTOR,
                                         final String target) {

        final String name = cu.getName().toLowerCase();
        if (cu.getType().equals("FIELD") && name.startsWith("this$")) {
            return false;
        }

        if (target != null && !target.isEmpty() && !name.contains(target)) {
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

    private static boolean privateFilter(final CandidateUnit cu, final boolean withCONSTRUCTOR, final String target) {

        final String name = cu.getName().toLowerCase();
        return !(cu.getType().equals("FIELD") && name.startsWith("this$")) && !(target != null && !target.isEmpty() && !name.contains(target)) && (withCONSTRUCTOR || !cu.getType().equals("CONSTRUCTOR"));

    }

    private static Collection<? extends CandidateUnit> publicReflect(final String fqcn, final boolean isStatic,
                                                                     final boolean withCONSTRUCTOR, final String target) {
        return doReflect(fqcn).stream().filter(md -> JavaCompletion.publicFilter(md, isStatic, withCONSTRUCTOR, target))
                .collect(Collectors.toSet());
    }

    private static Collection<? extends CandidateUnit> packageReflect(final String fqcn, final boolean noStatic,
                                                                      final boolean withCONSTRUCTOR, final String target) {
        return doReflect(fqcn).stream().filter(md -> JavaCompletion.packageFilter(md, noStatic, withCONSTRUCTOR, target))
                .collect(Collectors.toSet());
    }

    private static Collection<? extends CandidateUnit> completionConstructors(final Source source) {
        return source.importClass.values().parallelStream().map(JavaCompletion::doReflect).flatMap(Collection::parallelStream)
                .filter(md -> md.getType().equals(CandidateUnit.MemberType.CONSTRUCTOR.name()))
                .collect(Collectors.toSet());
    }

    private static Collection<? extends CandidateUnit> completionThis(final Source source, final int line, final String prefix) {
        final String fqcn = source.getTypeScope(line).getFQCN();
        return JavaCompletion.reflectSelf(fqcn, false, prefix);
    }

    private static Collection<? extends CandidateUnit> privateReflect(final String fqcn, final boolean noStatic,
                                                                      final boolean withCONSTRUCTOR, final String target) {
        return doReflect(fqcn).stream().filter(md -> JavaCompletion.privateFilter(md, noStatic, withCONSTRUCTOR, target))
                .collect(Collectors.toSet());
    }

    private static Collection<? extends CandidateUnit> completionSymbols(final Source source, final int line, final String prefix) {
        final List<CandidateUnit> result = new ArrayList<>(32);

        // prefix search
        log.debug("Search variables prefix:{} line:{}", prefix, line);

        final TypeScope typeScope = source.getTypeScope(line);
        if (typeScope == null) {
            return result;
        }
        final String fqcn = typeScope.getFQCN();

        // add this member
        JavaCompletion.reflectSelf(fqcn, true, prefix).stream().filter(c -> c.getName().startsWith(prefix)).forEach(result::add);

        if (fqcn.contains(ClassNameUtils.INNER_MARK)) {
            // add parent
            String parentClass = fqcn;
            while (true) {
                int i = parentClass.lastIndexOf('$');
                if (i < 0) {
                    break;
                }
                parentClass = parentClass.substring(0, i);
                JavaCompletion.reflectSelf(parentClass, true, prefix).stream().filter(c -> c.getName().startsWith(prefix))
                        .forEach(result::add);
            }
        }

        log.debug("self fqcn:{}", fqcn);

        final Map<String, Variable> symbols = source.getDeclaratorMap(line);
        log.debug("search variables size:{} result:{}", symbols.size(), symbols);

        symbols.entrySet().forEach(entry -> {
            final String key = entry.getKey();
            log.debug("check variable name:{}", key);
            if (key.startsWith(prefix)) {
                log.debug("match variable name:{}", key);
                final Variable value = entry.getValue();
                if (!value.isField) {
                    result.add(entry.getValue().toCandidateUnit());
                }
            }
        });

        source.importClass.entrySet().forEach(entry -> {
            final String key = entry.getKey();
            if (key.startsWith(prefix)) {
                result.add(ClassIndex.createClass(entry.getValue()));
            }
        });

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

    private static Collection<? extends CandidateUnit> reflect(final String ownPackage, final String fqcn,
                                                               final boolean isStatic, final boolean withConstructor, final String prefix) {
        if (fqcn.startsWith(ownPackage)) {
            // package
            return JavaCompletion.packageReflect(fqcn, isStatic, withConstructor, prefix);
        }
        return JavaCompletion.publicReflect(fqcn, isStatic, withConstructor, prefix);
    }

    private static Collection<MemberDescriptor> reflectSelf(final String fqcn, final boolean withConstructor,
                                                            final String prefix) {
        final String target = prefix.toLowerCase();
        return doReflect(fqcn).stream().filter(md -> JavaCompletion.privateFilter(md, withConstructor, target))
                .collect(Collectors.toSet());
    }

    private static Collection<MemberDescriptor> reflectWithFQCN(final String ownPackage, final String fqcn, final String prefix) {
        final String target = prefix.toLowerCase();
        return doReflect(fqcn).stream().filter(md -> JavaCompletion.publicFilter(md, target)).collect(Collectors.toSet());
    }

    private static Collection<? extends CandidateUnit> reflect(final String ownPackage, final String fqcn, final String prefix) {
        return JavaCompletion.reflect(ownPackage, fqcn, false, false, prefix);
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    private Source getSource(final File file) throws IOException, ExecutionException {
        final GlobalCache globalCache = GlobalCache.getInstance();
        return globalCache.getSource(project, file.getCanonicalFile());
    }

    public Collection<? extends CandidateUnit> completionAt(final File file, int line, int column, String prefix) {

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
        } catch (Exception e) {
            log.catching(e);
            return Collections.emptyList();
        }
    }

    private Collection<? extends CandidateUnit> specialCompletion(final Source source, final int line,
                                                                  final int column, final String searchWord) throws ExecutionException {

        // special command
        final boolean useFuzzySearch = Config.load().useClassFuzzySearch();
        if (searchWord.startsWith("*import")) {
            return Collections.emptyList();
        } else if (searchWord.startsWith("*new")) {
            // list all classes
            int idx = searchWord.lastIndexOf(':');
            if (idx > 0) {
                String classPrefix = searchWord.substring(idx + 1, searchWord.length());
                if (useFuzzySearch) {
                    return CachedASMReflector.getInstance().fuzzySearchClasses(classPrefix.toLowerCase());
                }
                return CachedASMReflector.getInstance().searchClasses(classPrefix.toLowerCase());
            }
            return JavaCompletion.completionConstructors(source);
        } else if (searchWord.startsWith("*method")) {
            final int prefixIdx = searchWord.lastIndexOf('#');
            final int classIdx = searchWord.lastIndexOf(':');
            final String pkg = source.packageName;

            if (classIdx > 0 && prefixIdx > 0) {
                final String prefix = searchWord.substring(prefixIdx + 1);
                // return methods of prefix class
                String fqcn = searchWord.substring(classIdx + 1, prefixIdx);
                fqcn = ClassNameUtils.replace(fqcn, ClassNameUtils.CAPTURE_OF, "");
                return reflectWithFQCN(pkg, fqcn, prefix);
            }
            // chained method completion

            if (classIdx > 0) {
                // return methods of prefix class
                String fqcn = searchWord.substring(classIdx + 1, searchWord.length());
                fqcn = ClassNameUtils.replace(fqcn, ClassNameUtils.CAPTURE_OF, "");
                return reflect(pkg, fqcn, "");

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
                            final String fqcn = ClassNameUtils.replace(accessSymbol.returnType, ClassNameUtils.CAPTURE_OF, "");
                            return reflect(pkg, fqcn, prefix);
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
        final int idx = searchWord.lastIndexOf('#');
        if (idx > 0) {
            final String var = searchWord.substring(1, idx);
            final String prefix = searchWord.substring(idx + 1);
            return this.completionFieldsOrMethods(source, line, var, prefix.toLowerCase());
        }

        return this.completionFieldsOrMethods(source, line, searchWord.substring(1), "");
    }

    private Collection<? extends CandidateUnit> completionPackage() {
        final GlobalCache globalCache = GlobalCache.getInstance();
        final LoadingCache<File, Source> sourceCache = globalCache.getSourceCache(project);
        return sourceCache.asMap().values().stream().map(source -> ClassIndex.createPackage(source.packageName))
                .collect(Collectors.toSet());
    }

    private Collection<? extends CandidateUnit> completionFieldsOrMethods(final Source source, final int line,
                                                                          final String var, final String target) {

        // completionAt methods or fields
        if (var.equals("this")) {
            return JavaCompletion.completionThis(source, line, target);
        }
        if (var.equals("super")) {
            return JavaCompletion.completionSuper(source, line, target);
        }

        log.debug("search '{}' field or method", var);

        final String ownPackage = source.packageName;
        final List<CandidateUnit> res = new ArrayList<>(32);

        {
            // completion static method
            String fqcn = source.importClass.get(var);
            if (fqcn != null) {
                if (!fqcn.contains(".") && ownPackage != null) {
                    fqcn = ownPackage + '.' + fqcn;
                }

                final Collection<? extends CandidateUnit> result = JavaCompletion.reflect(ownPackage, fqcn, true, false, target);
                res.addAll(result);

                // add inner class
                final Collection<? extends CandidateUnit> inners = CachedASMReflector.getInstance().searchInnerClasses(
                        fqcn);
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
                final Collection<? extends CandidateUnit> reflect = JavaCompletion.reflect(ownPackage, fqcn, target);
                res.addAll(reflect);
            }
        }

        {
            for (final ClassScope cs : source.getClassScopes()) {
                final String fqcn = cs.getFQCN();
                final Optional<MemberDescriptor> fieldResult = JavaCompletion.reflectSelf(fqcn, true, target).stream()
                        .filter(c -> c instanceof FieldDescriptor && c.getName().equals(var)).findFirst();
                if (fieldResult.isPresent()) {
                    final MemberDescriptor memberDescriptor = fieldResult.orElse(null);
                    final String returnType = memberDescriptor.getRawReturnType();
                    final Collection<? extends CandidateUnit> reflect = reflect(ownPackage, returnType, target);
                    res.addAll(reflect);
                }
            }
        }

        {
            // java.lang
            final String fqcn = "java.lang." + var;
            final Collection<? extends CandidateUnit> result = JavaCompletion.reflect(ownPackage, fqcn, true, false, target);
            res.addAll(result);
        }

        {
            final String fqcn = ownPackage + '.' + var;
            final Collection<? extends CandidateUnit> reflectResults = JavaCompletion.reflect(ownPackage, fqcn, true, false, target);
            res.addAll(reflectResults);
            final CachedASMReflector reflector = CachedASMReflector.getInstance();
            final Collection<? extends CandidateUnit> inners = reflector.searchInnerClasses(fqcn);
            res.addAll(inners);
        }
        return res;

    }

}
