package meghanada.parser.source;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.javaparser.Position;
import com.github.javaparser.Range;

public class MethodCallSymbolSerializer extends Serializer<MethodCallSymbol> {

    @Override
    public void write(Kryo kryo, Output output, MethodCallSymbol symbol) {
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
        // 5. nameRange
        final Range nameRange = symbol.nameRange;
        if (nameRange != null) {
            final Position begin = nameRange.begin;
            final Position end = nameRange.end;
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
        // 6. return type
        output.writeString(symbol.returnType);

    }

    @Override
    public MethodCallSymbol read(Kryo kryo, Input input, Class<MethodCallSymbol> type) {
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

        final Position begin1 = new Position(l1, c1);
        final Position end1 = new Position(l2, c2);
        final Range range = new Range(begin1, end1);

        // 5. nameRange
        final Integer nl1 = input.readInt(true);
        final Integer nc1 = input.readInt(true);
        final Integer nl2 = input.readInt(true);
        final Integer nc2 = input.readInt(true);

        final Position begin2 = new Position(nl1, nc1);
        final Position end2 = new Position(nl2, nc2);
        final Range nameRange = new Range(begin2, end2);

        final MethodCallSymbol methodCallSymbol = new MethodCallSymbol(scope, fieldName, range, nameRange, declaringClass);

        // 6. return type
        final String rt = input.readString();
        methodCallSymbol.returnType = rt;
        return methodCallSymbol;
    }
}
