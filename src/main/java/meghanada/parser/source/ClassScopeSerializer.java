package meghanada.parser.source;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import meghanada.reflect.MemberDescriptor;

import java.util.*;

public class ClassScopeSerializer extends Serializer<ClassScope> {

    @Override
    public void write(final Kryo kryo, final Output output, final ClassScope scope) {
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

        // 4. pkg
        output.writeString(scope.pkg);

        // 5. nameSymbols
        final Set<Variable> names = scope.getNameSymbols();
        output.writeInt(names.size(), true);
        for (final Variable v : names) {
            kryo.writeClassAndObject(output, v);
        }

        // 6. methodCalls
        final List<MethodCallSymbol> methodCallSymbols = scope.getMethodCallSymbols();
        output.writeInt(methodCallSymbols.size(), true);
        for (final MethodCallSymbol mcs : methodCallSymbols) {
            kryo.writeClassAndObject(output, mcs);
        }

        // 7. fieldAccesses
        final List<FieldAccessSymbol> fieldAccessSymbols = scope.getFieldAccessSymbols();
        output.writeInt(fieldAccessSymbols.size(), true);
        for (final FieldAccessSymbol fas : fieldAccessSymbols) {
            kryo.writeClassAndObject(output, fas);
        }

        // 8. expression
        final List<ExpressionScope> expressions = scope.expressions;
        output.writeInt(expressions.size(), true);
        for (final ExpressionScope ex : expressions) {
            kryo.writeClassAndObject(output, ex);
        }

        // 9. innerScope
        final List<BlockScope> innerScopes = scope.innerScopes;
        output.writeInt(innerScopes.size(), true);
        for (final BlockScope bs : innerScopes) {
            kryo.writeClassAndObject(output, bs);
        }

        // 10. fieldSymbols
        final Collection<Variable> values = scope.fieldSymbols.values();
        output.writeInt(values.size(), true);
        for (final Variable v : values) {
            kryo.writeClassAndObject(output, v);
        }

        // 11. memberDescriptors
        final List<MemberDescriptor> memberDescriptors = scope.memberDescriptors;
        output.writeInt(memberDescriptors.size(), true);
        for (final MemberDescriptor md : memberDescriptors) {
            kryo.writeClassAndObject(output, md);
        }

        // 12. parent
        final BlockScope parent = scope.parent;
        if (parent != null) {
            kryo.writeClassAndObject(output, parent);
        } else {
            kryo.writeClassAndObject(output, null);
        }

        // 13. lambdaBlock
        output.writeBoolean(scope.isLambdaBlock);

        // 14. isInterface
        output.writeBoolean(scope.isInterface);

        // 15. extendsClasses
        final List<String> extendsClasses = scope.extendsClasses;
        if (extendsClasses == null) {
            output.writeInt(0, true);
        } else {
            output.writeInt(extendsClasses.size(), true);
            extendsClasses.forEach(output::writeString);
        }

        // 16. implClasses
        final List<String> implClasses = scope.implClasses;
        if (implClasses == null) {
            output.writeInt(0, true);
        } else {
            output.writeInt(implClasses.size(), true);
            implClasses.forEach(output::writeString);
        }

        // 17. typeParameters;
        final List<String> typeParameters = scope.typeParameters;
        if (typeParameters == null) {
            output.writeInt(0, true);
        } else {
            output.writeInt(typeParameters.size(), true);
            for (final String t : typeParameters) {
                output.writeString(t);
            }
        }

        // 18. typeParameterMap
        kryo.writeClassAndObject(output, scope.typeParameterMap);
    }

    @Override
    public ClassScope read(Kryo kryo, Input input, Class<ClassScope> aClass) {
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

        // 4. pkg
        final String pkg = input.readString();

        final ClassScope scope = new ClassScope(pkg, name, range, nameRange, false);

        // 5. nameSymbols
        final int nameSize = input.readVarInt(true);
        for (int i = 0; i < nameSize; i++) {
            final Variable v = (Variable) kryo.readClassAndObject(input);
            scope.addNameSymbol(v);
        }

        // 6. methodCalls
        final int mcsSize = input.readInt(true);
        for (int i = 0; i < mcsSize; i++) {
            final MethodCallSymbol mcs = (MethodCallSymbol) kryo.readClassAndObject(input);
            scope.addMethodCall(mcs);
        }

        // 7. fieldAccesses
        final int fasSize = input.readInt(true);
        for (int i = 0; i < fasSize; i++) {
            final FieldAccessSymbol fas = (FieldAccessSymbol) kryo.readClassAndObject(input);
            scope.addFieldAccess(fas);
        }

        // 8. expression
        final int exSize = input.readInt(true);
        for (int i = 0; i < exSize; i++) {
            final ExpressionScope expressionScope = (ExpressionScope) kryo.readClassAndObject(input);
            scope.expressions.add(expressionScope);
        }

        // 9. innerScope
        final int inSize = input.readInt(true);
        for (int i = 0; i < inSize; i++) {
            final BlockScope blockScope = (BlockScope) kryo.readClassAndObject(input);
            scope.innerScopes.add(blockScope);
        }

        // 10. fieldSymbols
        final int fieldSize = input.readInt(true);
        for (int i = 0; i < fieldSize; i++) {
            final Variable v = (Variable) kryo.readClassAndObject(input);
            scope.addFieldSymbol(v);
        }

        // 11. memberDescriptors
        final int mdSize = input.readInt(true);
        for (int i = 0; i < mdSize; i++) {
            final MemberDescriptor md = (MemberDescriptor) kryo.readClassAndObject(input);
            scope.addMemberDescriptor(md);
        }

        // 12. parent
        final Object o = kryo.readClassAndObject(input);
        if (o != null) {
            scope.parent = (BlockScope) o;
        }

        // 13. lambdaBlock
        scope.isLambdaBlock = input.readBoolean();

        // 14. isInterface
        scope.isInterface = input.readBoolean();

        // 15. extendsClasses
        final int extendsClasses = input.readInt(true);
        if (extendsClasses > 0 && scope.extendsClasses == null) {
            scope.extendsClasses = new ArrayList<>();
        }
        for (int i = 0; i < extendsClasses; i++) {
            scope.extendsClasses.add(input.readString());
        }

        // 16. implClasses
        final int implClasses = input.readInt(true);
        if (implClasses > 0 && scope.implClasses == null) {
            scope.implClasses = new ArrayList<>();
        }
        for (int i = 0; i < implClasses; i++) {
            scope.implClasses.add(input.readString());
        }

        // 17. typeParameters;
        final int typeParameters = input.readInt(true);
        if (typeParameters > 0 && scope.typeParameters == null) {
            scope.typeParameters = new ArrayList<>();
        }
        for (int i = 0; i < typeParameters; i++) {
            scope.typeParameters.add(input.readString());
        }

        // 18. typeParameterMap
        @SuppressWarnings("unchecked")
        final Map<String, String> map = (Map<String, String>) kryo.readClassAndObject(input);
        scope.typeParameterMap = map;

        return scope;
    }
}
