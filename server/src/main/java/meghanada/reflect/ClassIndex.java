package meghanada.reflect;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityId;
import jetbrains.exodus.entitystore.StoreTransaction;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.store.Storable;
import meghanada.utils.ClassNameUtils;

public class ClassIndex implements CandidateUnit, Cloneable, Serializable, Storable {

  public static final String ENTITY_TYPE = "ClassIndex";
  public static final String FILE_ENTITY_TYPE = "ClassIndexFile";
  private static final long serialVersionUID = 4833311903131990013L;

  // fqcn
  private final String declaration;
  private final List<String> typeParameters;
  private final List<String> supers;
  public transient boolean loaded;
  private boolean isInterface;
  private boolean isAnnotation;
  private boolean functional;
  private String name;
  private String filePath;
  private MemberType memberType = MemberType.CLASS;
  private EntityId entityID;

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

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getType() {
    return this.memberType.toString();
  }

  @Override
  public String getExtra() {
    return "";
  }

  @Override
  public String getDeclaration() {
    return ClassNameUtils.replaceInnerMark(this.declaration);
  }

  @Override
  public String getDisplayDeclaration() {
    final StringBuilder sb = new StringBuilder(this.declaration);
    if (nonNull(this.typeParameters) && !this.typeParameters.isEmpty()) {
      sb.append('<');
      Joiner.on(", ").appendTo(sb, this.typeParameters).append('>');
    }
    return ClassNameUtils.replaceInnerMark(sb.toString());
  }

  @Override
  public String getReturnType() {
    StringBuilder sb = new StringBuilder(this.declaration);
    if (nonNull(this.typeParameters) && !this.typeParameters.isEmpty()) {
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
    if (isNull(o) || getClass() != o.getClass()) {
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

  @Override
  public String getStoreId() {
    return this.declaration;
  }

  @Override
  public String getEntityType() {
    return ENTITY_TYPE;
  }

  @Override
  public void store(StoreTransaction txn, Entity entity) {
    entity.setProperty("declaration", this.declaration);
    entity.setProperty("name", this.name);
    if (isNull(this.filePath)) {
      // use test only
      entity.setProperty("filePath", "");
    } else {
      entity.setProperty("filePath", this.filePath);
    }
    entity.setProperty("isAnnotation", this.isAnnotation);
    entity.setProperty("isInterface", this.isInterface);
    entity.setProperty("functional", this.functional);
  }

  @Override
  public void onSuccess(Entity entity) {
    this.entityID = entity.getId();
  }

  @Override
  public EntityId getEntityId() {
    return entityID;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public void addSuper(String clazz) {
    this.supers.add(clazz);
  }

  public List<String> getSupers() {
    return supers;
  }

  public boolean isInterface() {
    return isInterface;
  }

  public void setInterface(boolean anInterface) {
    isInterface = anInterface;
  }

  public boolean isAnnotation() {
    return isAnnotation;
  }

  public void setAnnotation(boolean annotation) {
    isAnnotation = annotation;
  }

  public boolean isFunctional() {
    return functional;
  }

  public void setFunctional(boolean functional) {
    this.functional = functional;
  }

  public MemberType getMemberType() {
    return memberType;
  }

  public void setMemberType(final MemberType memberType) {
    this.memberType = memberType;
  }

  public void setEntityID(EntityId entityID) {
    this.entityID = entityID;
  }

  public boolean isInnerClass() {
    return this.declaration.contains(ClassNameUtils.INNER_MARK);
  }
}
