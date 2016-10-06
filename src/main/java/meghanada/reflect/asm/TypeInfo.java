package meghanada.reflect.asm;

import com.google.common.base.Joiner;
import meghanada.utils.ClassNameUtils;

import java.util.List;

class TypeInfo {

    private final String fqcn;
    String name;
    List<TypeInfo> typeParameters;
    boolean isArray;
    boolean variableArguments;
    String paramName;
    String innerClass;

    TypeInfo(String name, String fqcn) {
        this.name = name;
        this.fqcn = fqcn;
        this.isArray = false;
        this.variableArguments = false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name);
        if (this.typeParameters != null && this.typeParameters.size() > 0) {
            sb.append("<");
            Joiner.on(", ").appendTo(sb, this.typeParameters).append(">");
        }
        if (innerClass != null) {
            sb.append(ClassNameUtils.INNER_MARK);
            sb.append(innerClass);
        }
        if (isArray) {
            sb.append("[]");
        }
        if (variableArguments) {
            sb.append("...");
        }
        if (paramName != null) {
            sb.append(" ").append(paramName);
        }
        return sb.toString();
    }

    String getFQCN() {
        StringBuilder sb = new StringBuilder(fqcn);
        if (this.typeParameters != null && this.typeParameters.size() > 0) {
            sb.append("<");
            Joiner.on(", ").appendTo(sb, this.typeParameters).append(">");
        }
        if (innerClass != null) {
            sb.append(ClassNameUtils.INNER_MARK);
            sb.append(innerClass);
        }
        if (isArray) {
            sb.append("[]");
        }
        return sb.toString();
    }
}
