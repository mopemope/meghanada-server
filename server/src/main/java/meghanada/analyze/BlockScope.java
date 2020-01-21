package meghanada.analyze;

import static java.util.Objects.nonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockScope extends Scope {

  private static final long serialVersionUID = -4472933510977481167L;
  private static final Logger log = LogManager.getLogger(ClassScope.class);

  public final List<BlockScope> scopes = new ArrayList<>(16);
  public final Deque<BlockScope> currentScope = new ArrayDeque<>(8);
  public final List<ExpressionScope> expressions = new ArrayList<>(16);
  public final Deque<ExpressionScope> currentExpr = new ArrayDeque<>(8);
  public BlockScope parent;

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
    return this.getCurrentBlock()
        .map(
            blockScope -> {
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

  public void endExpression() {
    this.getCurrentExpr()
        .map(
            expr -> {
              this.expressions.add(expr);
              return this.currentExpr.remove();
            });
  }

  public Optional<? extends Scope> getCurrentScope() {
    Optional<ExpressionScope> currentExpr = getCurrentExpr();
    if (currentExpr.isPresent()) {
      return currentExpr;
    }
    Optional<BlockScope> currentBlock = getCurrentBlock();
    if (currentBlock.isPresent()) {
      Optional<? extends Scope> currentScope = currentBlock.get().getCurrentExpr();
      if (currentScope.isPresent()) {
        return currentScope;
      }
    }
    return currentBlock;
  }

  @Override
  public Optional<Variable> findVariable(final int pos) {
    for (ExpressionScope expressionScope : this.expressions) {
      Optional<Variable> variable = expressionScope.findVariable(pos);
      if (variable.isPresent()) {
        return variable;
      }
    }

    for (BlockScope blockScope : this.scopes) {
      Optional<Variable> variable = blockScope.findVariable(pos);
      if (variable.isPresent()) {
        return variable;
      }
    }

    for (Variable v : this.variables) {
      if (v.pos == pos) {
        return Optional.of(v);
      }
    }
    return Optional.empty();
  }

  @Override
  public void dumpVariable() {
    super.dumpVariable();

    for (ExpressionScope expressionScope : this.expressions) {
      expressionScope.dumpVariable();
    }

    for (BlockScope blockScope : this.scopes) {
      blockScope.dumpVariable();
    }
  }

  @Override
  public void dumpFieldAccess() {
    super.dumpFieldAccess();

    for (ExpressionScope expressionScope : this.expressions) {
      expressionScope.dumpFieldAccess();
    }

    for (BlockScope blockScope : this.scopes) {
      blockScope.dumpFieldAccess();
    }
  }

  @Override
  public void dump() {
    super.dump();

    for (ExpressionScope expressionScope : this.expressions) {
      expressionScope.dump();
    }

    for (BlockScope blockScope : this.scopes) {
      blockScope.dump();
    }
  }

  protected Optional<ExpressionScope> getExpression(final int line) {
    log.traceEntry("line={} expressions={}", line, this.expressions);
    Optional<ExpressionScope> result =
        this.expressions.stream().filter(expr -> expr.contains(line)).findFirst();
    return log.traceExit(result);
  }

  public List<BlockScope> getScopes() {
    return scopes;
  }

  @Override
  public List<FieldAccess> getFieldAccess(int line) {
    if (this.contains(line)) {
      for (BlockScope bs : scopes) {
        if (bs.contains(line)) {
          for (ExpressionScope expression : bs.expressions) {
            List<FieldAccess> faList = expression.getFieldAccess(line);
            if (!faList.isEmpty()) {
              return faList;
            }
          }
          return bs.fieldAccesses.stream()
              .filter(fa -> fa.range.begin.line == line)
              .collect(Collectors.toList());
        }
      }
      for (ExpressionScope expression : this.expressions) {
        List<FieldAccess> faList = expression.getFieldAccess(line);
        if (!faList.isEmpty()) {
          return faList;
        }
      }
    }
    return super.getFieldAccess(line);
  }

  @Override
  public List<MethodCall> getMethodCall(final int line) {
    for (BlockScope bs : this.scopes) {
      List<MethodCall> methodCalls = bs.getMethodCall(line);
      if (nonNull(methodCalls) && !methodCalls.isEmpty()) {
        return methodCalls;
      }
    }

    for (ExpressionScope es : this.expressions) {
      List<MethodCall> methodCalls = es.getMethodCall(line);
      if (nonNull(methodCalls) && !methodCalls.isEmpty()) {
        return methodCalls;
      }
    }

    return super.getMethodCall(line);
  }

  @Override
  public Set<Variable> getVariables() {

    Set<Variable> result = new HashSet<>(this.variables);
    for (ExpressionScope e : this.expressions) {
      result.addAll(e.getVariables());
    }

    for (BlockScope bs : scopes) {
      result.addAll(bs.getVariables());
    }
    return result;
  }

  @Override
  public Collection<FieldAccess> getFieldAccesses() {

    List<FieldAccess> result = new ArrayList<>(this.fieldAccesses);
    for (ExpressionScope e : this.expressions) {
      result.addAll(e.getFieldAccesses());
    }

    for (BlockScope bs : scopes) {
      result.addAll(bs.getFieldAccesses());
    }
    return result;
  }

  @Override
  public Collection<MethodCall> getMethodCalls() {

    List<MethodCall> result = new ArrayList<>(this.methodCalls);
    for (ExpressionScope e : this.expressions) {
      result.addAll(e.getMethodCalls());
    }

    for (BlockScope bs : scopes) {
      result.addAll(bs.getMethodCalls());
    }
    return result;
  }

  @Override
  public Collection<AccessSymbol> getAccessSymbols() {

    List<AccessSymbol> result = new ArrayList<>(this.getAccessSymbols());
    for (ExpressionScope e : this.expressions) {
      result.addAll(e.getAccessSymbols());
    }

    for (BlockScope bs : scopes) {
      result.addAll(bs.getAccessSymbols());
    }
    return result;
  }

  public Optional<List<Variable>> getLocalVariables(final int line) {
    for (BlockScope bs : this.scopes) {
      Optional<List<Variable>> vs = bs.getLocalVariables(line);
      if (vs.isPresent()) {
        return vs;
      }
    }

    for (ExpressionScope es : this.expressions) {
      Optional<List<Variable>> vs = es.getLocalVariables(line);
      if (vs.isPresent()) {
        return vs;
      }
    }

    return super.getLocalVariables(line);
  }
}
