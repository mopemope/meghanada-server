package meghanada.parser.source;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.github.javaparser.Range;

@DefaultSerializer(MethodCallSymbolSerializer.class)
public class MethodCallSymbol extends AccessSymbol {

    final Range nameRange;

    public MethodCallSymbol(final String scope, final String methodName, final Range range, final Range nameRange, final String declaringClass) {
        super(scope, methodName, range, declaringClass);
        this.nameRange = nameRange;
    }

    public Range getNameRange() {
        return nameRange;
    }

    public boolean containsLine(final int line) {
        final int start = nameRange.begin.line;
        final int end = nameRange.end.line;
        return start <= line && line <= end;
    }

    public boolean nameContains(final int column) {
        final int start = this.nameRange.begin.column;
        final int end = this.nameRange.end.column;
        return start <= column && column <= end;
    }

    @Override
    public boolean match(final int line, final int column) {
        return this.range.end.line == line && this.range.end.column == column;
    }

    public String getDeclaringClass() {
        return declaringClass;
    }

}
