package meghanada.parser;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclaratorId;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.google.common.base.Strings;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.MethodDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.leacox.motif.MatchesExact.eq;
import static com.leacox.motif.Motif.match;

class TypeAnalyzer {

    private static Logger log = LogManager.getLogger(TypeAnalyzer.class);

    private final JavaSymbolAnalyzeVisitor visitor;
    private final FQCNResolver fqcnResolver;
    private final List<AnalyzeReturnTypeFunction> returnTypeFunctions;

    TypeAnalyzer(final JavaSymbolAnalyzeVisitor visitor) {
        this.visitor = visitor;
        this.fqcnResolver = FQCNResolver.getInstance();
        this.returnTypeFunctions = this.getReturnTypeAnalyzeFunctions();
    }

    Optional<String> getMethodSignature(final JavaSource src, final BlockScope bs, final String methodName, final List<Expression> args) {
        final List<String> argTypes = args.stream()
                .map(expr -> {
                    final Optional<String> result = this.analyzeExprClass(expr, bs, src);
                    if (result.isPresent()) {
                        return result.get();
                    }
                    return null;
                })
                .filter(s -> s != null)
                .collect(Collectors.toList());
        if (args.size() != argTypes.size()) {
            return Optional.empty();
        }
        return Optional.of(methodName + "::" + argTypes.toString());
    }

    private boolean isFunctionalInterface(final String className) {
        if (className == null) {
            return false;
        }
        CachedASMReflector reflector = CachedASMReflector.getInstance();
        final String fqcn = ClassNameUtils.removeTypeParameter(className);

        return reflector.containsClassIndex(fqcn)
                .map(classIndex -> classIndex.functional || classIndex.isInterface && reflector.reflect(fqcn).size() == 1)
                .orElse(false);
    }

    Optional<List<MemberDescriptor>> inferenceConstructors(final JavaSource source, final String createClass, final int size, final String sig) {
        return source.getCurrentType().map(ts -> {
            final CachedASMReflector reflector = CachedASMReflector.getInstance();
            return reflector.reflectConstructorStream(createClass, size, sig)
                    .collect(Collectors.toList());
        });
    }

    Optional<MemberDescriptor> getConstructor(final JavaSource source, final String createClass, final int size, final String sig) {
        return source.getCurrentType().flatMap(ts -> {
            final CachedASMReflector reflector = CachedASMReflector.getInstance();
            return reflector.reflectConstructorStream(createClass, size, sig)
                    .findFirst();
        });
    }

    Optional<List<MemberDescriptor>> getCallingMethods(final JavaSource source, final String declaringClass, final String name, final int size, final String sig) {
        return source.getCurrentType().map(ts -> {
            final CachedASMReflector reflector = CachedASMReflector.getInstance();
            return reflector.reflectMethodStream(declaringClass, name, size, sig)
                    .collect(Collectors.toList());
        });
    }

    Optional<MemberDescriptor> getCallingMethod(final JavaSource source, final String declaringClass, final String name, final int size, final String sig) {
        final Optional<MemberDescriptor> memberDescriptor = source.getCurrentType().map(ts -> {
            final CachedASMReflector reflector = CachedASMReflector.getInstance();
            return reflector.reflectMethodStream(declaringClass, name, size, sig)
                    .findFirst()
                    .orElseGet(() -> {
                        // get from static import
                        final Map<String, String> staticImp = source.staticImp;
                        if (staticImp.containsKey(name)) {
                            final String dec = staticImp.get(name);
                            return reflector.reflectMethodStream(dec, name, size)
                                    .findFirst()
                                    .orElse(null);
                        }
                        return null;
                    });
        });
        return memberDescriptor;
    }

    Optional<String> inferenceReturnType(final String methodName, final String declaringClass, final List<Expression> args, final BlockScope blockScope, final JavaSource source) {
        final CachedASMReflector reflector = CachedASMReflector.getInstance();

        final String sig = this.getMethodSignature(source, blockScope, methodName, args).orElse("");

        final Optional<MemberDescriptor> methodInfo = reflector.reflectMethodStream(declaringClass, methodName, args.size(), sig)
                .findFirst();

        return methodInfo.map(md -> {
            if (md.hasTypeParameters()) {

                // 1st stage
                return this.inferenceReturnFromHint(source, md)
                        .orElseGet(() -> {
                            // 2nd stage
                            final String resolveClass = this.getGenericsReturnType(args, blockScope, source, md);
                            if (resolveClass != null) {
                                log.trace("return resolveC;ass:{}", resolveClass);
                                return resolveClass;
                            }
                            return null;
                        });
            }
            return null;
        });
    }

    private String getGenericsReturnType(List<Expression> args, BlockScope blockScope, JavaSource source, MemberDescriptor md) {
        MethodDescriptor method = (MethodDescriptor) md;

        if (method.formalType == null) {
            return null;
        }

        final String returnType = md.returnType();
        final List<String> params = md.getParameters();

        for (int i = 0; i < params.size(); i++) {
            final String p = params.get(i);

            // TODO refactoring
            log.trace("AnalyzeReturnType p:{} returnType:{}", p, returnType);
            final String rt = ClassNameUtils.replace(returnType, ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK, "");
            //  A appendTo(A, OtherClass);
            //
            if (!p.contains(".") && rt.equals(p)) {
                Expression expression = args.get(i);
                log.trace("AnalyzeReturnTypeMATCH P:{} ReturnType:{}", p, returnType);

                final Optional<String> resolveClass = this.analyzeExprClass(expression, blockScope, source);

                if (resolveClass.isPresent()) {
                    return resolveClass.get();
                }
            }
        }
        return null;
    }

    private String getLambdaReturnType(JavaSource source, MemberDescriptor callMethod) {
        return source.typeHint.getLambdaReturnType();
    }

    private Optional<String> inferenceReturnFromHint(JavaSource source, MemberDescriptor callMethod) {
        final String lambdaReturnType = this.getLambdaReturnType(source, callMethod);
        final MemberDescriptor lambdaMethod = source.typeHint.getLambdaMethod();


        if (lambdaReturnType != null && lambdaMethod != null && !lambdaMethod.fixedReturnType()) {

            // <R> R apply(capture of ? super String t)
            final String returnTypeParameter = lambdaMethod.getReturnTypeKey();
            lambdaMethod.putTypeParameter(returnTypeParameter, lambdaReturnType);
            replaceCallMethodType(callMethod, returnTypeParameter, lambdaReturnType);

            return fqcnResolver.resolveFQCN(callMethod.getRawReturnType(), source);
        } else if (lambdaMethod != null && lambdaMethod.fixedReturnType()) {

            return fqcnResolver.resolveFQCN(callMethod.getRawReturnType(), source);
        }
        return Optional.empty();
    }

    private void replaceCallMethodType(MemberDescriptor callMethod, String replaceTypeKey, String returnType) {
        callMethod.putTypeParameter(replaceTypeKey, returnType);
    }

    private String replaceDeclaringClass(String lambdaReturnType, MemberDescriptor lambdaMethod, String returnTypeParameter) {
        final String declaringClass = lambdaMethod.getDeclaringClass();
        final String newDeclaringClass = declaringClass.replace("extends " + returnTypeParameter, "extends " + lambdaReturnType);
        lambdaMethod.setDeclaringClass(newDeclaringClass);
        return newDeclaringClass;
    }

    private void narrowingHint(final JavaSource source, final String exprClass) {
        if (source.typeHint.hasHintDescriptors()) {
            final TypeHint typeHint = source.typeHint;
            if (!source.typeHint.maybeResolved()) {
                final CachedASMReflector reflector = CachedASMReflector.getInstance();
                final Map<String, ClassIndex> globalClassIndex = reflector.getGlobalClassIndex();
                final Iterator<MemberDescriptor> it = typeHint.hintDescriptors.iterator();
                while (it.hasNext()) {
                    final MemberDescriptor md = it.next();
                    final List<String> parameters = md.getParameters();

                    if (parameters.size() > typeHint.narrowingHintIndex) {
                        final String param = md.getParameters().get(typeHint.narrowingHintIndex);
                        final ClassIndex paramInfo = globalClassIndex.get(param);
                        if (paramInfo != null && exprClass != null) {
                            final ClassIndex actual = globalClassIndex.get(ClassNameUtils.removeTypeParameter(exprClass));
                            if (actual == null || !actual.isImplements(param)) {
                                it.remove();
                            }
                        }
                    }
                }
                typeHint.narrowingHintIndex++;
            }
            typeHint.parameterHintIndex++;
        }
    }

    Optional<String> analyzeExprClass(final Expression expression, final BlockScope blockScope, final JavaSource source) {
        final EntryMessage entryMessage = log.traceEntry("expr={} range={}", expression.getClass(), expression.getRange());
        final Class scopeExprClass = expression.getClass();
        final Optional<String> resolved = match(scopeExprClass)
                .when(eq(IntegerLiteralExpr.class)).get(() -> Optional.of("java.lang.Integer"))
                .when(eq(BooleanLiteralExpr.class)).get(() -> Optional.of("java.lang.Boolean"))
                .when(eq(LongLiteralExpr.class)).get(() -> Optional.of("java.lang.Long"))
                .when(eq(CharLiteralExpr.class)).get(() -> Optional.of("java.lang.Character"))
                .when(eq(ClassExpr.class)).get(() -> Optional.of("java.lang.Class"))
                .when(eq(BinaryExpr.class)).get(() -> {
                    BinaryExpr x = (BinaryExpr) expression;
                    final BinaryExpr.Operator op = x.getOperator();
                    if (op == BinaryExpr.Operator.and ||
                            op == BinaryExpr.Operator.or ||
                            op == BinaryExpr.Operator.equals ||
                            op == BinaryExpr.Operator.notEquals ||
                            op == BinaryExpr.Operator.less ||
                            op == BinaryExpr.Operator.greater ||
                            op == BinaryExpr.Operator.lessEquals ||
                            op == BinaryExpr.Operator.greaterEquals) {
                        return Optional.of("java.lang.Boolean");
                    }

                    final Optional<String> left = this.analyzeExprClass(x.getLeft(), blockScope, source);
                    final Optional<String> right = this.analyzeExprClass(x.getRight(), blockScope, source);
                    return Optional.ofNullable(left.orElse(right.orElse(null)));
                })
                .when(eq(ConditionalExpr.class)).get(() -> {
                    final ConditionalExpr x = (ConditionalExpr) expression;
                    // eval
                    final Optional<String> condOp = this.analyzeExprClass(x.getCondition(), blockScope, source);
                    final Optional<String> thenOp = this.analyzeExprClass(x.getThenExpr(), blockScope, source);
                    final Optional<String> elseOp = this.analyzeExprClass(x.getElseExpr(), blockScope, source);
                    return Optional.ofNullable(thenOp.orElse(elseOp.orElse(null)));
                })
                .when(eq(UnaryExpr.class)).get(() -> {
                    UnaryExpr x = (UnaryExpr) expression;
                    return this.analyzeExprClass(x.getExpr(), blockScope, source);
                })
                .when(eq(AssignExpr.class)).get(() -> {
                    AssignExpr x = (AssignExpr) expression;
                    final Optional<String> targetOp = this.analyzeExprClass(x.getTarget(), blockScope, source);
                    final Optional<String> valOp = this.analyzeExprClass(x.getValue(), blockScope, source);
                    return Optional.ofNullable(targetOp.orElse(valOp.orElse(null)));
                })
                .when(eq(InstanceOfExpr.class)).get(() -> {
                    InstanceOfExpr x = (InstanceOfExpr) expression;
                    // eval
                    final Optional<String> condOp = this.analyzeExprClass(x.getExpr(), blockScope, source);
                    return Optional.of("java.lang.Boolean");
                })
                .when(eq(NameExpr.class)).get(() -> {
                    NameExpr x = (NameExpr) expression;
                    final Optional<String> result = this.fqcnResolver.resolveFQCN(x.getName(), source);
                    result.ifPresent(fqcn -> {
                        if (!blockScope.containsSymbol(x.getName())) {
                            final String parent = blockScope.getName();

                            final Variable symbol = new Variable(parent,
                                    x.getName(),
                                    x.getRange(),
                                    fqcn);
                            blockScope.addNameSymbol(symbol);
                        }
                    });
                    return result;
                })
                .when(eq(FieldAccessExpr.class)).get(() -> {
                    FieldAccessExpr x = (FieldAccessExpr) expression;
                    if (CachedASMReflector.getInstance().containsFQCN(x.toStringWithoutComments())) {
                        return Optional.of(x.toStringWithoutComments());
                    }
                    return this.visitor.fieldAccess(x, source, blockScope).map(AccessSymbol::getReturnType);
                })
                .when(eq(MethodCallExpr.class)).get(() -> {
                    MethodCallExpr x = (MethodCallExpr) expression;
                    return this.visitor.methodCall(x, source, blockScope).map(AccessSymbol::getReturnType);
                })
                .when(eq(ThisExpr.class)).get(() -> source.getCurrentType().map(TypeScope::getFQCN))
                .when(eq(SuperExpr.class)).get(() -> source.getCurrentType().flatMap(typeScope -> {
                    if (typeScope instanceof ClassScope) {
                        ClassScope classScope = (ClassScope) typeScope;
                        return classScope.getExtendsClasses()
                                .stream()
                                .findFirst();
                    }
                    return Optional.of(typeScope.getFQCN());
                }))
                .when(eq(ObjectCreationExpr.class)).get(() -> {
                    final ObjectCreationExpr x = (ObjectCreationExpr) expression;
                    final String constructor = x.getType().toString();
                    return this.fqcnResolver.resolveFQCN(constructor, source);
                })
                .when(eq(DoubleLiteralExpr.class)).get(() -> Optional.of("java.lang.Double"))
                .when(eq(StringLiteralExpr.class)).get(() -> Optional.of("java.lang.String"))
                .when(eq(EnclosedExpr.class)).get(() -> {
                    final EnclosedExpr x = (EnclosedExpr) expression;
                    return this.analyzeExprClass(x.getInner(), blockScope, source);
                })
                .when(eq(CastExpr.class)).get(() -> {
                    final CastExpr x = (CastExpr) expression;
                    return this.fqcnResolver.resolveFQCN(x.getType().toString(), source);
                })
                .when(eq(ArrayAccessExpr.class)).get(() -> {
                    final ArrayAccessExpr x = (ArrayAccessExpr) expression;
                    return this.analyzeExprClass(x.getName(), blockScope, source);
                })
                .when(eq(ArrayCreationExpr.class)).get(() -> {
                    ArrayCreationExpr x = (ArrayCreationExpr) expression;
                    return this.fqcnResolver.resolveFQCN(x.getType().toString(), source);
                })
                .when(eq(TypeExpr.class)).get(() -> {
                    TypeExpr x = (TypeExpr) expression;
                    return this.fqcnResolver.resolveFQCN(x.getType().toString(), source);
                })
                .when(eq(NullLiteralExpr.class)).get(Optional::empty)
                .when(eq(MethodReferenceExpr.class)).get(() -> {
                    // TODO
                    MethodReferenceExpr x = (MethodReferenceExpr) expression;
                    final String methodName = x.getIdentifier();
                    final Expression scope = x.getScope();
                    final Optional<String> scopeFqcn = this.analyzeExprClass(scope, blockScope, source);
                    log.trace("MethodReferenceExpr methodName:{} scope:{} type:{}", methodName, scope, x.getTypeArguments());

                    if (x.getParentNode() instanceof MethodCallExpr && scopeFqcn.isPresent()) {
                        final Optional<String> ref = analyzeLambdaMethodRef(source, methodName, scopeFqcn.get());
                        log.trace("MethodReferenceExpr methodRef:{}", ref);
                        if (ref.isPresent()) {
                            // fix returnType
                            ref.ifPresent(fqcn -> source.typeHint.addLambdaReturnType(fqcn));
                            return ref;
                        }
                    }

                    return scopeFqcn.flatMap(fqcn -> {
                        final CachedASMReflector reflector = CachedASMReflector.getInstance();
                        final Optional<String> result = reflector.reflectStream(fqcn)
                                .filter(m -> m.getName().equals(methodName) && m.getParameters().size() <= 1)
                                .map(MemberDescriptor::getRawReturnType)
                                .filter(s -> s != null)
                                .findFirst();

                        result.ifPresent(returnType -> source.typeHint.addLambdaReturnType(ClassNameUtils.boxing(returnType)));
                        log.trace("MethodReference Return:{}", result);
                        return result;
                    });

                })
                .when(eq(LambdaExpr.class)).get(() -> {
                    final LambdaExpr x = (LambdaExpr) expression;
                    final List<Parameter> parameters = x.getParameters();

                    parameters.forEach(parameter -> {
                        // TODO get lambda method
                    });

                    final Statement body = x.getBody();
                    if (body instanceof BlockStmt) {
                        this.visitor.visit((BlockStmt) body, source);
                        // TODO return type
                        return Optional.empty();
                    }
                    if (body instanceof ExpressionStmt) {
                        this.visitor.visit((ExpressionStmt) body, source);
                        // TODO return type
                        return Optional.empty();
                    }
                    log.warn("MISS LambdaExpr bodyClass:{} body:{}", body.getClass(), body);
                    return Optional.empty();
                })
                .orElse(x -> {
                    log.warn("UnsupportedExpr {}, {} Source:{} Range:{}", x, expression, source.getFile(), expression.getRange());
                    return Optional.empty();
                })
                .getMatch();

        return log.traceExit(entryMessage, resolved);
    }

    private Optional<String> analyzeLambdaMethodRef(JavaSource source, String methodName, String fqcn) {
        final CachedASMReflector reflector = CachedASMReflector.getInstance();

        final List<MemberDescriptor> refs = reflector.reflectMethodStream(fqcn, methodName)
                .collect(Collectors.toList());

        // TODO lambda paramIndex
        final Optional<List<MemberDescriptor>> lambdaMethodResults = getSimpleAnalyzeLambdaMethod(source);

        if (lambdaMethodResults.isPresent()) {
            final List<MemberDescriptor> lambdaDescriptors = lambdaMethodResults.get();

            for (MemberDescriptor lmd : lambdaDescriptors) {
                final List<String> lmdParameters = lmd.getParameters();
                final int lSize = lmdParameters.size();
                for (MemberDescriptor methodRef : refs) {
                    if (lSize > 0) {
                        final String paramFirst = ClassNameUtils.removeCapture(lmdParameters.get(0));
                        if (methodRef.isStatic()) {
                            // TODO
                            final int mSize = methodRef.getParameters().size();
                            if (mSize == 0) {
                                // flatMap(s -> AClass.get()) == flatMap(AClass::get)
                                return Optional.of(methodRef.getRawReturnType());
                            } else if (mSize == 1) {
                                // TODO
                                // flatMap(s -> AClass.get(s)) == flatMap(AClass::get)
                                return Optional.of(methodRef.getRawReturnType());
                            } else if (mSize == lSize) {
                                // TODO
                            }
                        } else {
                            // 1st apply
                            if (lSize == 0) {
                                // TODO
                                // likely ?
                            } else if (lSize == 1) {
                                final String mdName = methodRef.getName();
                                log.trace("analyzeLambdaMethodRef paramFirst:{} name:{}", paramFirst, mdName);
                                return reflector.reflect(paramFirst)
                                        .stream()
                                        .filter(md1 -> md1.getName().equals(mdName))
                                        .map(MemberDescriptor::getRawReturnType)
                                        .findFirst();
                            } else {
                                // TODO
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<List<MemberDescriptor>> getSimpleAnalyzeLambdaMethod(JavaSource source) {
        TypeHint typeHint = source.typeHint;
        return typeHint.get().map(descriptor -> {
            for (String parameterType : descriptor.getParameters()) {
                log.trace("MethodParameter parameterType:{}", parameterType);
                if (this.isFunctionalInterface(parameterType)) {
                    log.trace("IsFunctionalInterface:{} Parameter:{}", descriptor.getDeclaration(), parameterType);

                    final CachedASMReflector reflector = CachedASMReflector.getInstance();

                    final List<MemberDescriptor> functions = reflector.reflectStream(parameterType)
                            .filter(md -> !md.getDeclaringClass().equals(ClassNameUtils.OBJECT_CLASS))
                            .collect(Collectors.toList());

                    if (functions.size() == 1) {
                        return functions;
                    }
                    return functions.stream()
                            .filter(md -> !md.hasDefault())
                            .collect(Collectors.toList());

                }
            }
            return null;
        });
    }

    private Optional<MemberDescriptor> getAnalyzeLambdaMethod(JavaSource source, final int argCount) {
        TypeHint typeHint = source.typeHint;
        return typeHint.get().map(descriptor -> {
            final int index = typeHint.parameterHintIndex;
            log.trace("getAnalyzeLambdaMethod parameterHintIndex:{}", index);
            final List<String> parameters = descriptor.getParameters();
            if (index < parameters.size()) {
                String parameterType = descriptor.getParameters().get(index);
                if (this.isFunctionalInterface(parameterType)) {
                    log.trace("CallMethod:{} Fn ParameterType:{}", descriptor.getDeclaration(), parameterType);

                    final CachedASMReflector reflector = CachedASMReflector.getInstance();

                    final List<MemberDescriptor> functions = reflector.reflectStream(parameterType)
                            .filter(md -> !md.getDeclaringClass().equals(ClassNameUtils.OBJECT_CLASS))
                            .collect(Collectors.toList());

                    // select one
                    if (functions.size() == 1) {
                        return typeHint.setLambdaMethod(functions.get(0));
                    }

                    return typeHint.setLambdaMethod(
                            functions.stream()
                                    .filter(md -> !md.hasDefault())
                                    .filter(md -> md.getParameters().size() == argCount)
                                    .findFirst()
                                    .orElse(null));
                }
            }
            return null;
        });
    }

    private Optional<Map<String, Variable>> getLambdaParameterSymbols(JavaSource source, MemberDescriptor lambdaMethod, LambdaExpr expr) {

        final Iterator<String> lambdaIt = lambdaMethod.getParameters().iterator();
        final List<Parameter> parameters = expr.getParameters();
        final Map<String, Variable> nsMap = new HashMap<>(8);
        if (parameters == null || parameters.size() == 0) {
            return Optional.of(nsMap);
        }

        for (Parameter p : parameters) {
            if (lambdaIt.hasNext()) {
                final String fqcn = lambdaIt.next();
                log.trace("Parameter Type:{} FQCN:{} Range:{}", p.getType(), fqcn, p.getRange());
                final String name = p.getId().getName();
                final String type = p.getType().toString();
                // unknown?
                if (type.isEmpty()) {
                    // is Lambda or Method Ref

                    String paramFQCN = ClassNameUtils.removeCapture(fqcn);
                    final String normalType = ClassNameUtils.removeTypeParameter(paramFQCN);
                    if (!normalType.contains(".")) {
                        // resolve FQCN
                        paramFQCN = fqcnResolver.resolveFQCN(paramFQCN, source).orElse(paramFQCN);
                    }

                    // this is lambda parameter
                    final Variable variable = new Variable("", name, p.getRange(), paramFQCN);
                    nsMap.put(name, variable);
                }
            }
        }
        // log.info("inferenceType:{} ns:{}", inferenceType, nsMap);

        return Optional.of(nsMap);
    }

    private Optional<Map<String, Variable>> analyzeLambdaParameterTypes(JavaSource source, LambdaExpr x) {
        final List<Parameter> parameters = x.getParameters();
        if (parameters == null || parameters.size() == 0) {
            return Optional.empty();
        }
        final int argCount = parameters.size();

        return this.getAnalyzeLambdaMethod(source, argCount).map(lambdaMethod -> {

            log.trace("AnalyzedLambdaMethod method:{}", lambdaMethod.getDeclaration());

            final Map<String, Variable> nsMap = new HashMap<>(8);
            final Iterator<String> lambdaIt = lambdaMethod.getParameters().iterator();
            for (Parameter p : parameters) {
                //if (lambdaIt.hasNext()) {
                final String fqcn = lambdaIt.next();
                log.trace("Parameter:{} FQCN:{} Range:{}", p.getType(), fqcn, p.getRange());
                final boolean varArgs = p.isVarArgs();
                String type = p.getType().toString();
                final VariableDeclaratorId declaratorId = p.getId();
                if (declaratorId.getArrayCount() > 0) {
                    type = type + Strings.repeat(ClassNameUtils.ARRAY, declaratorId.getArrayCount());
                }

                if (type.isEmpty()) {
                    // TODO set Parent
                    String paramFqcn = ClassNameUtils.removeCapture(fqcn);
                    if (!paramFqcn.contains(".")) {
                        paramFqcn = fqcnResolver.resolveFQCN(paramFqcn, source).orElse(paramFqcn);
                    }
                    log.trace("Parameter paramFqcn:{} fqcn:{}", paramFqcn, fqcn);
                    final Variable variable = new Variable("", declaratorId.getName(), p.getRange(), paramFqcn);
                    nsMap.put(declaratorId.getName(), variable);
                }
                //}
            }
            // log.info("inferenceType:{} ns:{}", inferenceType, nsMap);
            return nsMap;
        });
    }

    Optional<String> analyzeReturnType(final String name, final String declaringClass, final boolean isLocal, final boolean isField, final JavaSource source) {
        log.traceEntry("name={} declaringClass={} isLocal={} isField={}", name, declaringClass, isLocal, isField);

        final Optional<String> result = this.returnTypeFunctions.stream()
                .map(function -> function.apply(name, declaringClass, isLocal, isField, source))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        return log.traceExit(result);
    }

    private List<AnalyzeReturnTypeFunction> getReturnTypeAnalyzeFunctions() {
        final List<AnalyzeReturnTypeFunction> searchFunctions = new ArrayList<>(4);
        searchFunctions.add(this::getReturnFromSource);
        searchFunctions.add(this::getReturnEnum);
        searchFunctions.add(this::getReturnFromStaticImp);
        searchFunctions.add(this::getReturnFromReflect);
        return searchFunctions;
    }

    private Optional<String> getReturnFromSource(final String name, final String declaringClass, final boolean isLocal, final boolean isField, final JavaSource source) {
        log.traceEntry("name={} declaringClass={} isLocal={} isField={}", name, declaringClass, isLocal, isField);

        if (isField) {
            if (isLocal) {
                final Optional<String> result = fqcnResolver.resolveThisScope(name, source);
                if (result.isPresent()) {
                    log.debug("resolved: {} -> FQCN:{}", name, result);
                    return log.traceExit(result);
                }
            }
            final Optional<String> result = source.getCurrentType().flatMap(typeScope -> {

                if (typeScope.getFQCN().equals(declaringClass)) {
                    final Optional<String> resolved = fqcnResolver.resolveFQCN(name, source);
                    log.debug("resolved: {} -> FQCN:{}", name, resolved);
                    return resolved;
                }
                return Optional.empty();
            });
            return log.traceExit(result);
        }
        final Optional<String> result = source.getCurrentType().flatMap(typeScope -> {
            if (typeScope.getFQCN().equals(declaringClass)) {
                final Optional<String> resolved = typeScope.getMemberDescriptors().stream()
                        .filter(md -> md.matchType(CandidateUnit.MemberType.METHOD) && md.getName().equals(name))
                        .map(CandidateUnit::getReturnType)
                        .findFirst();
                log.debug("resolved: {} -> FQCN:{}", name, resolved);
                return resolved;
            }
            return Optional.empty();
        });
        return log.traceExit(result);
    }

    private Optional<String> getReturnEnum(final String name, final String declaringClass, final boolean isLocal, final boolean isField, final JavaSource source) {
        log.traceEntry("name={} declaringClass={} isLocal={} isField={}", name, declaringClass, isLocal, isField);
        CachedASMReflector reflector = CachedASMReflector.getInstance();

        // Try Search Enum
        final Optional<String> result = reflector.containsClassIndex(declaringClass)
                .map(classIndex -> {
                    String enumName = classIndex.getRawDeclaration() + ClassNameUtils.INNER_MARK + name;
                    if (reflector.containsFQCN(enumName)) {
                        log.debug("resolved: {} -> FQCN:{}", name, enumName);
                        return enumName;
                    }
                    final String result1 = classIndex.supers.stream()
                            .map(s -> s + ClassNameUtils.INNER_MARK + name)
                            .filter(reflector::containsFQCN)
                            .findFirst().orElse(null);
                    if (result1 != null) {
                        log.debug("resolved: {} -> FQCN:{}", name, result1);
                    }
                    return result1;
                });
        return log.traceExit(result);
    }

    private Optional<String> getReturnFromStaticImp(final String name, final String declaringClass, final boolean isLocal, final boolean isField, final JavaSource source) {
        log.traceEntry("name={} declaringClass={} isLocal={} isField={}", name, declaringClass, isLocal, isField);
        if (source.staticImp.containsKey(name)) {
            final Optional<String> result = getReturnFromReflect(name, declaringClass, isLocal, isField, source);
            return log.traceExit(result);
        }
        final Optional<String> empty = Optional.empty();
        return log.traceExit(empty);
    }

    public Optional<String> getReturnFromReflect(final String name, String declaringClass, final boolean isLocal, final boolean isField, final JavaSource source) {
        final EntryMessage entryMessage = log.traceEntry("name={} declaringClass={} isLocal={} isField={}", name, declaringClass, isLocal, isField);

        CachedASMReflector reflector = CachedASMReflector.getInstance();

        // TODO check array access or class array type
        if (ClassNameUtils.isClassArray(declaringClass)) {
            // class array ?
            // TODO check access expr
            if (isField && name.equals("length")) {
                // is Class<?>[]
                final Optional<String> optional = Optional.of("int");
                return log.traceExit(entryMessage, optional);
            }
            if (!isField && name.equals("clone")) {
                // is Class<?>[]
                final Optional<String> optional = Optional.of(declaringClass);
                return log.traceExit(entryMessage, optional);
            }
        }

        File classFile = reflector.getClassFile(ClassNameUtils.removeTypeAndArray(declaringClass));
        if (classFile == null) {
            // try inner class
            final Optional<String> res = ClassNameUtils.toInnerClassName(declaringClass);
            if (res.isPresent()) {
                declaringClass = res.orElse(declaringClass);
            }
            classFile = reflector.getClassFile(ClassNameUtils.removeTypeAndArray(declaringClass));
            if (classFile == null) {
                log.debug("getReturnFromReflect classFile null name:{} declaringClass:{}", name, declaringClass);
                final Optional<String> empty = Optional.empty();
                return log.traceExit(entryMessage, empty);
            }
        }

        final String declaringClass2 = declaringClass;

        boolean onlyPublic = !isLocal && classFile.getName().endsWith("jar");

        final String result = reflector.reflectStream(declaringClass2)
                .filter(md -> this.returnTypeFilter(name, isField, onlyPublic, md))
                .map(md -> {
                    final String type = md.getRawReturnType();
                    return fqcnResolver.resolveFQCN(type, source).orElse(type);
                })
                .findFirst()
                .orElseGet(() -> {
                    String dc = declaringClass2;

                    while (dc.contains(ClassNameUtils.INNER_MARK)) {
                        dc = dc.substring(0, dc.lastIndexOf(ClassNameUtils.INNER_MARK));
                        final Optional<String> ret = reflector.reflectStream(dc)
                                .filter(md -> this.returnTypeFilter(name, isField, onlyPublic, md))
                                .map(md -> {
                                    final String type = md.getRawReturnType();
                                    return fqcnResolver.resolveFQCN(type, source).orElse(type);
                                })
                                .findFirst();
                        if (ret.isPresent()) {
                            return ret.orElse("");
                        }
                    }
                    return null;
                });
        if (result != null) {
            log.debug("resolved: {} -> FQCN:{}", name, result);
        }
        final Optional<String> result1 = Optional.ofNullable(result);
        return log.traceExit(entryMessage, result1);
    }

    private boolean returnTypeFilter(String name, Boolean isField, boolean onlyPublic, MemberDescriptor md) {
        final String mdName = md.getName();
        if (mdName.equals(name)) {
            if (isField && (md.matchType(CandidateUnit.MemberType.FIELD) || md.matchType(CandidateUnit.MemberType.VAR))) {
                // field
                return !onlyPublic || md.getDeclaration().contains("public");
            } else if (!isField && md.matchType(CandidateUnit.MemberType.METHOD)) {
                // method
                return !onlyPublic || md.getDeclaration().contains("public");
            }
        }
        return false;
    }

}
