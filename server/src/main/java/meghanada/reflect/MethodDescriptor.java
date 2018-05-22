package meghanada.reflect;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

public class MethodDescriptor extends MemberDescriptor {

  private static final Logger log = LogManager.getLogger(MethodDescriptor.class);
  private static final long serialVersionUID = 2705480824413845391L;

  private final List<MethodParameter> parameters;
  public String formalType;
  public boolean hasVarargs;
  private String[] exceptions;

  public MethodDescriptor(
      final String declaringClass,
      final String name,
      @Nullable final String modifier,
      final List<MethodParameter> parameters,
      final String[] exceptions,
      final String returnType,
      final boolean hashDefault,
      final MemberType memberType) {

    this.declaringClass = declaringClass;
    this.name = name.trim();
    if (isNull(modifier)) {
      this.modifier = "";
    } else {
      this.modifier = modifier.trim();
    }
    this.memberType = memberType;
    this.parameters = parameters;
    this.exceptions = exceptions;
    this.returnType = returnType;
    this.hasDefault = hashDefault;
    this.typeParameterMap = new HashMap<>(2);
  }

  private String getException() {
    StringBuilder exBuilder = new StringBuilder(32);
    if (nonNull(exceptions) && exceptions.length > 0) {
      exBuilder.append("throws ");
      for (String ex : exceptions) {
        if (exBuilder.length() > 7) {
          exBuilder.append(", ");
        }
        ex = ClassNameUtils.replaceSlash(ex);
        exBuilder.append(ClassNameUtils.getSimpleName(ex));
      }
    }
    return exBuilder.toString();
  }

  @Override
  public String getDeclaration() {
    if (this.memberType.equals(MemberType.CONSTRUCTOR)) {
      String s = this.getConstructorDeclaration();
      if (this.hasTypeParameters()) {
        s = renderTypeParameters(s, nonNull(formalType));
      }
      return ClassNameUtils.replaceInnerMark(s);
    } else {
      String s = this.getMethodDeclaration();
      if (this.hasTypeParameters()) {
        s = renderTypeParameters(s, nonNull(formalType));
      }
      return ClassNameUtils.replaceInnerMark(s);
    }
  }

  private StringBuilder appendParameters(final StringBuilder sb) {

    if (nonNull(this.parameters)) {
      final Iterator<MethodParameter> iterator = this.parameters.iterator();

      while (iterator.hasNext()) {
        sb.append(iterator.next().getParameter(true));
        if (iterator.hasNext()) {
          sb.append(", ");
        }
      }
    }
    return sb;
  }

  private StringBuilder appendParameterTypes(final StringBuilder sb) {

    if (nonNull(this.parameters)) {
      final Iterator<MethodParameter> iterator = this.parameters.iterator();

      while (iterator.hasNext()) {
        sb.append(ClassNameUtils.removeTypeParameter(iterator.next().getType()));
        if (iterator.hasNext()) {
          sb.append(", ");
        }
      }
    }
    return sb;
  }

  private String getConstructorDisplayDeclaration() {
    String simpleName = ClassNameUtils.getSimpleName(this.name);

    final StringBuilder sb = new StringBuilder(simpleName);
    sb.append('(');
    return appendParameters(sb).append(')').toString();
  }

  private String getConstructorDeclaration() {
    final StringBuilder sb = new StringBuilder(64);

    if (nonNull(this.modifier) && !modifier.isEmpty()) {
      sb.append(this.modifier).append(' ');
    }
    sb.append(this.getDisplayDeclaration()).append(' ');
    return sb.append(this.getException()).toString();
  }

  private String getMethodDisplayDeclaration() {
    final StringBuilder sb = new StringBuilder(ClassNameUtils.getSimpleName(this.returnType));
    sb.append(' ');
    if (showStaticClassName) {
      sb.append(ClassNameUtils.getSimpleName(getDeclaringClass())).append('.');
    }
    sb.append(this.name).append('(');
    return appendParameters(sb).append(')').toString();
  }

  private String getMethodDeclaration() {
    final StringBuilder sb = new StringBuilder(64);
    if (nonNull(this.modifier) && !this.modifier.isEmpty()) {
      sb.append(this.modifier).append(' ');
    }
    if (nonNull(this.formalType)) {
      sb.append(this.formalType).append(' ');
    }
    sb.append(this.getDisplayDeclaration()).append(' ');
    return sb.append(this.getException()).toString();
  }

  @Override
  public String getDisplayDeclaration() {
    if (this.memberType.equals(MemberType.CONSTRUCTOR)) {
      String s = this.getConstructorDisplayDeclaration();
      if (this.hasTypeParameters()) {
        s = renderTypeParameters(s, nonNull(formalType));
      }
      return ClassNameUtils.replaceInnerMark(s);
    } else {
      String s = this.getMethodDisplayDeclaration();
      if (this.hasTypeParameters()) {
        s = renderTypeParameters(s, nonNull(formalType));
      }
      return ClassNameUtils.replaceInnerMark(s).trim();
    }
  }

  @Nullable
  @Override
  public String getReturnType() {
    if (nonNull(this.returnType)) {
      final String rt = this.returnType;
      if (this.hasTypeParameters()) {
        return this.renderTypeParameters(rt, nonNull(formalType));
      }
      return rt;
    }
    return null;
  }

  @Override
  protected String renderTypeParameters(final String template, final boolean formalType) {
    final EntryMessage entryMessage =
        log.traceEntry(
            "template={}, formalType={} typeParameterMap={} typeParameters={}",
            template,
            formalType,
            typeParameterMap,
            typeParameters);
    String temp = template;
    if (this.typeParameterMap.size() > 0) {
      for (final Map.Entry<String, String> entry : this.typeParameterMap.entrySet()) {
        final String k = entry.getKey();
        final String v = entry.getValue();
        temp = StringUtils.replace(temp, ClassNameUtils.CLASS_TYPE_VARIABLE_MARK + k, v);
        if (formalType) {
          // follow intellij
          temp = StringUtils.replace(temp, ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK + k, v);
        }
      }
    } else {

      for (final String entry : this.typeParameters) {
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

      if (!this.modifier.contains("static")) {
        temp = TRIM_RE.matcher(temp).replaceAll("");
      }
    }
    final String rendered =
        StringUtils.replace(temp, ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK, "").trim();
    return log.traceExit(entryMessage, rendered);
  }

  @Override
  public String getRawReturnType() {
    if (nonNull(this.returnType) && this.hasTypeParameters()) {
      return renderTypeParameters(this.returnType, nonNull(formalType));
    }
    return returnType;
  }

  public boolean containsTypeParameter(String typeParameter) {
    return nonNull(this.typeParameters) && this.typeParameters.contains(typeParameter);
  }

  @Override
  public void clearTypeParameterMap() {
    typeParameterMap.clear();
  }

  public Map<String, String> getTypeParameterMap() {
    return typeParameterMap;
  }

  @Override
  public List<String> getParameters() {
    if (isNull(this.parameters)) {
      return Collections.emptyList();
    }
    return this.parameters
        .stream()
        .map(p -> renderTypeParameters(p.getType(), nonNull(formalType)))
        .collect(Collectors.toList());
  }

  @Override
  public String getSig() {
    final List<String> plist =
        this.parameters
            .stream()
            .map(p -> ClassNameUtils.removeTypeParameter(p.type))
            .collect(Collectors.toList());

    return this.name + "::" + plist;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("parameters", parameters)
        .add("exceptions", exceptions)
        .add("formalType", formalType)
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MethodDescriptor)) return false;
    if (!super.equals(o)) return false;
    MethodDescriptor that = (MethodDescriptor) o;
    return Objects.equal(parameters, that.parameters) && Objects.equal(formalType, that.formalType);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), parameters, formalType);
  }

  public String getSignature(final boolean includeReturn) {

    final StringBuilder sb = new StringBuilder(16);
    if (includeReturn) {
      sb.append(this.returnType).append(' ');
    }
    sb.append(this.name).append('(');
    return appendParameterTypes(sb).append(')').toString();
  }

  public String rawDeclaration() {
    if (this.memberType.equals(MemberType.CONSTRUCTOR)) {
      String s = this.getConstructorDeclaration();
      if (this.hasTypeParameters()) {
        return s;
      }
      return s;
    } else {
      String s = this.getMethodDeclaration();
      if (this.hasTypeParameters()) {
        return s;
      }
      return s;
    }
  }
}
