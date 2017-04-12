package meghanada.analyze;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class BlockScope extends Scope {

    private static final Logger log = LogManager.getLogger(ClassScope.class);
    public final List<BlockScope> scopes = new ArrayList<>(16);
    public final Deque<BlockScope> currentScope = new ArrayDeque<>(8);
    public final List<ExpressionScope> expressions = new ArrayList<>(16);
    public final Deque<ExpressionScope> currentExpr = new ArrayDeque<>(8);
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

    public String getName() {
        return "";
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
        expr.parent = this;
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

    protected Optional<ExpressionScope> getExpression(final int line) {
        log.traceEntry("line={} expressions={}", line, this.expressions);
        final Optional<ExpressionScope> result = this.expressions.stream()
                .filter(expr -> expr.contains(line))
                .findFirst();
        return log.traceExit(result);
    }

    public List<BlockScope> getScopes() {
        return scopes;
    }

    @Override
    public List<FieldAccess> getFieldAccess(int line) {
        if (this.contains(line)) {
            for (final BlockScope bs : scopes) {
                if (bs.contains(line)) {
                    for (final ExpressionScope expression : bs.expressions) {
                        final List<FieldAccess> faList = expression.getFieldAccess(line);
                        if (!faList.isEmpty()) {
                            return faList;
                        }
                    }
                    return bs.fieldAccesses
                            .stream()
                            .filter(fa -> fa.range.begin.line == line)
                            .collect(Collectors.toList());
                }
            }
            for (final ExpressionScope expression : this.expressions) {
                final List<FieldAccess> faList = expression.getFieldAccess(line);
                if (!faList.isEmpty()) {
                    return faList;
                }
            }
        }
        return super.getFieldAccess(line);
    }

    @Override
    public List<MethodCall> getMethodCall(final int line) {
        for (final BlockScope bs : this.scopes) {
            final List<MethodCall> methodCalls = bs.getMethodCall(line);
            if (methodCalls != null && !methodCalls.isEmpty()) {
                return methodCalls;
            }
        }

        for (final ExpressionScope es : this.expressions) {
            final List<MethodCall> methodCalls = es.getMethodCall(line);
            if (methodCalls != null && !methodCalls.isEmpty()) {
                return methodCalls;
            }
        }

        return super.getMethodCall(line);
    }
}