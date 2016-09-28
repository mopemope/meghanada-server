package meghanada.parser.source;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.javaparser.Position;
import com.github.javaparser.Range;

public class VariableSerializer extends Serializer<Variable> {

    @Override
    public void write(Kryo kryo, Output output, Variable symbol) {
        // 1. name
        output.writeString(symbol.name);
        // 2. fqcn
        output.writeString(symbol.fqcn);
        // 3. parent
        output.writeString(symbol.parent);
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
        // 5. declaration
        output.writeBoolean(symbol.declaration);

    }

    @Override
    public Variable read(Kryo kryo, Input input, Class<Variable> type) {
        // 1. name
        final String name = input.readString();
        // 2. fqcn
        final String fqcn = input.readString();
        // 3. parent
        final String parent = input.readString();
        // 4. range
        final Integer l1 = input.readInt(true);
        final Integer c1 = input.readInt(true);
        final Integer l2 = input.readInt(true);
        final Integer c2 = input.readInt(true);

        final Position begin = new Position(l1, c1);
        final Position end = new Position(l2, c2);
        final Range range = new Range(begin, end);

        // 5. declaration
        final boolean declaration = input.readBoolean();

        return new Variable(parent, name, range, fqcn, declaration);
    }
}
