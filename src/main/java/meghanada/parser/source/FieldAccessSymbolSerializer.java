package meghanada.parser.source;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FieldAccessSymbolSerializer extends Serializer<FieldAccessSymbol> {

    private static Logger log = LogManager.getLogger(FieldAccessSymbolSerializer.class);

    @Override
    public void write(Kryo kryo, Output output, FieldAccessSymbol symbol) {
        // 1. scope
        output.writeString(symbol.scope);
        // 2. fieldName
        output.writeString(symbol.name);
        // 3. declaringClass
        output.writeString(symbol.declaringClass);
        // 4. range
        final Range range = symbol.range;
        if (range != null) {
            final Position begin = range.begin;
            final Position end = range.end;
            output.writeInt(begin.line, true);
            output.writeInt(begin.column, true);
            output.writeInt(end.line, true);
            output.writeInt(end.column, true);
        } else {
            output.writeInt(0, true);
            output.writeInt(0, true);
            output.writeInt(0, true);
            output.writeInt(0, true);
        }
        // 5. return type
        output.writeString(symbol.returnType);
    }

    @Override
    public FieldAccessSymbol read(Kryo kryo, Input input, Class<FieldAccessSymbol> type) {

        // 1. scope
        final String scope = input.readString();
        // 2. fieldName
        final String fieldName = input.readString();
        // 3. declaringClass
        final String declaringClass = input.readString();
        // 4. range
        final Integer l1 = input.readInt(true);
        final Integer c1 = input.readInt(true);
        final Integer l2 = input.readInt(true);
        final Integer c2 = input.readInt(true);

        final Position begin = new Position(l1, c1);
        final Position end = new Position(l2, c2);
        final Range range = new Range(begin, end);
        final FieldAccessSymbol fieldAccessSymbol = new FieldAccessSymbol(scope, fieldName, range, declaringClass);

        // 5. return type
        final String rt = input.readString();
        fieldAccessSymbol.returnType = rt;
        return fieldAccessSymbol;
    }
}
