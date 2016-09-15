package meghanada.reflect;

import com.google.common.base.Objects;
import meghanada.utils.ClassNameUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public abstract class MemberDescriptor implements CandidateUnit, Cloneable {

    protected static final Pattern TRIM_RE = Pattern.compile("<[\\w \\?,]+>");
    public String declaringClass;
    public String name;
    public MemberType memberType;
    public String modifier;
    public String returnType;
    public boolean hasDefault;
    public Set<String> typeParameters;
    public Map<String, String> typeParameterMap;

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
        return this.typeParameters != null && this.typeParameters.size() > 0;
    }

    protected String renderTypeParameters(final String str, boolean formalType) {
        String temp = str;
        if (this.typeParameterMap.size() > 0) {
            for (Map.Entry<String, String> entry : this.typeParameterMap.entrySet()) {
                final String k = entry.getKey();
                final String v = entry.getValue();
                temp = ClassNameUtils.replace(temp, ClassNameUtils.CLASS_TYPE_VARIABLE_MARK + k, v);
                if (formalType) {
                    // follow intellij
                    temp = ClassNameUtils.replace(temp, ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK + k, v);
                }
            }
        } else {
            for (String entry : this.typeParameters) {
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
        return ClassNameUtils.replace(temp, ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK, "").trim();
    }

    public void clearTypeParameterMap() {
        if (this.typeParameterMap != null) {
            this.typeParameterMap.clear();
        }
    }

    private boolean containsTypeParameter(final String typeParameter) {
        return this.typeParameters != null && this.typeParameters.contains(typeParameter);
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

    public boolean isStatic() {
        return this.modifier.contains("static");
    }

    public boolean isAbstract() {
        return this.modifier.contains("abstract");
    }

    public boolean isPrivate() {
        return this.modifier.contains("private");
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
                temp = ClassNameUtils.replace(temp, replaceKey, v);
            }
        } else {
            for (String entry : this.typeParameters) {
                temp = ClassNameUtils.replace(temp, ClassNameUtils.CLASS_TYPE_VARIABLE_MARK + entry, ClassNameUtils.OBJECT_CLASS);
            }

            if (!this.modifier.contains("static ")) {
                temp = TRIM_RE.matcher(temp).replaceAll("");
            }
        }
        return !temp.contains(ClassNameUtils.CLASS_TYPE_VARIABLE_MARK) && !temp.contains(ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK);
    }

    public String getReturnTypeKey() {
        final int idx1 = this.returnType.indexOf(ClassNameUtils.CLASS_TYPE_VARIABLE_MARK);
        if (idx1 > -1) {
            return this.returnType.substring(idx1 + 2, idx1 + 3);
        }
        final int idx2 = this.returnType.indexOf(ClassNameUtils.FORMAL_TYPE_VARIABLE_MARK);
        if (idx2 > -1) {
            return this.returnType.substring(idx2 + 2, idx2 + 3);
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MemberDescriptor)) {
            return false;
        }
        MemberDescriptor that = (MemberDescriptor) o;
        return Objects.equal(name, that.name)
                && memberType == that.memberType
                && Objects.equal(modifier, that.modifier)
                && Objects.equal(returnType, that.returnType)
                && Objects.equal(typeParameters, that.typeParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, memberType, modifier, returnType, typeParameters);
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
}