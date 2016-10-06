package meghanada.reflect.asm;

import com.google.common.base.MoreObjects;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.*;

class FieldSignatureVisitor extends SignatureVisitor {

    private static Logger log = LogManager.getLogger(FieldSignatureVisitor.class);

    private final List<String> classTypeParameters;
    private final String name;
    private final Deque<TypeInfo> currentType = new ArrayDeque<>(2);
    // field typeParameters
    private Set<String> typeParameters;
    // main type
    private TypeInfo typeInfo;
    private boolean isInstance;
    private boolean isSuper;
    private boolean isExtends;
    private boolean holdArray;

    private Map<String, String> typeMap;

    // formal
    private boolean isClassBound;
    private boolean isInterfaceBound;
    private FieldSignatureVisitor parent;

    FieldSignatureVisitor(final String name, final List<String> classTypeParameters) {
        super(Opcodes.ASM5);
        final EntryMessage em = log.traceEntry("name={} classTypeParameters={}", name, classTypeParameters);
        this.name = name;
        this.classTypeParameters = classTypeParameters;
        this.typeParameters = new HashSet<>(2);
        log.traceExit(em);
        isInstance = false;
        isSuper = false;
        isExtends = false;
        holdArray = false;
    }

    private FieldSignatureVisitor(final String name, final FieldSignatureVisitor parent) {
        super(Opcodes.ASM5);
        log.traceEntry("name={} parent={}", name, parent);
        this.name = name;
        this.parent = parent;
        this.classTypeParameters = parent.classTypeParameters;
        this.typeMap = parent.typeMap;
        log.traceExit();
        isInstance = false;
        isSuper = false;
        isExtends = false;
        holdArray = false;
    }

    @Override
    public SignatureVisitor visitTypeArgument(final char c) {
        final EntryMessage em = log.traceEntry("name={} typeInfo={} currentType={}", this.name, this.typeInfo, this.currentType);

        this.isInstance = false;
        this.isExtends = false;
        this.isSuper = false;

        switch (c) {
            case SignatureVisitor.INSTANCEOF:
                this.isInstance = true;
                break;
            case SignatureVisitor.SUPER:
                this.isSuper = true;
                break;
            case SignatureVisitor.EXTENDS:
                this.isExtends = true;
                break;
        }

        if (this.currentType.size() == 0) {
            return log.traceExit(em, super.visitTypeArgument(c));
        }

        final TypeInfo typeInfo = this.currentType.peek();
        if (typeInfo.typeParameters == null) {
            typeInfo.typeParameters = new ArrayList<>(4);
        }
        return log.traceExit(em, super.visitTypeArgument(c));
    }

    @Override
    public void visitTypeArgument() {
        final EntryMessage m = log.traceEntry("mame={} typeInfo={} currentType={}", this.name, this.typeInfo, this.currentType);
        if (this.currentType.size() == 0) {
            this.visitClassType("?");
            log.traceExit();
            return;
        }

        final TypeInfo typeInfo = new TypeInfo("?", "?");
        final TypeInfo current = this.currentType.peek();
        if (current.typeParameters == null) {
            current.typeParameters = new ArrayList<>(4);
        }
        current.typeParameters.add(typeInfo);
        log.traceExit(m);
    }

    @Override
    public void visitClassType(final String s) {
        final String className = ClassNameUtils.replaceSlash(s);

        final TypeInfo typeInfo = new TypeInfo(className, className);
        if (this.typeInfo == null) {
            this.typeInfo = typeInfo;
            if (this.holdArray) {
                this.typeInfo.isArray = true;
                this.holdArray = false;
            }
        }
        final EntryMessage em = log.traceEntry("s={} name={} typeInfo={} currentType={}", s, this.name, this.typeInfo, this.currentType);

        if (this.currentType.size() == 0) {
            // set main
            this.currentType.push(typeInfo);
        } else {
            final TypeInfo current = this.currentType.peek();
            if (current != null
                    && current.typeParameters != null
                    && isInstance) {

                current.typeParameters.add(typeInfo);
                // swap
                this.currentType.push(typeInfo);
            }
        }
        log.traceExit(em);
    }

    @Override
    public SignatureVisitor visitArrayType() {

        final TypeInfo current = this.currentType.peek();
        if (current != null) {
            current.isArray = true;
        } else {
            // on hold array flag
            this.holdArray = true;
        }
        final EntryMessage em = log.traceEntry("name={} current={} currentType={}", this.name, current, this.currentType);
        return log.traceExit(em, super.visitArrayType());
    }

    @Override
    public void visitBaseType(final char c) {
        final String primitive = ASMReflector.toPrimitive(c);

        final TypeInfo typeInfo = new TypeInfo(primitive, primitive);
        if (this.typeInfo == null) {
            this.typeInfo = typeInfo;
            if (this.holdArray) {
                this.typeInfo.isArray = true;
                this.holdArray = false;
            }
        }

        if (this.currentType.size() == 0) {
            // set main
            this.currentType.push(typeInfo);
        }
        final EntryMessage em = log.traceEntry("c={} name={} typeInfo={} currentType={}", c, this.name, this.typeInfo, this.currentType);
        log.traceExit(em);
    }

    @Override
    public void visitTypeVariable(String typeVariable) {
        final EntryMessage em = log.traceEntry("name={} typeVariable={} typeInfo={} current={}", this.name, typeVariable, this.typeInfo, this.currentType);
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

        if (this.typeInfo == null) {
            this.typeInfo = typeInfo;
            if (this.holdArray) {
                this.typeInfo.isArray = true;
                this.holdArray = false;
            }
        }

        if (this.currentType.size() == 0) {
            // set main
            this.currentType.push(typeInfo);
        } else {
            TypeInfo current = this.currentType.peek();
            if (current != null && current.typeParameters != null) {
                current.typeParameters.add(typeInfo);
                // swap
                this.currentType.push(typeInfo);
            }
        }
        log.traceExit(em);
    }

    @Override
    public void visitEnd() {
        final EntryMessage em = log.traceEntry("name={} typeInfo={} currentType={} ", this.name, this.typeInfo, this.currentType);
        if (this.currentType.size() > 1) {
            this.currentType.pop();
        }
        log.traceExit(em);
    }

    @Override
    public void visitFormalTypeParameter(final String name) {
        final EntryMessage em = log.traceEntry("name={}", name);
        super.visitFormalTypeParameter(name);
        log.traceExit(em);
    }

    @Override
    public void visitInnerClassType(final String name) {
        final EntryMessage em = log.traceEntry("name={} typeInfo={}", name, this.typeInfo);
        this.typeInfo.innerClass = name;
        log.traceExit(em);
    }

    @Override
    public SignatureVisitor visitReturnType() {
        return super.visitReturnType();
    }

    public String getResult() {
        return this.typeInfo.toString();
    }

    Set<String> getTypeParameters() {
        return this.typeParameters;
    }

    void setTypeMap(Map<String, String> typeMap) {
        this.typeMap = typeMap;
    }

    private FieldSignatureVisitor getTopVisitor(FieldSignatureVisitor visitor) {
        if (visitor.parent == null) {
            return visitor;
        }
        return getTopVisitor(visitor.parent);
    }

    private TypeInfo getTypeInfo(String typeVariable) {
        TypeInfo typeInfo;
        if (isSuper) {
            typeInfo = new TypeInfo("? super " + typeVariable, "? super " + typeVariable);
        } else if (isExtends) {
            typeInfo = new TypeInfo("? extends " + typeVariable, "? extends " + typeVariable);
        } else {
            typeInfo = new TypeInfo(typeVariable, typeVariable);
        }
        return typeInfo;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("classTypeParameters", classTypeParameters)
                .add("name", name)
                .add("currentType", currentType)
                .add("typeParameters", typeParameters)
                .add("typeInfo", typeInfo)
                .add("isInstance", isInstance)
                .add("isSuper", isSuper)
                .add("isExtends", isExtends)
                .add("holdArray", holdArray)
                .add("typeMap", typeMap)
                .add("isClassBound", isClassBound)
                .add("isInterfaceBound", isInterfaceBound)
                .add("parent", parent)
                .toString();
    }
}
