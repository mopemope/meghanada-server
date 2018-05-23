package meghanada.reflect;

import static java.util.Objects.nonNull;
import static meghanada.index.IndexableWord.Field.C_BINARY;
import static meghanada.index.IndexableWord.Field.C_COMPLETION;
import static meghanada.index.IndexableWord.Field.C_DECLARING_CLASS;
import static meghanada.index.IndexableWord.Field.C_MEMBER_TYPE;
import static meghanada.index.IndexableWord.Field.C_MODIFIER;
import static org.apache.lucene.document.Field.Index.ANALYZED;
import static org.apache.lucene.document.Field.Store.NO;
import static org.apache.lucene.document.Field.Store.YES;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import meghanada.store.Serializer;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

public abstract class MemberDescriptor
    implements CandidateUnit, Cloneable, Comparable<MemberDescriptor>, Serializable {

  static final Pattern TRIM_RE = Pattern.compile("<[\\w ?,]+>");
  private static final long serialVersionUID = -3673903957102734585L;

  public String declaringClass;
  public String name;
  public Map<String, String> typeParameterMap;
  public transient boolean showStaticClassName;
  MemberType memberType;
  String modifier;
  String returnType;
  boolean hasDefault;
  Set<String> typeParameters;
  private transient String extra = "";

  public abstract List<String> getParameters();

  public abstract String getSig();

  public abstract String getRawReturnType();

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public String getType() {
    return this.memberType.name();
  }

  public String getDeclaringClass() {
    return this.declaringClass;
  }

  public void setDeclaringClass(final String declaringClass) {
    this.declaringClass = declaringClass;
  }

  public String returnType() {
    return this.returnType;
  }

  public boolean hasDefault() {
    return this.hasDefault;
  }

  public boolean hasTypeParameters() {
    return nonNull(this.typeParameters) && !this.typeParameters.isEmpty();
  }

  protected String renderTypeParameters(final String str, boolean formalType) {
    String temp = str;
    if (this.typeParameterMap.size() > 0) {
      for (Map.Entry<String, String> entry : this.typeParameterMap.entrySet()) {
        final String k = entry.getKey();
        final String v = entry.getValue();
        temp = StringUtils.replace(temp, ClassNameUtils.CLASS_TYPE_VARIABLE_MARK + k, v);
        if (formalType) {
          // follow intellij
          temp = StringUtils.replace(temp, ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK + k, v);
        }
      }
    } else {
      for (String entry : this.typeParameters) {
        temp =
            StringUtils.replace(
                temp, ClassNameUtils.CLASS_TYPE_VARIABLE_MARK + entry, ClassNameUtils.OBJECT_CLASS);
        if (formalType) {
          // follow intellij
          temp =
              StringUtils.replace(
                  temp,
                  ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK + entry,
                  ClassNameUtils.OBJECT_CLASS);
        }
      }

      if (!this.modifier.contains("static ")) {
        temp = TRIM_RE.matcher(temp).replaceAll("");
      }
    }
    return StringUtils.replace(temp, ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK, "").trim();
  }

  public void clearTypeParameterMap() {
    if (nonNull(this.typeParameterMap)) {
      this.typeParameterMap.clear();
    }
  }

  private boolean containsTypeParameter(final String typeParameter) {
    return nonNull(this.typeParameters) && this.typeParameters.contains(typeParameter);
  }

  public void putTypeParameter(final String t, final String real) {
    if (containsTypeParameter(t)) {
      this.typeParameterMap.put(t, real);
    }
  }

  public boolean matchType(final MemberType memberType) {
    return this.memberType.equals(memberType);
  }

  public Set<String> getTypeParameters() {
    return typeParameters;
  }

  public void setTypeParameters(Set<String> typeParameters) {
    this.typeParameters = typeParameters;
  }

  public boolean isStatic() {
    return this.modifier.contains("static");
  }

  public boolean isAbstract() {
    return this.modifier.contains("abstract");
  }

  public boolean isPrivate() {
    return this.modifier.contains("private");
  }

  public boolean isPublic() {
    return this.modifier.contains("public");
  }

  public boolean fixedReturnType() {
    String temp = this.returnType;
    if (this.typeParameterMap.size() > 0) {
      for (Map.Entry<String, String> entry : this.typeParameterMap.entrySet()) {
        final String k = entry.getKey();
        final String v = ClassNameUtils.removeCapture(entry.getValue());

        if (k.equals(v)) {
          return false;
        }
        if (v.contains("extends " + k) || v.contains("super " + k)) {
          return false;
        }
        final String replaceKey = ClassNameUtils.CLASS_TYPE_VARIABLE_MARK + k;
        temp = StringUtils.replace(temp, replaceKey, v);
      }
    } else {
      for (String entry : this.typeParameters) {
        temp =
            StringUtils.replace(
                temp, ClassNameUtils.CLASS_TYPE_VARIABLE_MARK + entry, ClassNameUtils.OBJECT_CLASS);
      }

      if (!this.modifier.contains("static")) {
        temp = TRIM_RE.matcher(temp).replaceAll("");
      }
    }
    return !temp.contains(ClassNameUtils.CLASS_TYPE_VARIABLE_MARK)
        && !temp.contains(ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MemberDescriptor that = (MemberDescriptor) o;
    return hasDefault == that.hasDefault
        && Objects.equal(declaringClass, that.declaringClass)
        && Objects.equal(name, that.name)
        && memberType == that.memberType
        && Objects.equal(modifier, that.modifier)
        && Objects.equal(returnType, that.returnType)
        && Objects.equal(typeParameters, that.typeParameters)
        && Objects.equal(typeParameterMap, that.typeParameterMap);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        declaringClass,
        name,
        memberType,
        modifier,
        returnType,
        hasDefault,
        typeParameters,
        typeParameterMap);
  }

  @Override
  public synchronized MemberDescriptor clone() {
    final MemberDescriptor descriptor;
    try {
      descriptor = (MemberDescriptor) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new UnsupportedOperationException(e);
    }
    descriptor.typeParameterMap = new HashMap<>(this.typeParameterMap);
    return descriptor;
  }

  public MemberType getMemberType() {
    return memberType;
  }

  @Override
  public int compareTo(@Nonnull MemberDescriptor other) {

    if (this.isStatic() && other.isStatic()) {
      return this.compareType(other, true);
    }
    if (this.isStatic()) {
      return -1;
    }
    if (other.isStatic()) {
      return 1;
    }
    return this.compareType(other, false);
  }

  private int compareType(MemberDescriptor other, boolean isStatic) {

    if (this.getMemberType() == other.getMemberType()) {
      if (this.getMemberType() == CandidateUnit.MemberType.FIELD) {
        if (this.isPublic()) {
          return -1;
        }
        if (other.isPublic()) {
          return 1;
        }
      }
      if (isStatic && this.getMemberType() == CandidateUnit.MemberType.METHOD) {
        if (this.isPublic()) {
          return -1;
        }
        if (other.isPublic()) {
          return 1;
        }
      }

      return this.getName().compareTo(other.getName());
    }

    if (this.getMemberType() == CandidateUnit.MemberType.FIELD) {
      return -1;
    }
    if (other.getMemberType() == CandidateUnit.MemberType.FIELD) {
      return 1;
    }

    if (this.getMemberType() == CandidateUnit.MemberType.CONSTRUCTOR) {
      return -1;
    }
    if (other.getMemberType() == CandidateUnit.MemberType.CONSTRUCTOR) {
      return 1;
    }

    return this.getName().compareTo(other.getName());
  }

  @Override
  public String getExtra() {
    return this.extra;
  }

  public void setExtra(String extra) {
    this.extra = extra;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("declaringClass", declaringClass)
        .add("name", name)
        .add("memberType", memberType)
        .add("modifier", modifier)
        .add("returnType", returnType)
        .add("hasDefault", hasDefault)
        .add("typeParameters", typeParameters)
        .add("typeParameterMap", typeParameterMap)
        .toString();
  }

  public Document toDocument() {
    Document doc = new Document();
    doc.add(new Field(C_BINARY.getName(), Serializer.asByte(this)));
    doc.add(new Field(C_DECLARING_CLASS.getName(), getDeclaringClass(), YES, ANALYZED));
    doc.add(new Field(C_COMPLETION.getName(), getName(), YES, ANALYZED));
    doc.add(new Field(C_MEMBER_TYPE.getName(), getMemberType().name(), NO, ANALYZED));
    doc.add(new Field(C_MODIFIER.getName(), modifier.trim(), NO, ANALYZED));
    return doc;
  }
}
