package meghanada.parser;

import com.github.javaparser.Range;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.TypeParameter;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.google.common.base.Strings;
import meghanada.parser.source.*;
import meghanada.reflect.*;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

class JavaSymbolAnalyzeVisitor extends VoidVisitorAdapter<JavaSource> {

    private static Logger log = LogManager.getLogger(JavaSymbolAnalyzeVisitor.class);
    private final FQCNResolver fqcnResolver;
    private final TypeAnalyzer typeAnalyzer;

    JavaSymbolAnalyzeVisitor() {
        this.fqcnResolver = FQCNResolver.getInstance();
        this.typeAnalyzer = new TypeAnalyzer(this);
    }

    static String checkArrayType(String type, final VariableDeclaratorId declaratorId) {
        if (declaratorId.getArrayCount() > 0) {
            type = type + Strings.repeat(ClassNameUtils.ARRAY, declaratorId.getArrayCount());
        }
        return type;
    }


    @Override
    public void visit(final PackageDeclaration n, final JavaSource source) {
        source.pkg = n.getName().toString();
        super.visit(n, source);
    }

    @Override
    public void visit(final ImportDeclaration node, final JavaSource source) {
        String fqcn = node.getName().toString();
        String name = node.getName().getName();
        if (node.isStatic()) {
            if (node.isAsterisk()) {
                CachedASMReflector reflector = CachedASMReflector.getInstance();
                reflector.reflectStaticStream(fqcn)
                        .forEach(md -> {
                            source.staticImp.putIfAbsent(md.getName(), md.getDeclaringClass());
                        });
            } else {
                fqcn = ClassNameUtils.getPackage(fqcn);
                source.staticImp.putIfAbsent(name, fqcn);
            }
        } else {
            if (!node.isAsterisk()) {
                source.importClass.put(name, fqcn);
                source.addUnusedClass(name, fqcn);
            } else {
                CachedASMReflector reflector = CachedASMReflector.getInstance();
                Map<String, String> symbols = reflector.getPackageClasses(fqcn);
                for (Map.Entry<String, String> entry : symbols.entrySet()) {
                    source.importClass.put(entry.getKey(), entry.getValue());
                    source.addUnusedClass(entry.getKey(), entry.getValue());
                }
            }
        }
        super.visit(node, source);
    }


    @Override
    public void visit(final EnumConstantDeclaration node, final JavaSource source) {
        log.traceEntry("EnumConstantDeclaration name={} range={}", node.getName(), node.getRange());

        source.getCurrentBlock().ifPresent(blockScope -> node.getArgs()
                .forEach(expression -> typeAnalyzer.analyzeExprClass(expression, blockScope, source)));
        final TypeScope current = source.currentType.peek();
        final String type = current.getType();
        if (node.getClassBody().size() > 0) {
            final EnumScope enumScope = (EnumScope) current;
            enumScope.incrIndex();

            final String className = type + ClassNameUtils.INNER_MARK + enumScope.getIndex();

            final ClassScope classScope = new ClassScope(source.pkg, className, node.getRange(), node.getRange(), false);
            source.currentType.push(classScope);

            log.trace("start EnumConstantDeclaration:{}, Range:{}", className, node.getRange());
            super.visit(node, source);
            log.trace("end   EnumConstantDeclaration:{}, Range:{}", className, node.getRange());

            final TypeScope typeScope = source.currentType.remove();
            source.typeScopes.add(typeScope);
        } else {
            final String className = type + ClassNameUtils.INNER_MARK + node.getName();
            final ClassScope classScope = new ClassScope(source.pkg, className, node.getRange(), node.getRange(), false);
            source.typeScopes.add(classScope);
        }
        log.traceExit();
    }

    @Override
    public void visit(final EnumDeclaration node, final JavaSource source) {
        log.traceEntry("EnumDeclaration name={} range={}", node.getName(), node.getRange());
        final List<ClassOrInterfaceType> nImplements = node.getImplements();
        String className = node.getName();

        final List<String> implClasses = new ArrayList<>(4);
        if (nImplements != null) {
            for (ClassOrInterfaceType clazz : nImplements) {
                final String name = clazz.getName();
                fqcnResolver.resolveFQCN(name, source).ifPresent(implClasses::add);
            }
        }
        final TypeScope current = source.currentType.peek();
        if (current != null) {
            String type = current.getType();
            className = type + ClassNameUtils.INNER_MARK + className;
        }
        final NameExpr nameExpr = node.getNameExpr();

        final EnumScope enumScope = new EnumScope(source.pkg, className, node.getRange(), nameExpr.getRange());
        enumScope.setImplClasses(implClasses);
        source.currentType.push(enumScope);

        log.trace("start Enum:{}, Range:{}", className, node.getRange());
        super.visit(node, source);
        log.trace("end   Enum:{}, Range:{}", className, node.getRange());

        final TypeScope typeScope = source.currentType.remove();
        source.typeScopes.add(typeScope);

        log.traceExit();
    }

    @Override
    public void visit(final ClassOrInterfaceDeclaration node, final JavaSource source) {
        final EntryMessage entryMessage = log.traceEntry("ClassOrInterfaceDeclaration name={} range={}", node.getName(), node.getRange());

        final List<ClassOrInterfaceType> nImplements = node.getImplements();
        final List<ClassOrInterfaceType> nExtends = node.getExtends();
        final List<TypeParameter> typeParameters = node.getTypeParameters();

        final List<String> typeParams = typeParameters.stream()
                .map(TypeParameter::getName)
                .collect(Collectors.toList());

        String className = node.getName();

        final TypeScope current = source.currentType.peek();
        if (current != null) {
            final String type = current.getType();
            className = type + ClassNameUtils.INNER_MARK + className;
        }
        final CachedASMReflector reflector = CachedASMReflector.getInstance();

        final ClassScope classScope = new ClassScope(source.pkg, className, node.getRange(), node.getNameExpr().getRange(), node.isInterface());
        final List<String> extendsClasses = new ArrayList<>(1);
        if (nExtends != null) {
            for (final ClassOrInterfaceType ci : nExtends) {
                final String name = ci.getName();
                final Optional<String> clazz = this.fqcnResolver.resolveFQCN(name, source);

                if (clazz.isPresent()) {
                    final String fqcn = clazz.get();
                    extendsClasses.add(fqcn);
                    this.markUsedClass(fqcn, source);

                    // import super class fields
                    reflector.reflectFieldStream(fqcn)
                            .forEach(md -> {
                                final String declaration = md.getDeclaration();
                                if (declaration.contains("public")
                                        || declaration.contains("protected")) {
                                    final String ret = this.fqcnResolver.resolveFQCN(md.getReturnType(), source).orElse(md.getReturnType());
                                    final Variable ns = new Variable(
                                            md.getDeclaringClass(),
                                            md.getName(),
                                            node.getRange(),
                                            ret,
                                            true);
                                    classScope.addFieldSymbol(ns);
                                }
                            });
                } else {
                    this.markUsedClass(name, source);
                }
            }
        }
        final List<String> implClasses = new ArrayList<>(4);
        if (nImplements != null) {
            for (final ClassOrInterfaceType ci : nImplements) {
                final String name = ci.getName();
                final Optional<String> clazz = fqcnResolver.resolveFQCN(name, source);
                if (clazz.isPresent()) {
                    final String fqcn = clazz.get();
                    implClasses.add(fqcn);
                    this.markUsedClass(fqcn, source);
                } else {
                    this.markUsedClass(name, source);
                }
            }
        }

        classScope.setExtendsClasses(extendsClasses);
        classScope.setImplClasses(implClasses);
        classScope.setTypeParameters(typeParams);

        source.currentType.push(classScope);
        this.setClassTypeParameter(typeParameters, classScope, source);
        log.trace("start ClassOrInterface:{}, Range:{}", className, node.getRange());
        super.visit(node, source);
        log.trace("end   ClassOrInterface:{}, Range:{}", className, node.getRange());

        final TypeScope typeScope = source.currentType.remove();
        source.typeScopes.add(typeScope);

        log.traceExit(entryMessage);
    }

    private void setClassTypeParameter(final List<TypeParameter> typeParameters, final ClassScope classScope, final JavaSource source) {
        typeParameters.forEach(tp -> {
            final String name = tp.getName();
            if (name != null && !name.isEmpty()) {
                final List<ClassOrInterfaceType> typeBounds = tp.getTypeBound();
                if (typeBounds != null && typeBounds.size() > 0) {
                    final ClassOrInterfaceType type = typeBounds.get(0);
                    final String typeBound = type.toString();
                    final String fqcn = this.toFQCN(typeBound, source);
                    classScope.getTypeParameterMap().put(name, fqcn);
                    log.trace("put typeParameterMap name={} typeBound={}", name, typeBound);
                } else {
                    classScope.getTypeParameterMap().put(name, ClassNameUtils.OBJECT_CLASS);
                    log.trace("put typeParameterMap name={} typeBound={}", name, ClassNameUtils.OBJECT_CLASS);
                }
            }
        });
    }

    @Override
    public void visit(final ConstructorDeclaration node, final JavaSource source) {
        final TypeScope typeScope = source.currentType.peek();
        if (typeScope != null) {

            final NameExpr nameExpr = node.getNameExpr();
            final String name = nameExpr.getName();

            List<MethodParameter> parameters = new ArrayList<>(4);
            List<String> exceptions = new ArrayList<>(2);
            node.getParameters().forEach(parameter -> {
                final String pName = parameter.getName();
                final Optional<String> optType = fqcnResolver.resolveFQCN(pName, source);
                final Type type = parameter.getType();
                this.markUsedClass(type.toString(), source);

                final String fqcn = optType.orElse(type.toString());
                parameters.add(new MethodParameter(fqcn, pName));
            });

            node.getThrows().forEach(ex -> {
                final String exClass = ex.getType().toString();
                this.markUsedClass(exClass, source);
                exceptions.add(exClass);
            });

            typeScope.startMethod(name);
            // start method
            typeScope.startBlock(name, node.getRange(), nameExpr.getRange());
            final EntryMessage entryMessage = log.traceEntry("start Constructor:{}, Range:{}", name, node.getRange());

            super.visit(node, source);

            typeScope.endMethod();
            typeScope.endBlock();

            final String modifier = toModifierString(node.getModifiers());
            final String fqcn = typeScope.getFQCN();

            // TODO need hasDefault?
            final String[] exs = exceptions.toArray(new String[exceptions.size()]);
            final MemberDescriptor memberDescriptor = new MethodDescriptor(fqcn, name, modifier, parameters, exs, fqcn, false);
            typeScope.addMemberDescriptor(memberDescriptor);

            log.traceExit(entryMessage);
        }
    }

    @Override
    public void visit(final FieldDeclaration node, final JavaSource source) {

        final EntryMessage entryMessage = log.traceEntry("Start FieldDeclaration:{}, Range:{}", node, node.getRange());
        source.getCurrentType().ifPresent(ts -> {

            for (final VariableDeclarator v : node.getVariables()) {
                String type = node.getType().toString();
                final VariableDeclaratorId declaratorId = v.getId();
                type = checkArrayType(type, declaratorId);

                Optional<String> result = this.resolveFQCN(type, source);
                log.debug("result:{} {}", result, declaratorId);

                if (!result.isPresent()) {
                    if (ClassNameUtils.isArray(type)) {
                        result = Optional.of(ClassNameUtils.OBJECT_CLASS + ClassNameUtils.ARRAY);
                        log.warn("Unknown Type:{}. use Object[]", type);
                    } else {
                        result = Optional.of(ClassNameUtils.OBJECT_CLASS);
                        log.warn("Unknown Type:{}. use Object", type);
                    }
                }
                result.ifPresent(fqcn -> {
                    this.markUsedClass(fqcn, source);

                    final String name = declaratorId.getName();
                    final String declaringClass = ts.getFQCN();

                    final Variable ns = new Variable(
                            declaringClass,
                            name,
                            declaratorId.getRange(),
                            fqcn,
                            true);
                    ts.addFieldSymbol(ns);

                    final String modifier = toModifierString(node.getModifiers());

                    final FieldDescriptor fd = new FieldDescriptor(
                            declaringClass,
                            name,
                            modifier,
                            fqcn);
                    ts.addMemberDescriptor(fd);
                });
            }
        });
        super.visit(node, source);
        log.traceExit(entryMessage);
    }

    @Override
    public void visit(final LambdaExpr node, final JavaSource arg) {
        log.trace("LambdaExpr:{} {}", node, node.isParametersEnclosed());
        log.trace("LambdaExpr Parent:{}", node.getParentNode().getClass());
        super.visit(node, arg);
    }

    @Override
    public void visit(final MethodDeclaration node, final JavaSource source) {
        final EntryMessage entryMessage = log.traceEntry("MethodDeclaration name={} range={}", node.getName(), node.getRange());
        final NameExpr nameExpr = node.getNameExpr();

        source.getCurrentType().ifPresent(typeScope -> {
            final String methodName = node.getName();

            // mark returnType
            this.markUsedClass(node.getType().toString(), source);
            node.getParameters().forEach(parameter -> {
                this.markUsedClass(parameter.getType().toString(), source);
            });

            node.getThrows().forEach(referenceType -> this.markUsedClass(referenceType.getType().toString(), source));
            log.trace("start Method:{}, Range:{}", methodName, node.getRange());
            typeScope.startMethod(methodName);
            typeScope.startBlock(methodName, node.getRange(), nameExpr.getRange());

            super.visit(node, source);
            typeScope.endMethod();

            typeScope.endBlock();
            this.processAfterMethod(node, source, typeScope);
            log.trace("end Method:{} Range:{}", methodName, node.getRange());

        });
        log.traceExit(entryMessage);
    }

    private void processAfterMethod(final MethodDeclaration node, final JavaSource source, final TypeScope typeScope) {
        final List<MethodParameter> paramList = new ArrayList<>(8);

        for (Parameter parameter : node.getParameters()) {
            String paramType = parameter.getType().toString();
            String paramName = parameter.getId().getName();
            String typeP = "";
            int idx = paramType.indexOf("<");
            if (idx > 0) {
                typeP = paramType.substring(idx, paramType.length());
                paramType = paramType.substring(0, idx);
            }
            String paramTypeFQCN = source.importClass.get(paramType);
            if (paramTypeFQCN == null) {
                paramTypeFQCN = paramType;
            }
            if (idx > 0) {
                paramTypeFQCN = paramTypeFQCN + typeP;
            }
            paramList.add(new MethodParameter(paramTypeFQCN, paramName));
        }

        final List<String> exList = new ArrayList<>();
        for (ReferenceType rt : node.getThrows()) {
            String exType = rt.getType().toString();
            String typeP = "";
            int idx = exType.indexOf("<");
            if (idx > 0) {
                typeP = exType.substring(idx, exType.length());
                exType = exType.substring(0, idx);
            }
            String exTypeFQCN = source.importClass.get(exType);
            if (exTypeFQCN == null) {
                exTypeFQCN = exType;
            }
            if (idx > 0) {
                exTypeFQCN = exTypeFQCN + typeP;
            }
            exList.add(exTypeFQCN);
        }

        final String returnType = node.getType().toString();
        String returnTypeFQCN = source.importClass.get(returnType);
        if (returnTypeFQCN == null) {
            final Optional<String> resolveFQCN = fqcnResolver.resolveFQCN(returnType, source);
            returnTypeFQCN = resolveFQCN.orElse(ClassNameUtils.getPackage(typeScope.getFQCN()) + "." + returnType);
        }

        final String modifier = toModifierString(node.getModifiers());
        final String fqcn = typeScope.getFQCN();

        // TODO check hasDefault
        final MemberDescriptor memberDescriptor = new MethodDescriptor(fqcn,
                node.getName(),
                modifier,
                paramList,
                exList.toArray(new String[exList.size()]),
                returnTypeFQCN,
                false);

//            String hintReturnType = node.getType().toString();
//            String returnTypeFQCN = source.getImportClassSymbol().get(hintReturnType);
//            if (returnTypeFQCN == null) {
//                returnTypeFQCN = hintReturnType;
//            }
//            memberDescriptor.addOption(MemberDescriptor2.OPT_RETURN_TYPE, returnTypeFQCN);
        typeScope.addMemberDescriptor(memberDescriptor);
    }

    @Override
    public void visit(FieldAccessExpr node, JavaSource source) {
        source.getCurrentBlock().ifPresent(blockScope -> {
            this.fieldAccess(node, source, blockScope);
        });
        super.visit(node, source);
    }

    Optional<FieldAccessSymbol> fieldAccess(final FieldAccessExpr node, final JavaSource source, final BlockScope blockScope) {

        final NameExpr nameExpr = node.getFieldExpr();
        final String fieldName = node.getField();
        final Expression scope = node.getScope();
        if (scope instanceof NameExpr) {
            final String type = ((NameExpr) scope).getName();
            if (Character.isUpperCase(type.charAt(0))) {
                this.markUsedClass(type, source);
            }
        }

        Optional<Expression> optional = Optional.ofNullable(scope);
        if (!optional.isPresent()) {
            Expression expr = new ThisExpr();
            optional = Optional.of(expr);
        }

        return optional.flatMap(scopeExpr -> {
            final String scopeString = scopeExpr.toString();
            return this.typeAnalyzer.analyzeExprClass(scopeExpr, blockScope, source)
                    .flatMap(exprClass -> {
                        final String declaringClass = this.toFQCN(exprClass, source);

                        return this.createFieldAccessSymbol(fieldName,
                                scopeString,
                                declaringClass,
                                nameExpr.getRange(),
                                source)
                                .map(blockScope::addFieldAccess);
                    });
        });
    }

    @Override
    public void visit(final MethodCallExpr node, final JavaSource source) {
        final EntryMessage entryMessage = log.traceEntry("MethodCallExpr name={} range={}", node.getName(), node.getRange());

        source.getCurrentBlock().ifPresent(blockScope -> {
            this.methodCall(node, source, blockScope);
        });
        // super.visit(node, source);
        log.traceExit(entryMessage);
    }

    Optional<MethodCallSymbol> methodCall(final MethodCallExpr node, final JavaSource src, final BlockScope bs) {
        final NameExpr methodNameExpr = node.getNameExpr();
        final String methodName = methodNameExpr.getName();
        final List<Expression> args = node.getArgs();
        final Expression scope = node.getScope();

        final EntryMessage entryMessage = log.traceEntry("name={} range={}", node.getName(), node.getRange());

        if (scope instanceof NameExpr) {
            final String type = ((NameExpr) scope).getName();
            if (Character.isUpperCase(type.charAt(0))) {
                this.markUsedClass(type, src);
            }
        }
        Optional<Expression> scopeExpression = Optional.ofNullable(scope);
        if (!scopeExpression.isPresent()) {
            if (src.staticImp.containsKey(methodName)) {
                final String dec = src.staticImp.get(methodName);
                final Expression expr = new NameExpr(dec);
                expr.setRange(node.getRange());
                scopeExpression = Optional.of(expr);
            } else {
                final Expression expr = new ThisExpr();
                expr.setRange(node.getRange());
                scopeExpression = Optional.of(expr);
            }
        }
        final Optional<MethodCallSymbol> result = scopeExpression.flatMap(scopeExpr -> {
            final String scopeString = scopeExpr.toString();

            return this.typeAnalyzer.analyzeExprClass(scopeExpr, bs, src)
                    .flatMap(declaringClass -> {
                        final String maybeReturn = this.typeAnalyzer.
                                getReturnType(src, bs, declaringClass, methodName, args).
                                orElse(null);

                        return this.createMethodCallSymbol(node,
                                scopeString,
                                declaringClass,
                                src,
                                maybeReturn)
                                .map(bs::addMethodCall);
                    });
        });

        return log.traceExit(entryMessage, result);
    }

    private Optional<MethodCallSymbol> createMethodCallSymbol(final MethodCallExpr node, final String scope, final String declaringClass, final JavaSource source, final String returnType) {
        final NameExpr methodNameExpr = node.getNameExpr();
        final String methodName = methodNameExpr.getName();
        // GET method scope
        final MethodCallSymbol symbol = new MethodCallSymbol(scope,
                methodName,
                node.getRange(),
                methodNameExpr.getRange(),
                declaringClass);

        boolean isLocal = scope.equals("this") || scope.equals("super");

        final Optional<MethodCallSymbol> result = Optional.ofNullable(returnType).map(type -> {
            final String returnFQCN = this.toFQCN(type, source);
            symbol.returnType = returnFQCN;
            return Optional.of(symbol);
        }).orElseGet(() -> {
            final Optional<MethodCallSymbol> mcs = typeAnalyzer.analyzeReturnType(methodName, declaringClass, isLocal, false, source).map(resolved -> {
                final String returnFQCN = this.toFQCN(resolved, source);
                symbol.returnType = returnFQCN;
                return symbol;
            });
            return mcs;
        });

        if (!result.isPresent()) {
            log.warn("MISSING ReturnType:{} :{}", symbol, source.getFile());
        }
        return result;
    }

    private Optional<FieldAccessSymbol> createFieldAccessSymbol(final String name, final String scope, final String declaringClass, final Range range, final JavaSource source) {
        final EntryMessage entryMessage = log.traceEntry("name={} scope={} declaringClass={} range={}", name, scope, declaringClass, range);

        final FieldAccessSymbol symbol = new FieldAccessSymbol(scope,
                name,
                range,
                declaringClass);

        boolean isLocal = scope.equals("this") || scope.equals("super");

        // 1. normal field access instanceA.name
        final Optional<FieldAccessSymbol> fas = this.typeAnalyzer.getReturnFromReflect(name, declaringClass, isLocal, true, source)
                .map(type -> {
                    symbol.returnType = this.toFQCN(type, source);
                    return symbol;
                });
        if (fas.isPresent()) {
            // fields
            return log.traceExit(entryMessage, fas);
        }

        // 2. access inner class instanceA.innerClassB or ClassA.innerClassB
        final Optional<FieldAccessSymbol> fas2 = typeAnalyzer.analyzeReturnType(name, declaringClass, isLocal, true, source)
                .map(type -> {
                    final String returnFQCN = this.toFQCN(type, source);
                    symbol.returnType = returnFQCN;
                    return symbol;
                });
        if (!fas2.isPresent()) {
            log.warn("MISSING ReturnType:{} File:{}", symbol, source.getFile());
        }
        return log.traceExit(entryMessage, fas2);
    }

    private boolean isLocal(String scope, String declaringClass, JavaSource source) {
        final Optional<TypeScope> currentType = source.getCurrentType();
        if (currentType.isPresent()) {
            final String fqcn = currentType.get().getFQCN();
            if (declaringClass.contains(fqcn)) {
                // inner or self
                return true;
            }
        }
        return scope.equals("this") || scope.equals("super");
    }

    @Override
    public void visit(final Parameter node, final JavaSource source) {
        final EntryMessage entryMessage = log.traceEntry("Parameter node='{}' range={}", node, node.getRange());

        final String tempType = node.isVarArgs() ? node.getType().toString() + ClassNameUtils.VA_ARGS : node.getType().toString();
        final VariableDeclaratorId declaratorId = node.getId();
        final String type = checkArrayType(tempType, declaratorId);

        source.getCurrentType().ifPresent(ts -> source.getCurrentBlock(ts)
                .ifPresent(bs -> fqcnResolver.resolveFQCN(type, source).ifPresent(fqcn -> {
                    final String name = declaratorId.getName();
                    final Variable ns = new Variable(
                            ts.getFQCN(),
                            name,
                            declaratorId.getRange(),
                            fqcn,
                            true);
                    bs.addNameSymbol(ns);
                })));

        super.visit(node, source);
        log.traceExit(entryMessage);
    }

    @Override
    public void visit(final CatchClause node, final JavaSource source) {
        source.getCurrentType().ifPresent(typeScope -> {
            final Parameter except = node.getParam();
            final VariableDeclaratorId variableDeclaratorId = except.getId();
            if (variableDeclaratorId != null) {
                final String name = variableDeclaratorId.getName();
                // sampling first
                final Type exType = except.getType();
                final String exTypeFQCN = fqcnResolver.resolveFQCN(exType.toString(), source).orElse(exType.toString());
                final Variable ns = new Variable(
                        typeScope.getFQCN(),
                        name,
                        variableDeclaratorId.getRange(),
                        exTypeFQCN);
                final BlockScope blockScope = source.getCurrentBlock(typeScope).orElse(typeScope);
                blockScope.addNameSymbol(ns);
            }
        });
        super.visit(node, source);
    }

    @Override
    public void visit(final ReferenceType node, final JavaSource source) {
        final EntryMessage entryMessage = log.traceEntry("ReferenceType node={} range={}", node, node.getRange());
        final String type = node.toString();
        this.markUsedClass(this.toFQCN(type, source), source);
        super.visit(node, source);
        log.traceExit(entryMessage);
    }

    @Override
    public void visit(final ObjectCreationExpr node, final JavaSource source) {
        final EntryMessage entryMessage = log.traceEntry("node={} source={}", node, source);
        // new statement
        String createType = node.getType().getName();
        this.markUsedClass(createType, source);
        if (source.isImported(createType)) {
            Optional<String> fqcn = fqcnResolver.resolveFQCN(createType, source);

            source.getCurrentBlock().ifPresent(bs -> {
                final String createClass = fqcn.orElse(createType);
                this.constructorCall(createClass, node, source, bs);
            });

        }
        log.traceExit(entryMessage);
    }

    private void markUsedClass(final String type, final JavaSource source) {
        final String nm = ClassNameUtils.getSimpleName(type);
        log.debug("mark className:{}", nm);

        if (!source.isImported(nm)) {
            log.debug("add unknown class:{}", nm);
            source.addUnknownClass(nm);
        } else {
            log.debug("remove unused class:{}", nm);
            source.removeUnusedClass(nm);
        }
    }

    private void constructorCall(final String createClass, final ObjectCreationExpr node, final JavaSource src, final BlockScope bs) {
        final List<Expression> args = node.getArgs();
        if (args != null) {
            final Optional<TypeAnalyzer.MethodSignature> sig = typeAnalyzer.getMethodSignature(src, bs, createClass, args);

            final String maybeReturn = sig.flatMap(ms ->
                    this.typeAnalyzer.getConstructor(src, createClass, args.size(), ms.signature).
                            map(CandidateUnit::getReturnType)).orElse(null);

            final String clazz = ClassNameUtils.getSimpleName(createClass);

            final MethodCallSymbol mcs = new MethodCallSymbol("",
                    clazz,
                    node.getRange(),
                    node.getType().getRange(),
                    createClass);
            mcs.returnType = maybeReturn;
            bs.addMethodCall(mcs);
        }
    }

    @Override
    public void visit(final NameExpr node, final JavaSource source) {
        source.getCurrentType().ifPresent(ts -> source.getCurrentBlock(ts).ifPresent(bs -> {
            final String name = node.getName();
            final Variable fieldSymbol = ts.getFieldSymbol(name);
            if (fieldSymbol != null) {
                final Variable ns = new Variable(
                        ts.getFQCN(),
                        name,
                        node.getRange(),
                        fieldSymbol.getFQCN());
                bs.addNameSymbol(ns);
            } else {
                final Map<String, Variable> map = bs.getDeclaratorMap();
                if (map.containsKey(name)) {
                    final String fqcn = map.get(name).getFQCN();
                    final Variable v = new Variable(
                            ts.getFQCN(),
                            name,
                            node.getRange(),
                            fqcn);
                    bs.addNameSymbol(v);
                }
            }
        }));
        super.visit(node, source);
    }

    @Override
    public void visit(final VariableDeclarationExpr node, final JavaSource source) {
        final EntryMessage entryMessage = log.traceEntry("VariableDeclarationExpr node='{}' range={}", node, node.getRange());

        source.getCurrentType().ifPresent(typeScope -> source.getCurrentBlock(typeScope).ifPresent(blockScope -> {

            for (VariableDeclarator v : node.getVars()) {
                String type = node.getType().toString();
                final VariableDeclaratorId declaratorId = v.getId();
                final String name = declaratorId.getName();
                type = checkArrayType(type, declaratorId);
                this.markUsedClass(type, source);

                fqcnResolver.resolveFQCN(type, source).ifPresent(fqcn -> {
                    final Variable ns = new Variable(
                            typeScope.getFQCN(),
                            name,
                            declaratorId.getRange(),
                            fqcn,
                            true);
                    blockScope.addNameSymbol(ns);
                });
            }
        }));
        super.visit(node, source);
        log.traceExit(entryMessage);
    }

    @Override
    public void visit(final ReturnStmt node, final JavaSource source) {
        // log.debug("ReturnStmt Line:{}", node.getBeginLine(), node);
        source.getCurrentBlock().ifPresent(blockScope -> {
            if (blockScope.isLambdaBlock) {
                // lambda return
                final Expression expression = node.getExpr();
                if (expression != null) {
                    typeAnalyzer.analyzeExprClass(expression, blockScope, source).ifPresent(fqcn -> {
                        final String s = ClassNameUtils.boxing(fqcn);
                        log.trace("Lambda Block ReturnFQCN:{} Expr:{}", s, expression);
                        source.typeHint.addLambdaReturnType(s);
                    });
                } else {
                    source.typeHint.addLambdaReturnType("void");
                }
            }
        });
        super.visit(node, source);
    }

    @Override
    public void visit(final BlockStmt node, final JavaSource source) {
        final EntryMessage entryMessage = log.traceEntry("BlockStmt range={}", node.getRange());

        final boolean called = source.getCurrentType().flatMap(typeScope -> source.getCurrentBlock(typeScope).map(blockScope -> {
            final Node parentNode = node.getParentNode();
            final Class<? extends Node> parentNodeClass = parentNode.getClass();
            if (parentNodeClass.equals(BlockStmt.class)
                    || parentNodeClass.equals(IfStmt.class)
                    || parentNodeClass.equals(TryStmt.class)) {
                blockScope.startBlock("", node.getRange(), blockScope.getDeclaratorMap());
                super.visit(node, source);
                blockScope.endBlock();
                return true;
            }

            super.visit(node, source);
            // called
            return true;
        })).orElse(false);

        if (!called) {
            super.visit(node, source);
        }
        log.traceExit(entryMessage);
    }

    @Override
    public void visit(final ExpressionStmt node, final JavaSource source) {
        final Optional<BlockScope> currentBlock = source.getCurrentBlock();
        if (currentBlock.isPresent()) {
            final BlockScope blockScope = currentBlock.get();
            final ExpressionScope expr = new ExpressionScope(blockScope.getName(), node.getRange());
            blockScope.startExpression(expr);
            super.visit(node, source);
            blockScope.endExpression();
        } else {
            super.visit(node, source);
        }
    }

    private String toModifierString(final int modifier) {
        final StringBuilder sb = new StringBuilder();
        if (Modifier.isPrivate(modifier)) {
            sb.append("private ");
        }
        if (Modifier.isPublic(modifier)) {
            sb.append("public ");
        }
        if (Modifier.isProtected(modifier)) {
            sb.append("protected ");
        }
        if (Modifier.isStatic(modifier)) {
            sb.append("static ");
        }
        if (Modifier.isAbstract(modifier)) {
            sb.append("abstract ");
        }
        if (Modifier.isFinal(modifier)) {
            sb.append("final ");
        }
        if (Modifier.isInterface(modifier)) {
            sb.append("interface ");
        }
        if (Modifier.isNative(modifier)) {
            sb.append("native ");
        }
        if (Modifier.isStrict(modifier)) {
            sb.append("strict ");
        }
        if (Modifier.isSynchronized(modifier)) {
            sb.append("synchronized ");
        }
        if (Modifier.isTransient(modifier)) {
            sb.append("transient ");
        }
        if (Modifier.isVolatile(modifier)) {
            sb.append("volatile ");
        }
        return sb.toString();
    }

    private String toFQCN(final String type, final JavaSource source) {
        String returnFQCN = ClassNameUtils.removeCapture(type);

        final Optional<TypeScope> currentType = source.getCurrentType();
        if (currentType.isPresent()) {
            final TypeScope typeScope = currentType.get();
            if (typeScope instanceof ClassScope) {
                final ClassScope classScope = (ClassScope) typeScope;
                final Map<String, String> map = classScope.getTypeParameterMap();
                if (map.containsKey(returnFQCN)) {
                    returnFQCN = ClassNameUtils.removeCapture(map.get(returnFQCN));
                }
            }
        }

        return this.fqcnResolver.resolveFQCN(returnFQCN, source).orElse(returnFQCN);
    }

    private Optional<String> resolveFQCN(final String type, final JavaSource source) {
        String returnFQCN = ClassNameUtils.removeCapture(type);

        final Optional<TypeScope> currentType = source.getCurrentType();
        if (currentType.isPresent()) {
            final TypeScope typeScope = currentType.get();
            if (typeScope instanceof ClassScope) {
                final ClassScope classScope = (ClassScope) typeScope;
                final Map<String, String> map = classScope.getTypeParameterMap();
                if (map.containsKey(returnFQCN)) {
                    returnFQCN = ClassNameUtils.removeCapture(map.get(returnFQCN));
                }
            }
        }

        return this.fqcnResolver.resolveFQCN(returnFQCN, source);
    }
}
