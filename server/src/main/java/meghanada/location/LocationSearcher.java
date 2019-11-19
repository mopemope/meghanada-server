package meghanada.location;

import static java.util.Objects.isNull;
import static meghanada.utils.FileUtils.existsFQCN;
import static meghanada.utils.FunctionUtils.wrapIO;
import static meghanada.utils.FunctionUtils.wrapIOConsumer;

import com.github.javaparser.Position;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.SimpleName;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import meghanada.analyze.ClassScope;
import meghanada.analyze.MethodCall;
import meghanada.analyze.MethodScope;
import meghanada.analyze.Source;
import meghanada.analyze.TypeScope;
import meghanada.analyze.Variable;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.telemetry.TelemetryUtils;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.FileUtils;
import meghanada.utils.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.windup.decompiler.api.DecompilationListener;
import org.jboss.windup.decompiler.api.DecompilationResult;
import org.jboss.windup.decompiler.fernflower.FernflowerDecompiler;
import org.jboss.windup.decompiler.util.Filter;

public class LocationSearcher {

  private static final String TEMP_FILE_PREFIX = "meghanada-server";
  private static final String TEMP_DECOMPILE_DIR = "meghanada_decompile";
  private static final Logger log = LogManager.getLogger(LocationSearcher.class);
  private static final Pattern IMPORT_RE = Pattern.compile("import .*\\d;$");

  private final List<LocationSearchFunction> functions;
  private final Map<String, File> copiedSrcFile = new HashMap<>(16);
  private final Map<String, List<String>> decompileFiles = new HashMap<>(16);
  private final Supplier<Project> projectSupplier;

  public LocationSearcher(final Supplier<Project> supplier) {
    this.functions = this.getFunctions();
    this.projectSupplier = supplier;
  }

  private static Source getSource(final File file) throws IOException, ExecutionException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.getSource")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder().put("file", file.getPath()).build("args"));

      final GlobalCache globalCache = GlobalCache.getInstance();
      return globalCache.getSource(file.getCanonicalFile());
    }
  }

  private static Location searchLocationFromFile(
      final SearchContext ctx, final String fqcn, final File targetFile) throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.searchLocationFromFile")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("fqcn", fqcn)
              .put("targetFile", targetFile.getPath())
              .build("args"));

      CompilationUnit compilationUnit;
      try {
        compilationUnit = StaticJavaParser.parse(targetFile);
      } catch (Throwable e) {
        log.warn(e.getMessage(), e);
        return new Location(targetFile.getCanonicalPath(), 0, 0);
      }
      final List<TypeDeclaration<?>> types = compilationUnit.getTypes();
      for (final TypeDeclaration<?> type : types) {
        if (ctx.kind.equals(SearchKind.CLASS)) {
          final SimpleName simpleName = type.getName();
          final String typeName = simpleName.getIdentifier();
          final String name = ClassNameUtils.getSimpleName(fqcn);
          final Optional<Position> begin = simpleName.getBegin();
          if (typeName.equals(name) && begin.isPresent()) {
            final Position position = begin.get();
            return new Location(targetFile.getCanonicalPath(), position.line, position.column);
          }
        }

        final List<BodyDeclaration<?>> members = type.getMembers();
        ConstructorDeclaration constructor = null;
        MethodDeclaration method = null;

        for (final BodyDeclaration<?> member : members) {
          if (member instanceof FieldDeclaration
              && ctx.name != null
              && ctx.kind.equals(SearchKind.FIELD)) {
            final Location variable = getFieldLocation(ctx, targetFile, (FieldDeclaration) member);
            if (variable != null) {
              return variable;
            }
          } else if (member instanceof ConstructorDeclaration
              && ctx.name != null
              && ctx.kind.equals(SearchKind.METHOD)) {
            final ConstructorDeclaration declaration = (ConstructorDeclaration) member;
            final SimpleName simpleName = declaration.getName();
            final String name = simpleName.getIdentifier();
            final Optional<Position> begin = simpleName.getBegin();
            if (name.equals(ctx.name) && begin.isPresent()) {
              final Position position = begin.get();
              final List<Parameter> parameters = declaration.getParameters();
              // TODO check FQCN types
              if (ctx.arguments.size() == parameters.size()) {
                return new Location(targetFile.getCanonicalPath(), position.line, position.column);
              } else {
                if (constructor == null) {
                  constructor = declaration;
                }
              }
            }
          } else if (member instanceof MethodDeclaration
              && ctx.name != null
              && ctx.kind.equals(SearchKind.METHOD)) {
            final MethodDeclaration declaration = (MethodDeclaration) member;
            final SimpleName simpleName = declaration.getName();
            final String name = simpleName.getIdentifier();
            final Optional<Position> begin = simpleName.getBegin();
            if (name.equals(ctx.name) && begin.isPresent()) {
              final Position position = begin.get();
              final List<Parameter> parameters = declaration.getParameters();
              if (ctx.arguments.size() == parameters.size()) {
                return new Location(targetFile.getCanonicalPath(), position.line, position.column);
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
          return new Location(targetFile.getCanonicalPath(), pos.line, pos.column);
        }
        if (method != null) {
          final Position pos = method.getName().getBegin().get();
          return new Location(targetFile.getCanonicalPath(), pos.line, pos.column);
        }
      }
      return null;
    }
  }

  private static Location getFieldLocation(
      SearchContext context, File targetFile, FieldDeclaration declaration) throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.getFieldLocation")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder().put("targetFile", targetFile.getPath()).build("args"));

      final List<VariableDeclarator> variables = declaration.getVariables();
      for (final VariableDeclarator variable : variables) {
        final SimpleName simpleName = variable.getName();
        final String name = simpleName.getIdentifier();
        final Optional<Position> begin = simpleName.getBegin();
        if (name.equals(context.name) && begin.isPresent()) {
          final Position position = begin.get();
          return new Location(targetFile.getCanonicalPath(), position.line, position.column);
        }
      }
      return null;
    }
  }

  private static String replaceIgnoreStmt(String s) {
    Map<String, String> rename = new HashMap<>(8);
    rename.put(", (0)null", "");
    rename.put(", (1)null", "");
    rename.put(", (2)null", "");
    rename.put(", (3)null", "");
    String replaced = ClassNameUtils.replaceFromMap(s, rename);
    rename.put("(0)null", "");
    rename.put("(1)null", "");
    rename.put("(2)null", "");
    rename.put("(3)null", "");
    return ClassNameUtils.replaceFromMap(replaced, rename);
  }

  private static void copyAndFilter(final File decompiled, final File temp) throws IOException {
    try (final BufferedWriter bw =
            Files.newBufferedWriter(
                temp.toPath(),
                StandardCharsets.UTF_8,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
        final Stream<String> stream = Files.lines(decompiled.toPath(), StandardCharsets.UTF_8)) {
      final Map<String, String> rename = new HashMap<>(8);
      rename.put("(0)null", "");
      rename.put("(1)null", "");
      rename.put("(2)null", "");
      rename.put("(3)null", "");
      rename.put(", (0)null", "");
      rename.put(", (1)null", "");
      rename.put(", (2)null", "");
      rename.put(", (3)null", "");
      stream.forEach(
          wrapIOConsumer(
              s -> {
                if (IMPORT_RE.matcher(s).matches()) {
                  final String inner = s.substring(s.length() - 2, s.length() - 1);
                  final String innerClass = "Inner" + inner;
                  rename.put(' ' + inner + ' ', ' ' + innerClass + ' ');
                  rename.put(' ' + inner + '(', ' ' + innerClass + '(');
                  rename.put(' ' + inner + '.', ' ' + innerClass + '.');
                  rename.put('(' + inner + '.', '(' + innerClass + '.');
                  final String replace = StringUtils.replace(s, inner, innerClass);
                  bw.write(replaceIgnoreStmt(replace));
                  bw.newLine();
                } else {
                  final boolean match = rename.keySet().stream().anyMatch(s::contains);
                  if (match) {
                    final String replace = ClassNameUtils.replaceFromMap(s, rename);
                    bw.write(replaceIgnoreStmt(replace));
                  } else {
                    bw.write(replaceIgnoreStmt(s));
                  }
                  bw.newLine();
                }
              }));
    }
  }

  private static boolean matchClassName(final ClassScope cs, final String fqcn) {
    if (cs.getName().equals(fqcn)) {
      return true;
    }
    final List<ClassScope> classScopes = cs.classScopes;
    if (classScopes == null || classScopes.isEmpty()) {
      return false;
    }
    for (final ClassScope classScope : classScopes) {
      if (matchClassName(classScope, fqcn)) {
        return true;
      }
    }
    return false;
  }

  private static ClassScope getMatchClassScope(final ClassScope cs, final String fqcn) {
    if (cs.getName().equals(fqcn)) {
      return cs;
    }
    final List<ClassScope> classScopes = cs.classScopes;
    if (classScopes == null || classScopes.isEmpty()) {
      return null;
    }
    for (final ClassScope classScope : classScopes) {
      if (matchClassName(classScope, fqcn)) {
        return classScope;
      }
    }
    return null;
  }

  private static Optional<Variable> getMatchField(
      final ClassScope cs, final String fqcn, final String fieldName) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.getMatchField")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("fqcn", fqcn)
              .put("fieldName", fieldName)
              .build("args"));

      final ClassScope matchClassScope = getMatchClassScope(cs, fqcn);
      if (matchClassScope == null) {
        return Optional.empty();
      }
      return matchClassScope.getField(fieldName);
    }
  }

  private Optional<Location> searchLocalVariable(
      final Source source, final int line, final int column, final String symbol) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.searchLocalVariable")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("source", source.getFile().getPath())
              .put("line", line)
              .put("column", column)
              .build("args"));

      final Map<String, Variable> variableMap = source.getVariableMap(line);
      final Optional<Variable> variable = Optional.ofNullable(variableMap.get(symbol));
      final Optional<Location> location =
          variable
              .map(
                  var -> {
                    if (var.isDecl()) {
                      final Location loc =
                          new Location(
                              source.getFile().getPath(),
                              var.range.begin.line,
                              var.range.begin.column);
                      return Optional.of(loc);
                    } else {
                      final String fqcn = var.fqcn;
                      final Location loc = getFQCNLocation(fqcn);
                      return Optional.ofNullable(loc);
                    }
                  })
              .orElseGet(
                  () -> {
                    // is field
                    final Optional<TypeScope> ts = source.getTypeScope(line);
                    if (!ts.isPresent()) {
                      return Optional.empty();
                    }
                    return ts.get()
                        .getField(symbol)
                        .map(
                            fieldSymbol ->
                                new Location(
                                    source.getFile().getPath(),
                                    fieldSymbol.range.begin.line,
                                    fieldSymbol.range.begin.column));
                  });
      return location;
    }
  }

  public Optional<Location> searchSymbol(final String symbol) {
    return Optional.ofNullable(this.getFQCNLocation(symbol));
  }

  public Optional<Location> searchDeclarationLocation(
      final File file, final int line, final int column, final String symbol)
      throws ExecutionException, IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.searchDeclarationLocation")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("line", line)
              .put("column", column)
              .put("symbol", symbol)
              .build("args"));

      final Source source = getSource(file);
      log.trace("search symbol {} {}", symbol, source.getFile());

      return this.functions.stream()
          .map(f -> f.apply(source, line, column, symbol))
          .filter(Optional::isPresent)
          .findFirst()
          .orElse(Optional.empty());
    }
  }

  private List<LocationSearchFunction> getFunctions() {
    final List<LocationSearchFunction> list = new ArrayList<>(4);
    list.add(this::searchFieldAccess);
    list.add(this::searchMethodCall);
    list.add(this::searchClassOrInterface);
    list.add(this::searchLocalVariable);
    return list;
  }

  private Optional<Location> searchMethodCall(Source source, int line, int column, String symbol) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.searchMethodCall")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("source", source.getFile().getPath())
              .put("line", line)
              .put("column", column)
              .put("symbol", symbol)
              .build("args"));
      Optional<MethodCall> methodCall = source.getMethodCall(line, column, true);
      Optional<Location> result = methodCall.flatMap(mc -> searchMethodCallLocation(mc));
      return result;
    }
  }

  @SuppressWarnings("try")
  private Optional<Location> searchMethodCallLocation(MethodCall mc) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.searchMethodCallLocation")) {

      String methodName = mc.name;
      List<String> arguments = mc.getArguments();
      String declaringClass = mc.declaringClass;
      if (isNull(declaringClass)) {
        return Optional.empty();
      }
      List<String> targets = new ArrayList<>(2);
      targets.add(declaringClass);

      CachedASMReflector reflector = CachedASMReflector.getInstance();
      targets.addAll(reflector.getSuperClass(declaringClass));
      Project project = this.projectSupplier.get();
      return targets.stream()
          .map(
              fqcn ->
                  existsFQCN(project.getAllSourcesWithDependencies(), fqcn)
                      .flatMap(file -> getMethodLocationFromProject(methodName, arguments, file))
                      .orElseGet(
                          wrapIO(
                              () -> {
                                SearchContext context = new SearchContext(fqcn, SearchKind.METHOD);
                                context.name = methodName;
                                context.arguments = arguments;
                                return Optional.ofNullable(searchFromSrcZip(context))
                                    .orElseGet(wrapIO(() -> searchFromDependency(context)));
                              })))
          .filter(Objects::nonNull)
          .findFirst();
    }
  }

  private static Optional<Location> getMethodLocationFromProject(
      String methodName, List<String> arguments, File file) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.getMethodLocationFromProject")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder().put("methodName", methodName).build("args"));

      Source declaringClassSrc = getSource(file);
      String path = declaringClassSrc.getFile().getPath();
      return declaringClassSrc.getClassScopes().stream()
          .flatMap(ts -> ts.getScopes().stream())
          .filter(
              bs -> {
                if (!methodName.equals(bs.getName())) {
                  return false;
                }
                if (!(bs instanceof MethodScope)) {
                  return false;
                }
                MethodScope ms = (MethodScope) bs;
                List<String> parameters = ms.getParameters();
                return ClassNameUtils.compareArgumentType(arguments, parameters, ms.vararg);
              })
          .map(MethodScope.class::cast)
          .map(ms -> new Location(path, ms.getBeginLine(), ms.getNameRange().begin.column))
          .findFirst();
    } catch (ExecutionException e) {
      throw new UncheckedExecutionException(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Optional<Location> searchClassOrInterface(
      final Source source, final int line, final int column, String symbol) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.searchClassOrInterface")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("source", source.getFile().getPath())
              .put("line", line)
              .put("column", column)
              .put("symbol", symbol)
              .build("args"));

      if (symbol.startsWith("@")) {
        symbol = symbol.substring(1);
      }

      final List<String> searchTargets = new ArrayList<>(4);
      String fqcn = source.getImportedClassFQCN(symbol, null);
      if (fqcn == null) {
        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        final Map<String, String> standardClasses = reflector.getStandardClasses();
        fqcn = standardClasses.get(symbol);
        if (fqcn == null) {
          if (!source.getPackageName().isEmpty()) {
            fqcn = source.getPackageName() + '.' + symbol;
          } else {
            fqcn = symbol;
          }
          searchTargets.add(fqcn);
          // Add inner class
          final String finalSym = symbol;
          source
              .getTypeScope(line)
              .ifPresent(
                  typeScope -> {
                    final String firstFQCN = typeScope.getFQCN();
                    searchTargets.add(firstFQCN + ClassNameUtils.INNER_MARK + finalSym);
                  });

          source.usingClasses.forEach(
              clazz -> {
                String name = ClassNameUtils.getSimpleName(clazz);
                if (name.equals(finalSym)) {
                  searchTargets.add(clazz);
                } else if (name.contains("$")) {
                  for (String s : name.split("$")) {
                    if (s.endsWith(finalSym)) {
                      searchTargets.add(clazz);
                    }
                  }
                }
              });
        }
      } else {
        searchTargets.add(fqcn);
      }

      return searchTargets.stream().map(this::getFQCNLocation).filter(Objects::nonNull).findFirst();
    }
  }

  private Location getFQCNLocation(final String fqcn) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.getFQCNLocation")) {

      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("fqcn", fqcn).build("args"));

      final Project project = this.projectSupplier.get();
      return existsFQCN(project.getAllSourcesWithDependencies(), fqcn)
          .flatMap(
              f -> {
                try {
                  final Source declaringClassSrc = getSource(f);
                  final String path = declaringClassSrc.getFile().getPath();
                  return declaringClassSrc.getClassScopes().stream()
                      .filter(cs -> matchClassName(cs, fqcn))
                      .map(
                          cs -> {
                            final ClassScope match = getMatchClassScope(cs, fqcn);
                            if (isNull(match)) {
                              return null;
                            }
                            return new Location(
                                path, match.getBeginLine(), match.getNameRange().begin.column);
                          })
                      .filter(Objects::nonNull)
                      .findFirst();
                } catch (Exception e) {
                  throw new UncheckedExecutionException(e);
                }
              })
          .orElseGet(
              wrapIO(
                  () -> {
                    final SearchContext context = new SearchContext(fqcn, SearchKind.CLASS);
                    return Optional.ofNullable(searchFromSrcZip(context))
                        .orElseGet(wrapIO(() -> searchFromDependency(context)));
                  }));
    }
  }

  @SuppressWarnings("try")
  private Location searchFromSrcZip(final SearchContext context) throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.searchFromSrcZip")) {

      final String javaHomeDir = Config.load().getJavaHomeDir();
      File srcZip;

      Config config = Config.load();
      if (config.isJava8()) {
        srcZip = new File(javaHomeDir, "src.zip");
        if (!srcZip.exists()) {
          srcZip = new File(new File(javaHomeDir).getParentFile(), "src.zip");
        }
        if (!srcZip.exists()) {
          return null;
        }
      } else {
        srcZip = new File(javaHomeDir + File.separator + "lib", "src.zip");
        if (!srcZip.exists()) {
          srcZip = new File(new File(javaHomeDir).getParentFile(), "src.zip");
        }
        if (!srcZip.exists()) {
          return null;
        }
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
  }

  @SuppressWarnings("try")
  private Location searchFromDependency(final SearchContext context) throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.searchFromDependency")) {

      final String searchFQCN = context.searchFQCN;
      final CachedASMReflector reflector = CachedASMReflector.getInstance();
      final File classFile = reflector.getClassFile(searchFQCN);
      final String tempDir = System.getProperty("java.io.tmpdir");
      if (classFile != null
          && classFile.exists()
          && classFile.getName().endsWith(FileUtils.JAR_EXT)) {

        final String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null) {
          final Optional<ProjectDependency> dependencyOptional =
              this.projectSupplier.get().getDependencies().stream()
                  .filter(dependency -> dependency.getFile().equals(classFile))
                  .findFirst();
          if (dependencyOptional.isPresent()) {
            final ProjectDependency dependency = dependencyOptional.get();
            final String sourceJar =
                ClassNameUtils.getSimpleName(dependency.getId())
                    + '-'
                    + dependency.getVersion()
                    + "-sources.jar";
            final File root = new File(androidHome, "extras");
            if (root.exists()) {
              return getLocationFromSrcOrDecompile(context, classFile, root, sourceJar);
            }
          }
        }

        final File depParent = classFile.getParentFile();
        final File dependencyDir = depParent.getParentFile();
        final String srcJarName =
            StringUtils.replace(classFile.getName(), FileUtils.JAR_EXT, "-sources.jar");

        final String disable = System.getProperty("disable-source-jar");
        if (disable != null && disable.equals("true")) {
          return searchLocationFromDecompileFile(context, searchFQCN, classFile, tempDir);
        }

        return getLocationFromSrcOrDecompile(context, classFile, dependencyDir, srcJarName);
      }

      return null;
    }
  }

  @SuppressWarnings("try")
  private Location getLocationFromSrcOrDecompile(
      final SearchContext context,
      final File classFile,
      final File dependencyDir,
      final String srcName)
      throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.getLocationFromSrcOrDecompile")) {

      final String tempDir = System.getProperty("java.io.tmpdir");
      final String searchFQCN = context.searchFQCN;
      return FileUtils.collectFile(dependencyDir, srcName)
          .map(
              wrapIO(
                  srcJar -> {
                    final File file = copyFromSrcZip(searchFQCN, srcJar);
                    if (file == null) {
                      return searchLocationFromDecompileFile(
                          context, searchFQCN, classFile, tempDir);
                    }
                    final String fqcn = ClassNameUtils.getParentClass(context.searchFQCN);
                    return searchLocationFromFile(context, fqcn, file);
                  }))
          .orElseGet(
              wrapIO(
                  () -> searchLocationFromDecompileFile(context, searchFQCN, classFile, tempDir)));
    }
  }

  @SuppressWarnings("try")
  private Location searchLocationFromDecompileFile(
      SearchContext context, String searchFQCN, File classFile, String tempDir) throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.searchLocationFromDecompileFile")) {

      final FernflowerDecompiler decompiler = new FernflowerDecompiler();
      decompiler.getLogger().setLevel(Level.OFF);
      final File output = new File(tempDir, TEMP_DECOMPILE_DIR);
      if (!output.exists() && !output.mkdirs()) {
        log.warn("{} mkdirs fail", output);
      }
      try {
        final DecompilationResult decompilationResult =
            decompiler.decompileArchive(
                classFile.toPath(),
                output.toPath(),
                zipEntry -> {
                  final String name = zipEntry.getName();
                  final String base = StringUtils.replace(searchFQCN, ".", "/");
                  final String search = base + FileUtils.CLASS_EXT;
                  if (name.equals(search)) {
                    return Filter.Result.ACCEPT;
                  }
                  final String inner = base + '$';
                  if (name.startsWith(inner)) {
                    return Filter.Result.ACCEPT;
                  }
                  return Filter.Result.REJECT;
                },
                new DefaultDecompileFilter());
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
            final File temp =
                File.createTempFile(TEMP_FILE_PREFIX + "-decompile-", FileUtils.JAVA_EXT);
            LocationSearcher.copyAndFilter(decompiled, temp);
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
        org.apache.commons.io.FileUtils.deleteDirectory(output);
      }
    }
  }

  @SuppressWarnings("try")
  private File copyFromSrcZip(final String searchFQCN, final File srcZip) throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.copyFromSrcZip")) {

      if (this.copiedSrcFile.containsKey(searchFQCN)) {
        final File file = this.copiedSrcFile.get(searchFQCN);
        if (file.exists()) {
          return file;
        }
        // deleted
        this.copiedSrcFile.remove(searchFQCN);
      }
      try (final ZipFile srcZipFile = new ZipFile(srcZip)) {
        final String s = StringUtils.replace(searchFQCN, ".", "/") + FileUtils.JAVA_EXT;
        ZipEntry entry = srcZipFile.getEntry(s);
        if (entry == null) {
          Optional<? extends ZipEntry> zipEntry =
              srcZipFile.stream()
                  .filter(
                      e -> {
                        Path p = Paths.get(e.getName());
                        if (p.getNameCount() < 2) {
                          return false;
                        }
                        Path subpath = p.subpath(1, p.getNameCount());
                        return subpath.toString().equals(s);
                      })
                  .findFirst();

          if (!zipEntry.isPresent()) {
            return null;
          }
          entry = zipEntry.get();
        }

        String tmpdir = System.getProperty("java.io.tmpdir");
        File tmpParent = new File(tmpdir);
        if (!tmpParent.exists() && !tmpParent.mkdirs()) {
          log.warn("fail create tmpdir");
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
  }

  private Optional<Location> searchFieldAccess(Source source, int line, int column, String symbol) {
    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.searchFieldAccess")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("source", source.getFile().getPath())
              .put("line", line)
              .put("column", column)
              .put("symbol", symbol)
              .build("args"));

      return source
          .searchFieldAccess(line, column, symbol)
          .flatMap(
              fa -> {
                String fieldName = fa.name;
                String declaringClass = fa.declaringClass;
                if (declaringClass == null) {
                  return Optional.empty();
                }
                List<String> targets = new ArrayList<>(2);
                targets.add(declaringClass);
                CachedASMReflector reflector = CachedASMReflector.getInstance();
                targets.addAll(reflector.getSuperClass(declaringClass));
                Project project = this.projectSupplier.get();
                return targets.stream()
                    .map(
                        fqcn ->
                            existsFQCN(project.getAllSourcesWithDependencies(), fqcn)
                                .flatMap(file -> getFieldLocationFromProject(fqcn, fieldName, file))
                                .orElseGet(
                                    wrapIO(
                                        () -> {
                                          SearchContext context =
                                              new SearchContext(fqcn, SearchKind.FIELD);
                                          context.name = fieldName;
                                          return Optional.ofNullable(searchFromSrcZip(context))
                                              .orElseGet(
                                                  wrapIO(() -> searchFromDependency(context)));
                                        })))
                    .filter(Objects::nonNull)
                    .findFirst();
              });
    }
  }

  private static Optional<Location> getFieldLocationFromProject(
      final String fqcn, final String fieldName, final File file) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("LocationSearcher.getFieldLocationFromProject")) {

      scope.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("fqcn", fqcn)
              .put("fieldName", fieldName)
              .put("file", file.getPath())
              .build("args"));

      final Source declaringClassSrc = getSource(file);
      final String path = declaringClassSrc.getFile().getPath();
      return declaringClassSrc.getClassScopes().stream()
          .map(cs -> getMatchField(cs, fqcn, fieldName))
          .filter(Optional::isPresent)
          .map(
              optional -> {
                final Variable variable = optional.get();
                return new Location(path, variable.range.begin.line, variable.range.begin.column);
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
    public void fileDecompiled(List<String> list, String s) {}

    @Override
    public void decompilationFailed(List<String> list, String s) {}

    @Override
    public void decompilationProcessComplete() {}

    @Override
    public boolean isCancelled() {
      return false;
    }
  }
}
