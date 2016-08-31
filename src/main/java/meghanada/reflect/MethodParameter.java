package meghanada.reflect;

import com.google.common.base.MoreObjects;
import meghanada.utils.ClassNameUtils;

import java.io.Serializable;

public class MethodParameter implements Serializable {

    private static final long serialVersionUID = -8745173932685379688L;

    public String type;
    public String name;

    public MethodParameter() {
    }

    public MethodParameter(final String type, final String name) {
        this.type = type;
        this.name = name;
    }

    public String getParameter(boolean simple) {
        if (simple) {
            return ClassNameUtils.getSimpleName(type) + " " + name;
        }
        return type + " " + name;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("type", type)
                .add("name", name)
                .toString();
    }
}
