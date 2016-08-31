package meghanada.parser;

import com.github.javaparser.Range;

public class FieldAccessSymbol extends AccessSymbol {

    FieldAccessSymbol(final String scope, final String fieldName, final Range range, final String declaringClass) {
        super(scope, fieldName, range, declaringClass);
    }

    @Override
    public boolean match(int line, int column) {
        return this.range.begin.line == line && this.contains(column);
    }

}
