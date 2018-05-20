package meghanada.analyze;

import static java.util.Objects.nonNull;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

public class ExpressionScope extends Scope {

  private static final Logger log = LogManager.getLogger(ExpressionScope.class);
  private static final long serialVersionUID = 5266844440597025135L;

  public AccessSymbol expressionReturn;
  public boolean isField;
  public Scope parent;
  public String modifier;
  public String declaringClass;

  public ExpressionScope(int pos, Range range) {
    super(pos, range);
  }

  @Override
  public void addMethodCall(MethodCall mcs) {
    final long endCol = super.range.end.column;
    final long endLine = super.range.end.line;
    final Position mcsEnd = mcs.range.end;
    if (mcsEnd.column + 1 == endCol && mcsEnd.line == endLine) {
      this.expressionReturn = mcs;
    }
    super.addMethodCall(mcs);
  }

  @Override
  public void addFieldAccess(FieldAccess fa) {
    final long endCol = super.range.end.column;
    final long endLine = super.range.end.line;
    final Position mcsEnd = fa.range.end;
    if (mcsEnd.column + 1 == endCol && mcsEnd.line == endLine) {
      this.expressionReturn = fa;
    }
    super.addFieldAccess(fa);
  }

  @Override
  public void dumpVariable() {
    EntryMessage em = log.traceEntry("**** {} {}", this.getClassName(), this.range);
    super.dumpVariable(log);
    log.traceExit(em);
  }

  @Override
  public void dumpFieldAccess() {
    EntryMessage em = log.traceEntry("**** {} {}", this.getClassName(), this.range);
    super.dumpFieldAccess(log);
    log.traceExit(em);
  }

  @Override
  public void dump() {
    EntryMessage em = log.traceEntry("**** {} {}", this.getClassName(), this.range);
    super.dumpVariable(log);
    super.dumpFieldAccess(log);
    log.traceExit(em);
  }

  public Optional<AccessSymbol> getExpressionReturn() {
    EntryMessage em = log.traceEntry("expressionReturn={}", this.expressionReturn);
    Optional<Variable> var = this.variables.stream().filter(Variable::isDecl).findFirst();

    if (var.isPresent()) {
      return log.traceExit(em, Optional.empty());
    }

    Optional<AccessSymbol> aReturn = Optional.ofNullable(this.expressionReturn);
    return log.traceExit(em, aReturn);
  }

  @Override
  public void addVariable(final Variable variable) {
    assert variable.fqcn != null;
    if (this.isField) {
      variable.isField = true;
      if (nonNull(this.declaringClass) && nonNull(this.modifier)) {
        // member
        variable.modifier = this.modifier.trim();
        variable.declaringClass = this.declaringClass.trim();
        Scope parent = this.parent;
        if (parent instanceof TypeScope) {
          TypeScope typeScope = (TypeScope) parent;
          variable.toMemberDescriptor().ifPresent(typeScope::addMember);
        }
      }
    }
    super.addVariable(variable);
  }
}
