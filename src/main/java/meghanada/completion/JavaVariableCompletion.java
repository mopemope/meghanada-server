package meghanada.completion;

import com.google.common.cache.LoadingCache;
import meghanada.parser.source.AccessSymbol;
import meghanada.parser.source.JavaSource;
import meghanada.utils.ClassNameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atteo.evo.inflector.English;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class JavaVariableCompletion {
    private static Logger log = LogManager.getLogger(JavaCompletion.class);
    private static String[] pluralClass = new String[]{
            "List",
            "Set",
            "Collections"
    };

    private LoadingCache<File, JavaSource> sourceCache;

    public JavaVariableCompletion(LoadingCache<File, JavaSource> sourceCache) {
        this.sourceCache = sourceCache;
    }

    public LocalVariable localVariable(final File file, final int line) throws ExecutionException {
        final JavaSource source = this.sourceCache.get(file);
        AccessSymbol accessSymbol = source.getExpressionReturn(line);
        if (accessSymbol != null) {
            final String returnType = accessSymbol.getReturnType();
            if (returnType != null) {
                return createLocalVariable(accessSymbol, returnType);
            }
        }
        return null;
    }

    LocalVariable createLocalVariable(final AccessSymbol accessSymbol, final String returnType) {
        if (returnType.equals("void")) {
            return new LocalVariable(returnType, new ArrayList<>());
        }

        // completion
        final List<String> candidates = new ArrayList<>();

        // 1. from type
        final List<String> fromTypes = this.fromType(accessSymbol, returnType);
        candidates.addAll(fromTypes);

        // 2. add method or field name
        final List<String> fromNames = this.fromName(accessSymbol);
        candidates.addAll(fromNames);

        // 3. add returnType initials
        if (returnType.startsWith("java.lang") || ClassNameUtils.isPrimitive(returnType)) {
            final String initial = fromInitial(returnType);
            candidates.add(initial);
        }
        return new LocalVariable(returnType, candidates);
    }

    private List<String> fromType(final AccessSymbol accessSymbol, final String returnType) {
        Set<String> names = new HashSet<>();

        final String simpleName = ClassNameUtils.getSimpleName(returnType);
        final int i = simpleName.indexOf("<");
        if (i > 0) {
            final String gen = simpleName.substring(i);
            final String cls = simpleName.substring(0, i);
            String removed = StringUtils.removePattern(gen, "[<> ,$.]");
            if (this.isPlural(cls)) {
                removed = StringUtils.uncapitalize(English.plural(removed));
            }
            final String[] strings = StringUtils.splitByCharacterTypeCamelCase(removed);
            final List<String> nameList = new ArrayList<>(Arrays.asList(strings));
            this.addName(names, nameList);
            names.add(StringUtils.uncapitalize(removed + StringUtils.capitalize(cls)));
        } else {
            if (!ClassNameUtils.isPrimitive(returnType)) {
                final String n = StringUtils.uncapitalize(simpleName);
                final String[] strings = StringUtils.splitByCharacterTypeCamelCase(n);
                final List<String> nameList = new ArrayList<>(Arrays.asList(strings));
                this.addName(names, nameList);
            }
        }
        return new ArrayList<>(names);
    }

    private String fromInitial(final String returnType) {
        final String simpleName = ClassNameUtils.getSimpleName(returnType);
        return simpleName.substring(0, 1).toLowerCase();
    }

    private List<String> fromName(final AccessSymbol accessSymbol) {
        Set<String> names = new HashSet<>();

        String name = accessSymbol.getName();
        if (name.startsWith("get")) {
            name = name.substring(3);
        }

        // add simpleName
        final String uncapitalize = StringUtils.uncapitalize(name);
        final String[] strings = StringUtils.splitByCharacterTypeCamelCase(uncapitalize);
        final List<String> nameList = new ArrayList<>(Arrays.asList(strings));

        addName(names, nameList);
        names.add(uncapitalize);

        // add scope + simpleName
        String scope = accessSymbol.getScope();
        if (!scope.isEmpty() && !StringUtils.containsAny(scope, "(", ".", "$")) {
            //
            final String scopeName = StringUtils.uncapitalize(scope) + StringUtils.capitalize(name);
            names.add(scopeName);
        }

        return new ArrayList<>(names);
    }

    private void addName(final Set<String> names, final List<String> strings) {
        if (strings == null || strings.isEmpty()) {
            return;
        }
        final String joins = StringUtils.join(strings, "");
        names.add(joins);
        strings.remove(0);
        if (!strings.isEmpty()) {
            final String s = strings.remove(0);
            strings.add(0, StringUtils.uncapitalize(s));
            this.addName(names, strings);
        }
    }

    private boolean isPlural(final String returnType) {
        log.traceEntry("returnType={}", returnType);
        if (ClassNameUtils.isArray(returnType)) {
            return log.traceExit(true);
        }
        for (String cls : pluralClass) {
            if (returnType.endsWith(cls)) {
                return log.traceExit(true);
            }
        }
        return log.traceExit(false);
    }
}
