package meghanada.reflect;

import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.annotation.Nullable;
import meghanada.utils.ClassNameUtils;

public class FieldDescriptor extends MemberDescriptor implements Serializable {

  private static final long serialVersionUID = 4643756658266028450L;

  public FieldDescriptor() {}

  public FieldDescriptor(String declaringClass, String name, String modifier, String returnType) {
    this.declaringClass = declaringClass;
    this.name = name;
    this.memberType = MemberType.FIELD;
    this.modifier = modifier;
    this.returnType = returnType;
    this.typeParameterMap = new HashMap<>(4);
  }

  public static CandidateUnit createVar(String declaringClass, String name, String returnType) {
    FieldDescriptor descriptor = new FieldDescriptor(declaringClass, name, "", returnType);
    descriptor.memberType = MemberType.VAR;
    return descriptor;
  }

  @Override
  public String getDeclaration() {
    StringBuilder sb = new StringBuilder(32);
    if (this.modifier != null && this.modifier.length() > 0) {
      sb.append(this.modifier).append(' ');
    }
    return sb.append(this.getDisplayDeclaration()).toString();
  }

  @Override
  public String getDisplayDeclaration() {
    final String returnType = this.getReturnType();
    if (returnType == null) {
      return "";
    }
    final String rt = ClassNameUtils.getSimpleName(returnType) + ' ' + this.name;
    return ClassNameUtils.replaceInnerMark(rt);
  }

  @Nullable
  @Override
  public String getReturnType() {
    if (this.returnType != null) {
      String rt = this.returnType;
      if (this.hasTypeParameters()) {
        rt = super.renderTypeParameters(rt, false);
      }
      return rt;
    }
    return null;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("declaringClass", declaringClass)
        .add("name", name)
        .add("returnType", returnType)
        .add("info", getDeclaration())
        .toString();
  }

  @Override
  public List<String> getParameters() {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public String getRawReturnType() {
    if (this.returnType != null) {
      final String rt = this.returnType;
      if (this.hasTypeParameters()) {
        return super.renderTypeParameters(rt, false);
      }
      return rt;
    }
    return null;
  }

  @Override
  public String getSig() {
    return ClassNameUtils.removeTypeParameter(this.returnType);
  }
}
