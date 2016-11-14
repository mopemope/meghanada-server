package meghanada.analyze;

import java.util.Optional;

public class TypeScope extends MethodScope {

    public TypeScope() {

    }

    public TypeScope(final String name, final Range nameRange, final int pos, final Range range) {
        super(name, nameRange, pos, range);
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
                                   final boolean isConstrucor) {
        // add method
        final MethodScope scope = new MethodScope(name, nameRange, pos, range, isConstrucor);
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

}
