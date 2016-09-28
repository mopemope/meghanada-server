package meghanada.parser.source;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.javaparser.Position;
import com.github.javaparser.Range;

import java.util.List;
import java.util.Set;

public class BlockScopeSerializer extends Serializer<BlockScope> {

    @Override
    public void write(Kryo kryo, Output output, BlockScope scope) {
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
        final Set<Variable> names = scope.getNameSymbols();
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

        // 6. expression
        final List<ExpressionScope> expressions = scope.expressions;
        output.writeInt(expressions.size(), true);
        for (final ExpressionScope ex : expressions) {
            kryo.writeClassAndObject(output, ex);
        }

        // 7. innerScope
        final List<BlockScope> innerScopes = scope.innerScopes;
        output.writeInt(innerScopes.size(), true);
        for (final BlockScope bs : innerScopes) {
            kryo.writeClassAndObject(output, bs);
        }

        // 8. parent
        final BlockScope parent = scope.parent;
        if (parent != null) {
            kryo.writeClassAndObject(output, parent);
        } else {
            kryo.writeClassAndObject(output, null);
        }

        // 9. lambdaBlock
        output.writeBoolean(scope.isLambdaBlock);
    }

    @Override
    public BlockScope read(Kryo kryo, Input input, Class<BlockScope> type) {
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

        final BlockScope scope = new BlockScope(name, range);

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

        // 6. expression
        final int exSize = input.readInt(true);
        for (int i = 0; i < exSize; i++) {
            final ExpressionScope expressionScope = (ExpressionScope) kryo.readClassAndObject(input);
            scope.expressions.add(expressionScope);
        }

        // 7. innerScope
        final int inSize = input.readInt(true);
        for (int i = 0; i < inSize; i++) {
            final BlockScope blockScope = (BlockScope) kryo.readClassAndObject(input);
            scope.innerScopes.add(blockScope);
        }

        // 8. parent
        final Object o = kryo.readClassAndObject(input);
        if (o != null) {
            scope.parent = (BlockScope) o;
        }

        // 9. lambdaBlock
        scope.isLambdaBlock = input.readBoolean();

        return scope;
    }
}
