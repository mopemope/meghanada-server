package meghanada.reflect;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassNameUtils;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ClassIndex implements CandidateUnit, Serializable, Cloneable {

    private static final long serialVersionUID = 4643906658266028478L;

    // fqcn
    public String declaration;
    public List<String> typeParameters;
    public List<String> supers;
    public boolean isInterface;
    public boolean isAnnotation;
    public boolean functional;
    public String name;

    public ClassIndex() {
    }

    public ClassIndex(final String declaration, final List<String> typeParameters, final List<String> supers) {
        this.declaration = declaration;
        this.typeParameters = typeParameters;
        this.supers = supers;
        this.name = ClassNameUtils.getSimpleName(this.declaration);
    }

    public static ClassIndex createPackage(String pkg) {
        return new ClassIndex(pkg, Collections.emptyList(), Collections.emptyList());
    }

    public static ClassIndex createClass(String fqcn) {
        return new ClassIndex(fqcn, Collections.emptyList(), Collections.emptyList());
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getType() {
        return MemberType.CLASS.toString();
    }

    @Override
    public String getDeclaration() {
        if (!this.declaration.contains(ClassNameUtils.INNER_MARK)) {
            return this.declaration;
        }
        return ClassNameUtils.replaceInnerMark(this.declaration);
    }

    @Override
    public String getDisplayDeclaration() {
        StringBuilder sb = new StringBuilder(ClassNameUtils.replaceInnerMark(this.declaration));
        if (this.typeParameters != null && this.typeParameters.size() > 0) {
            sb.append('<');
            Joiner.on(", ").appendTo(sb, this.typeParameters).append('>');
        }
        return sb.toString();
    }

    @Override
    public String getReturnType() {
        StringBuilder sb = new StringBuilder(this.declaration);
        if (this.typeParameters != null && this.typeParameters.size() > 0) {
            sb.append('<');
            Joiner.on(", ").appendTo(sb, this.typeParameters).append('>');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return declaration;
    }

    public String getRawDeclaration() {
        return this.declaration;
    }

    public List<String> getTypeParameters() {
        return typeParameters;
    }

    public String getPackage() {
        return ClassNameUtils.getPackage(this.declaration);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClassIndex that = (ClassIndex) o;
        return Objects.equal(declaration, that.declaration);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(declaration);
    }

    public boolean isImplements(final String fqcn) {
        final String className = ClassNameUtils.removeTypeParameter(this.declaration);

        if (className.equals(fqcn)) {
            return true;
        }
        final CachedASMReflector reflector = CachedASMReflector.getInstance();

        final Optional<String> result = this.supers.stream().filter(s -> {
            final String name = ClassNameUtils.removeTypeParameter(s);
            return reflector.containsClassIndex(name)
                    .map(classIndex -> classIndex.isImplements(fqcn)).orElse(false);
        }).findFirst();
        return result.isPresent();
    }

    @Override
    public synchronized ClassIndex clone() {
        final ClassIndex ci;
        try {
            ci = (ClassIndex) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new UnsupportedOperationException(e);
        }
        return ci;
    }

}
