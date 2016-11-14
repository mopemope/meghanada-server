package meghanada.analyze;

import com.google.common.base.MoreObjects;

public abstract class AccessSymbol {

    public String declaringClass;
    public String name;
    public int pos;
    public Range range;
    public String returnType;

    public AccessSymbol() {

    }

    public AccessSymbol(final String name, final int pos, final Range range) {
        this.name = name;
        this.pos = pos;
        this.range = range;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("declaringClass", declaringClass)
                .add("name", name)
                .add("returnType", returnType)
                .add("range", range)
                .toString();
    }
}