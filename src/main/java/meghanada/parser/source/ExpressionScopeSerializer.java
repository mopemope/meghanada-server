package meghanada.parser.source;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.javaparser.Position;
import com.github.javaparser.Range;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ExpressionScopeSerializer extends Serializer<ExpressionScope> {

    @Override
    public void write(Kryo kryo, Output output, ExpressionScope scope) {
        // 1. name
        output.writeString(scope.name);
        // 2. range
        final Range range = scope.range;
        final Position begin = range.begin;
        final Position end = range.end;
        output.writeInt(begin.line, true);
        output.writeInt(begin.column, true);
        output.writeInt(end.line, true);
        output.writeInt(end.column, true);

        // 3. nameSymbols
        Set<Variable> names = scope.getNameSymbols();
        output.writeInt(names.size(), true);
        for (final Variable v : names) {
            kryo.writeClassAndObject(output, v);
        }

        // 4. methodCalls
        final List<MethodCallSymbol> methodCallSymbols = scope.getMethodCallSymbols();
        output.writeInt(methodCallSymbols.size(), true);
        for (final MethodCallSymbol mcs : methodCallSymbols) {
            kryo.writeClassAndObject(output, mcs);
        }

        // 5. fieldAccesses
        final List<FieldAccessSymbol> fieldAccessSymbols = scope.getFieldAccessSymbols();
        output.writeInt(fieldAccessSymbols.size(), true);
        for (final FieldAccessSymbol fas : fieldAccessSymbols) {
            kryo.writeClassAndObject(output, fas);
        }

        // 6. returnType
        Optional<AccessSymbol> returnType = scope.getExpressionReturn();
        if (returnType.isPresent()) {
            kryo.writeClassAndObject(output, returnType.get());
        } else {
            kryo.writeClassAndObject(output, null);
        }
    }

    @Override
    public ExpressionScope read(final Kryo kryo, final Input input, final Class<ExpressionScope> type) {
        // 1. name
        final String name = input.readString();
        // 2. range
        final Integer l1 = input.readInt(true);
        final Integer c1 = input.readInt(true);
        final Integer l2 = input.readInt(true);
        final Integer c2 = input.readInt(true);

        final Position begin = new Position(l1, c1);
        final Position end = new Position(l2, c2);
        final Range range = new Range(begin, end);

        final ExpressionScope scope = new ExpressionScope(name, range);

        // 3. nameSymbols
        final int nameSize = input.readVarInt(true);
        for (int i = 0; i < nameSize; i++) {
            final Variable v = (Variable) kryo.readClassAndObject(input);
            scope.addNameSymbol(v);
        }

        // 4. methodCalls
        final int mcsSize = input.readInt(true);
        for (int i = 0; i < mcsSize; i++) {
            final MethodCallSymbol mcs = (MethodCallSymbol) kryo.readClassAndObject(input);
            scope.addMethodCall(mcs);
        }

        // 5. fieldAccesses
        final int fasSize = input.readInt(true);
        for (int i = 0; i < fasSize; i++) {
            final FieldAccessSymbol fas = (FieldAccessSymbol) kryo.readClassAndObject(input);
            scope.addFieldAccess(fas);
        }

        // 6. returnType
        final Object o = kryo.readClassAndObject(input);
        if (o != null) {
            scope.expressionReturn = (AccessSymbol) o;
        }
        return scope;
    }
}
