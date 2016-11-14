package meghanada.analyze;


import java.util.*;

public class BlockScope extends Scope {

    public List<BlockScope> scopes = new ArrayList<>(8);
    public Deque<BlockScope> currentScope = new ArrayDeque<>(8);
    public List<ExpressionScope> expressions = new ArrayList<>(8);
    public Deque<ExpressionScope> currentExpr = new ArrayDeque<>(8);
    public BlockScope parent;

    BlockScope() {
        super();
    }

    BlockScope(final int pos, final Range range) {
        super(pos, range);
    }

    public void startBlock(final BlockScope blockScope) {
        blockScope.parent = this;
        this.currentScope.push(blockScope);
    }

    public Optional<BlockScope> getCurrentBlock() {
        return Optional.ofNullable(this.currentScope.peek());
    }

    public Optional<BlockScope> endBlock() {
        return this.getCurrentBlock().map(blockScope -> {
            this.scopes.add(blockScope);
            return this.currentScope.remove();
        });
    }

    public void startExpression(final ExpressionScope expr) {
        this.currentExpr.push(expr);
    }

    private Optional<ExpressionScope> getCurrentExpr() {
        return Optional.ofNullable(this.currentExpr.peek());
    }

    public Optional<ExpressionScope> endExpression() {
        return this.getCurrentExpr().map(expr -> {
            this.expressions.add(expr);
            return this.currentExpr.remove();
        });
    }

    public Optional<? extends Scope> getCurrentScope() {
        final Optional<ExpressionScope> currentExpr = getCurrentExpr();
        if (currentExpr.isPresent()) {
            return currentExpr;
        }
        final Optional<BlockScope> currentBlock = getCurrentBlock();
        if (currentBlock.isPresent()) {
            final Optional<? extends Scope> currentScope = currentBlock.get().getCurrentExpr();
            if (currentScope.isPresent()) {
                return currentScope;
            }
        }
        return currentBlock;
    }

    @Override
    public Optional<Variable> findVariable(final int pos) {
        for (final ExpressionScope expressionScope : this.expressions) {
            final Optional<Variable> variable = expressionScope.findVariable(pos);
            if (variable.isPresent()) {
                return variable;
            }
        }

        for (final BlockScope blockScope : this.scopes) {
            final Optional<Variable> variable = blockScope.findVariable(pos);
            if (variable.isPresent()) {
                return variable;
            }
        }

        for (final Variable v : this.variables) {
            if (v.pos == pos) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    @Override
    public void dumpVariable() {
        super.dumpVariable();

        for (final ExpressionScope expressionScope : this.expressions) {
            expressionScope.dumpVariable();
        }

        for (final BlockScope blockScope : this.scopes) {
            blockScope.dumpVariable();
        }
    }

    @Override
    public void dumpFieldAccess() {
        super.dumpFieldAccess();

        for (final ExpressionScope expressionScope : this.expressions) {
            expressionScope.dumpFieldAccess();
        }

        for (final BlockScope blockScope : this.scopes) {
            blockScope.dumpFieldAccess();
        }
    }

    @Override
    public void dump() {
        super.dump();

        for (final ExpressionScope expressionScope : this.expressions) {
            expressionScope.dump();
        }

        for (final BlockScope blockScope : this.scopes) {
            blockScope.dump();
        }
    }
}
