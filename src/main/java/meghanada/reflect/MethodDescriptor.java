package meghanada.reflect;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MethodDescriptor extends MemberDescriptor implements Serializable {

    private static final long serialVersionUID = -8041709346815449477L;
    private static final Logger log = LogManager.getLogger(MethodDescriptor.class);

    public List<MethodParameter> parameters;
    public String[] exceptions;
    public String formalType;

    public MethodDescriptor() {

    }

    public MethodDescriptor(final String declaringClass, final String name, final String modifier, final List<MethodParameter> parameters, final String[] exceptions, final String returnType, final boolean hashDefault) {
        this.declaringClass = declaringClass;
        this.name = name;
        if (modifier == null) {
            this.modifier = "";
        } else {
            this.modifier = modifier;
        }
        this.memberType = MemberType.METHOD;
        this.parameters = parameters;
        this.exceptions = exceptions;
        this.returnType = returnType;
        this.hasDefault = hashDefault;
        this.typeParameterMap = new HashMap<>(2);
    }

    private String getException() {
        StringBuilder exBuilder = new StringBuilder(32);
        if (exceptions != null && exceptions.length > 0) {
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
                return renderTypeParameters(s, formalType != null);
            }
            return s;
        } else {
            String s = this.getMethodDeclaration();
            if (this.hasTypeParameters()) {
                return renderTypeParameters(s, formalType != null);
            }
            return s;
        }
    }

    private StringBuilder appendParameters(final StringBuilder sb, final boolean simple) {

        if (this.parameters != null) {
            final Iterator<MethodParameter> iterator = this.parameters.iterator();

            while (iterator.hasNext()) {
                sb.append(iterator.next().getParameter(simple));
                if (iterator.hasNext()) {
                    sb.append(", ");
                }
            }
        }
        return sb;
    }

    private StringBuilder appendParameterTypes(final StringBuilder sb) {

        if (this.parameters != null) {
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
        appendParameters(sb, true);
        return sb.append(')').toString();
    }

    private String getConstructorDeclaration() {
        final StringBuilder sb = new StringBuilder(64);

        if (this.modifier != null && modifier.length() > 0) {
            sb.append(this.modifier).append(' ');
        }
        sb.append(this.getDisplayDeclaration()).append(' ');
        return sb.append(this.getException()).toString();
    }

    private String getMethodDisplayDeclaration() {
        final StringBuilder sb = new StringBuilder(ClassNameUtils.getSimpleName(this.returnType));
        sb.append(' ').append(this.name).append('(');
        return appendParameters(sb, true).append(')').toString();
    }

    private String getMethodDeclaration() {
        final StringBuilder sb = new StringBuilder(64);
        if (this.modifier != null && modifier.length() > 0) {
            sb.append(this.modifier).append(' ');
        }
        if (this.formalType != null) {
            sb.append(this.formalType).append(' ');
        }
        sb.append(this.getDisplayDeclaration()).append(' ');
        return sb.append(this.getException()).toString();
    }

    @Override
    public String getDisplayDeclaration() {
        if (this.memberType.equals(MemberType.CONSTRUCTOR)) {
            final String s = this.getConstructorDisplayDeclaration();
            if (this.hasTypeParameters()) {
                return renderTypeParameters(s, formalType != null);
            }
            return s;
        } else {
            final String s = this.getMethodDisplayDeclaration();
            if (this.hasTypeParameters()) {
                return renderTypeParameters(s, formalType != null);
            }
            return s;
        }
    }

    @Override
    public String getReturnType() {
        if (this.returnType != null) {
            final String rt = ClassNameUtils.replaceInnerMark(this.returnType);
            if (this.hasTypeParameters()) {
                return this.renderTypeParameters(rt, formalType != null);
            }
            return rt;
        }
        return null;
    }

    @Override
    protected String renderTypeParameters(final String template, boolean formalType) {
        final EntryMessage entryMessage = log.traceEntry("template={}, formalType={} typeParameterMap={} typeParameters={}", template, formalType, typeParameterMap, typeParameters);
        String temp = template;
        if (this.typeParameterMap.size() > 0) {
            for (final Map.Entry<String, String> entry : this.typeParameterMap.entrySet()) {
                final String k = entry.getKey();
                final String v = entry.getValue();
                temp = ClassNameUtils.replace(temp, ClassNameUtils.CLASS_TYPE_VARIABLE_MARK + k, v);
                if (formalType) {
                    // follow intellij
                    temp = ClassNameUtils.replace(temp, ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK + k, v);
                }
            }
        } else {

            for (final String entry : this.typeParameters) {
                temp = ClassNameUtils.replace(temp, ClassNameUtils.CLASS_TYPE_VARIABLE_MARK + entry, ClassNameUtils.OBJECT_CLASS);
                if (formalType) {
                    // follow intellij
                    temp = ClassNameUtils.replace(temp, ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK + entry, ClassNameUtils.OBJECT_CLASS);
                }
            }

            if (!this.modifier.contains("static ")) {
                temp = TRIM_RE.matcher(temp).replaceAll("");
            }
        }
        final String rendered = ClassNameUtils.replace(temp, ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK, "").trim();
        return log.traceExit(entryMessage, rendered);
    }

    @Override
    public String getRawReturnType() {
        if (this.returnType != null && this.hasTypeParameters()) {
            return renderTypeParameters(this.returnType, formalType != null);
        }
        return returnType;
    }


    public boolean containsTypeParameter(String typeParameter) {
        return this.typeParameters != null && this.typeParameters.contains(typeParameter);
    }

    public void clearTypeParameterMap() {
        typeParameterMap.clear();
    }

    public Map<String, String> getTypeParameterMap() {
        return typeParameterMap;
    }

    @Override
    public List<String> getParameters() {
        if (this.parameters == null) {
            return null;
        }
        return this.parameters
                .stream()
                .map(p -> renderTypeParameters(p.type, formalType != null))
                .collect(Collectors.toList());
    }

    @Override
    public String getSig() {
        final List<String> plist = this.parameters
                .stream()
                .map(p -> ClassNameUtils.removeTypeParameter(p.type))
                .collect(Collectors.toList());

        return this.name + "::" + plist;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("declaringClass", declaringClass)
                .add("name", name)
                .add("parameters", parameters)
                .add("returnType", returnType)
                .add("exceptions", exceptions)
                .add("hasDefault", hasDefault)
                .add("typeParameters", typeParameters)
                .add("info", getDeclaration())
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MemberDescriptor)) {
            return false;
        }
        MethodDescriptor that = (MethodDescriptor) o;
        return Objects.equal(name, that.name)
                && memberType == that.memberType
                && Objects.equal(modifier, that.modifier)
                && Objects.equal(parameters, that.parameters)
                && Objects.equal(exceptions, that.exceptions)
                && Objects.equal(returnType, that.returnType)
                && Objects.equal(typeParameters, that.typeParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, memberType, modifier, parameters, exceptions, returnType, typeParameters);
    }

    public String getSignature(final boolean includeReturn) {

        final StringBuilder sb = new StringBuilder();
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
