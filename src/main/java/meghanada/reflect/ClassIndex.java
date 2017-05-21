package meghanada.reflect;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import meghanada.reflect.CandidateUnit.MemberType;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassNameUtils;

public class ClassIndex implements CandidateUnit, Cloneable {

  // fqcn
  public String declaration;
  public List<String> typeParameters;
  public List<String> supers;
  public boolean isInterface;
  public boolean isAnnotation;
  public boolean functional;
  public String name;

  private MemberType memberType = MemberType.CLASS;

  public ClassIndex() {}

  public ClassIndex(
      final String declaration, final List<String> typeParameters, final List<String> supers) {
    this.declaration = declaration;
    this.typeParameters = typeParameters;
    this.supers = supers;
    this.name = ClassNameUtils.getSimpleName(this.declaration);
  }

  public static ClassIndex createPackage(final String pkg) {
    return new ClassIndex(pkg, Collections.emptyList(), Collections.emptyList());
  }

  public static ClassIndex createClass(final String fqcn) {
    return new ClassIndex(fqcn, Collections.emptyList(), Collections.emptyList());
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public String getType() {
    return this.memberType.toString();
  }

  public void setMemberType(final MemberType memberType) {
    this.memberType = memberType;
  }

  @Override
  public String getDeclaration() {
    return ClassNameUtils.replaceInnerMark(this.declaration);
  }

  @Override
  public String getDisplayDeclaration() {
    final StringBuilder sb = new StringBuilder(this.declaration);
    if (this.typeParameters != null && this.typeParameters.size() > 0) {
      sb.append('<');
      Joiner.on(", ").appendTo(sb, this.typeParameters).append('>');
    }
    return ClassNameUtils.replaceInnerMark(sb.toString());
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

  private boolean isImplements(final String fqcn) {
    final String className = ClassNameUtils.removeTypeParameter(this.declaration);

    if (className.equals(fqcn)) {
      return true;
    }
    final CachedASMReflector reflector = CachedASMReflector.getInstance();

    final Optional<String> result =
        this.supers
            .stream()
            .filter(
                s -> {
                  final String name = ClassNameUtils.removeTypeParameter(s);
                  return reflector
                      .containsClassIndex(name)
                      .map(classIndex -> classIndex.isImplements(fqcn))
                      .orElse(false);
                })
            .findFirst();
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
