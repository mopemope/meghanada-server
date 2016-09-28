package meghanada.parser.source;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.github.javaparser.Range;

@DefaultSerializer(FieldAccessSymbolSerializer.class)
public class FieldAccessSymbol extends AccessSymbol {

    public FieldAccessSymbol(final String scope, final String fieldName, final Range range, final String declaringClass) {
        super(scope, fieldName, range, declaringClass);
    }

    @Override
    public boolean match(int line, int column) {
        return this.range.begin.line == line && this.contains(column);
    }

}
