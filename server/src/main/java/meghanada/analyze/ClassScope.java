package meghanada.analyze;

import static java.util.Objects.isNull;

import com.google.common.base.MoreObjects;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

public class ClassScope extends TypeScope {

  private static final long serialVersionUID = -7970631189101348627L;
  private static final Logger log = LogManager.getLogger(ClassScope.class);
  public final List<ClassScope> classScopes = new ArrayList<>(1);
  private final Deque<ClassScope> currentClassScope = new ArrayDeque<>(1);

  public ClassScope(
      final String fqcn, @Nullable final Range nameRange, final int pos, final Range range) {
    super(fqcn, nameRange, pos, range);
  }

  @Override
  public Optional<Variable> findVariable(int pos) {
    return super.findVariable(pos);
  }

  public void startClass(final ClassScope classScope) {
    this.currentClassScope.push(classScope);
  }

  public Optional<ClassScope> getCurrentClass() {
    final ClassScope classScope = this.currentClassScope.peek();
    if (isNull(classScope)) {
      return Optional.of(this);
    }
    return classScope.getCurrentClass();
  }

  public Optional<ClassScope> endClass() {
    return this.getCurrentClass()
        .map(
            classScope -> {
              this.classScopes.add(classScope);
              return this.currentClassScope.remove();
            });
  }

  @Override
  public void dumpVariable() {
    final EntryMessage entryMessage =
        log.traceEntry("**** {} {} methods:{}", this.getScopeType(), this.name, this.scopes.size());
    super.dumpVariable(log);

    for (final ExpressionScope expressionScope : this.expressions) {
      expressionScope.dumpVariable();
    }

    for (final BlockScope blockScope : this.scopes) {
      blockScope.dumpVariable();
    }

    for (final ClassScope cs : this.classScopes) {
      cs.dumpVariable();
    }
    log.traceExit(entryMessage);
  }

  @Override
  public void dumpFieldAccess() {
    final EntryMessage entryMessage =
        log.traceEntry("**** {} {} methods:{}", this.getScopeType(), this.name, this.scopes.size());
    super.dumpFieldAccess(log);

    for (final ExpressionScope expressionScope : this.expressions) {
      expressionScope.dumpFieldAccess();
    }

    for (final BlockScope blockScope : this.scopes) {
      blockScope.dumpFieldAccess();
    }

    for (final ClassScope cs : this.classScopes) {
      cs.dumpFieldAccess();
    }
    log.traceExit(entryMessage);
  }

  @Override
  public void dump() {
    final EntryMessage entryMessage =
        log.traceEntry("**** {} {} methods:{}", this.getScopeType(), this.name, this.scopes.size());
    super.dump(log);

    for (final ExpressionScope expressionScope : this.expressions) {
      expressionScope.dump();
    }

    for (final BlockScope blockScope : this.scopes) {
      blockScope.dump();
    }

    for (final ClassScope cs : this.classScopes) {
      cs.dump();
    }
    log.traceExit(entryMessage);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("fqcn", name)
        .add("range", range)
        .add("nameRange", nameRange)
        .toString();
  }

  @Override
  public Set<Variable> getVariables() {

    final Set<Variable> result = new HashSet<>(this.variables);
    for (final ExpressionScope e : this.expressions) {
      result.addAll(e.getVariables());
    }

    for (final BlockScope bs : scopes) {
      result.addAll(bs.getVariables());
    }

    for (final ClassScope c : this.classScopes) {
      result.addAll(c.getVariables());
    }

    return result;
  }

  @Override
  public Collection<FieldAccess> getFieldAccesses() {

    final List<FieldAccess> result = new ArrayList<>(this.fieldAccesses);
    for (final ExpressionScope e : this.expressions) {
      result.addAll(e.getFieldAccesses());
    }

    for (final BlockScope bs : scopes) {
      result.addAll(bs.getFieldAccesses());
    }

    for (final ClassScope c : this.classScopes) {
      result.addAll(c.getFieldAccesses());
    }
    return result;
  }

  @Override
  public Collection<MethodCall> getMethodCalls() {

    final List<MethodCall> result = new ArrayList<>(this.methodCalls);
    for (final ExpressionScope e : this.expressions) {
      result.addAll(e.getMethodCalls());
    }

    for (final BlockScope bs : scopes) {
      result.addAll(bs.getMethodCalls());
    }

    for (final ClassScope c : this.classScopes) {
      result.addAll(c.getMethodCalls());
    }

    return result;
  }

  @Override
  public Collection<AccessSymbol> getAccessSymbols() {

    final List<AccessSymbol> result = new ArrayList<>(this.getAccessSymbols());
    for (final ExpressionScope e : this.expressions) {
      result.addAll(e.getAccessSymbols());
    }

    for (final BlockScope bs : scopes) {
      result.addAll(bs.getAccessSymbols());
    }

    for (final ClassScope c : this.classScopes) {
      result.addAll(c.getAccessSymbols());
    }

    return result;
  }

  public List<ClassScope> getClassScopes() {
    return classScopes;
  }
}
