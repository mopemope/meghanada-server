package meghanada.analyze;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

public class MethodScope extends BlockScope {

  private static final long serialVersionUID = -6957893688780569397L;
  private static final Logger log = LogManager.getLogger(MethodScope.class);

  private final List<String> parameters = new ArrayList<>(3);
  protected String name;
  protected String returnType;
  protected Range nameRange;
  private boolean isConstructor;

  public MethodScope(
      final String name, @Nullable final Range nameRange, final int pos, final Range range) {
    this(name, nameRange, pos, range, false);
  }

  public MethodScope(
      final String name,
      @Nullable final Range nameRange,
      final int pos,
      final Range range,
      final boolean isConstructor) {
    super(pos, range);
    this.name = name;
    this.nameRange = nameRange;
    this.isConstructor = isConstructor;
  }

  public void addMethodParameter(final String fqcn) {
    this.parameters.add(fqcn);
  }

  @Override
  public void dumpVariable() {
    final EntryMessage entryMessage =
        log.traceEntry("**** {} {} return {}", this.getScopeType(), this.name, this.returnType);

    super.dumpVariable(log);

    for (final ExpressionScope expressionScope : this.expressions) {
      expressionScope.dumpVariable();
    }

    for (final BlockScope blockScope : this.scopes) {
      blockScope.dumpVariable();
    }
    log.traceExit(entryMessage);
  }

  @Override
  public void dumpFieldAccess() {
    final EntryMessage entryMessage =
        log.traceEntry("**** {} {} return {}", this.getScopeType(), this.name, this.returnType);

    super.dumpFieldAccess(log);

    for (final ExpressionScope expressionScope : this.expressions) {
      expressionScope.dumpFieldAccess();
    }

    for (final BlockScope blockScope : this.scopes) {
      blockScope.dumpFieldAccess();
    }
    log.traceExit(entryMessage);
  }

  @Override
  public void dump() {
    final EntryMessage entryMessage =
        log.traceEntry(
            "**** {} {} return {} isParameter {}",
            this.getScopeType(),
            this.name,
            this.returnType,
            this.parameters);
    super.dumpVariable(log);

    for (final ExpressionScope expressionScope : this.expressions) {
      expressionScope.dumpVariable();
    }

    for (final BlockScope blockScope : this.scopes) {
      blockScope.dumpVariable();
    }

    super.dumpFieldAccess(log);

    for (final ExpressionScope expressionScope : this.expressions) {
      expressionScope.dumpFieldAccess();
    }

    for (final BlockScope blockScope : this.scopes) {
      blockScope.dumpFieldAccess();
    }
    log.traceExit(entryMessage);
  }

  @Override
  public String getName() {
    return this.name;
  }

  public Range getNameRange() {
    return nameRange;
  }

  public long getBeginLine() {
    return range.begin.line;
  }

  @Override
  public Map<String, Variable> getDeclaratorMap() {
    final Map<String, Variable> declaratorMap = super.getDeclaratorMap();
    for (final ExpressionScope es : this.expressions) {
      declaratorMap.putAll(es.getDeclaratorMap());
    }
    return declaratorMap;
  }

  @Override
  public Map<String, Variable> getVariableMap() {
    final Map<String, Variable> variableMap = super.getVariableMap();
    for (final ExpressionScope es : this.expressions) {
      es.getVariableMap()
          .forEach(
              (k, v) -> {
                if (v.isDecl()) {
                  variableMap.put(k, v);
                } else {
                  variableMap.putIfAbsent(k, v);
                }
              });
    }
    return variableMap;
  }

  @Override
  public Set<Variable> getVariables() {
    final Set<Variable> variables = super.getVariables();
    for (final ExpressionScope es : this.expressions) {
      variables.addAll(es.getVariables());
    }
    return variables;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("nameRange", nameRange)
        .add("isConstructor", isConstructor)
        .add("returnType", returnType)
        .add("parameters", parameters)
        .toString();
  }

  public List<String> getParameters() {
    return parameters;
  }

  public boolean isConstructor() {
    return isConstructor;
  }
}
