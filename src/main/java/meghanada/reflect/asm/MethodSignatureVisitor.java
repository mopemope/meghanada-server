package meghanada.reflect.asm;

import com.google.common.base.MoreObjects;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.*;

class MethodSignatureVisitor extends SignatureVisitor {

    private static final Logger log = LogManager.getLogger(MethodSignatureVisitor.class);
    private final String name;
    private final List<String> classTypeParameters;
    // method parameters
    private List<TypeInfo> parameterTypes;
    // method typeParameters
    private Set<String> typeParameters;

    private TypeInfo current;
    private TypeInfo formalType;
    private TypeInfo returnType;
    private boolean isSuper;
    private boolean isExtends;
    private boolean isReturn;
    private boolean isParameter;
    private boolean hasTypes;
    private boolean isArray;

    // formal
    private boolean isClassBound;
    private boolean isInterfaceBound;
    private MethodSignatureVisitor parent;

    private Map<String, String> typeMap;
    private boolean isFormalType;

    MethodSignatureVisitor(final String name, final List<String> classTypeParameters) {
        super(Opcodes.ASM5);
        final EntryMessage message = log.traceEntry("name={} classTypeParameters={}", name, classTypeParameters);
        this.name = name;
        this.classTypeParameters = classTypeParameters;
        this.parameterTypes = new ArrayList<>(4);
        this.typeParameters = new HashSet<>(4);
        assert this.classTypeParameters != null;
        log.traceExit(message);
    }

    private MethodSignatureVisitor(String name, MethodSignatureVisitor parent) {
        super(Opcodes.ASM5);
        final EntryMessage message = log.traceEntry("name={}", name);
        this.name = name;
        this.parent = parent;
        this.classTypeParameters = parent.classTypeParameters;
        this.typeMap = parent.typeMap;
        log.traceExit(message);
    }

    @Override
    public SignatureVisitor visitTypeArgument(char c) {
        final EntryMessage message = log.traceEntry("params={} current={} c={}", this.parameterTypes, this.current, c);
        final MethodSignatureVisitor visitor = new MethodSignatureVisitor(this.name, this);
        visitor.hasTypes = true;
        visitor.isParameter = this.isParameter;
        visitor.isReturn = this.isReturn;
        visitor.current = this.current;
        switch (c) {
            case SignatureVisitor.INSTANCEOF:
                break;
            case SignatureVisitor.SUPER:
                visitor.isSuper = true;
                break;
            case SignatureVisitor.EXTENDS:
                visitor.isExtends = true;
                break;
        }
        log.traceExit(message);
        return visitor;
    }

    @Override
    public void visitTypeArgument() {
        log.traceEntry("current={}", this.current);
        final TypeInfo typeInfo = new TypeInfo("?", "?");
        if (this.current.typeParameters == null) {
            this.current.typeParameters = new ArrayList<>(4);
        }
        this.current.typeParameters.add(typeInfo);
        log.traceExit();
    }

    @Override
    public void visitClassType(final String s) {
        final String className = ClassNameUtils.replaceSlash(s);
        final EntryMessage message = log.traceEntry("current={} s={} className={} isClassBound={}", this.current, s, className, this.isClassBound);

        TypeInfo typeInfo = new TypeInfo(className, className);

        if (this.isInterfaceBound && !this.isFormalType) {
            final String name = this.current.name;
            current.name = '<' + name
                    + " extends "
                    + className
                    + '>';
            log.traceExit(message);
            return;
        }

        if (this.isClassBound && !this.isFormalType) {
            final String name = this.current.name;
            current.name = '<' + name + '>';
            log.traceExit(message);
            return;
        }

        if (this.hasTypes) {
            // override
            if (this.isExtends) {
                typeInfo = new TypeInfo("? extends " + className, "? extends " + className);
            } else if (this.isSuper) {
                typeInfo = new TypeInfo("? super " + className, "? super " + className);
            }
            this.current = typeInfo;
        }

        if (this.current == null) {
            this.current = typeInfo;
        }
        log.traceExit(message);
    }

    @Override
    public void visitFormalTypeParameter(final String s) {
        final EntryMessage message = log.traceEntry("s={} current={}", s, this.current);
        if (this.formalType == null) {
            this.formalType = new TypeInfo("", "");
            this.formalType.typeParameters = new ArrayList<>(4);
            this.formalType.typeParameters.add(new TypeInfo(s, s));
        } else {
            this.formalType.typeParameters.add(new TypeInfo(s, s));
        }
        log.traceExit(message);
    }

    @Override
    public SignatureVisitor visitArrayType() {
        log.traceEntry("current={}", this.current);
        this.isArray = true;
        log.traceExit();
        return this;
    }

    @Override
    public void visitBaseType(char c) {
        final String baseType = ASMReflector.toPrimitive(c);
        final EntryMessage message = log.traceEntry("baseType={} parameterTypes={} c={}", baseType, this.parameterTypes, c);

        TypeInfo typeInfo = new TypeInfo(baseType, baseType);
        if (this.parent != null && this.isReturn) {
            // set return type
            this.parent.returnType = typeInfo;
            log.traceExit(message);
            return;
        }

        if (this.current == null) {
            this.current = typeInfo;
            this.visitEnd();
        }
        log.traceExit(message);
    }

    @Override
    public void visitTypeVariable(String typeVariable) {
        final EntryMessage message = log.traceEntry("typeVariable={}", typeVariable);
        TypeInfo typeInfo;
        if (this.typeMap != null && typeMap.containsKey(typeVariable)) {
            String val = typeMap.get(typeVariable);
            if (val.equals(typeVariable)) {
                this.getTopVisitor(this).typeParameters.add(typeVariable);
                typeVariable = ClassNameUtils.CLASS_TYPE_VARIABLE_MARK + typeVariable;
            } else {
                ClassNameUtils.getTypeVariable(val).ifPresent(tv -> this.getTopVisitor(this).typeParameters.add(tv));
                typeVariable = val;
            }
        } else {
            if (this.classTypeParameters.contains(typeVariable)) {
                // mark
                this.getTopVisitor(this).typeParameters.add(typeVariable);
                typeVariable = ClassNameUtils.CLASS_TYPE_VARIABLE_MARK + typeVariable;
            } else {
                this.getTopVisitor(this).typeParameters.add(typeVariable);
                typeVariable = ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK + typeVariable;
            }
        }
        typeInfo = getTypeInfo(typeVariable);
        if (this.isReturn) {
            if (this.current == null) {
                // add direct
                typeInfo.isArray = this.isArray;
                this.parent.returnType = typeInfo;
            } else {
                if (this.current.typeParameters == null) {
                    this.current.typeParameters = new ArrayList<>(4);
                }
                this.current.typeParameters.add(typeInfo);
            }
            log.traceExit(message);
            return;
        }

        if (this.isParameter) {
            if (isSuper || isExtends) {
                if (this.current.typeParameters == null) {
                    this.current.typeParameters = new ArrayList<>(4);
                }
                this.current.typeParameters.add(typeInfo);
            } else {
                if (this.current != null) {
                    if (this.current.typeParameters == null) {
                        this.current.typeParameters = new ArrayList<>(4);
                    }
                    this.current.typeParameters.add(typeInfo);
                } else {
                    typeInfo.isArray = this.isArray;
                    this.parent.parameterTypes.add(typeInfo);
                }
            }
        }
        log.traceExit(message);
    }

    private TypeInfo getTypeInfo(String typeVariable) {
        log.traceEntry("typeVariable={}", typeVariable);
        TypeInfo typeInfo;
        if (isSuper) {
            typeInfo = new TypeInfo("? super " + typeVariable, "? super " + typeVariable);
        } else if (isExtends) {
            typeInfo = new TypeInfo("? extends " + typeVariable, "? extends " + typeVariable);
        } else {
            typeInfo = new TypeInfo(typeVariable, typeVariable);
        }
        return log.traceExit(typeInfo);
    }

    @Override
    public SignatureVisitor visitParameterType() {
        final EntryMessage message = log.traceEntry("name={} current={}", this.name, this.current);
        MethodSignatureVisitor visitor = new MethodSignatureVisitor(this.name, this);
        visitor.isParameter = true;
        log.traceExit(message);
        return visitor;
    }

    @Override
    public SignatureVisitor visitReturnType() {
        final EntryMessage message = log.traceEntry("name={} parameterTypes={}", this.name, this.parameterTypes);
        MethodSignatureVisitor visitor = new MethodSignatureVisitor(this.name, this);
        visitor.isReturn = true;
        log.traceExit(message);
        return visitor;
    }

    @Override
    public void visitEnd() {
        final EntryMessage message = log.traceEntry("current={} isClassBound={} isInterfaceBound={}", this.current, this.isClassBound, this.isInterfaceBound);

        if (this.isReturn && this.parent != null) {
            if (this.hasTypes) {
                if (this.parent.current.typeParameters == null) {
                    this.parent.current.typeParameters = new ArrayList<>(4);
                }
                this.parent.current.typeParameters.add(this.current);
            } else {
                if (this.isArray) {
                    this.parent.returnType = this.current;
                    this.parent.returnType.isArray = true;
                } else {
                    this.parent.returnType = this.current;
                }
            }
            log.traceExit(message);
            return;
        }

        if (this.isParameter && this.parent != null) {
            if (this.isArray) {
                this.current.variableArguments = true;
            }
            if (this.parent.current != null) {
                if (this.parent.current.typeParameters == null) {
                    this.parent.current.typeParameters = new ArrayList<>(4);
                }
                this.parent.current.typeParameters.add(this.current);
            } else {
                if (this.parent.parameterTypes == null) {
                    this.parent.parameterTypes = new ArrayList<>(4);
                }
                this.parent.parameterTypes.add(this.current);
            }

            log.traceExit(message);
            return;
        }

        if (this.isClassBound || this.isInterfaceBound) {
            assert this.parent != null;
            this.parent.formalType = this.current;
        }
        log.traceExit(message);
    }

    @Override
    public SignatureVisitor visitClassBound() {
        final EntryMessage message = log.traceEntry("current={}", this.current);
        MethodSignatureVisitor visitor = new MethodSignatureVisitor(this.name, this);
        visitor.current = this.formalType;
        this.formalType = null;
        visitor.isFormalType = true;
        visitor.isClassBound = true;
        log.traceExit(message);
        return visitor;
    }

    @Override
    public SignatureVisitor visitInterfaceBound() {
        log.traceEntry("current={}", this.current);
        MethodSignatureVisitor visitor = new MethodSignatureVisitor(this.name, this);
        visitor.current = this.formalType;
        this.formalType = null;
        visitor.isInterfaceBound = true;
        log.traceExit();
        return visitor;
    }

    @Override
    public SignatureVisitor visitSuperclass() {
        log.traceEntry("current={}", this.current);
        log.traceExit();
        return super.visitSuperclass();
    }

    @Override
    public SignatureVisitor visitInterface() {
        log.traceEntry("current={}", this.current);
        log.traceExit();
        return super.visitInterface();
    }

    @Override
    public SignatureVisitor visitExceptionType() {
        log.traceEntry("current={}", this.current);
        log.traceExit();
        return super.visitExceptionType();
    }

    @Override
    public void visitInnerClassType(String s) {
        log.traceEntry("current={}", this.current);
        super.visitInnerClassType(s);
        log.traceExit();
    }

    private MethodSignatureVisitor getTopVisitor(MethodSignatureVisitor visitor) {
        if (visitor.parent == null) {
            return visitor;
        }
        return getTopVisitor(visitor.parent);
    }

    List<TypeInfo> getParameterTypes() {
        this.replaceArrayArg();
        return parameterTypes;
    }

    private void replaceArrayArg() {
        boolean one = this.parameterTypes.size() == 1;
        int last = this.parameterTypes.size() - 1;
        for (int i = 0; i < this.parameterTypes.size(); i++) {
            TypeInfo ti = this.parameterTypes.get(i);
            if (ti.variableArguments
                    && (one || last != i)) {
                // change array
                ti.variableArguments = false;
                ti.isArray = true;
            }
        }
    }

    TypeInfo getReturnType() {
        return this.returnType;
    }

    public TypeInfo getFormalType() {
        return this.formalType;
    }

    Set<String> getTypeParameters() {
        return this.typeParameters;
    }

    void setTypeMap(Map<String, String> typeMap) {
        this.typeMap = typeMap;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("parameterTypes", parameterTypes)
                .add("typeParameters", typeParameters)
                .add("current", current)
                .add("formalType", formalType)
                .add("returnType", returnType)
                .add("isSuper", isSuper)
                .add("isExtends", isExtends)
                .add("isReturn", isReturn)
                .add("isParameter", isParameter)
                .add("hasTypes", hasTypes)
                .add("isArray", isArray)
                .add("isClassBound", isClassBound)
                .add("isInterfaceBound", isInterfaceBound)
                .add("typeMap", typeMap)
                .toString();
    }
}
