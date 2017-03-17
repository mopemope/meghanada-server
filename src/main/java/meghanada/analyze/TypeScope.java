package meghanada.analyze;

import meghanada.reflect.MemberDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class TypeScope extends MethodScope {

    public final List<MemberDescriptor> memberDescriptors = new ArrayList<>(32);

    public TypeScope() {

    }

    public TypeScope(final String name, final Range nameRange, final int pos, final Range range) {
        super(name, nameRange, pos, range);
        this.returnType = name;
    }

    @Override
    public Optional<BlockScope> getCurrentBlock() {
        final Optional<BlockScope> currentBlock = super.getCurrentBlock();
        if (currentBlock.isPresent()) {
            return currentBlock;
        }
        return Optional.of(this);
    }

    public MethodScope startMethod(final String name,
                                   final Range nameRange,
                                   final int pos,
                                   final Range range,
                                   final boolean isConstructor) {
        // add method
        final MethodScope scope = new MethodScope(name, nameRange, pos, range, isConstructor);
        super.startBlock(scope);
        return scope;
    }

    public Optional<MethodScope> endMethod() {
        final Optional<BlockScope> blockScope = super.endBlock();
        if (blockScope.isPresent()) {
            final BlockScope blockScope1 = blockScope.get();
            if (blockScope1 instanceof MethodScope) {
                return Optional.of((MethodScope) blockScope1);
            }
        }
        return Optional.empty();
    }

    public AccessSymbol getExpressionReturn(final int line) {
        final Scope scope = getScope(line, super.scopes);
        if (scope != null && (scope instanceof BlockScope)) {
            final BlockScope blockScope = (BlockScope) scope;
            final Optional<ExpressionScope> result = blockScope.getExpression(line);
            final Optional<AccessSymbol> accessSymbol = result.flatMap(ExpressionScope::getExpressionReturn);
            return accessSymbol.orElse(null);
        }

        final Optional<ExpressionScope> result = this.getExpression(line);
        final Optional<AccessSymbol> accessSymbol = result.flatMap(ExpressionScope::getExpressionReturn);
        return accessSymbol.orElse(null);
    }

    @Override
    public List<MethodCall> getMethodCall(final int line) {
        for (final ExpressionScope expressionScope : expressions) {
            final List<MethodCall> methodCalls = expressionScope.getMethodCall(line);
            if (methodCalls != null && !methodCalls.isEmpty()) {
                return methodCalls;
            }
        }
        final Scope scope = getScope(line, super.scopes);
        if (scope != null) {
            return scope.getMethodCall(line);
        }
        return super.methodCalls.stream()
                .filter(mc -> mc.containsLine(line))
                .collect(Collectors.toList());
    }

    public Variable getField(final String name) {
        for (final ExpressionScope es : expressions) {
            for (final Variable v : es.variables) {
                if (v.name.equals(name)) {
                    return v;
                }
            }
        }
        for (final Variable v : variables) {
            if (v.name.equals(name)) {
                return v;
            }
        }
        return null;
    }

    public String getFQCN() {
        return this.returnType;
    }

    public List<MemberDescriptor> getMemberDescriptors() {
        return memberDescriptors;
    }

}
