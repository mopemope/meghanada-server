package meghanada.analyze;

import com.google.common.base.Joiner;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassNameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

import static meghanada.utils.FunctionUtils.wrapIOConsumer;

public class TreeAnalyzer {

    private static final Logger log = LogManager.getLogger(TreeAnalyzer.class);

    private final Map<String, String> standardClasses;

    public TreeAnalyzer() {
        this.standardClasses = CachedASMReflector.getInstance().getStandardClasses();
    }

    private Source analyzeCompilationUnitTree(final CompilationUnitTree cut, final Source src) {
        final ExpressionTree packageExpr = cut.getPackageName();
        log.trace("file={}", src.getFile());
        if (packageExpr == null) {
            src.packageName = "";
        } else {
            src.packageName = packageExpr.toString();
        }
        final EndPosTable endPositions = ((JCTree.JCCompilationUnit) cut).endPositions;
        cut.getImports().forEach(imp -> {

            final String importClass = imp.getQualifiedIdentifier().toString();
            final String simpleName = ClassNameUtils.getSimpleName(importClass);
            if (simpleName.equals("*")) {
                // wild
                final CachedASMReflector reflector = CachedASMReflector.getInstance();
                Map<String, String> symbols = reflector.getPackageClasses(importClass);
                for (final String entry : symbols.values()) {
                    src.addImport(entry);
                }
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
        });

        cut.getTypeDecls().forEach(wrapIOConsumer(td -> {
            if (td instanceof JCTree.JCClassDecl) {
                final JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl) td;
                final int startPos = classDecl.getPreferredPosition();
                final int endPos = classDecl.getEndPosition(endPositions);

                final JCTree.JCExpression extendsClause = classDecl.getExtendsClause();
                if (extendsClause != null) {
                    this.analyzeParsedTree(extendsClause, src, endPositions);
                }
                final Name simpleName = classDecl.getSimpleName();
                final Range range = Range.create(src, startPos + 1, endPos);

                final int nameStart = startPos + 6;
                final Range nameRange = Range.create(src, nameStart, nameStart + simpleName.length());

                final String fqcn = src.packageName + "." + simpleName.toString();
                final ClassScope classScope = new ClassScope(fqcn, nameRange, startPos, range);
                log.trace("class={}", classScope);

                src.startClass(classScope);
                classDecl.getMembers().forEach(wrapIOConsumer(tree -> {
                    this.analyzeParsedTree(tree, src, endPositions);
                }));
                final Optional<ClassScope> endClass = src.endClass();
                log.trace("class={}", endClass);
            } else if (td instanceof JCTree.JCSkip) {
                // skip
            } else {
                log.warn("unknown td={} {}", td, td.getClass());
            }
        }));
        return src;
    }

    private void analyzeParsedTree(final JCTree tree, final Source src, final EndPosTable endPosTable) throws IOException {
        final JCDiagnostic.DiagnosticPosition pos = tree.pos();

        final int startPos = pos.getStartPosition();
        final int preferredPos = pos.getPreferredPosition();
        final int endPos = pos.getEndPosition(endPosTable);

        final EntryMessage entryMessage = log.traceEntry("# class={} preferredPos={} endPos={} expr='{}'",
                tree.getClass().getSimpleName(),
                preferredPos,
                endPos,
                tree);

        if (endPos == -1 &&
                !(tree instanceof JCTree.JCAssign) &&
                !(tree instanceof JCTree.JCIdent)) {
            // skip
            log.trace("skip expr={}", tree);
            log.traceExit(entryMessage);
            return;
        }

        if (tree instanceof JCTree.JCVariableDecl) {

            this.analyzeVariableDecl((JCTree.JCVariableDecl) tree, src, preferredPos, endPos, endPosTable);

        } else if (tree instanceof JCTree.JCTypeCast) {

            final JCTree.JCTypeCast cast = (JCTree.JCTypeCast) tree;
            final JCTree type = cast.getType();
            final JCTree.JCExpression expression = cast.getExpression();
            this.analyzeParsedTree(expression, src, endPosTable);

        } else if (tree instanceof JCTree.JCMethodDecl) {

            this.analyzeMethodDecl((JCTree.JCMethodDecl) tree, src, preferredPos, endPos, endPosTable);

        } else if (tree instanceof JCTree.JCClassDecl) {

            this.analyzeClassDecl((JCTree.JCClassDecl) tree, src, endPosTable, startPos, endPos);

        } else if (tree instanceof JCTree.JCBlock) {
            final JCTree.JCBlock block = (JCTree.JCBlock) tree;

            block.getStatements().forEach(wrapIOConsumer(stmt -> {
                this.analyzeParsedTree(stmt, src, endPosTable);
            }));

        } else if (tree instanceof JCTree.JCFieldAccess) {

            this.analyzeFieldAccess((JCTree.JCFieldAccess) tree, src, endPosTable, preferredPos, endPos);

        } else if (tree instanceof JCTree.JCArrayAccess) {

            final JCTree.JCArrayAccess arrayAccess = (JCTree.JCArrayAccess) tree;
            final JCTree.JCExpression expression = arrayAccess.getExpression();
            if (expression != null) {
                this.analyzeParsedTree(expression, src, endPosTable);
            }
            final JCTree.JCExpression index = arrayAccess.getIndex();
            if (index != null) {
                this.analyzeParsedTree(index, src, endPosTable);
            }

        } else if (tree instanceof JCTree.JCExpressionStatement) {

            this.analyzeExpressionStatement((JCTree.JCExpressionStatement) tree, src, endPosTable, preferredPos, endPos);

        } else if (tree instanceof JCTree.JCLiteral) {

            this.analyzeLiteral((JCTree.JCLiteral) tree, src, preferredPos, endPos);

        } else if (tree instanceof JCTree.JCIdent) {

            this.parseIdent((JCTree.JCIdent) tree, src, preferredPos, endPos);

        } else if (tree instanceof JCTree.JCContinue) {
            // skip
        } else if (tree instanceof JCTree.JCBreak) {
            // skip
        } else if (tree instanceof JCTree.JCUnary) {

            final JCTree.JCUnary unary = (JCTree.JCUnary) tree;
            final JCTree.JCExpression expression = unary.getExpression();
            final Symbol operator = unary.getOperator();
            // TODO mark operator ?
            this.analyzeParsedTree(expression, src, endPosTable);

        } else if (tree instanceof JCTree.JCSwitch) {

            this.analyzeSwitch((JCTree.JCSwitch) tree, src, endPosTable);

        } else if (tree instanceof JCTree.JCReturn) {

            final JCTree.JCReturn ret = (JCTree.JCReturn) tree;
            final JCTree.JCExpression expression = ret.getExpression();
            if (expression != null) {
                this.analyzeParsedTree(ret.getExpression(), src, endPosTable);
            }
        } else if (tree instanceof JCTree.JCForLoop) {

            this.analyzeForLoop((JCTree.JCForLoop) tree, src, endPosTable);

        } else if (tree instanceof JCTree.JCEnhancedForLoop) {

            this.analyzeEnhancedForLoop((JCTree.JCEnhancedForLoop) tree, src, endPosTable);

        } else if (tree instanceof JCTree.JCTry) {

            this.analyzeTry((JCTree.JCTry) tree, src, endPosTable);

        } else if (tree instanceof JCTree.JCIf) {

            final JCTree.JCIf ifExpr = (JCTree.JCIf) tree;
            final JCTree.JCExpression condition = ifExpr.getCondition();
            final JCTree.JCStatement thenStatement = ifExpr.getThenStatement();
            final JCTree.JCStatement elseStatement = ifExpr.getElseStatement();
            this.analyzeParsedTree(condition, src, endPosTable);
            if (thenStatement != null) {
                this.analyzeParsedTree(thenStatement, src, endPosTable);
            }
            if (elseStatement != null) {
                this.analyzeParsedTree(elseStatement, src, endPosTable);
            }

        } else if (tree instanceof JCTree.JCParens) {

            final JCTree.JCParens parens = (JCTree.JCParens) tree;
            final JCTree.JCExpression expression = parens.getExpression();
            this.analyzeParsedTree(expression, src, endPosTable);

        } else if (tree instanceof JCTree.JCNewClass) {

            this.analyzeNewClass((JCTree.JCNewClass) tree, src, endPosTable, preferredPos, endPos);

        } else if (tree instanceof JCTree.JCBinary) {

            final JCTree.JCBinary binary = (JCTree.JCBinary) tree;
            final Symbol operator = binary.getOperator();
            final JCTree.JCExpression leftOperand = binary.getLeftOperand();
            final JCTree.JCExpression rightOperand = binary.getRightOperand();

            this.analyzeParsedTree(leftOperand, src, endPosTable);
            this.analyzeParsedTree(rightOperand, src, endPosTable);

        } else if (tree instanceof JCTree.JCMethodInvocation) {

            this.analyzeMethodInvocation((JCTree.JCMethodInvocation) tree, src, endPosTable, preferredPos, endPos);

        } else if (tree instanceof JCTree.JCAssign) {

            final JCTree.JCAssign assign = (JCTree.JCAssign) tree;
            final JCTree.JCExpression expression = assign.getExpression();
            final JCTree.JCExpression variable = assign.getVariable();

            if (variable != null) {
                this.analyzeParsedTree(variable, src, endPosTable);
            }
            if (expression != null) {
                this.analyzeParsedTree(expression, src, endPosTable);
            }
        } else if (tree instanceof JCTree.JCNewArray) {

            final JCTree.JCNewArray newArray = (JCTree.JCNewArray) tree;
            final JCTree.JCExpression type = newArray.getType();
            if (type != null) {
                this.analyzeParsedTree(type, src, endPosTable);
            }
            final List<JCTree.JCExpression> initializes = newArray.getInitializers();
            if (initializes != null) {
                initializes.forEach(wrapIOConsumer(jcExpression -> {
                    this.analyzeParsedTree(jcExpression, src, endPosTable);
                }));
            }
            final List<JCTree.JCExpression> dimensions = newArray.getDimensions();

            if (dimensions != null) {
                dimensions.forEach(wrapIOConsumer(jcExpression -> {
                    this.analyzeParsedTree(jcExpression, src, endPosTable);
                }));
            }

        } else if (tree instanceof JCTree.JCPrimitiveTypeTree) {
            // skip
        } else if (tree instanceof JCTree.JCConditional) {

            final JCTree.JCConditional conditional = (JCTree.JCConditional) tree;
            final JCTree.JCExpression condition = conditional.getCondition();
            if (condition != null) {
                this.analyzeParsedTree(condition, src, endPosTable);
            }
            final JCTree.JCExpression trueExpression = conditional.getTrueExpression();
            if (trueExpression != null) {
                this.analyzeParsedTree(trueExpression, src, endPosTable);
            }
            final JCTree.JCExpression falseExpression = conditional.getFalseExpression();
            if (falseExpression != null) {
                this.analyzeParsedTree(falseExpression, src, endPosTable);
            }

        } else if (tree instanceof JCTree.JCLambda) {

            final JCTree.JCLambda lambda = (JCTree.JCLambda) tree;
            final java.util.List<? extends VariableTree> parameters = lambda.getParameters();
            if (parameters != null) {
                parameters.forEach(wrapIOConsumer(v -> {
                    if (v instanceof JCTree.JCVariableDecl) {
                        this.analyzeParsedTree((JCTree.JCVariableDecl) v, src, endPosTable);
                    }
                }));
            }
            final Type lambdaType = lambda.type;
            if (lambdaType != null) {
                // TODO mark
            }

            final JCTree body = lambda.getBody();
            if (body != null) {
                this.analyzeParsedTree(body, src, endPosTable);
            }
        } else if (tree instanceof JCTree.JCThrow) {

            final JCTree.JCThrow jcThrow = (JCTree.JCThrow) tree;
            final JCTree.JCExpression expression = jcThrow.getExpression();
            this.analyzeParsedTree(expression, src, endPosTable);

        } else if (tree instanceof JCTree.JCInstanceOf) {

            final JCTree.JCInstanceOf jcInstanceOf = (JCTree.JCInstanceOf) tree;
            final JCTree.JCExpression expression = jcInstanceOf.getExpression();
            if (expression != null) {
                this.analyzeParsedTree(expression, src, endPosTable);
            }
            final JCTree type = jcInstanceOf.getType();
            if (type != null) {
                this.analyzeParsedTree(type, src, endPosTable);
            }

        } else if (tree instanceof JCTree.JCMemberReference) {

            final JCTree.JCMemberReference memberReference = (JCTree.JCMemberReference) tree;
            final JCTree.JCExpression expression = memberReference.getQualifierExpression();
            if (expression != null) {
                this.analyzeParsedTree(expression, src, endPosTable);
            }
            // TODO more ?

        } else if (tree instanceof JCTree.JCWhileLoop) {

            final JCTree.JCWhileLoop whileLoop = (JCTree.JCWhileLoop) tree;
            final JCTree.JCExpression condition = whileLoop.getCondition();
            if (condition != null) {
                this.analyzeParsedTree(condition, src, endPosTable);
            }
            final JCTree.JCStatement statement = whileLoop.getStatement();
            if (statement != null) {
                this.analyzeParsedTree(statement, src, endPosTable);
            }

        } else if (tree instanceof JCTree.JCSynchronized) {
            final JCTree.JCSynchronized jcSynchronized = (JCTree.JCSynchronized) tree;
            final JCTree.JCExpression expression = jcSynchronized.getExpression();
            if (expression != null) {
                this.analyzeParsedTree(expression, src, endPosTable);
            }
            final JCTree.JCBlock block = jcSynchronized.getBlock();
            if (block != null) {
                this.analyzeParsedTree(block, src, endPosTable);
            }
        } else if (tree instanceof JCTree.JCAssert) {

            final JCTree.JCAssert jcAssert = (JCTree.JCAssert) tree;
            final JCTree.JCExpression condition = jcAssert.getCondition();
            if (condition != null) {
                this.analyzeParsedTree(condition, src, endPosTable);
            }
            final JCTree.JCExpression detail = jcAssert.getDetail();
            if (detail != null) {
                this.analyzeParsedTree(detail, src, endPosTable);
            }
        } else if (tree instanceof JCTree.JCArrayTypeTree) {
            final JCTree.JCArrayTypeTree arrayTypeTree = (JCTree.JCArrayTypeTree) tree;
            final JCTree type = arrayTypeTree.getType();
            if (type != null) {
                this.analyzeParsedTree(type, src, endPosTable);
            }
        } else if (tree instanceof JCTree.JCDoWhileLoop) {
            final JCTree.JCDoWhileLoop doWhileLoop = (JCTree.JCDoWhileLoop) tree;
            final JCTree.JCExpression condition = doWhileLoop.getCondition();
            if (condition != null) {
                this.analyzeParsedTree(condition, src, endPosTable);
            }
            final JCTree.JCStatement statement = doWhileLoop.getStatement();
            if (statement != null) {
                this.analyzeParsedTree(statement, src, endPosTable);
            }
        } else if (tree instanceof JCTree.JCLabeledStatement) {
            final JCTree.JCLabeledStatement labeledStatement = (JCTree.JCLabeledStatement) tree;
            final JCTree.JCStatement statement = labeledStatement.getStatement();
            if (statement != null) {
                this.analyzeParsedTree(statement, src, endPosTable);
            }
        } else if (tree instanceof JCTree.JCTypeApply) {
            final JCTree.JCTypeApply typeApply = (JCTree.JCTypeApply) tree;
            final JCTree type = typeApply.getType();
            if (type != null) {
                this.analyzeParsedTree(type, src, endPosTable);
            }
        } else if (tree instanceof JCTree.JCAssignOp) {
            final JCTree.JCAssignOp assignOp = (JCTree.JCAssignOp) tree;
            final JCTree.JCExpression expression = assignOp.getExpression();
            if (expression != null) {
                this.analyzeParsedTree(expression, src, endPosTable);
            }
        } else if (tree instanceof JCTree.JCSkip) {
            // skip
        } else {
            log.warn("@@ unknown tree class={} expr={} filePath={}", tree.getClass(), tree, src.filePath);
        }

        log.traceExit(entryMessage);
    }

    private void analyzeEnhancedForLoop(JCTree.JCEnhancedForLoop forLoop, Source src, EndPosTable endPosTable) throws IOException {
        final JCTree.JCExpression expression = forLoop.getExpression();
        if (expression != null) {
            this.analyzeParsedTree(expression, src, endPosTable);
        }
        final JCTree.JCVariableDecl variable = forLoop.getVariable();
        if (variable != null) {
            this.analyzeParsedTree(variable, src, endPosTable);
        }
        final JCTree.JCStatement statement = forLoop.getStatement();
        if (statement != null) {
            this.analyzeParsedTree(statement, src, endPosTable);
        }
    }

    private void analyzeSwitch(JCTree.JCSwitch jcSwitch, Source src, EndPosTable endPosTable) throws IOException {
        final JCTree.JCExpression expression = jcSwitch.getExpression();
        this.analyzeParsedTree(expression, src, endPosTable);
        final List<JCTree.JCCase> cases = jcSwitch.getCases();
        if (cases != null) {
            cases.forEach(wrapIOConsumer(jcCase -> {
                final JCTree.JCExpression expression1 = jcCase.getExpression();
                if (expression1 != null) {
                    this.analyzeParsedTree(expression1, src, endPosTable);
                }
                final List<JCTree.JCStatement> statements = jcCase.getStatements();
                if (statements != null) {
                    statements.forEach(wrapIOConsumer(jcStatement -> {
                        this.analyzeParsedTree(jcStatement, src, endPosTable);
                    }));
                }
            }));
        }
    }

    private void analyzeTry(JCTree.JCTry tryExpr, Source src, EndPosTable endPosTable) throws IOException {
        final JCTree.JCBlock block = tryExpr.getBlock();
        final List<JCTree.JCCatch> catches = tryExpr.getCatches();
        final JCTree.JCBlock finallyBlock = tryExpr.getFinallyBlock();

        this.analyzeParsedTree(block, src, endPosTable);

        if (catches != null) {
            catches.forEach(wrapIOConsumer(jcCatch -> {
                final JCTree.JCVariableDecl parameter = jcCatch.getParameter();
                this.analyzeParsedTree(parameter, src, endPosTable);
                this.analyzeParsedTree(jcCatch.getBlock(), src, endPosTable);
            }));
        }

        if (finallyBlock != null) {
            this.analyzeParsedTree(finallyBlock, src, endPosTable);
        }
    }

    private void analyzeForLoop(final JCTree.JCForLoop forLoop, Source src, EndPosTable endPosTable) throws IOException {
        final List<JCTree.JCStatement> initializers = forLoop.getInitializer();
        final JCTree.JCExpression condition = forLoop.getCondition();
        final List<JCTree.JCExpressionStatement> updates = forLoop.getUpdate();
        final JCTree.JCStatement statement = forLoop.getStatement();
        if (initializers != null) {
            initializers.forEach(wrapIOConsumer(s -> {
                this.analyzeParsedTree(s, src, endPosTable);
            }));
        }
        if (condition != null) {
            this.analyzeParsedTree(condition, src, endPosTable);
        }
        if (updates != null) {
            updates.forEach(wrapIOConsumer(s -> {
                this.analyzeParsedTree(s, src, endPosTable);
            }));
        }
        if (statement != null) {
            this.analyzeParsedTree(statement, src, endPosTable);
        }
    }

    private void analyzeMethodInvocation(JCTree.JCMethodInvocation methodInvocation, Source src, EndPosTable endPosTable, int preferredPos, int endPos) throws IOException {
        final Type returnType = methodInvocation.type;
        methodInvocation.getArguments().forEach(wrapIOConsumer(vd -> {
            this.analyzeParsedTree(vd, src, endPosTable);
        }));

        final JCTree.JCExpression methodSelect = methodInvocation.getMethodSelect();
        int identBegin = methodSelect.getStartPosition();
        int identEnd = methodSelect.getEndPosition(endPosTable);
        final Range nameRange = Range.create(src, identBegin, identEnd);

        if (methodSelect instanceof JCTree.JCIdent) {
            // super
            final JCTree.JCIdent ident = (JCTree.JCIdent) methodSelect;
            final String s = ident.getName().toString();
            final Symbol sym = ident.sym;
            if (sym != null) {
                final Symbol owner = sym.owner;
                final Range range = Range.create(src, preferredPos + 1, endPos);

                if (s.equals("super")) {
                    // call constructor
                    final String constructor = owner.flatName().toString();
                    final MethodCall methodCall = new MethodCall(s, constructor, preferredPos + 1, nameRange, range);
                    if (owner.type != null) {
                        this.toFQCNString(owner.type).ifPresent(fqcn -> {
                            methodCall.declaringClass = this.markFQCN(src, fqcn);
                        });
                    }
                    if (sym.type != null) {
                        this.toFQCNString(sym.type).ifPresent(fqcn -> {
                            methodCall.returnType = this.markFQCN(src, fqcn);
                        });
                    }
                    src.getCurrentScope().ifPresent(scope -> {
                        scope.addMethodCall(methodCall);
                    });
                } else {
                    final MethodCall methodCall = new MethodCall(s, preferredPos + 1, nameRange, range);

                    if (owner != null && owner.type != null) {
                        this.toFQCNString(owner.type).ifPresent(fqcn -> {
                            methodCall.declaringClass = this.markFQCN(src, fqcn);
                        });

                        this.toFQCNString(returnType).ifPresent(fqcn -> {
                            methodCall.returnType = this.markFQCN(src, fqcn);
                        });
                    }

//                    if (sym.type != null) {
//                        this.toFQCNString(sym.type).ifPresent(fqcn -> {
//                            methodCall.returnType = this.markFQCN(src, fqcn);
//                        });
//                    }
                    src.getCurrentScope().ifPresent(scope -> {
                        scope.addMethodCall(methodCall);
                    });
                }
            }
        } else if (methodSelect instanceof JCTree.JCFieldAccess) {
            final JCTree.JCFieldAccess fa = (JCTree.JCFieldAccess) methodSelect;
            final JCTree.JCExpression expression = fa.getExpression();
            final String selectScope = expression.toString();
            this.analyzeParsedTree(expression, src, endPosTable);

            final Type owner = expression.type;
            final String name = fa.getIdentifier().toString();

            final Range range = Range.create(src, preferredPos + 1, endPos);
            final MethodCall methodCall = new MethodCall(selectScope, name, preferredPos + 1, nameRange, range);

            if (owner == null) {
                // call static
                if (expression instanceof JCTree.JCIdent) {
                    final JCTree.JCIdent ident = (JCTree.JCIdent) expression;
                    final String nm = ident.getName().toString();
                    final String clazz = src.importClass.get(nm);
                    if (clazz != null) {
                        methodCall.declaringClass = this.markFQCN(src, clazz);
                    } else {
                        if (src.isReportUnknown()) {
                            log.warn("unknown ident class expression={} {} {}", expression, expression.getClass(), src.filePath);
                        }
                    }
                } else {
                    if (src.isReportUnknown()) {
                        log.warn("unknown expression expression={} {}", expression, expression.getClass(), src.filePath);
                    }
                }
            } else {
                this.toFQCNString(owner).ifPresent(fqcn -> {
                    methodCall.declaringClass = this.markFQCN(src, fqcn);
                });
            }

            if (returnType == null) {
                if (expression instanceof JCTree.JCIdent) {
                    final JCTree.JCIdent ident = (JCTree.JCIdent) expression;
                    final String nm = ident.getName().toString();
                    final String clazz = src.importClass.get(nm);
                    if (clazz != null) {
                        methodCall.returnType = this.markFQCN(src, clazz);
                    } else {
                        if (src.isReportUnknown()) {
                            log.warn("unknown ident class expression={} {} {}", expression, expression.getClass(), src.filePath);
                        }
                    }
                } else {
                    if (src.isReportUnknown()) {
                        log.warn("unknown expression expression={} {}", expression, expression.getClass(), src.filePath);
                    }
                }
            } else {
                this.toFQCNString(returnType).ifPresent(fqcn -> {
                    methodCall.returnType = this.markFQCN(src, fqcn);
                });
            }
            src.getCurrentScope().ifPresent(scope -> {
                scope.addMethodCall(methodCall);
            });

        } else {
            log.warn("unknown methoddSelect:{}", methodSelect);
            this.analyzeParsedTree(methodSelect, src, endPosTable);
        }
    }

    private void analyzeNewClass(final JCTree.JCNewClass newClass, Source src, EndPosTable endPosTable, int preferredPos, int endPos) throws IOException {
        newClass.getArguments().forEach(wrapIOConsumer(jcExpression -> {
            this.analyzeParsedTree(jcExpression, src, endPosTable);
        }));
        final JCTree.JCExpression identifier = newClass.getIdentifier();
        final String name = identifier.toString();

        final int start = identifier.getStartPosition();
        final int end = identifier.getEndPosition(endPosTable);
        final Range nameRange = Range.create(src, start, end);

        final Range range = Range.create(src, preferredPos + 4, endPos);
        final MethodCall methodCall = new MethodCall(name, preferredPos, nameRange, range);

        final Type type = identifier.type;
        this.toFQCNString(type).ifPresent(fqcn -> {
            methodCall.declaringClass = fqcn;
            methodCall.returnType = fqcn;
        });
        final JCTree.JCClassDecl classBody = newClass.getClassBody();
        if (classBody != null) {

        }
        src.getCurrentScope().ifPresent(scope -> {
            scope.addMethodCall(methodCall);
        });
    }

    private void analyzeLiteral(final JCTree.JCLiteral literal, final Source src, final int preferredPos, final int endPos) throws IOException {
        final Tree.Kind kind = literal.getKind();
        final Object value = literal.getValue();
        final Range range = Range.create(src, preferredPos, endPos);
        final Variable variable = new Variable(kind.toString(), preferredPos, range);
        if (value != null) {
            variable.fqcn = value.getClass().toString();
        }
        src.getCurrentScope().ifPresent(scope -> scope.addVariable(variable));
    }

    private void analyzeExpressionStatement(final JCTree.JCExpressionStatement expr, final Source src, final EndPosTable endPosTable, final int preferredPos, final int endPos) {

        final JCTree.JCExpression expression = expr.getExpression();
        final Tree.Kind expressionKind = expression.getKind();
        final JCTree expressionTree = expression.getTree();

        src.getCurrentBlock().ifPresent(wrapIOConsumer(blockScope -> {

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
                    this.analyzeParsedTree(lhs, src, endPosTable);
                }
                if (rhs != null) {
                    this.analyzeParsedTree(rhs, src, endPosTable);
                }
            } else if (expressionKind.equals(Tree.Kind.METHOD_INVOCATION)) {
                final JCTree.JCMethodInvocation methodInvocation = (JCTree.JCMethodInvocation) expressionTree;
                this.analyzeParsedTree(methodInvocation, src, endPosTable);
            } else if (expressionKind.equals(Tree.Kind.POSTFIX_DECREMENT) ||
                    expressionKind.equals(Tree.Kind.POSTFIX_INCREMENT)) {
                if (expressionTree instanceof JCTree.JCUnary) {
                    final JCTree.JCUnary jcUnary = (JCTree.JCUnary) expressionTree;
                    final JCTree.JCExpression args = jcUnary.getExpression();
                    if (args != null) {
                        this.analyzeParsedTree(args, src, endPosTable);
                    }
                } else {
                    log.warn("POSTFIX_XXXXX expressionKind:{} tree:{}", expressionKind, expressionTree.getClass());
                }

            } else if (expressionKind.equals(Tree.Kind.PREFIX_DECREMENT) ||
                    expressionKind.equals(Tree.Kind.PREFIX_INCREMENT)) {
                if (expressionTree instanceof JCTree.JCUnary) {
                    final JCTree.JCUnary jcUnary = (JCTree.JCUnary) expressionTree;
                    final JCTree.JCExpression args = jcUnary.getExpression();
                    if (args != null) {
                        this.analyzeParsedTree(args, src, endPosTable);
                    }
                } else {
                    log.warn("PREFIX_XXXXX expressionKind:{} tree:{}", expressionKind, expressionTree.getClass());
                }
            } else if (expressionKind.equals(Tree.Kind.PLUS_ASSIGNMENT) ||
                    expressionKind.equals(Tree.Kind.MINUS_ASSIGNMENT) ||
                    expressionKind.equals(Tree.Kind.AND_ASSIGNMENT) ||
                    expressionKind.equals(Tree.Kind.OR_ASSIGNMENT) ||
                    expressionKind.equals(Tree.Kind.XOR_ASSIGNMENT) ||
                    expressionKind.equals(Tree.Kind.DIVIDE_ASSIGNMENT) ||
                    expressionKind.equals(Tree.Kind.LEFT_SHIFT_ASSIGNMENT) ||
                    expressionKind.equals(Tree.Kind.RIGHT_SHIFT_ASSIGNMENT) ||
                    expressionKind.equals(Tree.Kind.UNSIGNED_RIGHT_SHIFT_ASSIGNMENT) ||
                    expressionKind.equals(Tree.Kind.REMAINDER_ASSIGNMENT) ||
                    expressionKind.equals(Tree.Kind.MULTIPLY_ASSIGNMENT)) {
                if (expressionTree instanceof JCTree.JCAssignOp) {
                    final JCTree.JCAssignOp assignOp = (JCTree.JCAssignOp) expressionTree;
                    final JCTree.JCExpression lhs = assignOp.lhs;
                    final JCTree.JCExpression rhs = assignOp.rhs;
                    if (lhs != null) {
                        this.analyzeParsedTree(lhs, src, endPosTable);
                    }
                    if (rhs != null) {
                        this.analyzeParsedTree(rhs, src, endPosTable);
                    }
                } else {
                    log.warn("XXXX_ASSIGNMENT expressionKind:{} tree:{}", expressionKind, expressionTree.getClass());
                }
            } else if (expressionKind.equals(Tree.Kind.NEW_CLASS)) {
                if (expressionTree instanceof JCTree.JCNewClass) {
                    final JCTree.JCNewClass newClass = (JCTree.JCNewClass) expressionTree;
                    this.analyzeParsedTree(newClass, src, endPosTable);
                } else {
                    log.warn("NEW_CLASS expressionKind:{} tree:{}", expressionKind, expressionTree.getClass());
                }
            } else if (expressionKind.equals(Tree.Kind.ERRONEOUS)) {
                if (expressionTree instanceof JCTree.JCErroneous) {
                    final JCTree.JCErroneous erroneous = (JCTree.JCErroneous) expressionTree;
                    final List<? extends JCTree> errorTrees = erroneous.getErrorTrees();
                    if (errorTrees != null) {
                        errorTrees.forEach(wrapIOConsumer(tree -> {
                            this.analyzeParsedTree(tree, src, endPosTable);
                        }));
                    }
                }
            } else {
                log.warn("expressionKind:{} tree:{}", expressionKind, expressionTree.getClass());

            }

            blockScope.endExpression();

        }));
    }

    private void analyzeFieldAccess(final JCTree.JCFieldAccess fieldAccess, final Source src, final EndPosTable endPosTable, final int preferredPos, final int endPos) throws IOException {

        final Symbol sym = fieldAccess.sym;
        final JCTree.JCExpression selected = fieldAccess.getExpression();
        this.analyzeParsedTree(selected, src, endPosTable);

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
            fa.scope = selectScope;
            final Symbol owner = sym.owner;

            if (owner.type != null) {
                this.toFQCNString(owner.type).ifPresent(fqcn -> {
                    fa.declaringClass = this.markFQCN(src, fqcn);
                });
            }
            if (sym.type != null) {
                this.toFQCNString(sym.type).ifPresent(fqcn -> {
                    fa.returnType = this.markFQCN(src, fqcn);
                });
            }
            src.getCurrentScope().ifPresent(scope -> {
                scope.addFieldAccess(fa);
            });

        } else if (kind.equals(ElementKind.METHOD)) {
            //

            final MethodCall methodCall = new MethodCall(selectScope, identifier.toString(), preferredPos + 1, range, range);
            final Symbol owner = sym.owner;
            if (owner != null && owner.type != null) {
                this.toFQCNString(owner.type).ifPresent(fqcn -> {
                    methodCall.declaringClass = this.markFQCN(src, fqcn);
                });
            }
            if (sym.type != null) {
                this.toFQCNString(sym.type).ifPresent(fqcn -> {
                    methodCall.returnType = this.markFQCN(src, fqcn);
                });
            }
            src.getCurrentScope().ifPresent(scope -> {
                scope.addMethodCall(methodCall);
            });

        } else if (kind.equals(ElementKind.ENUM)) {
            if (sym.type != null) {
                this.toFQCNString(sym.type).ifPresent(fqcn -> {
                    this.markFQCN(src, fqcn);
                });
            }
        } else if (kind.equals(ElementKind.ENUM_CONSTANT)) {
            final FieldAccess fa = new FieldAccess(identifier.toString(), preferredPos + 1, range);
            fa.scope = selectScope;
            fa.isEnum = true;
            final Symbol owner = sym.owner;

            if (owner.type != null) {
                this.toFQCNString(owner.type).ifPresent(fqcn -> {
                    fa.declaringClass = this.markFQCN(src, fqcn);
                });
            }
            if (sym.type != null) {
                this.toFQCNString(sym.type).ifPresent(fqcn -> {
                    fa.returnType = this.markFQCN(src, fqcn);
                });
            }
            src.getCurrentScope().ifPresent(scope -> {
                scope.addFieldAccess(fa);
            });

        } else if (kind.equals(ElementKind.PACKAGE)) {
            // skip
        } else if (kind.equals(ElementKind.CLASS)) {
            if (sym.type != null) {
                this.toFQCNString(sym.type).ifPresent(fqcn -> {
                    this.markFQCN(src, fqcn);
                });
            }
        } else if (kind.equals(ElementKind.INTERFACE)) {
            if (sym.type != null) {
                this.toFQCNString(sym.type).ifPresent(fqcn -> {
                    this.markFQCN(src, fqcn);
                });
            }
        } else {
            log.warn("other kind:{}", kind);
        }

    }

    private void analyzeClassDecl(JCTree.JCClassDecl tree, Source src, EndPosTable endPosTable, int startPos, int endPos) throws IOException {
        // innerClass
        final JCTree.JCClassDecl classDecl = tree;
        final Range range = Range.create(src, startPos, endPos);
        final Name simpleName = classDecl.getSimpleName();
        src.getCurrentClass().ifPresent(parent -> {
            final String parentName = parent.name;
            final String fqcn = parentName + ClassNameUtils.INNER_MARK + simpleName;

            final ClassScope classScope = new ClassScope(fqcn, null, startPos, range);

            log.trace("maybe inner class={}", classScope);
            parent.startClass(classScope);
            classDecl.getMembers().forEach(wrapIOConsumer(tree1 -> {
                this.analyzeParsedTree(tree1, src, endPosTable);
            }));
            parent.endClass();
        });
    }

    private void parseIdent(final JCTree.JCIdent ident, final Source src, final int preferredPos, final int endPos) throws IOException {
        final Symbol sym = ident.sym;

        final Range range = Range.create(src, preferredPos, endPos);
        if (sym != null) {
            final Type type = sym.asType();
            final Name name = ident.getName();

            final Variable variable = new Variable(name.toString(), preferredPos, range);
            this.toFQCNString(type).ifPresent(fqcn -> {
                variable.fqcn = this.markFQCN(src, fqcn);
            });
            src.getCurrentScope().ifPresent(scope -> scope.addVariable(variable));
        } else {
            String nm = ident.toString();
            final Variable variable = new Variable(nm, preferredPos, range);
            final Optional<ClassScope> currentClass = src.getCurrentClass();

            if (currentClass.isPresent()) {
                final String className = currentClass.get().name;
                if (ClassNameUtils.getSimpleName(className).equals(nm)) {
                    variable.fqcn = this.markFQCN(src, className);
                    src.getCurrentScope().ifPresent(scope -> scope.addVariable(variable));
                    return;
                }
            }

            if (src.importClass.containsKey(nm)) {
                variable.fqcn = this.markFQCN(src, src.importClass.get(nm));
                src.getCurrentScope().ifPresent(scope -> scope.addVariable(variable));
                return;
            }

            src.classScopes.forEach(classScope -> {
                final String className = currentClass.get().name;
                if (ClassNameUtils.getSimpleName(className).equals(nm)) {
                    variable.fqcn = this.markFQCN(src, className);
                    src.getCurrentScope().ifPresent(scope -> scope.addVariable(variable));
                }
            });
            this.markFQCN(src, nm);
        }
    }

    private void analyzeMethodDecl(final JCTree.JCMethodDecl md, final Source src, final int preferredPos, final int endPos, final EndPosTable endPosTable) throws IOException {
        final String name = md.getName().toString();

        // TODO length or bytes ?
        final Range nameRange = Range.create(src, preferredPos, preferredPos + name.length());
        final Range range = Range.create(src, preferredPos, endPos);

        src.getCurrentClass().ifPresent(wrapIOConsumer((ClassScope classScope) -> {
            String methodName = name;
            boolean isConstructor = false;
            String returnFQCN = "";

            if (!name.equals("<init>")) {
                // set return type
                final JCTree returnTypeExpr = md.getReturnType();
                if (returnTypeExpr != null) {
                    final Type type = returnTypeExpr.type;
                    if (type != null) {
                        final String fqcn = type.toString();
                        log.trace("method return={}", fqcn);
                        returnFQCN = fqcn;
                    } else {
                        returnFQCN = resolveTypeFromImport(src, returnTypeExpr);
                    }
                }
            } else {
                isConstructor = true;
                Symbol.MethodSymbol sym = md.sym;
                if (sym != null && sym.owner != null) {
                    final Type type = sym.owner.type;
                    Optional<String> s = toFQCNString(type);
                    methodName = s.orElse(name);
                    returnFQCN = methodName;
                }
            }

            final MethodScope methodScope = classScope.startMethod(methodName,
                    nameRange,
                    preferredPos,
                    range,
                    isConstructor);
            methodScope.returnType = this.markFQCN(src, returnFQCN);

            // check method parameter
            md.getParameters().forEach(wrapIOConsumer(vd -> {
                try {
                    src.isParameter = true;
                    this.analyzeParsedTree(vd, src, endPosTable);
                } finally {
                    src.isParameter = false;
                }
            }));


            // set exceptions
            md.getThrows().forEach(expr -> {
                final String ex = resolveTypeFromImport(src, expr);
                this.markFQCN(src, ex);
            });

            final JCTree.JCBlock body = md.getBody();
            // parse body
            if (body != null) {
                this.analyzeParsedTree(body, src, endPosTable);
            }
            classScope.endMethod();

        }));

    }

    private String resolveTypeFromImport(final Source src, final JCTree tree) {

        if (tree instanceof JCTree.JCTypeApply) {
            final JCTree.JCTypeApply typeApply = (JCTree.JCTypeApply) tree;
            String methodReturn = typeApply.getType().toString();
            methodReturn = src.importClass.getOrDefault(methodReturn, methodReturn);

            final List<JCTree.JCExpression> typeArguments = typeApply.getTypeArguments();
            if (typeArguments != null) {
                final java.util.List<String> temp = new ArrayList<>();
                for (final JCTree.JCExpression e : typeArguments) {
                    String typeArgType = e.toString();
                    typeArgType = src.importClass.getOrDefault(typeArgType, typeArgType);
                    temp.add(typeArgType);
                }
                final String join = Joiner.on(",").join(temp);
                if (!join.isEmpty()) {
                    methodReturn = methodReturn + "<" + join + ">";
                }
            }
            return methodReturn;
        } else if (tree instanceof JCTree.JCFieldAccess) {
            return tree.toString();
        } else if (tree instanceof JCTree.JCIdent) {
            final String ident = tree.toString();
            return src.importClass.getOrDefault(ident, ident);
        } else if (tree instanceof JCTree.JCPrimitiveTypeTree) {
            return tree.toString();
        } else if (tree instanceof JCTree.JCArrayTypeTree) {
            final JCTree.JCArrayTypeTree arrayTypeTree = (JCTree.JCArrayTypeTree) tree;
            final JCTree type = arrayTypeTree.getType();
            if (type != null) {
                final String k = type.toString();
                return src.importClass.getOrDefault(k, k) + "[]";
            }
        } else {
            log.warn("tree={} {}", tree, tree.getClass());
        }
        return tree.toString();
    }

    private void analyzeVariableDecl(final JCTree.JCVariableDecl vd, final Source src, final int preferredPos, final int endPos, final EndPosTable endPosTable) {

        final Name name = vd.getName();
        final JCTree.JCExpression initializer = vd.getInitializer();
        final JCTree.JCExpression nameExpression = vd.getNameExpression();
        final JCTree typeTree = vd.getType();
        final JCTree.JCModifiers modifiers = vd.getModifiers();

        if (modifiers != null) {
            final List<JCTree.JCAnnotation> annotations = modifiers.getAnnotations();
            if (annotations != null) {
                annotations.forEach(wrapIOConsumer(jcAnnotation -> {
                    final JCTree annotationType = jcAnnotation.getAnnotationType();
                    this.analyzeParsedTree(annotationType, src, endPosTable);
                    final List<JCTree.JCExpression> arguments = jcAnnotation.getArguments();
                    if (arguments != null) {
                        arguments.forEach(wrapIOConsumer(jcExpression -> {
                            this.analyzeParsedTree(jcExpression, src, endPosTable);
                        }));
                    }
                }));
            }
        }

        if (initializer != null || nameExpression != null) {
            log.trace("init={} name={} tree={}", initializer, nameExpression, typeTree);
        }

        src.getCurrentBlock().ifPresent(wrapIOConsumer(blockScope -> {

            final Range range = Range.create(src, preferredPos, endPos);

            final ExpressionScope expressionScope = new ExpressionScope(preferredPos, range);
            if (blockScope instanceof ClassScope) {
                expressionScope.isField = true;
            }
            blockScope.startExpression(expressionScope);

            if (typeTree instanceof JCTree.JCTypeUnion) {
                final JCTree.JCTypeUnion union = (JCTree.JCTypeUnion) typeTree;
                for (final JCTree.JCExpression expression : union.getTypeAlternatives()) {
                    String type = expression.toString();
                    final Variable variable = new Variable(name.toString(), preferredPos, range);
                    type = src.importClass.getOrDefault(type, type);
                    variable.fqcn = this.markFQCN(src, type);
                    src.getCurrentScope().ifPresent(scope -> scope.addVariable(variable));
                }
            } else {
                try {
                    final Variable variable = new Variable(name.toString(), preferredPos, range);

                    if (vd.getTag().equals(JCTree.Tag.VARDEF)) {
                        variable.def = true;
                    }
                    if (src.isParameter) {
                        variable.parameter = true;
                    }

                    if (typeTree instanceof JCTree.JCExpression) {
                        final JCTree.JCExpression expression = (JCTree.JCExpression) typeTree;
                        final Type type = expression.type;

                        if (type == null && expression instanceof JCTree.JCIdent) {
                            final JCTree.JCIdent ident = (JCTree.JCIdent) expression;
                            final String nm = ident.getName().toString();
                            final String identClazz = src.importClass.get(nm);
                            if (identClazz != null) {
                                variable.fqcn = this.markFQCN(src, identClazz);
                            } else {
                                if (src.isReportUnknown()) {
                                    log.warn("unknown ident class expression={} {} {}", expression, expression.getClass(), src.filePath);
                                }
                            }
                        } else {
                            final String fqcn = resolveTypeFromImport(src, expression);
                            variable.fqcn = this.markFQCN(src, fqcn);
                        }
                    } else {
                        if (src.isReportUnknown()) {
                            log.warn("unknown typeTree class expression={} {}", typeTree, src.filePath);
                        }
                    }

                    src.getCurrentScope().ifPresent(scope -> scope.addVariable(variable));

                    if (initializer != null) {
                        this.analyzeParsedTree(initializer, src, endPosTable);
                    }
                } finally {
                    blockScope.endExpression();
                }
            }
        }));
    }


    public Map<File, Source> analyze(final Iterable<? extends CompilationUnitTree> parsed, final Set<File> errorFiles) {
        final Map<File, Source> analyzedMap = new ConcurrentHashMap<>();

        if (log.isDebugEnabled()) {
            parsed.forEach(wrapIOConsumer(cut -> {
                this.analyzeUnit(analyzedMap, cut, errorFiles);
            }));
        } else {
            StreamSupport.stream(parsed.spliterator(), true).forEach(wrapIOConsumer(cut -> {
                this.analyzeUnit(analyzedMap, cut, errorFiles);
            }));
        }

        return analyzedMap;
    }

    private void analyzeUnit(final Map<File, Source> analyzedMap, final CompilationUnitTree cut, final Set<File> errorFiles) throws IOException {
        final URI uri = cut.getSourceFile().toUri();
        final File file = new File(uri.normalize());
        final String path = file.getCanonicalPath();
        final Source source = new Source(path);

        source.importClass.putAll(this.standardClasses);
        if (errorFiles.contains(file)) {
            source.hasCompileError = true;
        }
        final EntryMessage entryMessage = log.traceEntry("---------- analyze file:{} ----------", file);
        this.analyzeCompilationUnitTree(cut, source);
        log.traceExit(entryMessage);
        analyzedMap.put(file, source);
    }

    private Optional<String> toFQCNString(final Type type) {

        if (type != null) {

            if (type instanceof Type.ClassType) {
                String fqcn = type.toString();
                final Symbol.TypeSymbol typeSymbol = type.asElement();
                final String flatName = typeSymbol.flatName().toString();

                if (fqcn.contains("capture") && fqcn.contains("?")) {
                    fqcn = flatName + "<?>";
                }

                if (flatName.contains("$")) {
                    // has inner
                    String[] ss = StringUtils.split(flatName, "$");
                    for (final String s : ss) {
                        fqcn = ClassNameUtils.replace(fqcn, "." + s, "$" + s);
                    }
                }

                return Optional.of(fqcn);
            } else if (type instanceof Type.MethodType) {
                final Type.MethodType methodType = (Type.MethodType) type;
                String fqcn = methodType.getReturnType().toString();
                final Symbol.TypeSymbol typeSymbol = type.asElement();
                if (typeSymbol != null) {
                    final String flatName = typeSymbol.flatName().toString();
                    if (flatName.contains("$")) {
                        // has inner
                        String[] ss = StringUtils.split(flatName, "$");
                        for (final String s : ss) {
                            fqcn = ClassNameUtils.replace(fqcn, "." + s, "$" + s);
                        }
                    }
                }
                return Optional.of(fqcn);

            } else if (type instanceof Type.JCPrimitiveType) {
                String fqcn = type.toString();
                // box ???
                return Optional.of(fqcn);
            } else if (type instanceof Type.ArrayType) {
                final String fqcn = type.toString();
                return Optional.of(fqcn);
            } else if (type instanceof Type.TypeVar) {
                final Type.TypeVar tv = (Type.TypeVar) type;
                final Type bound = tv.getUpperBound();
                final String fqcn = bound.toString();
                return Optional.of(fqcn);
            } else if (type instanceof Type.ForAll) {
                final Type.ForAll fa = (Type.ForAll) type;
                final Type methodType = fa.asMethodType();
                final String fqcn = methodType.getReturnType().toString();
                return Optional.of(fqcn);
            } else if (type instanceof Type.JCVoidType) {
                return Optional.of("void");
            } else if (type instanceof Type.PackageType) {
                final Type.PackageType pt = (Type.PackageType) type;
                final String pa = pt.toString();
                return Optional.of(pa);
            } else {
                log.warn("type is ??? type:{}", type.getClass());
            }
        }
        return Optional.empty();
    }

    private String markFQCN(final Source src, final String fqcn) {
        if (fqcn.equals("void") ||
                fqcn.startsWith("capture of") ||
                fqcn.equals("any") ||
                fqcn.equals("<any>")) {
            return fqcn;
        }

        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        final String simpleName = ClassNameUtils.removeTypeAndArray(fqcn);

        ClassNameUtils.parseTypeParameter(fqcn).forEach(s -> {
            markFQCN(src, s);
        });

        if (!src.importClass.containsValue(simpleName) && !reflector.getGlobalClassIndex().containsKey(simpleName)) {
            // log.debug("add unknown={} fqcn={}", simpleName, fqcn);
            src.unknown.add(simpleName);
        } else {
            // contains
            if (src.unused.contains(simpleName)) {
                src.unused.remove(simpleName);
            }
        }
        return fqcn;
    }

}
