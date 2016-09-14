package meghanada.reflect.asm;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import meghanada.reflect.ClassIndex;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class ClassSignatureVisitor extends SignatureVisitor {

    private static Logger log = LogManager.getLogger(ClassSignatureVisitor.class);

    private final Set<ClassInfo> superClasses = new HashSet<>();
    private final ClassInfo classInfo;
    private final boolean isInterface;

    private boolean hasTypeParameter;
    private boolean visitedSuper;
    private boolean visitedInterface;
    private char wildcard;
    private ClassInfo current;

    ClassSignatureVisitor(String name, boolean isInterface) {
        super(Opcodes.ASM5);
        this.classInfo = new ClassInfo(name);
        this.current = this.classInfo;
        this.isInterface = isInterface;
    }

    @Override
    public void visitInnerClassType(String name) {
        // log.debug("# visitInnerClassType name:{}", name);
        super.visitInnerClassType(name);
    }

    @Override
    public void visitFormalTypeParameter(String s) {
        // log.debug("# visitFormalTypeParameter TypeParameter:{}", s);
        if (this.current != null) {
            this.current.addFormalParameter(s);
        }
    }

    @Override
    public SignatureVisitor visitClassBound() {
        // log.debug("# visitClassBound");
        return super.visitClassBound();
    }

    @Override
    public SignatureVisitor visitInterfaceBound() {
        // log.debug("# visitInterfaceBound");
        return super.visitInterfaceBound();
    }

    @Override
    public SignatureVisitor visitParameterType() {
        // log.debug("# visitParameterType");
        return super.visitParameterType();
    }

    @Override
    public void visitBaseType(char descriptor) {
        // log.debug("# visitBaseType descriptor:{}", descriptor);
        super.visitBaseType(descriptor);
    }

    @Override
    public void visitTypeVariable(String s) {
        // log.debug("# visitTypeVariable {} current:{}", s, this.current);
        if (this.visitedSuper || this.visitedInterface) {
            s = ClassNameUtils.CLASS_TYPE_VARIABLE_MARK + s;
        }
        final String tv = s;

        switch (this.wildcard) {
            case SignatureVisitor.INSTANCEOF:
                if (this.current != null) {
                    ClassInfo classInfo2 = new ClassInfo(tv, true);
                    current.addTypeParameter(classInfo2);
                }
                break;
            case SignatureVisitor.SUPER:
                if (this.current != null) {
                    ClassInfo classInfo2 = new ClassInfo("? super " + tv, true);
                    this.current.addTypeParameter(classInfo2);
                }
                break;
            case SignatureVisitor.EXTENDS:
                if (this.current != null) {
                    ClassInfo classInfo2 = new ClassInfo("? extend " + tv, true);
                    this.current.addTypeParameter(classInfo2);
                }
                break;
        }
        // log.debug("# visitTypeVariable {} current:{}", s, this.current);
    }


    @Override
    public SignatureVisitor visitTypeArgument(char wildcard) {
        // log.debug("# visitTypeArgument wildcard:{}", wildcard);
        this.hasTypeParameter = true;
        this.wildcard = wildcard;
        return this;
    }


    @Override
    public void visitClassType(String s) {

        String className = ClassNameUtils.replaceSlash(s);

        // log.debug("# visitClassType class:{}, current:{}", className, this.current);

        if (!className.equals(ClassNameUtils.OBJECT_CLASS)) {
            if (this.hasTypeParameter) {
                // type parameter
                if (this.current != null) {
                    ClassInfo classInfo2 = new ClassInfo(className);
                    this.current.addTypeParameter(classInfo2);
                }
            } else {
                if (this.visitedSuper) {
                    this.current = new ClassInfo(className);
                } else if (this.visitedInterface) {
                    this.current = new ClassInfo(className);
                } else {
                    // log.debug("!! ClassType {}", className);
                    this.current = new ClassInfo(className);
//                this.current.ifPresent(classDesc1 -> {
//                    this.superClasses.add(this.current);
//                });
                }
            }
        }
        // log.debug("# visitClassType class:{}, current:{}", className, this.current);
    }

    @Override
    public SignatureVisitor visitSuperclass() {
        // log.debug("# visitSuperclass current:{}", this.current);
        this.visitedSuper = true;
        this.current = null;
        return this;
    }

    @Override
    public SignatureVisitor visitInterface() {
        // log.debug("# visitedInterface current:{}", this.current);
        this.visitedInterface = true;
        this.current = null;
        return this;
    }

    @Override
    public void visitEnd() {
        // log.debug("# visitEnd {}", this.current);

        if (this.visitedSuper) {
            this.visitedSuper = false;
            if (this.current != null) {
                // log.debug("# add super:{}", classDesc1);
                this.superClasses.add(this.current);
            }
        } else if (this.visitedInterface) {
            this.visitedInterface = false;
            if (this.current != null) {
                // log.debug("# add interface:{}", classDesc1);
                this.superClasses.add(this.current);
            }
        }
        this.hasTypeParameter = false;
        super.visitEnd();
    }

    List<String> getTypeParameters() {
        List<String> params = new ArrayList<>(4);
        if (this.classInfo.formalTypeParameters != null) {
            params.addAll(this.classInfo.formalTypeParameters);
        }
        return params;
    }

    public String getName() {
        return this.classInfo.getName();
    }

    ClassIndex getClassIndex() {
        final ClassIndex classIndex = new ClassIndex(
                this.getName(),
                this.getTypeParameters(),
                this.superClasses
                        .stream()
                        .map(ClassInfo::toString).collect(Collectors.toList())
        );
        classIndex.isInterface = this.isInterface;
        return classIndex;
    }

    Set<ClassInfo> getSuperClasses() {
        return superClasses;
    }

    static class ClassInfo {

        private final String name;
        private final boolean typeVariable;
        private List<String> formalTypeParameters;
        private List<ClassInfo> typeParameters;

        ClassInfo(String name) {
            this(name, false);
        }

        ClassInfo(String name, boolean typeVariable) {
            this.name = name;
            this.typeVariable = typeVariable;
        }

        void addFormalParameter(String p) {
            if (this.formalTypeParameters == null) {
                this.formalTypeParameters = new ArrayList<>();
            }
            this.formalTypeParameters.add(p);
        }

        void addTypeParameter(ClassInfo c) {
            if (this.typeParameters == null) {
                this.typeParameters = new ArrayList<>();
            }
            if (this.typeParameters.size() > 0) {
                ClassInfo last = this.typeParameters.get(this.typeParameters.size() - 1);
                if (!last.typeVariable) {
                    last.addTypeParameter(c);
                    return;
                }
            }
            this.typeParameters.add(c);
        }

        public String getName() {
            return name;
        }

        public String getClassName() {
            if (this.formalTypeParameters != null && this.formalTypeParameters.size() > 0) {
                StringBuilder sb = new StringBuilder(this.name);
                sb.append('<');
                return Joiner.on(", ").appendTo(sb, this.formalTypeParameters).append('>').toString();
            }
            if (this.typeParameters != null && this.typeParameters.size() > 0) {
                StringBuilder sb = new StringBuilder(this.name);
                sb.append('<');
                return Joiner.on(", ").appendTo(sb, this.typeParameters).append('>').toString();
            }
            return this.name;
        }

        @Override
        public String toString() {
            return this.getClassName();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClassInfo classInfo = (ClassInfo) o;
            return typeVariable == classInfo.typeVariable
                    && com.google.common.base.Objects.equal(name, classInfo.name)
                    && Objects.equal(formalTypeParameters, classInfo.formalTypeParameters)
                    && Objects.equal(typeParameters, classInfo.typeParameters);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name, formalTypeParameters, typeParameters, typeVariable);
        }
    }
}
