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
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;
import org.jboss.windup.decompiler.api.DecompilationListener;
import org.jboss.windup.decompiler.api.DecompilationResult;
import org.jboss.windup.decompiler.fernflower.FernflowerDecompiler;
import org.jboss.windup.decompiler.util.Filter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static meghanada.utils.FunctionUtils.wrapIO;

public class LocationSearcher {

    public static final String TEMP_FILE_PREFIX = "meghanada-server";
    public static final String TEMP_DECOMPILE_DIR = "meghanada_decompile";
    private static final Logger log = LogManager.getLogger(LocationSearcher.class);
    private final List<LocationSearchFunction> locationSearchFunctions;
    private final Map<String, File> copiedSrcFile = new HashMap<>(16);
    private final Map<String, List<String>> decompileFiles = new HashMap<>(16);
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
        final String path = ClassNameUtils.replace(clazzName, ".", File.separator) + FileUtils.JAVA_EXT;
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

    private static Location searchLocationFromFile(final SearchContext ctx, final String fqcn, final File targetFile) throws IOException {
        try {
            final CompilationUnit compilationUnit = JavaParser.parse(targetFile, String.valueOf(StandardCharsets.UTF_8));
            final List<TypeDeclaration> types = compilationUnit.getTypes();
            for (final TypeDeclaration type : types) {
                if (ctx.kind.equals(SearchKind.CLASS)) {
                    final String typeName = type.getName();
                    final String simpleName = ClassNameUtils.getSimpleName(fqcn);
                    if (typeName.equals(simpleName)) {
                        return new Location(targetFile.getCanonicalPath(),
                                type.getBegin().line,
                                type.getBegin().column);
                    }
                }

                final List<BodyDeclaration> members = type.getMembers();
                ConstructorDeclaration constructor = null;
                MethodDeclaration method = null;

                for (final BodyDeclaration member : members) {
                    if (member instanceof FieldDeclaration &&
                            ctx.name != null &&
                            ctx.kind.equals(SearchKind.FIELD)) {
                        final Location variable = getFieldLocation(ctx, targetFile, (FieldDeclaration) member);
                        if (variable != null) {
                            return variable;
                        }
                    } else if (member instanceof ConstructorDeclaration &&
                            ctx.name != null &&
                            ctx.kind.equals(SearchKind.METHOD)) {
                        final ConstructorDeclaration declaration = (ConstructorDeclaration) member;
                        final String name = declaration.getName();
                        if (name.equals(ctx.name)) {
                            final List<Parameter> parameters = declaration.getParameters();
                            // TODO check FQCN types
                            if (ctx.arguments.size() == parameters.size()) {
                                return new Location(targetFile.getCanonicalPath(),
                                        declaration.getBegin().line,
                                        declaration.getBegin().column);
                            } else {
                                if (constructor == null) {
                                    constructor = declaration;
                                }
                            }
                        }
                    } else if (member instanceof MethodDeclaration &&
                            ctx.name != null &&
                            ctx.kind.equals(SearchKind.METHOD)) {
                        final MethodDeclaration declaration = (MethodDeclaration) member;
                        final String name = declaration.getName();
                        if (name.equals(ctx.name)) {
                            final List<Parameter> parameters = declaration.getParameters();
                            // TODO check FQCN types
                            if (ctx.arguments.size() == parameters.size()) {
                                return new Location(targetFile.getCanonicalPath(),
                                        declaration.getBegin().line,
                                        declaration.getBegin().column);
                            } else {
                                if (method == null) {
                                    method = declaration;
                                }
                            }
                        }
                    }
                }
                if (constructor != null) {
                    return new Location(targetFile.getCanonicalPath(),
                            constructor.getBegin().line,
                            constructor.getBegin().column);
                }
                if (method != null) {
                    return new Location(targetFile.getCanonicalPath(),
                            method.getBegin().line,
                            method.getBegin().column);
                }
            }
        } catch (ParseException e) {
            log.debug(e.getMessage());
        }
        return null;
    }

    private static Location getFieldLocation(SearchContext context, File targetFile, FieldDeclaration declaration) throws IOException {
        final List<VariableDeclarator> variables = declaration.getVariables();
        for (VariableDeclarator variable : variables) {
            final VariableDeclaratorId variableId = variable.getId();
            final String name = variableId.getName();
            if (name.equals(context.name)) {
                return new Location(targetFile.getCanonicalPath(),
                        variable.getBegin().line,
                        variable.getBegin().column);
            }
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
                            return Optional.ofNullable(searchFromSrcZip(context)).orElseGet(() -> {
                                try {
                                    return searchFromDependency(context);
                                } catch (IOException e) {
                                    throw new UncheckedExecutionException(e);
                                }
                            });
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
            return Optional.ofNullable(searchFromSrcZip(context)).orElseGet(() -> {
                try {
                    return searchFromDependency(context);
                } catch (IOException e) {
                    throw new UncheckedExecutionException(e);
                }
            });
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

        final Location loc = searchLocationFromFile(context, fqcn, temp);
        final boolean only = temp.setReadOnly();
        if (loc != null) {
            return loc;
        }
        return null;
    }

    private Location searchFromDependency(final SearchContext context) throws IOException {
        final String searchFQCN = context.searchFQCN;
        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        final File classFile = reflector.getClassFile(searchFQCN);
        final String tempDir = System.getProperty("java.io.tmpdir");
        if (classFile != null && classFile.exists() && classFile.getName().endsWith(FileUtils.JAR_EXT)) {
            final File depParent = classFile.getParentFile();
            final File dependencyDir = depParent.getParentFile();
            final String srcName = ClassNameUtils.replace(classFile.getName(), FileUtils.JAR_EXT, "-sources.jar");

            final String disable = System.getProperty("disable-source-jar");
            if (disable != null && disable.equals("true")) {
                return searchLocationFromDecompileFile(context, searchFQCN, classFile, tempDir);
            }

            return FileUtils.collectFile(dependencyDir, srcName).map(wrapIO(srcJar -> {
                final File file = copyFromSrcZip(searchFQCN, srcJar);
                if (file == null) {
                    return searchLocationFromDecompileFile(context, searchFQCN, classFile, tempDir);
                }
                final String fqcn = ClassNameUtils.getParentClass(context.searchFQCN);
                return searchLocationFromFile(context, fqcn, file);
            })).orElseGet(wrapIO(() -> {
                return searchLocationFromDecompileFile(context, searchFQCN, classFile, tempDir);
            }));
        }

        return null;
    }

    private Location searchLocationFromDecompileFile(SearchContext context, String searchFQCN, File classFile, String tempDir) throws IOException {
        final FernflowerDecompiler decompiler = new FernflowerDecompiler();
        decompiler.getLogger().setLevel(Level.OFF);
        final File output = new File(tempDir, TEMP_DECOMPILE_DIR);
        if (!output.exists()) {
            output.mkdirs();
        }
        try {
            final DecompilationResult decompilationResult = decompiler.decompileArchive(classFile.toPath(),
                    output.toPath(),
                    zipEntry -> {
                        final String name = zipEntry.getName();
                        final String base = ClassNameUtils.replace(searchFQCN, ".", "/");
                        final String search = base + FileUtils.CLASS_EXT;
                        if (name.equals(search)) {
                            return Filter.Result.ACCEPT;
                        }
                        final String inner = base + "$";
                        if (name.startsWith(inner)) {
                            return Filter.Result.ACCEPT;
                        }
                        return Filter.Result.REJECT;
                    }, new DefaultDecompileFilter());
            final String fqcn = ClassNameUtils.getParentClass(context.searchFQCN);

            if (this.decompileFiles.containsKey(fqcn)) {
                final List<String> files = this.decompileFiles.get(fqcn);
                for (final String decompileFile : files) {
                    final File file = new File(decompileFile);
                    final Location location = searchLocationFromFile(context, fqcn, file);
                    if (location != null) {
                        return location;
                    }
                }
            } else {
                final Map<String, String> decompiledFiles = decompilationResult.getDecompiledFiles();
                final List<String> tempList = new ArrayList<>();
                for (final String decompileFile : decompiledFiles.values()) {
                    final File decompiled = new File(decompileFile);
                    final File temp = File.createTempFile(TEMP_FILE_PREFIX, FileUtils.JAVA_EXT);
                    Files.copy(decompiled.toPath(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    tempList.add(temp.getCanonicalPath());

                    decompiled.deleteOnExit();
                    decompiled.delete();

                    temp.setReadOnly();
                    temp.deleteOnExit();
                    final Location location = searchLocationFromFile(context, fqcn, temp);
                    if (location != null) {
                        this.decompileFiles.put(fqcn, tempList);
                        return location;
                    }
                }
                this.decompileFiles.put(fqcn, tempList);
            }
            return null;
        } finally {
            FileUtils.deleteFiles(output, false);
        }
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
        try (final ZipFile srcZipFile = new ZipFile(srcZip)) {
            final String s = ClassNameUtils.replace(searchFQCN, ".", "/") + FileUtils.JAVA_EXT;
            final ZipEntry entry = srcZipFile.getEntry(s);
            if (entry == null) {
                return null;
            }

            // copy from src.zip
            final File temp = File.createTempFile(TEMP_FILE_PREFIX, FileUtils.JAVA_EXT);
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
                            return Optional.ofNullable(searchFromSrcZip(context)).orElseGet(() -> {
                                try {
                                    return searchFromDependency(context);
                                } catch (IOException e) {
                                    throw new UncheckedExecutionException(e);
                                }
                            });
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

    private static class DefaultDecompileFilter implements DecompilationListener {

        @Override
        public void fileDecompiled(List<String> list, String s) {

        }

        @Override
        public void decompilationFailed(List<String> list, String s) {

        }

        @Override
        public void decompilationProcessComplete() {

        }
    }
}
