package meghanada.analyze;

import com.google.common.base.MoreObjects;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

public class ClassScope extends TypeScope {

  private static final Logger log = LogManager.getLogger(ClassScope.class);
  public final List<ClassScope> classScopes = new ArrayList<>(1);
  public final Deque<ClassScope> currentClassScope = new ArrayDeque<>(1);

  public ClassScope() {}

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
    if (classScope == null) {
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
    return MoreObjects.toStringHelper(this).add("fqcn", name).add("range", range).toString();
  }
}
