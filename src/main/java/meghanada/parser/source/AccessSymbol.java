package meghanada.parser.source;

import com.github.javaparser.Range;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public abstract class AccessSymbol {

    final String declaringClass;
    final String scope;
    final String name;
    final Range range;

    // return FQCN
    public String returnType;

    public AccessSymbol(final String scope, final String name, final Range range, final String declaringClass) {
        this.name = name;
        this.declaringClass = declaringClass;
        this.scope = scope;
        this.range = range;
    }

    public String getScope() {
        return scope;
    }

    public abstract boolean match(int line, int column);

    public boolean contains(int column) {
        final int start = this.range.begin.column;
        final int end = this.range.end.column;
        return start <= column && column <= end;
    }

    public String getDeclaringClass() {
        return declaringClass;
    }

    public String getName() {
        return name;
    }

    public int getLine() {
        return this.range.begin.line;
    }

    public String getReturnType() {
        return returnType;
    }

    public Range getRange() {
        return range;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccessSymbol)) {
            return false;
        }
        AccessSymbol that = (AccessSymbol) o;
        return Objects.equal(declaringClass, that.declaringClass)
                && Objects.equal(scope, that.scope)
                && Objects.equal(name, that.name)
                && Objects.equal(range, that.range)
                && Objects.equal(returnType, that.returnType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(declaringClass, scope, name, range, returnType);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("declaringClass", declaringClass)
                .add("scope", scope)
                .add("name", name)
                .add("range", range)
                .add("returnType", returnType)
                .toString();
    }
}
