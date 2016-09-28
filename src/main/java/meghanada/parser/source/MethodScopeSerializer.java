package meghanada.parser.source;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.javaparser.Position;
import com.github.javaparser.Range;

import java.util.List;
import java.util.Set;

public class MethodScopeSerializer extends Serializer<MethodScope> {
    @Override
    public void write(Kryo kryo, Output output, MethodScope scope) {
        // 1. name
        output.writeString(scope.name);
        // 2. range
        final Range range = scope.range;
        final Position begin1 = range.begin;
        final Position end1 = range.end;
        output.writeInt(begin1.line, true);
        output.writeInt(begin1.column, true);
        output.writeInt(end1.line, true);
        output.writeInt(end1.column, true);

        // 3. nameRange
        final Range nameRange = scope.nameRange;
        final Position begin2 = nameRange.begin;
        final Position end2 = nameRange.end;
        output.writeInt(begin2.line, true);
        output.writeInt(begin2.column, true);
        output.writeInt(end2.line, true);
        output.writeInt(end2.column, true);

        // 4. nameSymbols
        final Set<Variable> names = scope.getNameSymbols();
        output.writeInt(names.size(), true);
        for (final Variable v : names) {
            kryo.writeClassAndObject(output, v);
        }

        // 5. methodCalls
        final List<MethodCallSymbol> methodCallSymbols = scope.getMethodCallSymbols();
        output.writeInt(methodCallSymbols.size(), true);
        for (final MethodCallSymbol mcs : methodCallSymbols) {
            kryo.writeClassAndObject(output, mcs);
        }

        // 6. fieldAccesses
        final List<FieldAccessSymbol> fieldAccessSymbols = scope.getFieldAccessSymbols();
        output.writeInt(fieldAccessSymbols.size(), true);
        for (final FieldAccessSymbol fas : fieldAccessSymbols) {
            kryo.writeClassAndObject(output, fas);
        }

        // 7. expression
        final List<ExpressionScope> expressions = scope.expressions;
        output.writeInt(expressions.size(), true);
        for (final ExpressionScope ex : expressions) {
            kryo.writeClassAndObject(output, ex);
        }

        // 8. innerScope
        final List<BlockScope> innerScopes = scope.innerScopes;
        output.writeInt(innerScopes.size(), true);
        for (final BlockScope bs : innerScopes) {
            kryo.writeClassAndObject(output, bs);
        }

        // 9. parent
        final BlockScope parent = scope.parent;
        if (parent != null) {
            kryo.writeClassAndObject(output, parent);
        } else {
            kryo.writeClassAndObject(output, null);
        }

        // 10. lambdaBlock
        output.writeBoolean(scope.isLambdaBlock);

    }

    @Override
    public MethodScope read(Kryo kryo, Input input, Class<MethodScope> type) {
        // 1. name
        final String name = input.readString();

        // 2. range
        final Integer l1 = input.readInt(true);
        final Integer c1 = input.readInt(true);
        final Integer l2 = input.readInt(true);
        final Integer c2 = input.readInt(true);
        final Range range = new Range(new Position(l1, c1), new Position(l2, c2));

        // 3. range
        final Integer nl1 = input.readInt(true);
        final Integer nc1 = input.readInt(true);
        final Integer nl2 = input.readInt(true);
        final Integer nc2 = input.readInt(true);
        final Range nameRange = new Range(new Position(nl1, nc1), new Position(nl2, nc2));

        final MethodScope scope = new MethodScope(name, range, nameRange);

        // 4. nameSymbols
        final int nameSize = input.readVarInt(true);
        for (int i = 0; i < nameSize; i++) {
            final Variable v = (Variable) kryo.readClassAndObject(input);
            scope.addNameSymbol(v);
        }

        // 5. methodCalls
        final int mcsSize = input.readInt(true);
        for (int i = 0; i < mcsSize; i++) {
            final MethodCallSymbol mcs = (MethodCallSymbol) kryo.readClassAndObject(input);
            scope.addMethodCall(mcs);
        }

        // 6. fieldAccesses
        final int fasSize = input.readInt(true);
        for (int i = 0; i < fasSize; i++) {
            final FieldAccessSymbol fas = (FieldAccessSymbol) kryo.readClassAndObject(input);
            scope.addFieldAccess(fas);
        }

        // 7. expression
        final int exSize = input.readInt(true);
        for (int i = 0; i < exSize; i++) {
            final ExpressionScope expressionScope = (ExpressionScope) kryo.readClassAndObject(input);
            scope.expressions.add(expressionScope);
        }

        // 8. innerScope
        final int inSize = input.readInt(true);
        for (int i = 0; i < inSize; i++) {
            final BlockScope blockScope = (BlockScope) kryo.readClassAndObject(input);
            scope.innerScopes.add(blockScope);
        }

        // 9. parent
        final Object o = kryo.readClassAndObject(input);
        if (o != null) {
            scope.parent = (BlockScope) o;
        }

        // 10. lambdaBlock
        scope.isLambdaBlock = input.readBoolean();

        return scope;
    }
}
