package meghanada.analyze;

import static meghanada.utils.FunctionUtils.wrapIOConsumer;

import com.google.common.base.Joiner;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassName;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

public class TreeAnalyzer {

  private static final Logger log = LogManager.getLogger(TreeAnalyzer.class);
  private static final int SOURCE_LIMIT = 16;

  TreeAnalyzer() {}

  private static Optional<String> getExpressionType(final Source src, final JCTree.JCExpression e) {
    if (e instanceof JCTree.JCFieldAccess) {
      final JCTree.JCFieldAccess fieldAccess = (JCTree.JCFieldAccess) e;
      final Symbol sym = fieldAccess.sym;
      if (sym != null) {
        final com.sun.tools.javac.util.Name name = sym.flatName();
        return Optional.of(name.toString());
      }
      return Optional.empty();
    } else if (e instanceof JCTree.JCWildcard) {
      final JCTree.JCWildcard wildcard = (JCTree.JCWildcard) e;
      final Type type = wildcard.type;
      final Type.WildcardType wildcardType = (Type.WildcardType) type;
      if (wildcardType != null && wildcardType.type != null) {
        if (wildcardType.kind.toString().equals("?") && wildcardType.type.tsym != null) {
          final String s = wildcardType.type.tsym.flatName().toString();
          return Optional.of(s);
        }
        if (wildcardType.type.tsym != null) {
          final String s =
              wildcardType.kind.toString() + wildcardType.type.tsym.flatName().toString();
          return Optional.of(s);
        }
      }
      return Optional.empty();
    } else if (e instanceof JCTree.JCArrayTypeTree) {
      final JCTree.JCArrayTypeTree arrayTypeTree = (JCTree.JCArrayTypeTree) e;
      final Type type = arrayTypeTree.type;
      if (type != null && type instanceof Type.ArrayType) {
        final Type.ArrayType arrayType = (Type.ArrayType) type;
        if (arrayType.getComponentType() != null && arrayType.getComponentType().tsym != null) {
          final String base = arrayType.getComponentType().tsym.flatName().toString();
          return Optional.of(base + ClassNameUtils.ARRAY);
        }
      }
      return Optional.empty();
    } else {
      final Type type = e.type;
      String typeArgType = e.toString();
      if (type != null && type.tsym != null) {
        typeArgType = type.tsym.flatName().toString();
      } else {
        typeArgType = src.getImportedClassFQCN(typeArgType, typeArgType);
      }
      return Optional.ofNullable(typeArgType);
    }
  }

  private static String resolveTypeFromImport(final Source src, final JCTree tree) {

    if (tree instanceof JCTree.JCTypeApply) {
      final JCTree.JCTypeApply typeApply = (JCTree.JCTypeApply) tree;
      final Type type = typeApply.type;
      String methodReturn;
      if (type != null && type.tsym != null) {
        methodReturn = type.tsym.flatName().toString();
      } else {
        final String clazz = typeApply.getType().toString();
        methodReturn = src.getImportedClassFQCN(clazz, clazz);
      }

      final List<JCTree.JCExpression> typeArguments = typeApply.getTypeArguments();
      if (typeArguments != null) {
        final java.util.List<String> temp = new ArrayList<>(typeArguments.length());
        for (final JCTree.JCExpression e : typeArguments) {
          getExpressionType(src, e).ifPresent(temp::add);
        }
        final String join = Joiner.on(",").join(temp);
        if (!join.isEmpty()) {
          methodReturn = methodReturn + '<' + join + '>';
        }
      }
      return methodReturn;
    } else if (tree instanceof JCTree.JCFieldAccess) {
      final JCTree.JCFieldAccess fieldAccess = (JCTree.JCFieldAccess) tree;
      final Symbol sym = fieldAccess.sym;
      if (sym != null) {
        return sym.flatName().toString();
      }
      return tree.toString();
    } else if (tree instanceof JCTree.JCIdent) {
      final Symbol sym = ((JCTree.JCIdent) tree).sym;
      if (sym != null && sym.asType() != null) {
        final Type type = sym.asType();
        if (type instanceof Type.CapturedType) {
          final Type upperBound = type.getUpperBound();
          if (upperBound != null && upperBound.tsym != null) {
            return upperBound.tsym.flatName().toString();
          }
        } else {
          if (type.tsym != null) {
            return type.tsym.flatName().toString();
          }
        }
      }
      final String ident = tree.toString();
      return src.getImportedClassFQCN(ident, ident);
    } else if (tree instanceof JCTree.JCPrimitiveTypeTree) {
      return tree.toString();
    } else if (tree instanceof JCTree.JCArrayTypeTree) {
      final JCTree.JCArrayTypeTree arrayTypeTree = (JCTree.JCArrayTypeTree) tree;
      final JCTree type = arrayTypeTree.getType();
      if (type != null) {
        final String k = type.toString();
        return src.getImportedClassFQCN(k, k) + "[]";
      }
    } else {
      log.warn("tree={} {}", tree, tree.getClass());
    }
    return tree.toString();
  }

  private static void analyzeLiteral(
      final SourceContext context,
      final JCTree.JCLiteral literal,
      final int preferredPos,
      final int endPos)
      throws IOException {
    final Source src = context.getSource();
    final Tree.Kind kind = literal.getKind();
    final Object value = literal.getValue();
    final Range range = Range.create(src, preferredPos, endPos);
    final Variable variable = new Variable(kind.toString(), preferredPos, range);
    if (value != null) {
      variable.fqcn = value.getClass().getCanonicalName();
      variable.argumentIndex = context.getArgumentIndex();
      context.setArgumentFQCN(variable.fqcn);
    } else {
      variable.fqcn = "<null>";
      variable.argumentIndex = context.getArgumentIndex();
      context.setArgumentFQCN(variable.fqcn);
    }
    src.getCurrentScope().ifPresent(scope -> scope.addVariable(variable));
  }

  private static String markFQCN(final Source src, final String fqcn) {
    return markFQCN(src, fqcn, true);
  }

  private static String markFQCN(final Source src, final String fqcn, final boolean markUnUse) {
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
    ClassNameUtils.parseTypeParameter(fqcn)
        .forEach(
            s -> {
              if (s.startsWith(ClassNameUtils.CAPTURE_OF)) {
                final String cls = ClassNameUtils.removeCapture(s);
                if (cls.equals(ClassNameUtils.CAPTURE_OF)) {
                  final String ignore = markFQCN(src, ClassNameUtils.OBJECT_CLASS);
                } else {
                  final String ignore = markFQCN(src, cls);
                }
              } else {
                final String ignore = markFQCN(src, s);
              }
            });
    final CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
    if (!src.importClasses.contains(simpleName)
        && !cachedASMReflector.getGlobalClassIndex().containsKey(simpleName)) {
      src.unknown.add(simpleName);
    } else {
      if (markUnUse) {
        // contains
        final String name = ClassNameUtils.replaceInnerMark(simpleName);
        if (src.unused.contains(name)) {
          src.unused.remove(name);
        }
        final int i = simpleName.indexOf('$');
        if (i > 0) {
          final String parentClass = simpleName.substring(0, i);
          if (src.unused.contains(parentClass)) {
            src.unused.remove(parentClass);
          }
        }
      }
      final File classFile = cachedASMReflector.getClassFile(simpleName);
      if (classFile == null || !classFile.getName().endsWith(".jar")) {
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

  private static String getFieldScope(final FieldAccess fa, final String selectScope) {
    if (selectScope.length() <= AccessSymbol.SCOPE_LIMIT) {
      return selectScope.trim();
    }
    return fa.name;
  }

  private void analyzeCompilationUnitTree(
      final SourceContext context, final CompilationUnitTree cut) {
    final ExpressionTree packageExpr = cut.getPackageName();
    final Source src = context.getSource();
    log.trace("file={}", src.getFile());
    if (packageExpr == null) {
      src.packageName = "";
    } else {
      src.packageName = packageExpr.toString();
    }

    final EndPosTable endPosTable = ((JCTree.JCCompilationUnit) cut).endPositions;
    context.setEndPosTable(endPosTable);
    final CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();

    int firstLine = 0;
    for (final ImportTree imp : cut.getImports()) {
      final JCTree.JCImport jcImport = (JCTree.JCImport) imp;
      final int startPos = jcImport.getPreferredPosition();
      final int endPos = jcImport.getEndPosition(endPosTable);
      try {
        final Range range = Range.create(src, startPos + 1, endPos);
        firstLine = range.begin.line;
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }

      final String importClass = imp.getQualifiedIdentifier().toString();
      final String simpleName = ClassNameUtils.getSimpleName(importClass);
      if (simpleName.equals("*")) {
        // wild
        cachedASMReflector.getPackageClasses(importClass).values().forEach(src::addImport);
      } else {
        if (imp.isStatic()) {
          final Tree tree = imp.getQualifiedIdentifier();
          if (tree instanceof JCTree.JCFieldAccess) {
            final JCTree.JCFieldAccess fieldAccess = (JCTree.JCFieldAccess) tree;
            final com.sun.tools.javac.util.Name name = fieldAccess.getIdentifier();
            final JCTree.JCExpression expression = fieldAccess.getExpression();
            final String methodName = name.toString();
            final String decClazz = expression.toString();
            src.addStaticImport(methodName, decClazz);
          } else {
            log.warn("Not impl");
          }
        } else {
          src.addImport(importClass);
        }
      }
    }
    src.classStartLine = firstLine;

    try {
      for (final Tree td : cut.getTypeDecls()) {
        if (td instanceof JCTree.JCClassDecl) {
          final JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl) td;
          final Tree.Kind classDeclKind = classDecl.getKind();
          final boolean isInterface = classDeclKind.equals(Tree.Kind.INTERFACE);
          final boolean isEnum = classDeclKind.equals(Tree.Kind.ENUM);
          final int startPos = classDecl.getPreferredPosition();
          final int endPos = classDecl.getEndPosition(endPosTable);
          final JCTree.JCModifiers modifiers = classDecl.getModifiers();
          parseModifiers(context, modifiers);

          final JCTree.JCExpression extendsClause = classDecl.getExtendsClause();
          if (extendsClause != null) {
            this.analyzeParsedTree(context, extendsClause);
          }
          final List<JCTree.JCExpression> implementsClauses = classDecl.getImplementsClause();
          if (implementsClauses != null) {
            for (final JCTree.JCExpression implementsClause : implementsClauses) {
              this.analyzeParsedTree(context, implementsClause);
            }
          }

          final Name simpleName = classDecl.getSimpleName();
          final Range range = Range.create(src, startPos + 1, endPos);

          int nameStart = startPos + 6;
          if (isInterface) {
            nameStart = startPos + 10;
          } else if (isEnum) {
            nameStart = startPos + 5;
          }
          final Range nameRange = Range.create(src, nameStart, nameStart + simpleName.length());

          final String fqcn;
          if (src.packageName == null || src.packageName.isEmpty()) {
            fqcn = simpleName.toString();
          } else {
            fqcn = src.packageName + '.' + simpleName.toString();
          }
          final ClassScope classScope = new ClassScope(fqcn, nameRange, startPos, range);
          classScope.isEnum = isEnum;
          classScope.isInterface = isInterface;
          log.trace("class={}", classScope);

          src.startClass(classScope);
          classDecl.getMembers().forEach(wrapIOConsumer(tree -> analyzeParsedTree(context, tree)));
          final Optional<ClassScope> endClass = src.endClass();
          log.trace("class={}", endClass);
        } else if (td instanceof JCTree.JCSkip) {
          // skip
        } else {
          log.warn("unknown td={} {}", td, td.getClass());
        }
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void analyzeParsedTree(final SourceContext context, final JCTree tree)
      throws IOException {
    final JCDiagnostic.DiagnosticPosition pos = tree.pos();

    final EndPosTable endPosTable = context.getEndPosTable();
    final int startPos = pos.getStartPosition();
    final int preferredPos = pos.getPreferredPosition();
    final int endPos = pos.getEndPosition(endPosTable);

    final EntryMessage entryMessage =
        log.traceEntry(
            "# class={} preferredPos={} endPos={} expr='{}'",
            tree.getClass().getSimpleName(),
            preferredPos,
            endPos,
            tree);

    if (endPos == -1 && !(tree instanceof JCTree.JCAssign) && !(tree instanceof JCTree.JCIdent)) {
      // skip
      log.trace("skip expr={}", tree);
      log.traceExit(entryMessage);
      return;
    }

    if (tree instanceof JCTree.JCVariableDecl) {

      this.analyzeVariableDecl(context, (JCTree.JCVariableDecl) tree, preferredPos, endPos);

    } else if (tree instanceof JCTree.JCTypeCast) {

      final JCTree.JCTypeCast cast = (JCTree.JCTypeCast) tree;
      final JCTree.JCExpression expression = cast.getExpression();
      this.analyzeParsedTree(context, expression);

    } else if (tree instanceof JCTree.JCMethodDecl) {

      this.analyzeMethodDecl(context, (JCTree.JCMethodDecl) tree, preferredPos, endPos);

    } else if (tree instanceof JCTree.JCClassDecl) {

      this.analyzeClassDecl(context, (JCTree.JCClassDecl) tree, startPos, endPos);

    } else if (tree instanceof JCTree.JCBlock) {
      final JCTree.JCBlock block = (JCTree.JCBlock) tree;
      final int argumentIndex = context.getArgumentIndex();

      context.setArgumentIndex(-1);
      block.getStatements().forEach(wrapIOConsumer(stmt -> this.analyzeParsedTree(context, stmt)));

      context.setArgumentIndex(argumentIndex);
    } else if (tree instanceof JCTree.JCFieldAccess) {

      this.analyzeFieldAccess(context, (JCTree.JCFieldAccess) tree, preferredPos, endPos);

    } else if (tree instanceof JCTree.JCArrayAccess) {

      final JCTree.JCArrayAccess arrayAccess = (JCTree.JCArrayAccess) tree;
      final JCTree.JCExpression expression = arrayAccess.getExpression();
      if (expression != null) {
        this.analyzeParsedTree(context, expression);
      }
      final JCTree.JCExpression index = arrayAccess.getIndex();
      if (index != null) {
        this.analyzeParsedTree(context, index);
      }

    } else if (tree instanceof JCTree.JCExpressionStatement) {

      this.analyzeExpressionStatement(
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

      final JCTree.JCUnary unary = (JCTree.JCUnary) tree;
      final JCTree.JCExpression expression = unary.getExpression();
      this.analyzeParsedTree(context, expression);

    } else if (tree instanceof JCTree.JCSwitch) {

      this.analyzeSwitch(context, (JCTree.JCSwitch) tree);

    } else if (tree instanceof JCTree.JCReturn) {

      final JCTree.JCReturn ret = (JCTree.JCReturn) tree;
      final JCTree.JCExpression expression = ret.getExpression();
      if (expression != null) {
        this.analyzeParsedTree(context, ret.getExpression());
      }
    } else if (tree instanceof JCTree.JCForLoop) {

      this.analyzeForLoop(context, (JCTree.JCForLoop) tree);

    } else if (tree instanceof JCTree.JCEnhancedForLoop) {

      this.analyzeEnhancedForLoop(context, (JCTree.JCEnhancedForLoop) tree);

    } else if (tree instanceof JCTree.JCTry) {

      this.analyzeTry(context, (JCTree.JCTry) tree);

    } else if (tree instanceof JCTree.JCIf) {

      final JCTree.JCIf ifExpr = (JCTree.JCIf) tree;
      final JCTree.JCExpression condition = ifExpr.getCondition();
      final JCTree.JCStatement thenStatement = ifExpr.getThenStatement();
      final JCTree.JCStatement elseStatement = ifExpr.getElseStatement();
      this.analyzeParsedTree(context, condition);
      if (thenStatement != null) {
        this.analyzeParsedTree(context, thenStatement);
      }
      if (elseStatement != null) {
        this.analyzeParsedTree(context, elseStatement);
      }

    } else if (tree instanceof JCTree.JCParens) {

      final JCTree.JCParens parens = (JCTree.JCParens) tree;
      final JCTree.JCExpression expression = parens.getExpression();
      this.analyzeParsedTree(context, expression);

    } else if (tree instanceof JCTree.JCNewClass) {

      this.analyzeNewClass(context, (JCTree.JCNewClass) tree, preferredPos, endPos);

    } else if (tree instanceof JCTree.JCBinary) {

      final JCTree.JCBinary binary = (JCTree.JCBinary) tree;
      final JCTree.JCExpression leftOperand = binary.getLeftOperand();
      final JCTree.JCExpression rightOperand = binary.getRightOperand();

      this.analyzeParsedTree(context, leftOperand);
      this.analyzeParsedTree(context, rightOperand);

    } else if (tree instanceof JCTree.JCMethodInvocation) {

      this.analyzeMethodInvocation(context, (JCTree.JCMethodInvocation) tree, preferredPos, endPos);

    } else if (tree instanceof JCTree.JCAssign) {

      final JCTree.JCAssign assign = (JCTree.JCAssign) tree;
      final JCTree.JCExpression expression = assign.getExpression();
      final JCTree.JCExpression variable = assign.getVariable();

      if (variable != null) {
        this.analyzeParsedTree(context, variable);
      }
      if (expression != null) {
        this.analyzeParsedTree(context, expression);
      }
    } else if (tree instanceof JCTree.JCNewArray) {

      final JCTree.JCNewArray newArray = (JCTree.JCNewArray) tree;
      final JCTree.JCExpression type = newArray.getType();
      if (type != null) {
        this.analyzeParsedTree(context, type);
      }
      final List<JCTree.JCExpression> initializes = newArray.getInitializers();
      if (initializes != null) {
        initializes.forEach(
            wrapIOConsumer(jcExpression -> this.analyzeParsedTree(context, jcExpression)));
      }
      final List<JCTree.JCExpression> dimensions = newArray.getDimensions();

      if (dimensions != null) {
        dimensions.forEach(
            wrapIOConsumer(jcExpression -> this.analyzeParsedTree(context, jcExpression)));
      }
      if (newArray.type != null) {
        this.getTypeString(context.getSource(), newArray.type).ifPresent(context::setArgumentFQCN);
      }
    } else if (tree instanceof JCTree.JCPrimitiveTypeTree) {
      // skip
    } else if (tree instanceof JCTree.JCConditional) {

      final JCTree.JCConditional conditional = (JCTree.JCConditional) tree;
      final JCTree.JCExpression condition = conditional.getCondition();
      if (condition != null) {
        this.analyzeParsedTree(context, condition);
      }
      final JCTree.JCExpression trueExpression = conditional.getTrueExpression();
      if (trueExpression != null) {
        this.analyzeParsedTree(context, trueExpression);
      }
      final JCTree.JCExpression falseExpression = conditional.getFalseExpression();
      if (falseExpression != null) {
        this.analyzeParsedTree(context, falseExpression);
      }

    } else if (tree instanceof JCTree.JCLambda) {

      this.analyzeLambda(context, (JCTree.JCLambda) tree);

    } else if (tree instanceof JCTree.JCThrow) {

      final JCTree.JCThrow jcThrow = (JCTree.JCThrow) tree;
      final JCTree.JCExpression expression = jcThrow.getExpression();
      this.analyzeParsedTree(context, expression);

    } else if (tree instanceof JCTree.JCInstanceOf) {

      final JCTree.JCInstanceOf jcInstanceOf = (JCTree.JCInstanceOf) tree;
      final JCTree.JCExpression expression = jcInstanceOf.getExpression();
      if (expression != null) {
        this.analyzeParsedTree(context, expression);
      }
      final JCTree typeTree = jcInstanceOf.getType();
      if (typeTree != null) {
        this.analyzeParsedTree(context, typeTree);
      }

    } else if (tree instanceof JCTree.JCMemberReference) {
      final Source src = context.getSource();
      final JCTree.JCMemberReference memberReference = (JCTree.JCMemberReference) tree;
      final JCTree.JCExpression expression = memberReference.getQualifierExpression();
      final com.sun.tools.javac.util.Name name = memberReference.getName();
      final String methodName = name.toString();
      if (expression != null) {
        this.analyzeParsedTree(context, expression);
        final Symbol sym = memberReference.sym;
        if (sym != null) {
          // method invoke
          final int start = expression.getEndPosition(endPosTable) + 2;
          final Range range = Range.create(src, start, start + methodName.length());
          final String s = memberReference.toString();

          final MethodCall methodCall =
              new MethodCall(s, methodName, preferredPos + 1, range, range);
          if (sym instanceof Symbol.MethodSymbol) {
            final Symbol.MethodSymbol methodSymbol = (Symbol.MethodSymbol) sym;
            final java.util.List<String> arguments =
                methodSymbol
                    .getParameters()
                    .stream()
                    .map(varSymbol -> varSymbol.asType().toString())
                    .collect(Collectors.toList());
            if (arguments != null) {
              methodCall.arguments = arguments;
            }
          }
          final Symbol owner = sym.owner;
          if (owner.type != null) {
            this.getTypeString(src, owner.type)
                .ifPresent(fqcn -> methodCall.declaringClass = TreeAnalyzer.markFQCN(src, fqcn));
          }

          if (sym.type != null) {
            this.getTypeString(src, sym.type)
                .ifPresent(
                    fqcn -> {
                      methodCall.returnType = TreeAnalyzer.markFQCN(src, fqcn);
                      // TODO add args
                    });
          }
          src.getCurrentScope().ifPresent(scope -> scope.addMethodCall(methodCall));
        }
      }

    } else if (tree instanceof JCTree.JCWhileLoop) {

      final JCTree.JCWhileLoop whileLoop = (JCTree.JCWhileLoop) tree;
      final JCTree.JCExpression condition = whileLoop.getCondition();
      if (condition != null) {
        this.analyzeParsedTree(context, condition);
      }
      final JCTree.JCStatement statement = whileLoop.getStatement();
      if (statement != null) {
        this.analyzeParsedTree(context, statement);
      }

    } else if (tree instanceof JCTree.JCSynchronized) {
      final JCTree.JCSynchronized jcSynchronized = (JCTree.JCSynchronized) tree;
      final JCTree.JCExpression expression = jcSynchronized.getExpression();
      if (expression != null) {
        this.analyzeParsedTree(context, expression);
      }
      final JCTree.JCBlock block = jcSynchronized.getBlock();
      if (block != null) {
        this.analyzeParsedTree(context, block);
      }
    } else if (tree instanceof JCTree.JCAssert) {

      final JCTree.JCAssert jcAssert = (JCTree.JCAssert) tree;
      final JCTree.JCExpression condition = jcAssert.getCondition();
      if (condition != null) {
        this.analyzeParsedTree(context, condition);
      }
      final JCTree.JCExpression detail = jcAssert.getDetail();
      if (detail != null) {
        this.analyzeParsedTree(context, detail);
      }
    } else if (tree instanceof JCTree.JCArrayTypeTree) {
      final JCTree.JCArrayTypeTree arrayTypeTree = (JCTree.JCArrayTypeTree) tree;
      final JCTree type = arrayTypeTree.getType();
      if (type != null) {
        this.analyzeParsedTree(context, type);
      }
    } else if (tree instanceof JCTree.JCDoWhileLoop) {
      final JCTree.JCDoWhileLoop doWhileLoop = (JCTree.JCDoWhileLoop) tree;
      final JCTree.JCExpression condition = doWhileLoop.getCondition();
      if (condition != null) {
        this.analyzeParsedTree(context, condition);
      }
      final JCTree.JCStatement statement = doWhileLoop.getStatement();
      if (statement != null) {
        this.analyzeParsedTree(context, statement);
      }
    } else if (tree instanceof JCTree.JCLabeledStatement) {
      final JCTree.JCLabeledStatement labeledStatement = (JCTree.JCLabeledStatement) tree;
      final JCTree.JCStatement statement = labeledStatement.getStatement();
      if (statement != null) {
        this.analyzeParsedTree(context, statement);
      }
    } else if (tree instanceof JCTree.JCTypeApply) {
      final JCTree.JCTypeApply typeApply = (JCTree.JCTypeApply) tree;
      final JCTree type = typeApply.getType();
      if (type != null) {
        this.analyzeParsedTree(context, type);
      }
    } else if (tree instanceof JCTree.JCAssignOp) {
      final JCTree.JCAssignOp assignOp = (JCTree.JCAssignOp) tree;
      final JCTree.JCExpression expression = assignOp.getExpression();
      if (expression != null) {
        this.analyzeParsedTree(context, expression);
      }
    } else if (tree instanceof JCTree.JCAnnotation) {
      final JCTree.JCAnnotation annotation = (JCTree.JCAnnotation) tree;
      for (final JCTree.JCExpression expression : annotation.getArguments()) {
        this.analyzeParsedTree(context, expression);
      }
      final JCTree annotationType = annotation.getAnnotationType();
      this.analyzeParsedTree(context, annotationType);
    } else if (tree instanceof JCTree.JCSkip) {
      // skip
    } else {
      Source src = context.getSource();
      log.warn("@@ unknown tree class={} expr={} filePath={}", tree.getClass(), tree, src.filePath);
    }

    log.traceExit(entryMessage);
  }

  private void analyzeLambda(final SourceContext context, final JCTree.JCLambda lambda)
      throws IOException {
    final boolean isParameter = context.isParameter();
    final boolean isArgument = context.isArgument();
    final int argumentIndex = context.getArgumentIndex();

    final java.util.List<? extends VariableTree> parameters = lambda.getParameters();
    if (parameters != null) {
      parameters.forEach(
          wrapIOConsumer(
              v -> {
                if (v instanceof JCTree.JCVariableDecl) {
                  this.analyzeParsedTree(context, (JCTree.JCVariableDecl) v);
                }
              }));
    }
    final JCTree body = lambda.getBody();
    if (body != null) {
      context.setArgumentIndex(-1);
      this.analyzeParsedTree(context, body);
      context.setArgumentIndex(argumentIndex);
    }

    context.setParameter(isParameter);
    final Type lambdaType = lambda.type;
    if (lambdaType != null) {
      final Source src = context.getSource();
      this.getTypeString(src, lambdaType)
          .ifPresent(
              fqcn -> {
                context.setArgument(isArgument);
                // TODO
                context.setArgumentFQCN(fqcn);
              });
    }
  }

  private void analyzeEnhancedForLoop(
      final SourceContext context, final JCTree.JCEnhancedForLoop forLoop) throws IOException {
    final JCTree.JCExpression expression = forLoop.getExpression();
    if (expression != null) {
      this.analyzeParsedTree(context, expression);
    }
    final JCTree.JCVariableDecl variable = forLoop.getVariable();
    if (variable != null) {
      this.analyzeParsedTree(context, variable);
    }
    final JCTree.JCStatement statement = forLoop.getStatement();
    if (statement != null) {
      this.analyzeParsedTree(context, statement);
    }
  }

  private void analyzeSwitch(final SourceContext context, final JCTree.JCSwitch jcSwitch)
      throws IOException {
    final JCTree.JCExpression expression = jcSwitch.getExpression();
    this.analyzeParsedTree(context, expression);
    final List<JCTree.JCCase> cases = jcSwitch.getCases();
    if (cases != null) {
      cases.forEach(
          wrapIOConsumer(
              jcCase -> {
                final JCTree.JCExpression expression1 = jcCase.getExpression();
                if (expression1 != null) {
                  this.analyzeParsedTree(context, expression1);
                }
                final List<JCTree.JCStatement> statements = jcCase.getStatements();
                if (statements != null) {
                  statements.forEach(
                      wrapIOConsumer(jcStatement -> this.analyzeParsedTree(context, jcStatement)));
                }
              }));
    }
  }

  private void analyzeTry(final SourceContext context, final JCTree.JCTry tryExpr)
      throws IOException {
    final JCTree.JCBlock block = tryExpr.getBlock();
    final List<JCTree> resources = tryExpr.getResources();
    final List<JCTree.JCCatch> catches = tryExpr.getCatches();
    final JCTree.JCBlock finallyBlock = tryExpr.getFinallyBlock();
    if (resources != null && !resources.isEmpty()) {
      for (final JCTree resource : resources) {
        this.analyzeParsedTree(context, resource);
      }
    }
    this.analyzeParsedTree(context, block);

    if (catches != null) {
      catches.forEach(
          wrapIOConsumer(
              jcCatch -> {
                final JCTree.JCVariableDecl parameter = jcCatch.getParameter();
                this.analyzeParsedTree(context, parameter);
                this.analyzeParsedTree(context, jcCatch.getBlock());
              }));
    }

    if (finallyBlock != null) {
      this.analyzeParsedTree(context, finallyBlock);
    }
  }

  private void analyzeForLoop(final SourceContext context, final JCTree.JCForLoop forLoop)
      throws IOException {
    final List<JCTree.JCStatement> initializer = forLoop.getInitializer();
    final JCTree.JCExpression condition = forLoop.getCondition();
    final List<JCTree.JCExpressionStatement> updates = forLoop.getUpdate();
    final JCTree.JCStatement statement = forLoop.getStatement();
    if (initializer != null) {
      initializer.forEach(wrapIOConsumer(s -> this.analyzeParsedTree(context, s)));
    }
    if (condition != null) {
      this.analyzeParsedTree(context, condition);
    }
    if (updates != null) {
      updates.forEach(wrapIOConsumer(s -> this.analyzeParsedTree(context, s)));
    }
    if (statement != null) {
      this.analyzeParsedTree(context, statement);
    }
  }

  private Optional<String> getTypeString(final Source src, @Nullable final Type type) {
    if (type == null) {
      return Optional.empty();
    }
    if (type instanceof Type.CapturedType) {
      final Type.CapturedType capturedType = (Type.CapturedType) type;
      final Type.WildcardType wildcardType = capturedType.wildcard;
      if (wildcardType.kind.toString().equals("?")) {
        final Type upperBound = type.getUpperBound();
        if (upperBound != null && upperBound.tsym != null) {
          final String s = upperBound.tsym.flatName().toString();
          return Optional.of(s);
        }
      }
      if (wildcardType.type != null && wildcardType.type.tsym != null) {
        final String s =
            wildcardType.kind.toString() + wildcardType.type.tsym.flatName().toString();
        return Optional.of(s);
      }
      return Optional.empty();
    } else if (type instanceof Type.ArrayType) {
      final Type.ArrayType arrayType = (Type.ArrayType) type;
      final Type componentType = arrayType.getComponentType();
      if (componentType != null && componentType.tsym != null) {
        final String s = arrayType.toString();
        return Optional.of(s);
      }
      return Optional.empty();
    } else if (type instanceof Type.WildcardType) {
      final Type.WildcardType wildcardType = (Type.WildcardType) type;
      if (wildcardType.type != null) {
        if (wildcardType.kind.toString().equals("?") && wildcardType.type.tsym != null) {
          final String s = wildcardType.type.tsym.flatName().toString();
          return Optional.of(s);
        }
        if (wildcardType.type.tsym != null) {
          final String s =
              wildcardType.kind.toString() + wildcardType.type.tsym.flatName().toString();
          return Optional.of(s);
        }
      }
      return Optional.empty();
    } else if (type instanceof Type.MethodType) {
      final Type.MethodType methodType = (Type.MethodType) type;
      final Type returnType = methodType.getReturnType();
      if (returnType != null && returnType.tsym != null) {
        String baseType = returnType.tsym.flatName().toString();
        final List<Type> typeArguments = returnType.getTypeArguments();
        baseType = getFlatName(src, baseType, typeArguments);
        return Optional.of(baseType);
      }
      return Optional.empty();
    } else {
      if (type.toString().equals("?")) {
        return Optional.of(ClassNameUtils.OBJECT_CLASS);
      }
      if (type.tsym == null) {
        return Optional.empty();
      }
      String baseType = type.tsym.flatName().toString();
      if (baseType.equals("Array")) {
        return Optional.of(ClassNameUtils.OBJECT_CLASS + ClassNameUtils.ARRAY);
      }
      if (baseType.equals("Method")) {
        return Optional.empty();
      }

      final List<Type> typeArguments = type.getTypeArguments();
      baseType = getFlatName(src, baseType, typeArguments);
      return Optional.of(baseType);
    }
  }

  private String getFlatName(final Source src, String baseType, final List<Type> typeArguments) {
    if (typeArguments != null && !typeArguments.isEmpty()) {
      final java.util.List<String> temp = new ArrayList<>(typeArguments.length());
      for (final Type typeArgument : typeArguments) {
        getTypeString(src, typeArgument).ifPresent(temp::add);
      }
      final String join = Joiner.on(",").join(temp);
      if (!join.isEmpty()) {
        baseType = baseType + '<' + join + '>';
      }
    }
    return baseType;
  }

  private void analyzeMethodInvocation(
      final SourceContext context,
      final JCTree.JCMethodInvocation methodInvocation,
      int preferredPos,
      int endPos)
      throws IOException {
    final Source src = context.getSource();
    EndPosTable endPosTable = context.getEndPosTable();
    final Type returnType = methodInvocation.type;

    final boolean isParameter = context.isParameter();
    final int argumentIndex = context.getArgumentIndex();
    final List<JCTree.JCExpression> argumentExpressions = methodInvocation.getArguments();
    final java.util.List<String> arguments = getArgumentsType(context, argumentExpressions);
    context.setParameter(isParameter);
    context.setArgumentIndex(argumentIndex);

    final JCTree.JCExpression methodSelect = methodInvocation.getMethodSelect();

    if (methodSelect instanceof JCTree.JCIdent) {
      // super
      final JCTree.JCIdent ident = (JCTree.JCIdent) methodSelect;
      final String s = ident.getName().toString();
      final Symbol sym = ident.sym;
      if (sym != null) {
        final Symbol owner = sym.owner;
        final int nameBegin = ident.getStartPosition();
        final int nameEnd = ident.getEndPosition(endPosTable);
        final Range nameRange = Range.create(src, nameBegin, nameEnd);
        final Range range = Range.create(src, nameBegin, endPos);

        if (s.equals("super")) {
          // call super constructor
          final String constructor = owner.flatName().toString();
          final MethodCall methodCall = new MethodCall(s, constructor, nameBegin, nameRange, range);
          if (owner.type != null) {
            this.getTypeString(src, owner.type)
                .ifPresent(fqcn -> methodCall.declaringClass = TreeAnalyzer.markFQCN(src, fqcn));
          }
          if (owner.type != null) {
            this.setReturnTypeAndArgType(context, src, owner.type, methodCall);
          }
          src.getCurrentScope()
              .ifPresent(
                  scope -> {
                    if (arguments != null) {
                      methodCall.arguments = arguments;
                    }
                    final MethodCall methodCall1 = scope.addMethodCall(methodCall);
                  });
        } else {
          final MethodCall methodCall = new MethodCall(s, preferredPos + 1, nameRange, range);

          if (owner != null && owner.type != null) {
            this.getTypeString(src, owner.type)
                .ifPresent(
                    fqcn -> {
                      final String className = src.staticImportClass.get(s);
                      if (fqcn.equals(className)) {
                        // static imported
                        methodCall.declaringClass = TreeAnalyzer.markFQCN(src, fqcn, false);
                      } else {
                        methodCall.declaringClass = TreeAnalyzer.markFQCN(src, fqcn);
                      }
                    });
            this.setReturnTypeAndArgType(context, src, returnType, methodCall);
          }

          src.getCurrentScope()
              .ifPresent(
                  scope -> {
                    if (arguments != null) {
                      methodCall.arguments = arguments;
                    }
                    final MethodCall methodCall1 = scope.addMethodCall(methodCall);
                  });
        }
      }
    } else if (methodSelect instanceof JCTree.JCFieldAccess) {
      final JCTree.JCFieldAccess fa = (JCTree.JCFieldAccess) methodSelect;
      final JCTree.JCExpression expression = fa.getExpression();
      final String selectScope = expression.toString();
      this.analyzeParsedTree(context, expression);
      final Type owner = expression.type;
      final String name = fa.getIdentifier().toString();
      final int start = preferredPos - name.length();

      final Range nameRange = Range.create(src, start, preferredPos);
      final Range range = Range.create(src, start, endPos);
      final MethodCall methodCall = new MethodCall(selectScope, name, start + 1, nameRange, range);

      if (owner == null) {
        // call static
        if (expression instanceof JCTree.JCIdent) {
          final JCTree.JCIdent ident = (JCTree.JCIdent) expression;
          final String nm = ident.getName().toString();
          final String clazz = src.getImportedClassFQCN(nm, null);
          if (clazz != null) {
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
        this.getTypeString(src, owner)
            .ifPresent(fqcn -> methodCall.declaringClass = TreeAnalyzer.markFQCN(src, fqcn));
      }

      if (returnType == null) {
        if (expression instanceof JCTree.JCIdent) {
          final JCTree.JCIdent ident = (JCTree.JCIdent) expression;
          final String nm = ident.getName().toString();
          final String clazz = src.getImportedClassFQCN(nm, null);
          if (clazz != null) {
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
        this.getTypeString(src, returnType)
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
                if (arguments != null) {
                  methodCall.arguments = arguments;
                }
                final MethodCall methodCall1 = scope.addMethodCall(methodCall);
              });

    } else {
      log.warn("unknown method select:{}", methodSelect);
      this.analyzeParsedTree(context, methodSelect);
    }
  }

  private void analyzeNewClass(
      final SourceContext context, final JCTree.JCNewClass newClass, int preferredPos, int endPos)
      throws IOException {
    final Source src = context.getSource();
    final EndPosTable endPosTable = context.getEndPosTable();
    final boolean isParameter = context.isParameter();
    final boolean isArgument = context.isArgument();
    final int argumentIndex = context.getArgumentIndex();

    final List<JCTree.JCExpression> argumentExpressions = newClass.getArguments();
    final java.util.List<String> arguments = getArgumentsType(context, argumentExpressions);

    context.setParameter(isParameter);
    context.setArgument(isArgument);
    context.setArgumentIndex(argumentIndex);

    final JCTree.JCExpression identifier = newClass.getIdentifier();
    final String name = identifier.toString();

    final int start = identifier.getStartPosition();
    final int end = identifier.getEndPosition(endPosTable);
    final Range nameRange = Range.create(src, start, end);

    final Range range = Range.create(src, preferredPos + 4, endPos);
    final MethodCall methodCall = new MethodCall(name, preferredPos, nameRange, range);

    final Type type = identifier.type;

    this.getTypeString(src, type)
        .ifPresent(
            fqcn -> {
              methodCall.declaringClass = TreeAnalyzer.markFQCN(src, fqcn);
              methodCall.returnType = fqcn;
              methodCall.argumentIndex = argumentIndex;
              context.setArgumentFQCN(fqcn);
            });
    if (type == null) {
      // add className to unknown
      final ClassName className = new ClassName(name);
      final String simpleName = className.getName();
      if (!src.getImportedClassMap().containsKey(simpleName)
          && !CachedASMReflector.getInstance().getGlobalClassIndex().containsKey(simpleName)) {
        src.unknown.add(simpleName);
      }
    }

    final JCTree.JCClassDecl classBody = newClass.getClassBody();
    if (classBody != null) {
      this.analyzeParsedTree(context, classBody);
    }
    src.getCurrentScope()
        .ifPresent(
            scope -> {
              if (arguments != null) {
                methodCall.arguments = arguments;
              }
              final MethodCall methodCall1 = scope.addMethodCall(methodCall);
            });
  }

  @Nonnull
  private java.util.List<String> getArgumentsType(
      final SourceContext context, final List<JCTree.JCExpression> arguments) {
    final SourceContext newContext =
        new SourceContext(context.getSource(), context.getEndPosTable());
    newContext.setArgumentIndex(0);
    try {
      return arguments
          .stream()
          .map(
              expression -> {
                try {
                  newContext.setArgument(true);
                  this.analyzeParsedTree(newContext, expression);
                  newContext.incrArgumentIndex();
                  newContext.setArgument(false);
                  return newContext.getArgumentFQCN();
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              })
          .collect(Collectors.toList());
    } finally {
      // reset
      context.setArgumentIndex(-1);
    }
  }

  private void analyzeExpressionStatement(
      final SourceContext context,
      final JCTree.JCExpressionStatement expr,
      final int preferredPos,
      final int endPos) {
    final Source src = context.getSource();
    final JCTree.JCExpression expression = expr.getExpression();
    final Tree.Kind expressionKind = expression.getKind();
    final JCTree expressionTree = expression.getTree();

    src.getCurrentBlock()
        .ifPresent(
            wrapIOConsumer(
                blockScope -> {
                  final Range range = Range.create(src, preferredPos, endPos);

                  final ExpressionScope expressionScope = new ExpressionScope(preferredPos, range);
                  if (blockScope instanceof ClassScope) {
                    expressionScope.isField = true;
                  }
                  blockScope.startExpression(expressionScope);
                  if (expressionKind.equals(Tree.Kind.ASSIGNMENT)) {
                    final JCTree.JCAssign assign = (JCTree.JCAssign) expressionTree;
                    final JCTree.JCExpression lhs = assign.lhs;
                    final JCTree.JCExpression rhs = assign.rhs;
                    if (lhs != null) {
                      this.analyzeParsedTree(context, lhs);
                    }
                    if (rhs != null) {
                      this.analyzeParsedTree(context, rhs);
                    }
                  } else if (expressionKind.equals(Tree.Kind.METHOD_INVOCATION)) {
                    final JCTree.JCMethodInvocation methodInvocation =
                        (JCTree.JCMethodInvocation) expressionTree;
                    this.analyzeParsedTree(context, methodInvocation);
                  } else if (expressionKind.equals(Tree.Kind.POSTFIX_DECREMENT)
                      || expressionKind.equals(Tree.Kind.POSTFIX_INCREMENT)) {
                    if (expressionTree instanceof JCTree.JCUnary) {
                      final JCTree.JCUnary jcUnary = (JCTree.JCUnary) expressionTree;
                      final JCTree.JCExpression args = jcUnary.getExpression();
                      if (args != null) {
                        this.analyzeParsedTree(context, args);
                      }
                    } else {
                      log.warn(
                          "POSTFIX_XXXXX expressionKind:{} tree:{}",
                          expressionKind,
                          expressionTree.getClass());
                    }

                  } else if (expressionKind.equals(Tree.Kind.PREFIX_DECREMENT)
                      || expressionKind.equals(Tree.Kind.PREFIX_INCREMENT)) {
                    if (expressionTree instanceof JCTree.JCUnary) {
                      final JCTree.JCUnary jcUnary = (JCTree.JCUnary) expressionTree;
                      final JCTree.JCExpression args = jcUnary.getExpression();
                      if (args != null) {
                        this.analyzeParsedTree(context, args);
                      }
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
                      final JCTree.JCAssignOp assignOp = (JCTree.JCAssignOp) expressionTree;
                      final JCTree.JCExpression lhs = assignOp.lhs;
                      final JCTree.JCExpression rhs = assignOp.rhs;
                      if (lhs != null) {
                        this.analyzeParsedTree(context, lhs);
                      }
                      if (rhs != null) {
                        this.analyzeParsedTree(context, rhs);
                      }
                    } else {
                      log.warn(
                          "XXXX_ASSIGNMENT expressionKind:{} tree:{}",
                          expressionKind,
                          expressionTree.getClass());
                    }
                  } else if (expressionKind.equals(Tree.Kind.NEW_CLASS)) {
                    if (expressionTree instanceof JCTree.JCNewClass) {
                      final JCTree.JCNewClass newClass = (JCTree.JCNewClass) expressionTree;
                      this.analyzeParsedTree(context, newClass);
                    } else {
                      log.warn(
                          "NEW_CLASS expressionKind:{} tree:{}",
                          expressionKind,
                          expressionTree.getClass());
                    }
                  } else if (expressionKind.equals(Tree.Kind.ERRONEOUS)) {
                    if (expressionTree instanceof JCTree.JCErroneous) {
                      final JCTree.JCErroneous erroneous = (JCTree.JCErroneous) expressionTree;
                      final List<? extends JCTree> errorTrees = erroneous.getErrorTrees();
                      if (errorTrees != null) {
                        errorTrees.forEach(
                            wrapIOConsumer(tree -> this.analyzeParsedTree(context, tree)));
                      }
                    }
                  } else {
                    log.warn(
                        "expressionKind:{} tree:{}", expressionKind, expressionTree.getClass());
                  }

                  blockScope.endExpression();
                }));
  }

  private void analyzeFieldAccess(
      final SourceContext context,
      final JCTree.JCFieldAccess fieldAccess,
      final int preferredPos,
      final int endPos)
      throws IOException {

    final Source src = context.getSource();
    final Symbol sym = fieldAccess.sym;
    final JCTree.JCExpression selected = fieldAccess.getExpression();
    this.analyzeParsedTree(context, selected);

    final String selectScope = selected.toString();
    final Name identifier = fieldAccess.getIdentifier();
    final Range range = Range.create(src, preferredPos + 1, endPos);
    if (sym == null) {
      if (src.isReportUnknown()) {
        log.warn("unknown fieldAccess sym is null fieldAccess:{} {}", fieldAccess, src.filePath);
      }
      return;
    }

    final ElementKind kind = sym.getKind();

    if (kind.equals(ElementKind.FIELD)) {
      //
      final FieldAccess fa = new FieldAccess(identifier.toString(), preferredPos + 1, range);
      fa.scope = getFieldScope(fa, selectScope);
      final Symbol owner = sym.owner;

      if (owner.type != null) {
        this.getTypeString(src, owner.type)
            .ifPresent(fqcn -> fa.declaringClass = TreeAnalyzer.markFQCN(src, fqcn));
      }
      if (sym.type != null) {
        this.setReturnTypeAndArgType(context, src, sym.type, fa);
      }
      src.getCurrentScope().ifPresent(scope -> scope.addFieldAccess(fa));

    } else if (kind.equals(ElementKind.METHOD)) {
      final MethodCall methodCall =
          new MethodCall(selectScope, identifier.toString(), preferredPos + 1, range, range);
      final Symbol owner = sym.owner;
      if (owner != null && owner.type != null) {
        this.getTypeString(src, owner.type)
            .ifPresent(fqcn -> methodCall.declaringClass = TreeAnalyzer.markFQCN(src, fqcn));
      }
      if (sym.type != null) {
        this.setReturnTypeAndArgType(context, src, sym.type, methodCall);
      }
      src.getCurrentScope().ifPresent(scope -> scope.addMethodCall(methodCall));

    } else if (kind.equals(ElementKind.ENUM)) {
      if (sym.type != null) {
        this.getTypeString(src, sym.type).ifPresent(fqcn -> TreeAnalyzer.markFQCN(src, fqcn));
      }
    } else if (kind.equals(ElementKind.ENUM_CONSTANT)) {
      final FieldAccess fa = new FieldAccess(identifier.toString(), preferredPos + 1, range);
      fa.scope = getFieldScope(fa, selectScope);
      fa.isEnum = true;
      final Symbol owner = sym.owner;

      if (owner.type != null) {
        this.getTypeString(src, owner.type)
            .ifPresent(fqcn -> fa.declaringClass = TreeAnalyzer.markFQCN(src, fqcn));
      }
      if (sym.type != null) {
        this.setReturnTypeAndArgType(context, src, sym.type, fa);
      }
      src.getCurrentScope().ifPresent(scope -> scope.addFieldAccess(fa));

    } else if (kind.equals(ElementKind.PACKAGE)) {
      // skip
    } else if (kind.equals(ElementKind.CLASS)) {
      if (sym.type != null) {
        this.getTypeString(src, sym.type).ifPresent(fqcn -> TreeAnalyzer.markFQCN(src, fqcn));
      }
    } else if (kind.equals(ElementKind.INTERFACE)) {
      if (sym.type != null) {
        this.getTypeString(src, sym.type).ifPresent(fqcn -> TreeAnalyzer.markFQCN(src, fqcn));
      }
    } else if (kind.equals(ElementKind.ANNOTATION_TYPE)) {
      if (sym.type != null) {
        this.getTypeString(src, sym.type).ifPresent(fqcn -> TreeAnalyzer.markFQCN(src, fqcn));
      }
    } else {
      log.warn("other kind:{}", kind);
    }
  }

  private void setReturnTypeAndArgType(
      SourceContext context, Source src, Type type, AccessSymbol as) {
    this.getTypeString(src, type)
        .ifPresent(
            fqcn -> {
              as.returnType = TreeAnalyzer.markFQCN(src, fqcn);
              as.argumentIndex = context.getArgumentIndex();
              context.setArgumentFQCN(as.returnType);
            });
  }

  private void analyzeClassDecl(
      final SourceContext context,
      final JCTree.JCClassDecl classDecl,
      final int startPos,
      final int endPos)
      throws IOException {
    // innerClass
    final Source src = context.getSource();
    final Range range = Range.create(src, startPos, endPos);
    final Name simpleName = classDecl.getSimpleName();

    final JCTree.JCModifiers modifiers = classDecl.getModifiers();
    final int modPos = modifiers.pos;
    final int modEndPos = context.getEndPosTable().getEndPos(modifiers);
    int modLen = 0;
    if (modEndPos > 0) {
      modLen = modEndPos - modPos + 1;
    }
    parseModifiers(context, modifiers);
    final JCTree.JCExpression extendsClause = classDecl.getExtendsClause();
    if (extendsClause != null) {
      this.analyzeParsedTree(context, extendsClause);
    }
    final List<JCTree.JCExpression> implementsClauses = classDecl.getImplementsClause();
    if (implementsClauses != null) {
      for (final JCTree.JCExpression implementsClause : implementsClauses) {
        this.analyzeParsedTree(context, implementsClause);
      }
    }

    final Tree.Kind kind = classDecl.getKind();
    final boolean isInterface = kind.equals(Tree.Kind.INTERFACE);
    final boolean isEnum = kind.equals(Tree.Kind.ENUM);
    int nameStartPos = startPos + modLen;
    if (isInterface) {
      nameStartPos += 10;
    } else if (isEnum) {
      nameStartPos += 5;
    } else {
      nameStartPos += 6;
    }
    final Range nameRange = Range.create(src, nameStartPos, nameStartPos + simpleName.length());

    src.getCurrentClass()
        .ifPresent(
            wrapIOConsumer(
                parent -> {
                  final String parentName = parent.name;
                  final String fqcn = parentName + ClassNameUtils.INNER_MARK + simpleName;
                  final ClassScope classScope = new ClassScope(fqcn, nameRange, startPos, range);
                  classScope.isInterface = isInterface;
                  classScope.isEnum = isEnum;
                  log.trace("maybe inner class={}", classScope);
                  parent.startClass(classScope);
                  classDecl
                      .getMembers()
                      .forEach(wrapIOConsumer(tree1 -> this.analyzeParsedTree(context, tree1)));
                  final Optional<ClassScope> ignore = parent.endClass();
                }));
  }

  private void parseModifiers(
      final SourceContext context, @Nullable final JCTree.JCModifiers modifiers) {
    if (modifiers != null) {
      final List<JCTree.JCAnnotation> annotations = modifiers.getAnnotations();
      if (annotations != null) {
        annotations.forEach(
            wrapIOConsumer(
                jcAnnotation -> {
                  final JCTree annotationType = jcAnnotation.getAnnotationType();
                  this.analyzeParsedTree(context, annotationType);
                  final List<JCTree.JCExpression> arguments = jcAnnotation.getArguments();
                  if (arguments != null) {
                    arguments.forEach(
                        wrapIOConsumer(
                            jcExpression -> this.analyzeParsedTree(context, jcExpression)));
                  }
                }));
      }
    }
  }

  private void analyzeIdent(
      final SourceContext context,
      final JCTree.JCIdent ident,
      final int preferredPos,
      final int endPos)
      throws IOException {
    if (endPos == -1) {
      return;
    }
    final Symbol sym = ident.sym;
    final Source src = context.getSource();
    final Range range = Range.create(src, preferredPos, endPos);
    if (sym != null) {
      final Type type = sym.asType();
      final String name = ident.getName().toString();

      final Variable variable = new Variable(name, preferredPos, range);

      this.getTypeString(src, type)
          .ifPresent(
              fqcn -> {
                variable.fqcn = TreeAnalyzer.markFQCN(src, fqcn);
                variable.argumentIndex = context.getArgumentIndex();
                context.setArgumentFQCN(variable.fqcn);
              });

      src.getCurrentScope().ifPresent(scope -> scope.addVariable(variable));
    } else {
      String nm = ident.toString();
      final Variable variable = new Variable(nm, preferredPos, range);
      final Optional<ClassScope> currentClass = src.getCurrentClass();

      if (currentClass.isPresent()) {
        final String className = currentClass.get().name;
        if (ClassNameUtils.getSimpleName(className).equals(nm)) {
          variable.fqcn = TreeAnalyzer.markFQCN(src, className);
          variable.argumentIndex = context.getArgumentIndex();
          context.setArgumentFQCN(variable.fqcn);
          src.getCurrentScope().ifPresent(scope -> scope.addVariable(variable));
          return;
        }
      }

      final String clazz = src.getImportedClassFQCN(nm, null);
      if (clazz != null) {
        variable.fqcn = TreeAnalyzer.markFQCN(src, clazz);
        variable.argumentIndex = context.getArgumentIndex();
        context.setArgumentFQCN(variable.fqcn);
        src.getCurrentScope().ifPresent(scope -> scope.addVariable(variable));
        return;
      }

      src.classScopes.forEach(
          classScope -> {
            final String className = currentClass.get().name;
            if (ClassNameUtils.getSimpleName(className).equals(nm)) {
              variable.fqcn = TreeAnalyzer.markFQCN(src, className);
              variable.argumentIndex = context.getArgumentIndex();
              context.setArgumentFQCN(variable.fqcn);
              src.getCurrentScope().ifPresent(scope -> scope.addVariable(variable));
            }
          });
      // mark unknown
      final String unknown = TreeAnalyzer.markFQCN(src, nm);
    }
  }

  private void analyzeMethodDecl(
      final SourceContext context,
      final JCTree.JCMethodDecl md,
      final int preferredPos,
      final int endPos)
      throws IOException {

    final Source src = context.getSource();
    final String name = md.getName().toString();
    final JCTree.JCModifiers modifiers = md.getModifiers();
    parseModifiers(context, modifiers);
    final Range nameRange = Range.create(src, preferredPos, preferredPos + name.length());
    final Range range = Range.create(src, preferredPos, endPos);

    src.getCurrentClass()
        .ifPresent(
            wrapIOConsumer(
                (ClassScope classScope) -> {
                  String methodName = name;
                  boolean isConstructor = false;
                  String returnFQCN = "";

                  if (!name.equals("<init>")) {
                    // set return type
                    final JCTree returnTypeExpr = md.getReturnType();
                    if (returnTypeExpr != null) {
                      final Type type = returnTypeExpr.type;
                      if (type != null) {
                        returnFQCN = getTypeString(src, type).orElse(type.toString());
                      } else {
                        returnFQCN = resolveTypeFromImport(src, returnTypeExpr);
                      }
                    }
                  } else {
                    isConstructor = true;
                    Symbol.MethodSymbol sym = md.sym;
                    if (sym != null && sym.owner != null) {
                      final Type type = sym.owner.type;
                      methodName = getTypeString(src, type).orElse(name);
                      returnFQCN = methodName;
                    }
                  }

                  final MethodScope methodScope =
                      classScope.startMethod(
                          methodName, nameRange, preferredPos, range, isConstructor);
                  methodScope.returnType = TreeAnalyzer.markFQCN(src, returnFQCN);

                  // check method parameter
                  context.setParameter(true);
                  md.getParameters()
                      .forEach(wrapIOConsumer(vd -> this.analyzeParsedTree(context, vd)));
                  context.setParameter(false);

                  // set exceptions
                  md.getThrows()
                      .forEach(
                          expr -> {
                            final String ex = resolveTypeFromImport(src, expr);
                            final String fqcn = TreeAnalyzer.markFQCN(src, ex);
                          });

                  final JCTree.JCBlock body = md.getBody();
                  // parse body
                  if (body != null) {
                    this.analyzeParsedTree(context, body);
                  }
                  final Optional<MethodScope> endMethod = classScope.endMethod();
                }));
  }

  private void analyzeVariableDecl(
      final SourceContext context,
      final JCTree.JCVariableDecl vd,
      final int preferredPos,
      final int endPos) {
    final Source src = context.getSource();
    final Name name = vd.getName();
    final JCTree.JCExpression initializer = vd.getInitializer();
    final JCTree.JCExpression nameExpression = vd.getNameExpression();
    final JCTree typeTree = vd.getType();
    final JCTree.JCModifiers modifiers = vd.getModifiers();

    parseModifiers(context, modifiers);

    if (initializer != null || nameExpression != null) {
      log.trace("init={} name={} tree={}", initializer, nameExpression, typeTree);
    }

    src.getCurrentBlock()
        .ifPresent(
            wrapIOConsumer(
                blockScope -> {
                  final String vName = name.toString();
                  final Range range = Range.create(src, preferredPos, endPos);
                  final Range nameRange =
                      Range.create(src, preferredPos, preferredPos + vName.length());

                  final ExpressionScope expressionScope = new ExpressionScope(preferredPos, range);
                  if (blockScope instanceof ClassScope) {
                    expressionScope.isField = true;
                  }
                  blockScope.startExpression(expressionScope);

                  if (typeTree instanceof JCTree.JCTypeUnion) {
                    final JCTree.JCTypeUnion union = (JCTree.JCTypeUnion) typeTree;
                    for (final JCTree.JCExpression expression : union.getTypeAlternatives()) {
                      String type = expression.toString();
                      final Variable variable = new Variable(vName, preferredPos, nameRange);
                      type = src.getImportedClassFQCN(type, type);
                      variable.fqcn = TreeAnalyzer.markFQCN(src, type);
                      src.getCurrentScope().ifPresent(scope -> scope.addVariable(variable));
                    }
                  } else {
                    try {
                      final Variable variable = new Variable(vName, preferredPos, nameRange);

                      if (vd.getTag().equals(JCTree.Tag.VARDEF)) {
                        variable.def = true;
                      }
                      if (context.isParameter()) {
                        variable.parameter = true;
                      }

                      if (typeTree instanceof JCTree.JCExpression) {
                        final JCTree.JCExpression expression = (JCTree.JCExpression) typeTree;
                        final Type type = expression.type;

                        if (type == null && expression instanceof JCTree.JCIdent) {
                          final JCTree.JCIdent ident = (JCTree.JCIdent) expression;
                          final String nm = ident.getName().toString();
                          final String identClazz = src.getImportedClassFQCN(nm, null);
                          if (identClazz != null) {
                            variable.fqcn = TreeAnalyzer.markFQCN(src, identClazz);
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
                          final String fqcn = resolveTypeFromImport(src, expression);
                          if (fqcn != null) {
                            variable.fqcn = TreeAnalyzer.markFQCN(src, fqcn);
                          }
                        }
                      } else {
                        if (src.isReportUnknown()) {
                          log.warn(
                              "unknown typeTree class expression={} {}", typeTree, src.filePath);
                        }
                      }
                      src.getCurrentScope()
                          .ifPresent(
                              scope -> {
                                if (variable.fqcn != null) {
                                  if (variable.parameter) {
                                    Scope parent = scope;
                                    if (scope instanceof ExpressionScope) {
                                      final ExpressionScope expressionScope1 =
                                          (ExpressionScope) scope;
                                      parent = expressionScope1.parent;
                                    }
                                    if (parent instanceof MethodScope) {
                                      final MethodScope methodScope = (MethodScope) parent;
                                      methodScope.addMethodParameter(variable.fqcn);
                                    }
                                  }
                                  scope.addVariable(variable);
                                }
                              });
                      if (initializer != null) {
                        this.analyzeParsedTree(context, initializer);
                      }
                    } finally {
                      blockScope.endExpression();
                    }
                  }
                }));
  }

  public Map<File, Source> analyze(
      final Iterable<? extends CompilationUnitTree> parsed,
      final Set<File> errorFiles,
      @Nullable final JavaAnalyzer.SourceAnalyzedHandler handler) {

    final Map<File, Source> analyzeMap = new ConcurrentHashMap<>(SOURCE_LIMIT);

    if (log.isDebugEnabled()) {
      parsed.forEach(
          wrapIOConsumer(
              cut -> {
                final Source source = this.analyzeUnit(cut, errorFiles);
                if (handler != null) {
                  handler.analyzed(source);
                }
                if (analyzeMap.size() < SOURCE_LIMIT) {
                  final File file = source.getFile();
                  analyzeMap.putIfAbsent(file, source);
                }
              }));
    } else {
      try (Stream<? extends CompilationUnitTree> stream =
          StreamSupport.stream(parsed.spliterator(), true)) {
        stream.forEach(
            wrapIOConsumer(
                cut -> {
                  final Source source = this.analyzeUnit(cut, errorFiles);
                  if (handler != null) {
                    handler.analyzed(source);
                  }
                  if (analyzeMap.size() < SOURCE_LIMIT) {
                    final File file = source.getFile();
                    analyzeMap.putIfAbsent(file, source);
                  }
                }));
      }
    }

    if (handler != null) {
      try {
        handler.complete();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    return analyzeMap;
  }

  private Source analyzeUnit(final CompilationUnitTree cut, final Set<File> errorFiles)
      throws IOException {
    final URI uri = cut.getSourceFile().toUri();
    final File file = new File(uri.normalize());
    final String path = file.getCanonicalPath();
    final Source source = new Source(path);
    if (errorFiles.contains(file)) {
      source.hasCompileError = true;
    }
    final SourceContext context = new SourceContext(source);
    final EntryMessage entryMessage = log.traceEntry("---------- analyze file:{} ----------", file);
    this.analyzeCompilationUnitTree(context, cut);
    source.resetLineRange();
    log.traceExit(entryMessage);
    return source;
  }
}
