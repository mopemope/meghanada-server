package meghanada.analyze;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.base.Joiner;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import meghanada.index.IndexableWord;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassName;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

public class TreeAnalyzer {

  private static final Logger log = LogManager.getLogger(TreeAnalyzer.class);

  TreeAnalyzer() {}

  private static Optional<String> getExpressionType(Source src, JCTree.JCExpression e) {
    if (e instanceof JCTree.JCFieldAccess) {
      JCTree.JCFieldAccess fieldAccess = (JCTree.JCFieldAccess) e;
      Symbol sym = fieldAccess.sym;
      if (nonNull(sym)) {

        com.sun.tools.javac.util.Name name = sym.flatName();
        return Optional.of(name.toString());
      }
      return Optional.empty();
    } else if (e instanceof JCTree.JCWildcard) {
      JCTree.JCWildcard wildcard = (JCTree.JCWildcard) e;
      Type type = wildcard.type;
      Type.WildcardType wildcardType = (Type.WildcardType) type;
      if (nonNull(wildcardType) && nonNull(wildcardType.type)) {
        if (wildcardType.kind.toString().equals("?") && nonNull(wildcardType.type.tsym)) {
          String s = wildcardType.type.tsym.flatName().toString();
          return Optional.of(s);
        }
        if (nonNull(wildcardType.type.tsym)) {
          String s = wildcardType.kind.toString() + wildcardType.type.tsym.flatName().toString();
          return Optional.of(s);
        }
      }
      return Optional.empty();
    } else if (e instanceof JCTree.JCArrayTypeTree) {
      JCTree.JCArrayTypeTree arrayTypeTree = (JCTree.JCArrayTypeTree) e;
      Type type = arrayTypeTree.type;
      if (nonNull(type) && type instanceof Type.ArrayType) {
        Type.ArrayType arrayType = (Type.ArrayType) type;
        Type componentType = arrayType.getComponentType();
        if (nonNull(componentType) && nonNull(componentType.tsym)) {
          String base = arrayType.getComponentType().tsym.flatName().toString();
          return Optional.of(base + ClassNameUtils.ARRAY);
        }
      }
      return Optional.empty();
    } else {
      Type type = e.type;
      String typeArgType = e.toString();
      if (nonNull(type) && nonNull(type.tsym)) {
        typeArgType = type.tsym.flatName().toString();
      } else {
        typeArgType = src.getImportedClassFQCN(typeArgType, typeArgType);
      }
      return Optional.ofNullable(typeArgType);
    }
  }

  private static String resolveTypeFromImport(Source src, JCTree tree) {

    if (tree instanceof JCTree.JCTypeApply) {
      JCTree.JCTypeApply typeApply = (JCTree.JCTypeApply) tree;
      JCTree.JCExpression classExpression = typeApply.clazz;
      Type type = typeApply.type;
      String methodReturn;
      if (type instanceof Type.ErrorType) {
        String clazzName = classExpression.toString();

        List<JCTree.JCExpression> typeArguments = typeApply.getTypeArguments();
        if (nonNull(typeArguments)) {
          java.util.List<String> temp = new ArrayList<>(typeArguments.length());
          for (JCTree.JCExpression e : typeArguments) {
            getExpressionType(src, e)
                .ifPresent(
                    fqcn -> {
                      String ignore = TreeAnalyzer.markFQCN(src, fqcn);
                    });
          }
        }

        return markFQCN(src, clazzName);
      }

      if (nonNull(type) && nonNull(type.tsym)) {
        methodReturn = type.tsym.flatName().toString();
      } else {
        String clazz = typeApply.getType().toString();
        methodReturn = src.getImportedClassFQCN(clazz, clazz);
      }

      List<JCTree.JCExpression> typeArguments = typeApply.getTypeArguments();
      if (nonNull(typeArguments)) {
        java.util.List<String> temp = new ArrayList<>(typeArguments.length());
        for (JCTree.JCExpression e : typeArguments) {
          getExpressionType(src, e).ifPresent(temp::add);
        }
        String join = Joiner.on(",").join(temp);
        if (!join.isEmpty()) {
          methodReturn = methodReturn + '<' + join + '>';
        }

        return methodReturn;
      }
    } else if (tree instanceof JCTree.JCFieldAccess) {
      JCTree.JCFieldAccess fieldAccess = (JCTree.JCFieldAccess) tree;
      Symbol sym = fieldAccess.sym;
      if (nonNull(sym)) {
        return sym.flatName().toString();
      }
      return tree.toString();
    } else if (tree instanceof JCTree.JCIdent) {
      Symbol sym = ((JCTree.JCIdent) tree).sym;
      if (nonNull(sym) && nonNull(sym.asType())) {
        Type type = sym.asType();
        if (type instanceof Type.CapturedType) {
          Type upperBound = type.getUpperBound();
          if (nonNull(upperBound) && nonNull(upperBound.tsym)) {
            return upperBound.tsym.flatName().toString();
          }
        } else {
          if (nonNull(type.tsym)) {
            return type.tsym.flatName().toString();
          }
        }
      }
      String ident = tree.toString();
      return src.getImportedClassFQCN(ident, ident);
    } else if (tree instanceof JCTree.JCPrimitiveTypeTree) {
      return tree.toString();
    } else if (tree instanceof JCTree.JCArrayTypeTree) {
      JCTree.JCArrayTypeTree arrayTypeTree = (JCTree.JCArrayTypeTree) tree;
      JCTree type = arrayTypeTree.getType();
      if (nonNull(type)) {
        String k = type.toString();
        return src.getImportedClassFQCN(k, k) + "[]";
      }
    } else if (tree instanceof JCTree.JCErroneous) {
      return tree.toString();
    } else {
      log.warn("tree={} {}", tree, tree.getClass());
    }
    return tree.toString();
  }

  private static void analyzeLiteral(
      SourceContext context, JCTree.JCLiteral literal, int preferredPos, int endPos) {
    Source src = context.getSource();
    Tree.Kind kind = literal.getKind();
    Object value = literal.getValue();
    Range range = Range.create(src, preferredPos, endPos);
    Variable variable = new Variable(kind.toString(), preferredPos, range);
    if (nonNull(value)) {
      variable.fqcn = value.getClass().getCanonicalName();
      variable.argumentIndex = context.getArgumentIndex();
      context.setArgumentFQCN(variable.fqcn);
    } else {
      variable.fqcn = "<null>";
      variable.argumentIndex = context.getArgumentIndex();
      context.setArgumentFQCN(variable.fqcn);
    }
    src.getCurrentScope()
        .ifPresent(
            scope -> {
              scope.addVariable(variable);
              addSymbolIndex(src, scope, variable);
            });
  }

  private static String markFQCN(Source src, String fqcn) {
    return markFQCN(src, fqcn, true);
  }

  private static String markFQCN(Source src, String fqcn, boolean markUnUse) {
    if (fqcn.equals("void")) {
      return fqcn;
    }

    if (fqcn.startsWith("capture of") || fqcn.equals("any") || fqcn.equals("<any>")) {
      // log.warn("unknown type={}", fqcn);
      return fqcn;
    }

    String simpleName = ClassNameUtils.removeTypeAndArray(fqcn);
    simpleName = ClassNameUtils.removeWildcard(simpleName);
    if (ClassNameUtils.isPrimitive(simpleName)) {
      return fqcn;
    }
    // checkLoadable(src, fqcn, simpleName);
    for (String s : ClassNameUtils.parseTypeParameter(fqcn)) {
      if (s.startsWith(ClassNameUtils.CAPTURE_OF)) {
        String cls = ClassNameUtils.removeCapture(s);
        if (cls.equals(ClassNameUtils.CAPTURE_OF)) {
          String ignore = markFQCN(src, ClassNameUtils.OBJECT_CLASS);
        } else {
          String ignore = markFQCN(src, cls);
        }
      } else {
        String ignore = markFQCN(src, s);
      }
    }

    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    Map<String, ClassIndex> classIndex = cachedASMReflector.getGlobalClassIndex();
    if (!src.importClasses.contains(simpleName) && !classIndex.containsKey(simpleName)) {

      src.addUnknown(simpleName);

    } else {

      if (markUnUse) {
        // contains
        String name = ClassNameUtils.replaceInnerMark(simpleName);
        src.unused.remove(name);
        int i = simpleName.indexOf('$');
        if (i > 0) {
          String parentClass = simpleName.substring(0, i);
          src.unused.remove(parentClass);
        }
      }

      File classFile = cachedASMReflector.getClassFile(simpleName);
      if (isNull(classFile) || !classFile.getName().endsWith(".jar")) {
        src.usingClasses.add(simpleName);
      }
    }
    return fqcn;
  }

  private static void checkLoadable(Source src, String fqcn, String simpleName) {
    try {
      if (simpleName.length() > 1) {
        Class.forName(simpleName);
      }
    } catch (ClassNotFoundException e) {
      log.warn("can't load class={} file={}", fqcn, src.getFile());
    }
  }

  private static String getFieldScope(FieldAccess fa, String selectScope) {
    if (selectScope.length() <= AccessSymbol.SCOPE_LIMIT) {
      return selectScope.trim();
    }
    return fa.name;
  }

  private static boolean requireImport(Source src, String typeSym, @Nullable String type) {

    if (isNull(type)) {
      return false;
    }

    Map<String, String> importMap = src.getImportedClassMap();
    int idx = typeSym.indexOf('.');
    int innerIdx = type.indexOf('$');
    String replaced = ClassNameUtils.replaceInnerMark(type);
    if (idx > 0) {
      if (typeSym.equals(replaced)) {
        return false;
      }
      String parent = typeSym.substring(0, idx);
      if (importMap.containsKey(parent)) {
        String parentClass = importMap.get(parent);
        return src.unused.contains(parentClass);
      }
    } else {
      if (innerIdx > 0) {
        boolean imported = importMap.containsKey(typeSym);
        if (imported) {
          return true;
        }
      }
    }
    return true;
  }

  private static void addNormalVariable(Source src, Variable variable) {
    Optional<? extends Scope> currentScope = src.getCurrentScope();
    currentScope.ifPresent(
        sc -> {
          if (nonNull(variable.fqcn)) {
            if (variable.isParameter) {
              Scope parent = sc;
              if (sc instanceof ExpressionScope) {
                ExpressionScope expressionScope1 = (ExpressionScope) sc;
                parent = expressionScope1.parent;
              }
              if (parent instanceof MethodScope) {
                MethodScope methodScope = (MethodScope) parent;
                methodScope.addMethodParameter(variable.fqcn);
              }
            }
            sc.addVariable(variable);
            addSymbolIndex(src, sc, variable);
          }
        });
  }

  private static void parseUnionVariable(
      JCTree.JCVariableDecl vd,
      int preferredPos,
      Source src,
      JCTree typeTree,
      String vName,
      Range nameRange,
      JCTree.JCTypeUnion union) {

    for (JCTree.JCExpression expression : union.getTypeAlternatives()) {

      String type = expression.toString();
      Variable variable = new Variable(vName, preferredPos, nameRange);
      type = src.getImportedClassFQCN(type, type);

      if (vd.getTag().equals(JCTree.Tag.VARDEF)) {
        variable.isDef = true;
      }

      String typeSym = typeTree.toString();
      boolean markUnUse = requireImport(src, typeSym, type);
      log.trace("typeSym:{} type:{} markUnuse:{}", typeSym, type, markUnUse);

      if (nonNull(type)) {
        variable.fqcn = TreeAnalyzer.markFQCN(src, type, markUnUse);
        src.getCurrentScope()
            .ifPresent(
                scope -> {
                  scope.addVariable(variable);
                  addSymbolIndex(src, scope, variable);
                });
      }
    }
  }

  private static void analyzeImports(CompilationUnitTree cut, Source src, EndPosTable endPosTable) {
    CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

    long firstLine = 0;

    for (ImportTree imp : cut.getImports()) {
      JCTree.JCImport jcImport = (JCTree.JCImport) imp;
      int startPos = jcImport.getPreferredPosition();
      int endPos = jcImport.getEndPosition(endPosTable);
      Range range = Range.create(src, startPos + 1, endPos);
      firstLine = range.begin.line;

      String importClass = imp.getQualifiedIdentifier().toString();
      String simpleName = ClassNameUtils.getSimpleName(importClass);

      if (imp.isStatic()) {
        // TODO static asterisk
        Tree tree = imp.getQualifiedIdentifier();
        if (tree instanceof JCTree.JCFieldAccess) {
          JCTree.JCFieldAccess fieldAccess = (JCTree.JCFieldAccess) tree;
          com.sun.tools.javac.util.Name name = fieldAccess.getIdentifier();
          JCTree.JCExpression expression = fieldAccess.getExpression();
          String methodName = name.toString();
          String decClazz = expression.toString();
          src.addStaticImport(methodName, decClazz);
        } else {
          log.warn("Not impl");
        }
      } else {
        if (simpleName.equals("*")) {
          // wild
          for (String s : cachedASMReflector.getPackageClasses(importClass).values()) {
            src.addImport(s);
          }
        } else {
          src.addImport(importClass);
        }
      }
    }
    src.setClassStartLine(firstLine);
  }

  private static void analyzePackageName(
      CompilationUnitTree cut, Source src, EndPosTable endPosTable) {
    ExpressionTree packageExpr = cut.getPackageName();
    if (isNull(packageExpr)) {
      src.setPackageName("");
    } else {
      src.setPackageName(packageExpr.toString());
    }
    if (packageExpr instanceof JCTree.JCIdent) {
      JCTree.JCIdent ident = (JCTree.JCIdent) packageExpr;
      int startPos = ident.getPreferredPosition();
      int endPos = ident.getEndPosition(endPosTable);
      Range range = Range.create(src, startPos + 1, endPos);
      long pkgLine = range.begin.line;
      src.setPackageStartLine(pkgLine);
      addPackageIndex(src, pkgLine, 8, src.getPackageName());
    }
  }

  private static void analyzeCompilationUnitTree(SourceContext context, CompilationUnitTree cut) {

    Source src = context.getSource();
    log.trace("file={}", src.getFile());
    EndPosTable endPosTable = ((JCTree.JCCompilationUnit) cut).endPositions;
    context.setEndPosTable(endPosTable);

    analyzePackageName(cut, src, endPosTable);

    analyzeImports(cut, src, endPosTable);

    try {

      for (Tree td : cut.getTypeDecls()) {
        // start class
        if (td instanceof JCTree.JCClassDecl) {
          analyzeTopLevelClass(context, (JCTree.JCClassDecl) td);
        } else if (td instanceof JCTree.JCSkip) {
          // skip
        } else if (td instanceof JCTree.JCErroneous) {
          // skip erroneous
        } else {
          log.warn("unknown td={} {}", td, td.getClass());
        }
      }
    } catch (IOException e) {
      log.catching(e);
      throw new UncheckedIOException(e);
    }
  }

  public static Map<File, Source> analyze(
      Iterable<? extends CompilationUnitTree> parsed, Set<File> errorFiles) {

    Map<File, Source> analyzeMap = new ConcurrentHashMap<>(64);

    if (log.isDebugEnabled()) {
      parsed.forEach(cut -> tryAnalyzeUnit(errorFiles, analyzeMap, cut));
    } else {
      try (Stream<? extends CompilationUnitTree> stream =
          StreamSupport.stream(parsed.spliterator(), true)) {
        // parallel ?
        stream.forEach(cut -> tryAnalyzeUnit(errorFiles, analyzeMap, cut));
      }
    }

    return analyzeMap;
  }

  private static void tryAnalyzeUnit(
      Set<File> errorFiles, Map<File, Source> analyzeMap, CompilationUnitTree cut) {
    try {
      Source source = analyzeUnit(cut, errorFiles);
      File file = source.getFile();
      analyzeMap.putIfAbsent(file, source);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Source analyzeUnit(CompilationUnitTree cut, Set<File> errorFiles)
      throws IOException {

    LineMap lineMap = cut.getLineMap();
    URI uri = cut.getSourceFile().toUri();
    File file = new File(uri.normalize());
    String path = file.getCanonicalPath();
    Source source = new Source(path, lineMap);
    if (errorFiles.contains(file)) {
      source.hasCompileError = true;
    }
    SourceContext context = new SourceContext(source);
    analyzeCompilationUnitTree(context, cut);
    source.resetLineRange();
    return source;
  }

  private static void analyzeTopLevelClass(SourceContext context, JCTree.JCClassDecl classDecl)
      throws IOException {
    Source src = context.getSource();
    EndPosTable endPosTable = context.getEndPosTable();

    Tree.Kind classDeclKind = classDecl.getKind();

    boolean isInterface = classDeclKind.equals(Tree.Kind.INTERFACE);
    boolean isEnum = classDeclKind.equals(Tree.Kind.ENUM);

    int startPos = classDecl.getPreferredPosition();
    int endPos = classDecl.getEndPosition(endPosTable);

    parseModifiers(context, classDecl.getModifiers());

    analyzeParsedTree(context, classDecl.getExtendsClause());

    analyzeSimpleExpressions(context, classDecl.getImplementsClause());

    Name simpleName = classDecl.getSimpleName();
    Range range = Range.create(src, startPos + 1, endPos);

    int nameStart = startPos + 6;
    if (isInterface) {
      nameStart = startPos + 10;
    } else if (isEnum) {
      nameStart = startPos + 5;
    }
    Range nameRange = Range.create(src, nameStart, nameStart + simpleName.length());

    String fqcn;
    if (src.getPackageName().isEmpty()) {
      fqcn = simpleName.toString();
    } else {
      fqcn = src.getPackageName() + '.' + simpleName.toString();
    }
    ClassScope classScope = new ClassScope(fqcn, nameRange, startPos, range);
    classScope.isEnum = isEnum;
    classScope.isInterface = isInterface;
    log.trace("class={}", classScope);

    src.startClass(classScope);

    for (JCTree tree : classDecl.getMembers()) {
      analyzeParsedTree(context, tree);
    }
    addClassNameIndex(
        src, classScope.range.begin.line, classScope.range.begin.column, classScope.getFQCN());
    Optional<ClassScope> endClass = src.endClass();
    log.trace("class={}", endClass);
  }

  private static void analyzeSimpleExpressions(
      SourceContext context, @Nullable List<JCTree.JCExpression> expressions) throws IOException {
    if (nonNull(expressions)) {
      for (JCTree.JCExpression expression : expressions) {
        analyzeParsedTree(context, expression);
      }
    }
  }

  private static void analyzeSimpleStatements(
      SourceContext context, @Nullable List<JCTree.JCStatement> statements) throws IOException {
    if (nonNull(statements)) {
      for (JCTree.JCStatement statement : statements) {
        analyzeParsedTree(context, statement);
      }
    }
  }

  private static void analyzeParsedTree(SourceContext context, @Nullable JCTree tree)
      throws IOException {
    if (isNull(tree)) {
      return;
    }
    JCDiagnostic.DiagnosticPosition pos = tree.pos();

    EndPosTable endPosTable = context.getEndPosTable();
    int startPos = pos.getStartPosition();
    int preferredPos = pos.getPreferredPosition();
    int endPos = pos.getEndPosition(endPosTable);

    EntryMessage em =
        log.traceEntry(
            "# class={} preferredPos={} endPos={} expr='{}'",
            tree.getClass().getSimpleName(),
            preferredPos,
            endPos,
            tree);

    if (endPos == -1 && !(tree instanceof JCTree.JCAssign) && !(tree instanceof JCTree.JCIdent)) {
      // skip
      log.trace("skip expr={}", tree);
      log.traceExit(em);
      return;
    }

    if (tree instanceof JCTree.JCVariableDecl) {

      analyzeVariableDecl(context, (JCTree.JCVariableDecl) tree, preferredPos, endPos);

    } else if (tree instanceof JCTree.JCTypeCast) {

      JCTree.JCTypeCast cast = (JCTree.JCTypeCast) tree;
      JCTree.JCExpression expression = cast.getExpression();
      analyzeParsedTree(context, expression);
      if (context.isArgument()) {
        getExpressionType(context.getSource(), cast).ifPresent(context::setArgumentFQCN);
      }

    } else if (tree instanceof JCTree.JCMethodDecl) {

      analyzeMethodDecl(context, (JCTree.JCMethodDecl) tree, preferredPos, endPos);

    } else if (tree instanceof JCTree.JCClassDecl) {

      analyzeInnerClassDecl(context, (JCTree.JCClassDecl) tree, preferredPos, endPos);

    } else if (tree instanceof JCTree.JCBlock) {

      JCTree.JCBlock block = (JCTree.JCBlock) tree;
      int argumentIndex = context.getArgumentIndex();
      context.setArgumentIndex(-1);
      analyzeSimpleStatements(context, block.getStatements());
      context.setArgumentIndex(argumentIndex);

    } else if (tree instanceof JCTree.JCFieldAccess) {

      analyzeFieldAccess(context, (JCTree.JCFieldAccess) tree, preferredPos, endPos);

    } else if (tree instanceof JCTree.JCArrayAccess) {

      JCTree.JCArrayAccess arrayAccess = (JCTree.JCArrayAccess) tree;
      analyzeParsedTree(context, arrayAccess.getExpression());
      analyzeParsedTree(context, arrayAccess.getIndex());

    } else if (tree instanceof JCTree.JCExpressionStatement) {

      analyzeExpressionStatement(
          context, (JCTree.JCExpressionStatement) tree, preferredPos, endPos);

    } else if (tree instanceof JCTree.JCLiteral) {

      analyzeLiteral(context, (JCTree.JCLiteral) tree, preferredPos, endPos);

    } else if (tree instanceof JCTree.JCIdent) {

      analyzeIdent(context, (JCTree.JCIdent) tree, preferredPos, endPos);

    } else if (tree instanceof JCTree.JCContinue) {
      // skip
    } else if (tree instanceof JCTree.JCBreak) {
      // skip
    } else if (tree instanceof JCTree.JCUnary) {

      JCTree.JCUnary unary = (JCTree.JCUnary) tree;
      JCTree.JCExpression expression = unary.getExpression();
      analyzeParsedTree(context, expression);

    } else if (tree instanceof JCTree.JCSwitch) {

      analyzeSwitch(context, (JCTree.JCSwitch) tree);

    } else if (tree instanceof JCTree.JCReturn) {

      JCTree.JCReturn ret = (JCTree.JCReturn) tree;
      JCTree.JCExpression expression = ret.getExpression();
      analyzeParsedTree(context, ret.getExpression());
    } else if (tree instanceof JCTree.JCForLoop) {

      analyzeForLoop(context, (JCTree.JCForLoop) tree);

    } else if (tree instanceof JCTree.JCEnhancedForLoop) {

      analyzeEnhancedForLoop(context, (JCTree.JCEnhancedForLoop) tree);

    } else if (tree instanceof JCTree.JCTry) {

      analyzeTry(context, (JCTree.JCTry) tree);

    } else if (tree instanceof JCTree.JCIf) {

      JCTree.JCIf ifExpr = (JCTree.JCIf) tree;
      JCTree.JCExpression condition = ifExpr.getCondition();
      JCTree.JCStatement thenStatement = ifExpr.getThenStatement();
      JCTree.JCStatement elseStatement = ifExpr.getElseStatement();
      analyzeParsedTree(context, condition);

      analyzeParsedTree(context, thenStatement);

      analyzeParsedTree(context, elseStatement);

    } else if (tree instanceof JCTree.JCParens) {

      JCTree.JCParens parens = (JCTree.JCParens) tree;
      JCTree.JCExpression expression = parens.getExpression();
      analyzeParsedTree(context, expression);

    } else if (tree instanceof JCTree.JCNewClass) {

      analyzeNewClass(context, (JCTree.JCNewClass) tree, preferredPos, endPos);

    } else if (tree instanceof JCTree.JCBinary) {

      JCTree.JCBinary binary = (JCTree.JCBinary) tree;
      JCTree.JCExpression leftOperand = binary.getLeftOperand();
      JCTree.JCExpression rightOperand = binary.getRightOperand();

      analyzeParsedTree(context, leftOperand);
      analyzeParsedTree(context, rightOperand);

    } else if (tree instanceof JCTree.JCMethodInvocation) {

      analyzeMethodInvocation(context, (JCTree.JCMethodInvocation) tree, preferredPos, endPos);

    } else if (tree instanceof JCTree.JCAssign) {

      JCTree.JCAssign assign = (JCTree.JCAssign) tree;
      JCTree.JCExpression expression = assign.getExpression();
      JCTree.JCExpression variable = assign.getVariable();

      analyzeParsedTree(context, variable);

      analyzeParsedTree(context, expression);

    } else if (tree instanceof JCTree.JCNewArray) {

      JCTree.JCNewArray newArray = (JCTree.JCNewArray) tree;
      JCTree.JCExpression type = newArray.getType();

      analyzeParsedTree(context, type);

      List<JCTree.JCExpression> initializes = newArray.getInitializers();
      analyzeSimpleExpressions(context, initializes);

      List<JCTree.JCExpression> dimensions = newArray.getDimensions();
      analyzeSimpleExpressions(context, dimensions);

      if (nonNull(newArray.type)) {
        getTypeString(context.getSource(), newArray.type).ifPresent(context::setArgumentFQCN);
      }
    } else if (tree instanceof JCTree.JCPrimitiveTypeTree) {
      // skip
    } else if (tree instanceof JCTree.JCConditional) {

      JCTree.JCConditional conditional = (JCTree.JCConditional) tree;
      JCTree.JCExpression condition = conditional.getCondition();
      analyzeParsedTree(context, condition);

      JCTree.JCExpression trueExpression = conditional.getTrueExpression();
      analyzeParsedTree(context, trueExpression);

      JCTree.JCExpression falseExpression = conditional.getFalseExpression();
      analyzeParsedTree(context, falseExpression);

    } else if (tree instanceof JCTree.JCLambda) {

      analyzeLambda(context, (JCTree.JCLambda) tree);

    } else if (tree instanceof JCTree.JCThrow) {

      JCTree.JCThrow jcThrow = (JCTree.JCThrow) tree;
      JCTree.JCExpression expression = jcThrow.getExpression();
      analyzeParsedTree(context, expression);

    } else if (tree instanceof JCTree.JCInstanceOf) {

      JCTree.JCInstanceOf jcInstanceOf = (JCTree.JCInstanceOf) tree;
      JCTree.JCExpression expression = jcInstanceOf.getExpression();
      analyzeParsedTree(context, expression);

      JCTree typeTree = jcInstanceOf.getType();
      analyzeParsedTree(context, typeTree);

    } else if (tree instanceof JCTree.JCMemberReference) {
      Source src = context.getSource();
      JCTree.JCMemberReference memberReference = (JCTree.JCMemberReference) tree;
      JCTree.JCExpression expression = memberReference.getQualifierExpression();
      com.sun.tools.javac.util.Name name = memberReference.getName();
      String methodName = name.toString();
      if (nonNull(expression)) {
        analyzeParsedTree(context, expression);
        Symbol sym = memberReference.sym;
        if (nonNull(sym)) {
          // method invoke
          int start = expression.getEndPosition(endPosTable) + 2;
          Range range = Range.create(src, start, start + methodName.length());
          String s = memberReference.toString();

          MethodCall methodCall = new MethodCall(s, methodName, preferredPos + 1, range, range);
          if (sym instanceof Symbol.MethodSymbol) {
            Symbol.MethodSymbol methodSymbol = (Symbol.MethodSymbol) sym;

            java.util.List<String> arguments = new ArrayList<>(methodSymbol.getParameters().size());

            for (VarSymbol varSymbol : methodSymbol.getParameters()) {
              arguments.add(varSymbol.asType().toString());
            }
            methodCall.setArguments(arguments);
          }
          Symbol owner = sym.owner;
          if (nonNull(owner) && nonNull(owner.type)) {
            getTypeString(src, owner.type)
                .ifPresent(fqcn -> methodCall.declaringClass = TreeAnalyzer.markFQCN(src, fqcn));
          }

          if (nonNull(sym.type)) {
            getTypeString(src, sym.type)
                .ifPresent(
                    fqcn -> {
                      methodCall.returnType = TreeAnalyzer.markFQCN(src, fqcn);
                      // TODO add args
                    });
          }

          src.getCurrentScope()
              .ifPresent(
                  scope -> {
                    scope.addMethodCall(methodCall);
                    addUsageIndex(src, methodCall);
                  });
        }
      }

    } else if (tree instanceof JCTree.JCWhileLoop) {

      JCTree.JCWhileLoop whileLoop = (JCTree.JCWhileLoop) tree;
      JCTree.JCExpression condition = whileLoop.getCondition();
      analyzeParsedTree(context, condition);

      JCTree.JCStatement statement = whileLoop.getStatement();
      analyzeParsedTree(context, statement);

    } else if (tree instanceof JCTree.JCSynchronized) {

      JCTree.JCSynchronized jcSynchronized = (JCTree.JCSynchronized) tree;
      JCTree.JCExpression expression = jcSynchronized.getExpression();
      analyzeParsedTree(context, expression);

      JCTree.JCBlock block = jcSynchronized.getBlock();
      analyzeParsedTree(context, block);

    } else if (tree instanceof JCTree.JCAssert) {

      JCTree.JCAssert jcAssert = (JCTree.JCAssert) tree;
      JCTree.JCExpression condition = jcAssert.getCondition();
      analyzeParsedTree(context, condition);

      JCTree.JCExpression detail = jcAssert.getDetail();
      analyzeParsedTree(context, detail);

    } else if (tree instanceof JCTree.JCArrayTypeTree) {

      JCTree.JCArrayTypeTree arrayTypeTree = (JCTree.JCArrayTypeTree) tree;
      JCTree type = arrayTypeTree.getType();
      analyzeParsedTree(context, type);

    } else if (tree instanceof JCTree.JCDoWhileLoop) {

      JCTree.JCDoWhileLoop doWhileLoop = (JCTree.JCDoWhileLoop) tree;
      JCTree.JCExpression condition = doWhileLoop.getCondition();
      analyzeParsedTree(context, condition);

      JCTree.JCStatement statement = doWhileLoop.getStatement();
      analyzeParsedTree(context, statement);

    } else if (tree instanceof JCTree.JCLabeledStatement) {

      JCTree.JCLabeledStatement labeledStatement = (JCTree.JCLabeledStatement) tree;
      JCTree.JCStatement statement = labeledStatement.getStatement();
      analyzeParsedTree(context, statement);

    } else if (tree instanceof JCTree.JCTypeApply) {

      JCTree.JCTypeApply typeApply = (JCTree.JCTypeApply) tree;
      JCTree type = typeApply.getType();
      analyzeParsedTree(context, type);

    } else if (tree instanceof JCTree.JCAssignOp) {

      JCTree.JCAssignOp assignOp = (JCTree.JCAssignOp) tree;
      JCTree.JCExpression expression = assignOp.getExpression();
      analyzeParsedTree(context, expression);

    } else if (tree instanceof JCTree.JCAnnotation) {

      JCTree.JCAnnotation annotation = (JCTree.JCAnnotation) tree;

      analyzeSimpleExpressions(context, annotation.getArguments());

      JCTree annotationType = annotation.getAnnotationType();
      analyzeParsedTree(context, annotationType);

    } else if (tree instanceof JCTree.JCSkip) {

      // skip

    } else if (tree instanceof JCTree.JCErroneous) {

      // skip error

    } else {

      Source src = context.getSource();
      log.warn(
          "@@ unknown or broken tree class={} expr={} filePath={}",
          tree.getClass(),
          tree,
          src.filePath);
    }

    log.traceExit(em);
  }

  private static void parseModifiers(SourceContext context, @Nullable JCTree.JCModifiers modifiers)
      throws IOException {

    if (nonNull(modifiers)) {
      List<JCTree.JCAnnotation> annotations = modifiers.getAnnotations();
      if (nonNull(annotations)) {
        for (JCTree.JCAnnotation anno : annotations) {
          JCTree annotationType = anno.getAnnotationType();
          if (nonNull(annotationType)) {
            analyzeParsedTree(context, annotationType);
          }
          List<JCTree.JCExpression> arguments = anno.getArguments();
          if (nonNull(arguments)) {
            for (JCTree.JCExpression jcExpression : arguments) {
              analyzeParsedTree(context, jcExpression);
            }
          }
        }
      }
    }
  }

  private static void analyzeVariableDecl(
      SourceContext context, JCTree.JCVariableDecl vd, int preferredPos, int endPos)
      throws IOException {

    Source src = context.getSource();
    Name name = vd.getName();
    JCTree.JCExpression initializer = vd.getInitializer();
    JCTree.JCExpression nameExpression = vd.getNameExpression();
    JCTree typeTree = vd.getType();
    JCTree.JCModifiers modifiers = vd.getModifiers();
    String fieldModfier = modifiers.toString();
    parseModifiers(context, modifiers);

    if (nonNull(initializer) || nonNull(nameExpression)) {
      log.trace("init={} name={} tree={}", initializer, nameExpression, typeTree);
    }

    Optional<BlockScope> cb = src.getCurrentBlock();

    cb.ifPresent(
        bs -> {
          try {
            String vName = name.toString();
            Range range = Range.create(src, preferredPos, endPos);
            Range nameRange = Range.create(src, preferredPos, preferredPos + vName.length());

            ExpressionScope expr = new ExpressionScope(preferredPos, range);
            if (bs instanceof ClassScope) {
              ClassScope cs = (ClassScope) bs;
              expr.isField = true;
              expr.modifier = fieldModfier;
              expr.declaringClass = cs.getFQCN();
            }
            bs.startExpression(expr);

            if (typeTree instanceof JCTree.JCTypeUnion) {

              JCTree.JCTypeUnion union = (JCTree.JCTypeUnion) typeTree;
              TreeAnalyzer.parseUnionVariable(
                  vd, preferredPos, src, typeTree, vName, nameRange, union);

            } else {

              try {

                Variable variable = new Variable(vName, preferredPos, nameRange);
                if (vd.getTag().equals(JCTree.Tag.VARDEF)) {
                  variable.isDef = true;
                }
                if (context.isParameter()) {
                  variable.isParameter = true;
                }

                if (typeTree instanceof JCTree.JCExpression) {

                  JCTree.JCExpression expression = (JCTree.JCExpression) typeTree;
                  Type type = expression.type;

                  if (isNull(type) && expression instanceof JCTree.JCIdent) {

                    JCTree.JCIdent ident = (JCTree.JCIdent) expression;
                    String nm = ident.getName().toString();
                    String identClazz = src.getImportedClassFQCN(nm, null);

                    if (nonNull(identClazz)) {
                      String typeSym = typeTree.toString();
                      boolean markUnUse = requireImport(src, typeSym, identClazz);
                      log.trace("typeSym:{} type:{} markUnuse:{}", typeSym, identClazz, markUnUse);

                      variable.fqcn = TreeAnalyzer.markFQCN(src, identClazz, markUnUse);

                    } else {

                      src.addUnknown(nm);
                      if (src.isReportUnknown()) {
                        log.warn(
                            "unknown ident class expression={} {} {}",
                            expression,
                            expression.getClass(),
                            src.filePath);
                      }
                    }

                  } else {

                    String fqcn = resolveTypeFromImport(src, expression);
                    String typeSym = typeTree.toString();
                    boolean markUnUse = requireImport(src, typeSym, fqcn);
                    log.trace("typeSym:{} type:{} markUnuse:{}", typeSym, fqcn, markUnUse);

                    if (nonNull(fqcn)) {
                      variable.fqcn = TreeAnalyzer.markFQCN(src, fqcn, markUnUse);
                    }
                  }
                } else {
                  if (src.isReportUnknown()) {
                    log.warn("unknown typeTree class expression={} {}", typeTree, src.filePath);
                  }
                }

                TreeAnalyzer.addNormalVariable(src, variable);

                analyzeParsedTree(context, initializer);

              } finally {
                bs.endExpression();
              }
            }
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  private static void analyzeMethodDecl(
      SourceContext context, final JCTree.JCMethodDecl md, int preferredPos, int endPos)
      throws IOException {

    Source src = context.getSource();
    String name = md.getName().toString();
    JCTree.JCModifiers modifiers = md.getModifiers();
    final String methodModifier = modifiers.toString();
    parseModifiers(context, modifiers);
    Range nameRange = Range.create(src, preferredPos, preferredPos + name.length());
    Range range = Range.create(src, preferredPos, endPos);

    src.getCurrentClass()
        .ifPresent(
            classScope -> {
              try {
                String methodName = name;
                boolean isConstructor = false;
                String returnFQCN = "";
                final String declaringClass = classScope.getFQCN();
                if (!name.equals("<init>")) {
                  // set return type
                  JCTree returnTypeExpr = md.getReturnType();
                  if (nonNull(returnTypeExpr)) {
                    Type type = returnTypeExpr.type;
                    if (nonNull(type)) {
                      returnFQCN = getTypeString(src, type).orElse(type.toString());
                    } else {
                      returnFQCN = resolveTypeFromImport(src, returnTypeExpr);
                    }
                  }
                } else {
                  isConstructor = true;
                  Symbol.MethodSymbol sym = md.sym;
                  if (nonNull(sym) && nonNull(sym.owner) && nonNull(sym.owner.type)) {
                    Type type = sym.owner.type;
                    methodName = getTypeString(src, type).orElse(name);
                    returnFQCN = methodName;
                  }
                }
                MethodScope scope =
                    classScope.startMethod(
                        declaringClass, methodName, nameRange, preferredPos, range, isConstructor);
                scope.modifier = methodModifier.trim();
                scope.returnType = TreeAnalyzer.markFQCN(src, returnFQCN);

                // check method parameter
                context.setParameter(true);

                for (JCTree.JCVariableDecl vd : md.getParameters()) {
                  String s = vd.toString();
                  if (s.contains("...")) {
                    scope.vararg = true;
                  }
                  analyzeParsedTree(context, vd);
                }

                context.setParameter(false);

                // set exceptions
                List<JCTree.JCExpression> throwsList = md.getThrows();
                if (nonNull(throwsList)) {
                  for (JCTree.JCExpression expr : throwsList) {
                    String ex = resolveTypeFromImport(src, expr);
                    String fqcn = TreeAnalyzer.markFQCN(src, ex);
                    scope.addException(fqcn);
                  }
                }

                analyzeParsedTree(context, md.getBody());
                addMethodNameIndex(
                    src, scope.range.begin.line, scope.range.begin.column, methodName);
                Optional<MethodScope> endMethod = classScope.endMethod();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  private static Optional<String> getTypeString(Source src, @Nullable Type type) {

    if (isNull(type)) {
      return Optional.empty();
    }

    if (type instanceof Type.ErrorType) {
      Type originalType = type.getOriginalType();
      if (isNull(originalType)) {
        return Optional.empty();
      }
      String s;
      if (originalType.toString().equals("<any>")) {
        s = type.toString();
      } else {
        s = originalType.toString();
      }
      src.addUnknown(s);
      return Optional.empty();
    }

    if (type instanceof Type.CapturedType) {
      Type.CapturedType capturedType = (Type.CapturedType) type;
      Type.WildcardType wildcardType = capturedType.wildcard;
      if (wildcardType.kind.toString().equals("?")) {
        Type upperBound = type.getUpperBound();
        if (nonNull(upperBound) && nonNull(upperBound.tsym)) {
          String s = upperBound.tsym.flatName().toString();
          return Optional.of(s);
        }
      }
      if (nonNull(wildcardType.type) && nonNull(wildcardType.type.tsym)) {
        String s = wildcardType.kind.toString() + wildcardType.type.tsym.flatName().toString();
        return Optional.of(s);
      }
      return Optional.empty();
    } else if (type instanceof Type.ArrayType) {
      Type.ArrayType arrayType = (Type.ArrayType) type;
      Type componentType = arrayType.getComponentType();
      if (nonNull(componentType) && nonNull(componentType.tsym)) {
        String s = arrayType.toString();
        return Optional.of(s);
      }
      return Optional.empty();
    } else if (type instanceof Type.WildcardType) {
      Type.WildcardType wildcardType = (Type.WildcardType) type;
      if (nonNull(wildcardType.type)) {
        if (wildcardType.kind.toString().equals("?") && nonNull(wildcardType.type.tsym)) {
          String s = wildcardType.type.tsym.flatName().toString();
          return Optional.of(s);
        }
        if (nonNull(wildcardType.type.tsym)) {
          String s = wildcardType.kind.toString() + wildcardType.type.tsym.flatName().toString();
          return Optional.of(s);
        }
      }
      return Optional.empty();
    } else if (type instanceof Type.MethodType) {
      Type.MethodType methodType = (Type.MethodType) type;
      Type returnType = methodType.getReturnType();
      if (nonNull(returnType) && nonNull(returnType.tsym)) {
        String baseType = returnType.tsym.flatName().toString();
        List<Type> typeArguments = returnType.getTypeArguments();
        baseType = getFlatName(src, baseType, typeArguments);
        return Optional.of(baseType);
      }
      return Optional.empty();
    } else {
      if (type.toString().equals("?")) {
        return Optional.of(ClassNameUtils.OBJECT_CLASS);
      }
      if (isNull(type.tsym)) {
        return Optional.empty();
      }
      String baseType = type.tsym.flatName().toString();
      if (baseType.equals("Array")) {
        return Optional.of(ClassNameUtils.OBJECT_CLASS + ClassNameUtils.ARRAY);
      }
      if (baseType.equals("Method")) {
        return Optional.empty();
      }

      List<Type> typeArguments = type.getTypeArguments();
      baseType = getFlatName(src, baseType, typeArguments);
      return Optional.of(baseType);
    }
  }

  private static void analyzeInnerClassDecl(
      SourceContext context, JCTree.JCClassDecl classDecl, int startPos, int endPos)
      throws IOException {
    // innerClass
    Source src = context.getSource();
    Range range = Range.create(src, startPos, endPos);
    Name simpleName = classDecl.getSimpleName();

    JCTree.JCModifiers modifiers = classDecl.getModifiers();
    int modPos = modifiers.pos;
    int modEndPos = context.getEndPosTable().getEndPos(modifiers);
    int modLen = 0;
    if (modEndPos > 0) {
      modLen = modEndPos - modPos + 1;
    }
    parseModifiers(context, modifiers);

    analyzeParsedTree(context, classDecl.getExtendsClause());
    analyzeSimpleExpressions(context, classDecl.getImplementsClause());

    Tree.Kind kind = classDecl.getKind();
    boolean isInterface = kind.equals(Tree.Kind.INTERFACE);
    boolean isEnum = kind.equals(Tree.Kind.ENUM);
    int nameStartPos = startPos + modLen;
    if (isInterface) {
      nameStartPos += 10;
    } else if (isEnum) {
      nameStartPos += 5;
    } else {
      nameStartPos += 6;
    }
    Range nameRange = Range.create(src, nameStartPos, nameStartPos + simpleName.length());

    src.getCurrentClass()
        .ifPresent(
            parent -> {
              try {
                String parentName = parent.name;
                String fqcn = parentName + ClassNameUtils.INNER_MARK + simpleName;
                ClassScope classScope = new ClassScope(fqcn, nameRange, startPos, range);
                classScope.isInterface = isInterface;
                classScope.isEnum = isEnum;
                log.trace("maybe inner class={}", classScope);
                parent.startClass(classScope);
                for (final JCTree memberTree : classDecl.getMembers()) {
                  analyzeParsedTree(context, memberTree);
                }
                addClassNameIndex(
                    src,
                    classScope.range.begin.line,
                    classScope.range.begin.column,
                    classScope.getFQCN());
                Optional<ClassScope> ignore = parent.endClass();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  private static void analyzeFieldAccess(
      SourceContext context, JCTree.JCFieldAccess fieldAccess, int preferredPos, int endPos)
      throws IOException {

    Source src = context.getSource();
    Symbol sym = fieldAccess.sym;
    JCTree.JCExpression selected = fieldAccess.getExpression();
    analyzeParsedTree(context, selected);

    String selectScope = selected.toString();
    Name identifier = fieldAccess.getIdentifier();
    Range range = Range.create(src, preferredPos + 1, endPos);
    if (isNull(sym)) {
      if (src.isReportUnknown()) {
        log.warn("unknown fieldAccess sym is null fieldAccess:{} {}", fieldAccess, src.filePath);
      }
      return;
    }

    ElementKind kind = sym.getKind();

    if (kind.equals(ElementKind.FIELD)) {
      //
      FieldAccess fa = new FieldAccess(identifier.toString(), preferredPos + 1, range);
      fa.scope = getFieldScope(fa, selectScope);
      Symbol owner = sym.owner;

      if (nonNull(owner) && nonNull(owner.type)) {
        getTypeString(src, owner.type)
            .ifPresent(
                fqcn -> {
                  fa.declaringClass = TreeAnalyzer.markFQCN(src, fqcn);
                  addVariable(context, src, selected, selectScope, fqcn);
                });
      }
      if (nonNull(sym.type)) {
        setReturnTypeAndArgType(context, src, sym.type, fa);
      }
      src.getCurrentScope()
          .ifPresent(
              scope -> {
                scope.addFieldAccess(fa);
                addUsageIndex(src, fa);
              });

    } else if (kind.equals(ElementKind.METHOD)) {
      MethodCall methodCall =
          new MethodCall(selectScope, identifier.toString(), preferredPos + 1, range, range);
      Symbol owner = sym.owner;
      if (nonNull(owner) && nonNull(owner.type)) {
        getTypeString(src, owner.type)
            .ifPresent(fqcn -> methodCall.declaringClass = TreeAnalyzer.markFQCN(src, fqcn));
      }

      if (nonNull(sym.type)) {
        setReturnTypeAndArgType(context, src, sym.type, methodCall);
      }
      src.getCurrentScope()
          .ifPresent(
              scope -> {
                scope.addMethodCall(methodCall);
                addUsageIndex(src, methodCall);
              });

    } else if (kind.equals(ElementKind.ENUM)) {

      if (nonNull(sym.type)) {
        getTypeString(src, sym.type)
            .ifPresent(
                fqcn -> {
                  String ignore = TreeAnalyzer.markFQCN(src, fqcn);
                });
      }

    } else if (kind.equals(ElementKind.ENUM_CONSTANT)) {

      FieldAccess fa = new FieldAccess(identifier.toString(), preferredPos + 1, range);
      fa.scope = getFieldScope(fa, selectScope);
      fa.isEnum = true;
      Symbol owner = sym.owner;
      if (nonNull(owner) && nonNull(owner.type)) {
        getTypeString(src, owner.type)
            .ifPresent(fqcn -> fa.declaringClass = TreeAnalyzer.markFQCN(src, fqcn));
      }
      if (nonNull(sym.type)) {
        setReturnTypeAndArgType(context, src, sym.type, fa);
      }
      src.getCurrentScope()
          .ifPresent(
              scope -> {
                scope.addFieldAccess(fa);
                addUsageIndex(src, fa);
              });

    } else if (kind.equals(ElementKind.PACKAGE)) {
      // skip
    } else if (kind.equals(ElementKind.CLASS)) {
      if (nonNull(sym.type)) {
        getTypeString(src, sym.type)
            .ifPresent(
                fqcn -> {
                  String ignore = TreeAnalyzer.markFQCN(src, fqcn);
                });
      }
    } else if (kind.equals(ElementKind.INTERFACE)) {
      if (nonNull(sym.type)) {
        getTypeString(src, sym.type)
            .ifPresent(
                fqcn -> {
                  String ignore = TreeAnalyzer.markFQCN(src, fqcn);
                });
      }
    } else if (kind.equals(ElementKind.ANNOTATION_TYPE)) {
      if (nonNull(sym.type)) {
        getTypeString(src, sym.type)
            .ifPresent(
                fqcn -> {
                  String ignore = TreeAnalyzer.markFQCN(src, fqcn);
                });
      }
    } else {
      log.warn("other kind:{}", kind);
    }
  }

  private static void addVariable(
      SourceContext context,
      Source src,
      JCTree.JCExpression selected,
      String selectScope,
      String fqcn) {
    if (selected instanceof JCTree.JCIdent) {
      JCTree.JCIdent ident = (JCTree.JCIdent) selected;
      int vStart = ident.getStartPosition();
      int vEnd = ident.getEndPosition(context.getEndPosTable());
      Range vRange = Range.create(src, vStart, vEnd);
      Variable variable = new Variable(selectScope, ident.pos, vRange);
      variable.fqcn = fqcn;
      src.getCurrentScope()
          .ifPresent(
              scope -> {
                scope.addVariable(variable);
                addSymbolIndex(src, scope, variable);
              });
    }
  }

  private static void setReturnTypeAndArgType(
      SourceContext context, Source src, Type type, AccessSymbol as) {

    getTypeString(src, type)
        .ifPresent(
            fqcn -> {
              as.returnType = TreeAnalyzer.markFQCN(src, fqcn);
              as.argumentIndex = context.getArgumentIndex();
              context.setArgumentFQCN(as.returnType);
            });
  }

  private static void analyzeExpressionStatement(
      SourceContext context, JCTree.JCExpressionStatement exprStmt, int preferredPos, int endPos) {

    Source src = context.getSource();
    JCTree.JCExpression expression = exprStmt.getExpression();
    Tree.Kind expressionKind = expression.getKind();
    JCTree expressionTree = expression.getTree();
    src.getCurrentBlock()
        .ifPresent(
            bs -> {
              try {
                Range range = Range.create(src, preferredPos, endPos);
                ExpressionScope expr = new ExpressionScope(preferredPos, range);
                if (bs instanceof ClassScope) {
                  expr.isField = true;
                }

                bs.startExpression(expr);
                if (expressionKind.equals(Tree.Kind.ASSIGNMENT)) {

                  JCTree.JCAssign assign = (JCTree.JCAssign) expressionTree;
                  analyzeParsedTree(context, assign.lhs);
                  analyzeParsedTree(context, assign.rhs);

                } else if (expressionKind.equals(Tree.Kind.METHOD_INVOCATION)) {

                  JCTree.JCMethodInvocation methodInvocation =
                      (JCTree.JCMethodInvocation) expressionTree;
                  analyzeParsedTree(context, methodInvocation);

                } else if (expressionKind.equals(Tree.Kind.POSTFIX_DECREMENT)
                    || expressionKind.equals(Tree.Kind.POSTFIX_INCREMENT)) {

                  if (expressionTree instanceof JCTree.JCUnary) {
                    JCTree.JCUnary jcUnary = (JCTree.JCUnary) expressionTree;
                    JCTree.JCExpression args = jcUnary.getExpression();
                    analyzeParsedTree(context, args);

                  } else {
                    log.warn(
                        "POSTFIX_XXXXX expressionKind:{} tree:{}",
                        expressionKind,
                        expressionTree.getClass());
                  }

                } else if (expressionKind.equals(Tree.Kind.PREFIX_DECREMENT)
                    || expressionKind.equals(Tree.Kind.PREFIX_INCREMENT)) {
                  if (expressionTree instanceof JCTree.JCUnary) {
                    JCTree.JCUnary jcUnary = (JCTree.JCUnary) expressionTree;
                    JCTree.JCExpression args = jcUnary.getExpression();
                    analyzeParsedTree(context, args);

                  } else {
                    log.warn(
                        "PREFIX_XXXXX expressionKind:{} tree:{}",
                        expressionKind,
                        expressionTree.getClass());
                  }
                } else if (expressionKind.equals(Tree.Kind.PLUS_ASSIGNMENT)
                    || expressionKind.equals(Tree.Kind.MINUS_ASSIGNMENT)
                    || expressionKind.equals(Tree.Kind.AND_ASSIGNMENT)
                    || expressionKind.equals(Tree.Kind.OR_ASSIGNMENT)
                    || expressionKind.equals(Tree.Kind.XOR_ASSIGNMENT)
                    || expressionKind.equals(Tree.Kind.DIVIDE_ASSIGNMENT)
                    || expressionKind.equals(Tree.Kind.LEFT_SHIFT_ASSIGNMENT)
                    || expressionKind.equals(Tree.Kind.RIGHT_SHIFT_ASSIGNMENT)
                    || expressionKind.equals(Tree.Kind.UNSIGNED_RIGHT_SHIFT_ASSIGNMENT)
                    || expressionKind.equals(Tree.Kind.REMAINDER_ASSIGNMENT)
                    || expressionKind.equals(Tree.Kind.MULTIPLY_ASSIGNMENT)) {
                  if (expressionTree instanceof JCTree.JCAssignOp) {

                    JCTree.JCAssignOp assignOp = (JCTree.JCAssignOp) expressionTree;
                    JCTree.JCExpression lhs = assignOp.lhs;
                    JCTree.JCExpression rhs = assignOp.rhs;
                    analyzeParsedTree(context, lhs);
                    analyzeParsedTree(context, rhs);

                  } else {
                    log.warn(
                        "XXXX_ASSIGNMENT expressionKind:{} tree:{}",
                        expressionKind,
                        expressionTree.getClass());
                  }
                } else if (expressionKind.equals(Tree.Kind.NEW_CLASS)) {
                  if (expressionTree instanceof JCTree.JCNewClass) {
                    JCTree.JCNewClass newClass = (JCTree.JCNewClass) expressionTree;
                    analyzeParsedTree(context, newClass);
                  } else {
                    log.warn(
                        "NEW_CLASS expressionKind:{} tree:{}",
                        expressionKind,
                        expressionTree.getClass());
                  }
                } else if (expressionKind.equals(Tree.Kind.ERRONEOUS)) {
                  if (expressionTree instanceof JCTree.JCErroneous) {
                    JCTree.JCErroneous erroneous = (JCTree.JCErroneous) expressionTree;
                    List<? extends JCTree> errorTrees = erroneous.getErrorTrees();
                    if (nonNull(errorTrees)) {

                      for (JCTree tree : errorTrees) {
                        analyzeParsedTree(context, tree);
                      }
                    }
                  }
                } else {
                  log.warn("expressionKind:{} tree:{}", expressionKind, expressionTree.getClass());
                }

                bs.endExpression();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  private static void analyzeIdent(
      SourceContext context, JCTree.JCIdent ident, int preferredPos, int endPos) {
    if (endPos == -1) {
      return;
    }

    Symbol sym = ident.sym;
    Source src = context.getSource();
    Range range = Range.create(src, preferredPos, endPos);
    if (nonNull(sym)) {
      Symbol owner = sym.owner;
      Type type = sym.asType();
      String name = ident.getName().toString();

      if (nonNull(owner) && nonNull(owner.type)) {

        FieldAccess fa = new FieldAccess(name, preferredPos, range);
        // this
        fa.scope = "";
        getTypeString(src, owner.type)
            .ifPresent(
                fqcn -> {
                  setReturnTypeAndArgType(context, src, sym.type, fa);
                  fa.declaringClass = TreeAnalyzer.markFQCN(src, fqcn);
                  src.getCurrentScope()
                      .ifPresent(
                          scope -> {
                            scope.addFieldAccess(fa);
                            addUsageIndex(src, fa);
                          });
                });

      } else {
        Variable variable = new Variable(name, preferredPos, range);

        getTypeString(src, type)
            .ifPresent(
                fqcn -> {
                  variable.fqcn = TreeAnalyzer.markFQCN(src, fqcn);
                  variable.argumentIndex = context.getArgumentIndex();
                  context.setArgumentFQCN(variable.fqcn);
                  src.getCurrentScope()
                      .ifPresent(
                          scope -> {
                            scope.addVariable(variable);
                            addSymbolIndex(src, scope, variable);
                          });
                });
      }
    } else {
      String nm = ident.toString();
      Variable variable = new Variable(nm, preferredPos, range);
      Optional<ClassScope> currentClass = src.getCurrentClass();

      if (currentClass.isPresent()) {
        String className = currentClass.get().name;
        if (ClassNameUtils.getSimpleName(className).equals(nm)) {
          variable.fqcn = TreeAnalyzer.markFQCN(src, className);
          variable.argumentIndex = context.getArgumentIndex();
          context.setArgumentFQCN(variable.fqcn);
          src.getCurrentScope()
              .ifPresent(
                  scope -> {
                    scope.addVariable(variable);
                    addSymbolIndex(src, scope, variable);
                  });
          return;
        }
      }

      String clazz = src.getImportedClassFQCN(nm, null);
      if (nonNull(clazz)) {
        {
          variable.fqcn = TreeAnalyzer.markFQCN(src, clazz);
          variable.argumentIndex = context.getArgumentIndex();
          context.setArgumentFQCN(variable.fqcn);
          src.getCurrentScope()
              .ifPresent(
                  scope -> {
                    scope.addVariable(variable);
                    addSymbolIndex(src, scope, variable);
                  });
        }
        return;
      }

      for (ClassScope classScope : src.classScopes) {
        currentClass.ifPresent(
            cs -> {
              String className = cs.name;
              if (ClassNameUtils.getSimpleName(className).equals(nm)) {
                variable.fqcn = TreeAnalyzer.markFQCN(src, className);
                variable.argumentIndex = context.getArgumentIndex();
                context.setArgumentFQCN(variable.fqcn);
                src.getCurrentScope()
                    .ifPresent(
                        scope -> {
                          scope.addVariable(variable);
                          addSymbolIndex(src, scope, variable);
                        });
              }
            });
      }

      // mark unknown
      String unknown = TreeAnalyzer.markFQCN(src, nm);
      log.trace("unknwon: {}", unknown);
    }
  }

  private static void analyzeSwitch(SourceContext context, JCTree.JCSwitch jcSwitch)
      throws IOException {

    JCTree.JCExpression expression = jcSwitch.getExpression();
    analyzeParsedTree(context, expression);
    List<JCTree.JCCase> cases = jcSwitch.getCases();
    if (nonNull(cases)) {
      for (JCTree.JCCase jcCase : cases) {
        JCTree.JCExpression expression1 = jcCase.getExpression();
        analyzeParsedTree(context, expression1);

        List<JCTree.JCStatement> statements = jcCase.getStatements();
        analyzeSimpleStatements(context, statements);
      }
    }
  }

  private static void analyzeForLoop(SourceContext context, JCTree.JCForLoop forLoop)
      throws IOException {
    List<JCTree.JCStatement> initializer = forLoop.getInitializer();
    JCTree.JCExpression condition = forLoop.getCondition();
    List<JCTree.JCExpressionStatement> updates = forLoop.getUpdate();
    JCTree.JCStatement statement = forLoop.getStatement();

    analyzeSimpleStatements(context, initializer);

    analyzeParsedTree(context, condition);

    analyzeExpressionStatements(context, updates);

    analyzeParsedTree(context, statement);
  }

  private static void analyzeExpressionStatements(
      SourceContext context, @Nullable List<JCTree.JCExpressionStatement> updates)
      throws IOException {
    if (nonNull(updates)) {
      for (JCTree.JCExpressionStatement s : updates) {
        analyzeParsedTree(context, s);
      }
    }
  }

  private static void analyzeEnhancedForLoop(
      SourceContext context, JCTree.JCEnhancedForLoop forLoop) throws IOException {
    JCTree.JCExpression expression = forLoop.getExpression();
    analyzeParsedTree(context, expression);

    JCTree.JCVariableDecl variable = forLoop.getVariable();
    analyzeParsedTree(context, variable);

    JCTree.JCStatement statement = forLoop.getStatement();
    analyzeParsedTree(context, statement);
  }

  private static void analyzeTry(SourceContext context, JCTree.JCTry tryExpr) throws IOException {
    JCTree.JCBlock block = tryExpr.getBlock();
    List<JCTree> resources = tryExpr.getResources();
    List<JCTree.JCCatch> catches = tryExpr.getCatches();
    JCTree.JCBlock lyBlock = tryExpr.getFinallyBlock();

    if (nonNull(resources)) {
      for (JCTree resource : resources) {
        analyzeParsedTree(context, resource);
      }
    }

    analyzeParsedTree(context, block);

    if (nonNull(catches)) {

      for (JCTree.JCCatch jcCatch : catches) {

        JCTree.JCVariableDecl parameter = jcCatch.getParameter();
        analyzeParsedTree(context, parameter);

        JCTree.JCBlock catchBlock = jcCatch.getBlock();
        analyzeParsedTree(context, catchBlock);
      }
    }

    analyzeParsedTree(context, lyBlock);
  }

  private static void analyzeNewClass(
      SourceContext context, JCTree.JCNewClass newClass, int preferredPos, int endPos)
      throws IOException {
    Source src = context.getSource();
    EndPosTable endPosTable = context.getEndPosTable();
    boolean isParameter = context.isParameter();
    boolean isArgument = context.isArgument();
    int argumentIndex = context.getArgumentIndex();

    List<JCTree.JCExpression> argumentExpressions = newClass.getArguments();
    java.util.List<String> arguments = getArgumentsType(context, argumentExpressions);

    context.setParameter(isParameter);
    context.setArgument(isArgument);
    context.setArgumentIndex(argumentIndex);

    JCTree.JCExpression identifier = newClass.getIdentifier();
    String name = identifier.toString();

    int start = identifier.getStartPosition();
    int end = identifier.getEndPosition(endPosTable);
    Range nameRange = Range.create(src, start, end);

    Range range = Range.create(src, preferredPos + 4, endPos);
    MethodCall methodCall = new MethodCall(name, preferredPos, nameRange, range, true);

    Type type = identifier.type;
    getTypeString(src, type)
        .ifPresent(
            fqcn -> {
              methodCall.declaringClass = TreeAnalyzer.markFQCN(src, fqcn);
              methodCall.returnType = fqcn;
              methodCall.argumentIndex = argumentIndex;
              context.setArgumentFQCN(fqcn);
            });

    if (isNull(type) || type instanceof Type.ErrorType) {
      // add className to unknown
      ClassName className = new ClassName(name);
      String simpleName = className.getName();
      if (!src.getImportedClassMap().containsKey(simpleName)
          && !CachedASMReflector.getInstance().getGlobalClassIndex().containsKey(simpleName)) {
        src.addUnknown(simpleName);
      }
    }

    JCTree.JCClassDecl classBody = newClass.getClassBody();
    analyzeParsedTree(context, classBody);

    src.getCurrentScope()
        .ifPresent(
            scope -> {
              if (nonNull(arguments)) {
                methodCall.setArguments(arguments);
              }
              scope.addMethodCall(methodCall);
              addUsageIndex(src, methodCall);
            });
  }

  @Nonnull
  private static java.util.List<String> getArgumentsType(
      SourceContext context, List<JCTree.JCExpression> arguments) throws IOException {

    SourceContext newContext = new SourceContext(context.getSource(), context.getEndPosTable());
    newContext.setArgumentIndex(0);
    try {
      java.util.List<String> result = new ArrayList<>(arguments.size());
      for (JCTree.JCExpression expression : arguments) {
        newContext.setArgument(true);
        analyzeParsedTree(newContext, expression);
        newContext.incrArgumentIndex();
        newContext.setArgument(false);
        result.add(newContext.getArgumentFQCN());
      }
      return result;
    } finally {
      // reset
      context.setArgumentIndex(-1);
    }
  }

  private static void analyzeMethodInvocation(
      SourceContext context,
      JCTree.JCMethodInvocation methodInvocation,
      int preferredPos,
      int endPos)
      throws IOException {

    Source src = context.getSource();
    EndPosTable endPosTable = context.getEndPosTable();
    Type returnType = methodInvocation.type;

    boolean isParameter = context.isParameter();
    int argumentIndex = context.getArgumentIndex();
    List<JCTree.JCExpression> argumentExpressions = methodInvocation.getArguments();
    java.util.List<String> arguments = getArgumentsType(context, argumentExpressions);

    context.setParameter(isParameter);
    context.setArgumentIndex(argumentIndex);

    JCTree.JCExpression methodSelect = methodInvocation.getMethodSelect();

    if (methodSelect instanceof JCTree.JCIdent) {
      // super
      JCTree.JCIdent ident = (JCTree.JCIdent) methodSelect;
      String s = ident.getName().toString();
      Symbol sym = ident.sym;
      if (nonNull(sym)) {
        Symbol owner = sym.owner;
        int nameBegin = ident.getStartPosition();
        int nameEnd = ident.getEndPosition(endPosTable);
        Range nameRange = Range.create(src, nameBegin, nameEnd);
        Range range = Range.create(src, nameBegin, endPos);

        if (s.equals("super")) {
          // call super constructor
          if (nonNull(owner) && nonNull(owner.type)) {
            String constructor = owner.flatName().toString();
            MethodCall mc = new MethodCall(s, constructor, nameBegin, nameRange, range);

            getTypeString(src, owner.type)
                .ifPresent(fqcn -> mc.declaringClass = TreeAnalyzer.markFQCN(src, fqcn));

            setReturnTypeAndArgType(context, src, owner.type, mc);
            src.getCurrentScope()
                .ifPresent(
                    scope -> {
                      mc.setArguments(arguments);
                      scope.addMethodCall(mc);
                      addUsageIndex(src, mc);
                    });
          }

        } else {
          MethodCall mc = new MethodCall(s, preferredPos + 1, nameRange, range);
          if (nonNull(owner) && nonNull(owner.type)) {
            getTypeString(src, owner.type)
                .ifPresent(
                    fqcn -> {
                      String className = src.staticImportClass.get(s);
                      if (fqcn.equals(className)) {
                        // static imported
                        mc.declaringClass = TreeAnalyzer.markFQCN(src, fqcn, false);
                      } else {
                        mc.declaringClass = TreeAnalyzer.markFQCN(src, fqcn);
                      }
                    });
            setReturnTypeAndArgType(context, src, returnType, mc);
          }

          src.getCurrentScope()
              .ifPresent(
                  scope -> {
                    mc.setArguments(arguments);
                    scope.addMethodCall(mc);
                    addUsageIndex(src, mc);
                  });
        }
      }
    } else if (methodSelect instanceof JCTree.JCFieldAccess) {
      JCTree.JCFieldAccess fa = (JCTree.JCFieldAccess) methodSelect;
      JCTree.JCExpression expression = fa.getExpression();
      String selectScope = expression.toString();
      analyzeParsedTree(context, expression);
      Type owner = expression.type;
      String name = fa.getIdentifier().toString();
      int start = preferredPos - name.length();

      Range nameRange = Range.create(src, start, preferredPos);
      Range range = Range.create(src, start, endPos);
      MethodCall methodCall = new MethodCall(selectScope, name, start + 1, nameRange, range);

      if (isNull(owner)) {
        // call static
        if (expression instanceof JCTree.JCIdent) {
          JCTree.JCIdent ident = (JCTree.JCIdent) expression;
          String nm = ident.getName().toString();
          String clazz = src.getImportedClassFQCN(nm, null);
          if (nonNull(clazz)) {
            methodCall.declaringClass = TreeAnalyzer.markFQCN(src, clazz);
          } else {
            if (src.isReportUnknown()) {
              log.warn(
                  "unknown ident class expression={} {} {}",
                  expression,
                  expression.getClass(),
                  src.filePath);
            }
          }
        } else {
          if (src.isReportUnknown()) {
            log.warn(
                "unknown expression expression={} {}",
                expression,
                expression.getClass(),
                src.filePath);
          }
        }
      } else {
        getTypeString(src, owner)
            .ifPresent(
                fqcn -> {
                  methodCall.declaringClass = TreeAnalyzer.markFQCN(src, fqcn);

                  addVariable(context, src, expression, selectScope, fqcn);
                });
      }

      if (isNull(returnType)) {
        if (expression instanceof JCTree.JCIdent) {
          JCTree.JCIdent ident = (JCTree.JCIdent) expression;
          String nm = ident.getName().toString();
          String clazz = src.getImportedClassFQCN(nm, null);
          if (nonNull(clazz)) {
            methodCall.returnType = TreeAnalyzer.markFQCN(src, clazz);
            methodCall.argumentIndex = context.getArgumentIndex();
            context.setArgumentFQCN(methodCall.returnType);
          } else {
            if (src.isReportUnknown()) {
              log.warn(
                  "unknown ident class expression={} {} {}",
                  expression,
                  expression.getClass(),
                  src.filePath);
            }
          }
        } else {
          if (src.isReportUnknown()) {
            log.warn(
                "unknown expression expression={} {}",
                expression,
                expression.getClass(),
                src.filePath);
          }
        }
      } else {
        getTypeString(src, returnType)
            .ifPresent(
                fqcn -> {
                  methodCall.returnType = TreeAnalyzer.markFQCN(src, fqcn);
                  methodCall.argumentIndex = context.getArgumentIndex();
                  context.setArgumentFQCN(methodCall.returnType);
                });
      }
      src.getCurrentScope()
          .ifPresent(
              scope -> {
                methodCall.setArguments(arguments);
                scope.addMethodCall(methodCall);
                addUsageIndex(src, methodCall);
              });

    } else {
      log.warn("unknown method select:{}", methodSelect);
      analyzeParsedTree(context, methodSelect);
    }
  }

  private static void analyzeLambda(SourceContext context, JCTree.JCLambda lambda)
      throws IOException {
    boolean isParameter = context.isParameter();
    boolean isArgument = context.isArgument();
    int argumentIndex = context.getArgumentIndex();

    java.util.List<? extends VariableTree> parameters = lambda.getParameters();
    if (nonNull(parameters)) {
      for (VariableTree v : parameters) {
        if (v instanceof JCTree.JCVariableDecl) {
          analyzeParsedTree(context, (JCTree.JCVariableDecl) v);
        }
      }
    }
    JCTree body = lambda.getBody();
    if (nonNull(body)) {
      context.setArgumentIndex(-1);
      analyzeParsedTree(context, body);
      context.setArgumentIndex(argumentIndex);
    }

    context.setParameter(isParameter);
    Type lambdaType = lambda.type;
    if (nonNull(lambdaType)) {
      Source src = context.getSource();
      getTypeString(src, lambdaType)
          .ifPresent(
              fqcn -> {
                context.setArgument(isArgument);
                // TODO
                context.setArgumentFQCN(fqcn);
              });
    }
  }

  private static String getFlatName(
      Source src, String baseType, @Nullable List<Type> typeArguments) {
    if (nonNull(typeArguments) && !typeArguments.isEmpty()) {
      java.util.List<String> temp = new ArrayList<>(typeArguments.length());
      for (Type typeArgument : typeArguments) {
        getTypeString(src, typeArgument).ifPresent(temp::add);
      }
      String join = Joiner.on(",").join(temp);
      if (!join.isEmpty()) {
        baseType = baseType + '<' + join + '>';
      }
    }
    return baseType;
  }

  private static void addPackageIndex(Source src, long line, long column, String name) {
    src.addIndexWord(IndexableWord.Field.PACKAGE_NAME, line, column, name);
  }

  private static void addClassNameIndex(Source src, long line, long column, String name) {
    String simpleName = ClassNameUtils.getSimpleName(name);
    int index = simpleName.lastIndexOf(ClassNameUtils.INNER_MARK);
    if (index > 0) {
      simpleName = simpleName.substring(index + 1);
    }
    src.addIndexWord(IndexableWord.Field.CLASS_NAME, line, column, simpleName);
  }

  private static void addMethodNameIndex(Source src, long line, long column, String name) {
    // check inner class constructor
    int index = name.lastIndexOf(ClassNameUtils.INNER_MARK);
    if (index > 0) {
      name = name.substring(index + 1);
    }
    src.addIndexWord(IndexableWord.Field.METHOD_NAME, line, column, name);
  }

  private static void addSymbolIndex(final Source src, final Scope scope, final Variable v) {
    if (scope instanceof ExpressionScope) {
      ExpressionScope es = (ExpressionScope) scope;
      if (es.isField && v.isDecl()) {
        long line = v.range.begin.line;
        long column = v.range.begin.column;
        src.addIndexWord(IndexableWord.Field.SYMBOL_NAME, line, column, v.name);
      }
    }
  }

  private static void addUsageIndex(final Source src, AccessSymbol accessSymbol) {
    long line = accessSymbol.range.begin.line;
    long column = accessSymbol.range.begin.column;
    String name = accessSymbol.name;
    String declaringClass = accessSymbol.declaringClass;
    src.addIndexWord(IndexableWord.Field.USAGE, line, column, name);
    src.addIndexWord(IndexableWord.Field.DECLARING_CLASS, line, column, declaringClass);
  }
}
