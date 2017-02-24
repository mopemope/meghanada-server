package meghanada.location;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.google.common.util.concurrent.UncheckedExecutionException;
import meghanada.analyze.*;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static meghanada.utils.FunctionUtils.wrapIO;

public class LocationSearcher {

    private static final Logger log = LogManager.getLogger(LocationSearcher.class);
    private final List<LocationSearchFunction> locationSearchFunctions;
    private final Map<String, File> copiedSrcFile = new HashMap<>(16);
    private Project project;

    public LocationSearcher(final Project project) {
        this.locationSearchFunctions = this.getLocationSearchFunctions();
        this.project = project;
    }

    private static Optional<File> existsFQCN(final Set<File> roots, final String fqcn) {
        return roots.stream()
                .map(root -> toFile(root, fqcn))
                .filter(File::exists)
                .findFirst();
    }

    private static File toFile(final File root, final String fqcn) {
        final String clazzName = ClassNameUtils.getParentClass(fqcn);
        final String path = ClassNameUtils.replace(clazzName, ".", File.separator) + ".java";
        return new File(root, path);
    }

    private static Location searchLocalVariable(final Source source, final int line, final int col, final String symbol) {
        final EntryMessage entryMessage = log.traceEntry("line={} col={} symbol={}", line, col, symbol);

        final Map<String, Variable> variableMap = source.getDeclaratorMap(line);
        log.trace("variables={}", variableMap);
        final Optional<Variable> variable = Optional.ofNullable(variableMap.get(symbol));
        final Location location = variable.map(var -> {
            return new Location(source.getFile().getPath(),
                    var.range.begin.line,
                    var.range.begin.column);
        }).orElseGet(() -> {
            // isField
            final TypeScope ts = source.getTypeScope(line);
            if (ts == null) {
                return null;
            }
            final Variable fieldSymbol = ts.getField(symbol);
            if (fieldSymbol == null) {
                return null;
            }
            return new Location(source.getFile().getPath(),
                    fieldSymbol.range.begin.line,
                    fieldSymbol.range.begin.column);
        });
        log.traceExit(entryMessage);
        return location;
    }

    private static Source getSource(final Project project, final File file) throws IOException, ExecutionException {
        final GlobalCache globalCache = GlobalCache.getInstance();
        return globalCache.getSource(project, file.getCanonicalFile());
    }

    private static Location searchLocationFromSrcZIP(final SearchContext context, final String fqcn, final File temp) throws IOException {
        try {
            final CompilationUnit compilationUnit = JavaParser.parse(temp, String.valueOf(StandardCharsets.UTF_8));
            final List<TypeDeclaration> types = compilationUnit.getTypes();
            for (final TypeDeclaration type : types) {
                if (context.kind.equals(SearchKind.CLASS)) {
                    final String typeName = type.getName();
                    final String simpleName = ClassNameUtils.getSimpleName(fqcn);
                    if (typeName.equals(simpleName)) {
                        return new Location(temp.getCanonicalPath(),
                                type.getBegin().line,
                                type.getBegin().column);
                    }
                }

                final List<BodyDeclaration> members = type.getMembers();
                for (final BodyDeclaration member : members) {
                    if (member instanceof FieldDeclaration &&
                            context.name != null &&
                            context.kind.equals(SearchKind.FIELD)) {
                        final FieldDeclaration declaration = (FieldDeclaration) member;
                        final List<VariableDeclarator> variables = declaration.getVariables();
                        for (VariableDeclarator variable : variables) {
                            final VariableDeclaratorId variableId = variable.getId();
                            final String name = variableId.getName();
                            if (name.equals(context.name)) {
                                return new Location(temp.getCanonicalPath(),
                                        variable.getBegin().line,
                                        variable.getBegin().column);
                            }
                        }
                    } else if (member instanceof ConstructorDeclaration &&
                            context.name != null &&
                            context.kind.equals(SearchKind.METHOD)) {
                        final ConstructorDeclaration declaration = (ConstructorDeclaration) member;
                        final String name = declaration.getName();
                        if (name.equals(context.name)) {
                            final List<Parameter> parameters = declaration.getParameters();
                            // TODO check FQCN types
                            if (context.arguments.size() == parameters.size()) {
                                return new Location(temp.getCanonicalPath(),
                                        declaration.getBegin().line,
                                        declaration.getBegin().column);
                            }
                        }
                    } else if (member instanceof MethodDeclaration &&
                            context.name != null &&
                            context.kind.equals(SearchKind.METHOD)) {
                        final MethodDeclaration declaration = (MethodDeclaration) member;
                        final String name = declaration.getName();
                        if (name.equals(context.name)) {
                            final List<Parameter> parameters = declaration.getParameters();
                            // TODO check FQCN types
                            if (context.arguments.size() == parameters.size()) {
                                return new Location(temp.getCanonicalPath(),
                                        declaration.getBegin().line,
                                        declaration.getBegin().column);
                            }
                        }
                    }
                }
            }
        } catch (ParseException e) {
            throw new UncheckedExecutionException(e);
        }
        return null;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Location searchDeclaration(final File file, final int line, final int column, final String symbol) throws ExecutionException, IOException {
        final Source source = getSource(project, file);
        log.trace("search symbol {}", symbol);

        return this.locationSearchFunctions.stream()
                .map(f -> f.apply(source, line, column, symbol))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private List<LocationSearchFunction> getLocationSearchFunctions() {
        List<LocationSearchFunction> list = new ArrayList<>(4);
        list.add(this::searchField);
        list.add(this::searchMethodCall);
        list.add(this::searchClassOrInterface);
        list.add(LocationSearcher::searchLocalVariable);
        return list;
    }

    private Location searchMethodCall(final Source source, final int line, final int col, final String symbol) {
        final EntryMessage entryMessage = log.traceEntry("line={} col={} symbol={}", line, col, symbol);
        final Optional<MethodCall> methodCall = source.getMethodCall(line, col, true);

        final Location result = methodCall.map(mc -> {
            final String methodName = mc.name;
            final List<String> arguments = mc.arguments;
            final String fqcn = mc.declaringClass;

            final List<String> searchTargets = new ArrayList<>(2);
            searchTargets.add(fqcn);
            final CachedASMReflector reflector = CachedASMReflector.getInstance();
            reflector.containsClassIndex(fqcn).ifPresent(classIndex -> {
                final List<String> supers = classIndex.supers;
                searchTargets.addAll(supers);
            });

            for (final String targetFqcn : searchTargets) {
                final Location location = existsFQCN(project.getAllSources(), targetFqcn)
                        .flatMap(file -> {
                            return getMethodLocationFromProject(methodName, arguments, file);
                        }).orElseGet(wrapIO(() -> {
                            final SearchContext context = new SearchContext(targetFqcn, SearchKind.METHOD);
                            context.name = methodName;
                            context.arguments = arguments;
                            return searchFromSrcZip(context);
                        }));
                if (Objects.nonNull(location)) {
                    return location;
                }
            }
            return null;
        }).orElse(null);

        log.traceExit(entryMessage);
        return result;
    }

    private Optional<Location> getMethodLocationFromProject(final String methodName, final List<String> arguments, final File file) {
        try {
            final Source declaringClassSrc = getSource(project, file);
            final String path = declaringClassSrc.getFile().getPath();
            return declaringClassSrc.getClassScopes()
                    .stream()
                    .flatMap(ts -> ts.getScopes().stream())
                    .filter(bs -> {
                        if (!methodName.equals(bs.getName())) {
                            return false;
                        }
                        if (!(bs instanceof MethodScope)) {
                            return false;
                        }
                        final MethodScope methodScope = (MethodScope) bs;
                        final List<String> parameters = methodScope.parameters;
                        return ClassNameUtils.compareArgumentType(arguments, parameters);
                    })
                    .map(MethodScope.class::cast)
                    .map(ms -> new Location(path,
                            ms.getBeginLine(),
                            ms.getNameRange().begin.column))
                    .findFirst();
        } catch (ExecutionException e) {
            throw new UncheckedExecutionException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Location searchClassOrInterface(final Source source, final int line, final int col, final String symbol) {
        final EntryMessage entryMessage = log.traceEntry("line={} col={} symbol={}", line, col, symbol);
        String fqcn = source.importClass.get(symbol);
        if (fqcn == null) {
            final CachedASMReflector reflector = CachedASMReflector.getInstance();
            final Map<String, String> standardClasses = reflector.getStandardClasses();
            fqcn = standardClasses.get(symbol);
            if (fqcn == null) {
                if (source.packageName != null) {
                    fqcn = source.packageName + '.' + symbol;
                } else {
                    fqcn = symbol;
                }
            }
        }
        final String searchFQCN = fqcn;

        final Location location = existsFQCN(project.getAllSources(), fqcn).flatMap(f -> {
            try {
                final Source declaringClassSrc = getSource(project, f);
                final String path = declaringClassSrc.getFile().getPath();
                return declaringClassSrc
                        .getClassScopes()
                        .stream()
                        .filter(cs -> cs.getName().equals(searchFQCN))
                        .map(cs -> new Location(path,
                                cs.getBeginLine(),
                                cs.getNameRange().begin.column))
                        .findFirst();

            } catch (Exception e) {
                throw new UncheckedExecutionException(e);
            }
        }).orElseGet(wrapIO(() -> {
            final SearchContext context = new SearchContext(searchFQCN, SearchKind.CLASS);
            return searchFromSrcZip(context);
        }));
        log.traceExit(entryMessage);
        return location;
    }

    private Location searchFromSrcZip(final SearchContext context) throws IOException {

        final String javaHomeDir = Config.load().getJavaHomeDir();
        File srcZip = new File(javaHomeDir, "src.zip");
        if (!srcZip.exists()) {
            srcZip = new File(new File(javaHomeDir).getParentFile(), "src.zip");
        }
        if (!srcZip.exists()) {
            return null;
        }

        final String fqcn = ClassNameUtils.getParentClass(context.searchFQCN);
        final File temp = copyFromSrcZip(fqcn, srcZip);
        if (temp == null) {
            return null;
        }

        final Location type = searchLocationFromSrcZIP(context, fqcn, temp);
        if (type != null) {
            return type;
        }
        return null;
    }

    private File copyFromSrcZip(final String searchFQCN, final File srcZip) throws IOException {
        if (this.copiedSrcFile.containsKey(searchFQCN)) {
            final File file = this.copiedSrcFile.get(searchFQCN);
            if (file.exists()) {
                return file;
            }
            // deleted
            this.copiedSrcFile.remove(searchFQCN);
        }
        final ZipFile srcZipFile = new ZipFile(srcZip);
        final String s = ClassNameUtils.replace(searchFQCN, ".", "/") + ".java";
        final ZipEntry entry = srcZipFile.getEntry(s);
        if (entry == null) {
            return null;
        }

        // copy from src.zip
        final File temp = File.createTempFile("meghanada-server", ".java");
        temp.deleteOnExit();
        try (final InputStream inputStream = srcZipFile.getInputStream(entry);
             final OutputStream outputStream = new FileOutputStream(temp)) {
            final byte[] buf = new byte[1024];
            int ret;
            while ((ret = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, ret);
            }
        }
        // reuse
        this.copiedSrcFile.put(searchFQCN, temp);
        return temp;
    }

    private Location searchField(final Source src, final int line, final int col, final String symbol) {
        final EntryMessage entryMessage = log.traceEntry("line={} col={} symbol={}", line, col, symbol);

        final Location result = src.searchFieldAccess(line, symbol).map(fa -> {
            final String fieldName = fa.name;
            final String fqcn = fa.declaringClass;

            final List<String> searchTargets = new ArrayList<>(2);
            searchTargets.add(fqcn);

            final CachedASMReflector reflector = CachedASMReflector.getInstance();
            reflector.containsClassIndex(fqcn).ifPresent(classIndex -> {
                final List<String> supers = classIndex.supers;
                searchTargets.addAll(supers);
            });

            for (final String targetFqcn : searchTargets) {
                final Location location = existsFQCN(project.getAllSources(), targetFqcn)
                        .flatMap(file -> {
                            return getFieldLocationFromProject(fieldName, file);
                        }).orElseGet(wrapIO(() -> {
                            final SearchContext context = new SearchContext(targetFqcn, SearchKind.FIELD);
                            context.name = fieldName;
                            return searchFromSrcZip(context);
                        }));
                if (Objects.nonNull(location)) {
                    return location;
                }
            }
            return null;
        }).orElse(null);
        log.traceExit(entryMessage);
        return result;
    }

    private Optional<Location> getFieldLocationFromProject(String fieldName, File file) {
        try {
            final Source declaringClassSrc = getSource(project, file);
            final String path = declaringClassSrc.getFile().getPath();
            return declaringClassSrc
                    .getClassScopes()
                    .stream()
                    .map(ts -> ts.getField(fieldName))
                    .filter(Objects::nonNull)
                    .map(ns -> new Location(path,
                            ns.range.begin.line,
                            ns.range.begin.column))
                    .findFirst();
        } catch (Exception e) {
            throw new UncheckedExecutionException(e);
        }
    }

    private static class SearchContext {
        final String searchFQCN;
        final SearchKind kind;
        String name;
        List<String> arguments;

        SearchContext(final String searchFQCN, final SearchKind kind) {
            this.searchFQCN = searchFQCN;
            this.kind = kind;
        }
    }
}
