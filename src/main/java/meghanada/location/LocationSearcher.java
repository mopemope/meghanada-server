package meghanada.location;

import com.github.javaparser.JavaParser;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.SimpleName;
import com.google.common.util.concurrent.UncheckedExecutionException;
import meghanada.analyze.*;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
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
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static meghanada.utils.FileUtils.existsFQCN;
import static meghanada.utils.FunctionUtils.wrapIO;
import static meghanada.utils.FunctionUtils.wrapIOConsumer;

public class LocationSearcher {

    private static final String TEMP_FILE_PREFIX = "meghanada-server";
    private static final String TEMP_DECOMPILE_DIR = "meghanada_decompile";

    private static final Logger log = LogManager.getLogger(LocationSearcher.class);
    private final List<LocationSearchFunction> functions;
    private final Map<String, File> copiedSrcFile = new HashMap<>(16);
    private final Map<String, List<String>> decompileFiles = new HashMap<>(16);
    private Project project;

    public LocationSearcher(final Project project) {
        this.functions = this.getFunctions();
        this.project = project;
    }

    private static Optional<Location> searchLocalVariable(final Source source,
                                                          final int line,
                                                          final int col,
                                                          final String symbol) {
        final EntryMessage entryMessage = log.traceEntry("line={} col={} symbol={}", line, col, symbol);

        final Map<String, Variable> variableMap = source.getDeclaratorMap(line);
        log.trace("variables={}", variableMap);
        final Optional<Variable> variable = Optional.ofNullable(variableMap.get(symbol));
        final Optional<Location> location = variable.map(var -> {
            final Location loc = new Location(source.getFile().getPath(),
                    var.range.begin.line,
                    var.range.begin.column);
            return Optional.of(loc);
        }).orElseGet(() -> {
            // isField
            final Optional<TypeScope> ts = source.getTypeScope(line);
            if (!ts.isPresent()) {
                return Optional.empty();
            }
            return ts.get().getField(symbol).map(fieldSymbol ->
                    new Location(source.getFile().getPath(),
                            fieldSymbol.range.begin.line,
                            fieldSymbol.range.begin.column));
        });
        log.traceExit(entryMessage);
        return location;
    }

    private static Source getSource(final Project project, final File file) throws IOException, ExecutionException {
        final GlobalCache globalCache = GlobalCache.getInstance();
        return globalCache.getSource(project, file.getCanonicalFile());
    }

    private static Location searchLocationFromFile(final SearchContext ctx, final String fqcn, final File targetFile) throws IOException {
        final CompilationUnit compilationUnit = JavaParser.parse(targetFile, StandardCharsets.UTF_8);
        final List<TypeDeclaration<?>> types = compilationUnit.getTypes();
        for (final TypeDeclaration<?> type : types) {
            if (ctx.kind.equals(SearchKind.CLASS)) {
                final SimpleName simpleName = type.getName();
                final String typeName = simpleName.getIdentifier();
                final String name = ClassNameUtils.getSimpleName(fqcn);
                final Optional<Position> begin = simpleName.getBegin();
                if (typeName.equals(name) && begin.isPresent()) {
                    final Position position = begin.get();
                    return new Location(targetFile.getCanonicalPath(),
                            position.line,
                            position.column);
                }
            }

            final List<BodyDeclaration<?>> members = type.getMembers();
            ConstructorDeclaration constructor = null;
            MethodDeclaration method = null;

            for (final BodyDeclaration<?> member : members) {
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
                    final SimpleName simpleName = declaration.getName();
                    final String name = simpleName.getIdentifier();
                    final Optional<Position> begin = simpleName.getBegin();
                    if (name.equals(ctx.name) && begin.isPresent()) {
                        final Position position = begin.get();
                        final List<Parameter> parameters = declaration.getParameters();
                        // TODO check FQCN types
                        if (ctx.arguments.size() == parameters.size()) {
                            return new Location(targetFile.getCanonicalPath(),
                                    position.line,
                                    position.column);
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
                    final SimpleName simpleName = declaration.getName();
                    final String name = simpleName.getIdentifier();
                    final Optional<Position> begin = simpleName.getBegin();
                    if (name.equals(ctx.name) && begin.isPresent()) {
                        final Position position = begin.get();
                        final List<Parameter> parameters = declaration.getParameters();
                        if (ctx.arguments.size() == parameters.size()) {
                            return new Location(targetFile.getCanonicalPath(),
                                    position.line,
                                    position.column);
                        } else {
                            if (method == null) {
                                method = declaration;
                            }
                        }
                    }
                }
            }
            if (constructor != null) {
                final Position pos = constructor.getName().getBegin().get();
                return new Location(targetFile.getCanonicalPath(),
                        pos.line,
                        pos.column);
            }
            if (method != null) {
                final Position pos = method.getName().getBegin().get();
                return new Location(targetFile.getCanonicalPath(),
                        pos.line,
                        pos.column);
            }
        }
        return null;
    }

    private static Location getFieldLocation(SearchContext context, File targetFile, FieldDeclaration declaration) throws IOException {
        final List<VariableDeclarator> variables = declaration.getVariables();
        for (final VariableDeclarator variable : variables) {
            final SimpleName simpleName = variable.getName();
            final String name = simpleName.getIdentifier();
            final Optional<Position> begin = simpleName.getBegin();
            if (name.equals(context.name) && begin.isPresent()) {
                final Position position = begin.get();
                return new Location(targetFile.getCanonicalPath(),
                        position.line,
                        position.column);
            }
        }
        return null;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Optional<Location> searchDeclarationLocation(final File file, final int line, final int column, final String symbol) throws ExecutionException, IOException {
        final Source source = getSource(project, file);
        log.trace("search symbol {}", symbol);

        return this.functions.stream()
                .map(f -> f.apply(source, line, column, symbol))
                .filter(Optional::isPresent)
                .findFirst()
                .orElse(Optional.empty());
    }

    private List<LocationSearchFunction> getFunctions() {
        final List<LocationSearchFunction> list = new ArrayList<>(4);
        list.add(this::searchField);
        list.add(this::searchMethodCall);
        list.add(this::searchClassOrInterface);
        list.add(LocationSearcher::searchLocalVariable);
        return list;
    }

    private Optional<Location> searchMethodCall(final Source source,
                                                final int line,
                                                final int col,
                                                final String symbol) {
        final EntryMessage entryMessage = log.traceEntry("line={} col={} symbol={}", line, col, symbol);
        final Optional<MethodCall> methodCall = source.getMethodCall(line, col, true);
        final Optional<Location> result = methodCall.flatMap(mc -> {
            final String methodName = mc.name;
            final List<String> arguments = mc.arguments;
            final String declaringClass = mc.declaringClass;

            final List<String> searchTargets = new ArrayList<>(2);
            searchTargets.add(declaringClass);
            final CachedASMReflector reflector = CachedASMReflector.getInstance();
            reflector.containsClassIndex(declaringClass).ifPresent(classIndex -> {
                final List<String> supers = classIndex.supers;
                searchTargets.addAll(supers);
            });

            return searchTargets.stream().map(targetFqcn -> existsFQCN(project.getAllSourcesWithDependencies(), targetFqcn)
                    .flatMap(file ->
                            getMethodLocationFromProject(methodName, arguments, file))
                    .orElseGet(wrapIO(() -> {
                        final SearchContext context = new SearchContext(targetFqcn, SearchKind.METHOD);
                        context.name = methodName;
                        context.arguments = arguments;
                        return Optional.ofNullable(searchFromSrcZip(context))
                                .orElseGet(wrapIO(() ->
                                        searchFromDependency(context)));
                    })))
                    .filter(Objects::nonNull)
                    .findFirst();
        });

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

    private Optional<Location> searchClassOrInterface(final Source source,
                                                      final int line,
                                                      final int col,
                                                      String symbol) {
        final EntryMessage entryMessage = log.traceEntry("line={} col={} symbol={}", line, col, symbol);
        if (symbol.startsWith("@")) {
            symbol = symbol.substring(1);
        }
        String fqcn = source.getImportedClassFQCN(symbol, null);
        if (fqcn == null) {
            final CachedASMReflector reflector = CachedASMReflector.getInstance();
            final Map<String, String> standardClasses = reflector.getStandardClasses();
            fqcn = standardClasses.get(symbol);
            if (fqcn == null) {
                if (source.packageName != null && !source.packageName.isEmpty()) {
                    fqcn = source.packageName + '.' + symbol;
                } else {
                    fqcn = symbol;
                }
            }
        }
        final String searchFQCN = fqcn;

        final Location location = existsFQCN(project.getAllSourcesWithDependencies(), fqcn).flatMap(f -> {
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
            return Optional.ofNullable(searchFromSrcZip(context))
                    .orElseGet(wrapIO(() ->
                            searchFromDependency(context)));
        }));

        log.traceExit(entryMessage);
        return Optional.ofNullable(location);
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
        if (!temp.setReadOnly()) {
            log.warn("{} setReadOnly fail", temp);
        }
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

            final String androidHome = System.getenv("ANDROID_HOME");
            if (androidHome != null) {
                final Optional<ProjectDependency> dependencyOptional = this.project.getDependencies()
                        .stream()
                        .filter(dependency -> dependency.getFile().equals(classFile))
                        .findFirst();
                if (dependencyOptional.isPresent()) {
                    final ProjectDependency dependency = dependencyOptional.get();
                    final String sourceJar = ClassNameUtils.getSimpleName(dependency.getId()) + '-' + dependency.getVersion() + "-sources.jar";
                    final File root = new File(androidHome, "extras");
                    if (root.exists()) {
                        return getLocationFromSrcOrDecompile(context, classFile, root, sourceJar);
                    }
                }
            }

            final File depParent = classFile.getParentFile();
            final File dependencyDir = depParent.getParentFile();
            final String srcJarName = ClassNameUtils.replace(classFile.getName(), FileUtils.JAR_EXT, "-sources.jar");

            final String disable = System.getProperty("disable-source-jar");
            if (disable != null && disable.equals("true")) {
                return searchLocationFromDecompileFile(context, searchFQCN, classFile, tempDir);
            }

            return getLocationFromSrcOrDecompile(context, classFile, dependencyDir, srcJarName);
        }

        return null;
    }

    private Location getLocationFromSrcOrDecompile(final SearchContext context, final File classFile, final File dependencyDir, final String srcName) throws IOException {
        final String tempDir = System.getProperty("java.io.tmpdir");
        final String searchFQCN = context.searchFQCN;
        return FileUtils.collectFile(dependencyDir, srcName).map(wrapIO(srcJar -> {
            final File file = copyFromSrcZip(searchFQCN, srcJar);
            if (file == null) {
                return searchLocationFromDecompileFile(context, searchFQCN, classFile, tempDir);
            }
            final String fqcn = ClassNameUtils.getParentClass(context.searchFQCN);
            return searchLocationFromFile(context, fqcn, file);
        })).orElseGet(wrapIO(() ->
                searchLocationFromDecompileFile(context, searchFQCN, classFile, tempDir)));
    }

    private Location searchLocationFromDecompileFile(SearchContext context, String searchFQCN, File classFile, String tempDir) throws IOException {
        final FernflowerDecompiler decompiler = new FernflowerDecompiler();
        decompiler.getLogger().setLevel(Level.OFF);
        final File output = new File(tempDir, TEMP_DECOMPILE_DIR);
        if (!output.exists() && !output.mkdirs()) {
            log.warn("{} mkdirs fail", output);
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
                        final String inner = base + '$';
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
                final List<String> tempList = new ArrayList<>(4);
                for (final String decompileFile : decompiledFiles.values()) {
                    final File decompiled = new File(decompileFile);
                    final File temp = File.createTempFile(TEMP_FILE_PREFIX + "-decompile-", FileUtils.JAVA_EXT);
                    this.copyAndFilter(decompiled, temp);
                    tempList.add(temp.getCanonicalPath());
                    decompiled.deleteOnExit();
                    if (!decompiled.delete()) {
                        log.warn("{} delete fail", decompiled);
                    }

                    if (!temp.setReadOnly()) {
                        log.warn("{} setReadOnly fail", temp);
                    }
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

    private void copyAndFilter(File decompiled, File temp) throws IOException {
        try (final BufferedWriter bw = Files.newBufferedWriter(temp.toPath(),
                StandardCharsets.UTF_8,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
             final Stream<String> stream = Files.lines(decompiled.toPath(), StandardCharsets.UTF_8)) {
            final Map<String, String> rename = new HashMap<>();

            stream.forEach(wrapIOConsumer(s -> {
                if (s.matches(".*\\d;$")) {
                    final String inner = s.substring(s.length() - 2, s.length() - 1);
                    final String innerClass = "Inner" + inner;
                    rename.put(" " + inner + " ", " " + innerClass + " ");
                    rename.put(" " + inner + "(", " " + innerClass + "(");
                    rename.put(" " + inner + ".", " " + innerClass + ".");
                    rename.put("(" + inner + ".", "(" + innerClass + ".");
                    final String replace = ClassNameUtils.replace(s, inner, innerClass);
                    bw.write(replace);
                    bw.newLine();
                } else {
                    final boolean match = rename.keySet()
                            .stream()
                            .anyMatch(s::contains);
                    if (match) {
                        final String replace = ClassNameUtils.replaceFromMap(s, rename);
                        bw.write(replace);
                    } else {
                        bw.write(s);
                    }
                    bw.newLine();
                }
            }));
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

    private Optional<Location> searchField(final Source src,
                                           final int line,
                                           final int col,
                                           final String symbol) {
        final EntryMessage entryMessage = log.traceEntry("line={} col={} symbol={}", line, col, symbol);

        final Optional<Location> result = src.searchFieldAccess(line, col, symbol).flatMap(fa -> {
            final String fieldName = fa.name;
            final String declaringClass = fa.declaringClass;

            final List<String> searchTargets = new ArrayList<>(2);
            searchTargets.add(declaringClass);

            final CachedASMReflector reflector = CachedASMReflector.getInstance();
            reflector.containsClassIndex(declaringClass).ifPresent(classIndex -> {
                final List<String> supers = classIndex.supers;
                searchTargets.addAll(supers);
            });

            return searchTargets.stream().map(fqcn -> existsFQCN(project.getAllSourcesWithDependencies(), fqcn)
                    .flatMap(file ->
                            getFieldLocationFromProject(fieldName, file))
                    .orElseGet(wrapIO(() -> {
                        final SearchContext context = new SearchContext(fqcn, SearchKind.FIELD);
                        context.name = fieldName;
                        return Optional.ofNullable(searchFromSrcZip(context))
                                .orElseGet(wrapIO(() -> searchFromDependency(context)));
                    })))
                    .filter(Objects::nonNull)
                    .findFirst();
        });
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
                    .filter(Optional::isPresent)
                    .map(optional -> {
                        final Variable variable = optional.get();
                        return new Location(path,
                                variable.range.begin.line,
                                variable.range.begin.column);
                    })
                    .findFirst();
        } catch (Exception e) {
            throw new UncheckedExecutionException(e);
        }
    }

    enum SearchKind {
        CLASS,
        FIELD,
        METHOD
    }

    @FunctionalInterface
    interface LocationSearchFunction {
        Optional<Location> apply(Source javaSource, Integer line, Integer column, String symbol);
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
