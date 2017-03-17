package meghanada.completion;

import meghanada.analyze.AccessSymbol;
import meghanada.analyze.Source;
import meghanada.cache.GlobalCache;
import meghanada.project.Project;
import meghanada.utils.ClassNameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atteo.evo.inflector.English;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class JavaVariableCompletion {

    private static final Logger log = LogManager.getLogger(JavaCompletion.class);

    private static final String[] pluralClass = new String[]{
            "List",
            "Set",
            "Collections"
    };

    private Project project;

    public JavaVariableCompletion(final Project project) {
        this.project = project;
    }

    private static String fromInitial(final String returnType) {
        final String simpleName = ClassNameUtils.getSimpleName(returnType);
        return simpleName.substring(0, 1).toLowerCase();
    }

    private static boolean isPlural(final String returnType) {
        log.traceEntry("returnType={}", returnType);
        if (ClassNameUtils.isArray(returnType)) {
            return log.traceExit(true);
        }
        for (final String cls : pluralClass) {
            if (returnType.endsWith(cls)) {
                return log.traceExit(true);
            }
        }
        return log.traceExit(false);
    }

    public void setProject(Project project) {
        this.project = project;
    }

    private Source getSource(final File file) throws IOException, ExecutionException {
        final GlobalCache globalCache = GlobalCache.getInstance();
        return globalCache.getSource(project, file.getCanonicalFile());
    }

    public Optional<LocalVariable> localVariable(final File file, final int line) throws ExecutionException, IOException {
        final Source source = this.getSource(file);
        return source.getExpressionReturn(line)
                .filter(as -> as.returnType != null)
                .flatMap(as ->
                        createLocalVariable(as, as.returnType));
    }

    Optional<LocalVariable> createLocalVariable(final AccessSymbol accessSymbol, final String returnType) {
        if (returnType.equals("void")) {
            return Optional.of(new LocalVariable(returnType, new ArrayList<>(0)));
        }

        // completion
        final List<String> candidates = new ArrayList<>(4);

        // 1. from type
        final List<String> fromTypes = this.fromType(returnType);
        candidates.addAll(fromTypes);

        // 2. add method or isField name
        final List<String> fromNames = this.fromName(accessSymbol);
        candidates.addAll(fromNames);

        // 3. add returnType initials
        if (returnType.startsWith("java.lang") || ClassNameUtils.isPrimitive(returnType)) {
            final String initial = fromInitial(returnType);
            candidates.add(initial);
        }

        final List<String> results = candidates.stream()
                .map(s -> StringUtils.removePattern(s, "[<> ,$.]"))
                .collect(Collectors.toList());

        return Optional.of(new LocalVariable(returnType, results));
    }

    private List<String> fromType(final String returnType) {
        Set<String> names = new HashSet<>(4);

        final String simpleName = ClassNameUtils.getSimpleName(returnType);
        final int i = simpleName.indexOf('<');
        if (i > 0) {
            final String gen = simpleName.substring(i);
            final String cls = simpleName.substring(0, i);
            String removed = StringUtils.removePattern(gen, "[<> ,$.]");
            if (JavaVariableCompletion.isPlural(cls)) {
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

    private List<String> fromName(final AccessSymbol accessSymbol) {
        final Set<String> names = new HashSet<>(4);

        String name = accessSymbol.name;
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
        final String scope = accessSymbol.scope;
        if (!scope.isEmpty() && !StringUtils.containsAny(scope, "(", ".", "$")) {
            //
            final String scopeName = StringUtils.uncapitalize(scope) + StringUtils.capitalize(name);
            names.add(scopeName);
        }

        return new ArrayList<>(names);
    }

    private void addName(final Set<String> names, final List<String> strings) {
        if (strings.isEmpty()) {
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
}
