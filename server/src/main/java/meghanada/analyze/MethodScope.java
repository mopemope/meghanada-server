package meghanada.analyze;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.MethodDescriptor;
import meghanada.reflect.MethodParameter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

public class MethodScope extends BlockScope {

  private static final Logger log = LogManager.getLogger(MethodScope.class);
  private static final long serialVersionUID = -5255555840551352713L;
  protected final String declaringClass;
  private final List<String> parameters = new ArrayList<>(3);
  public boolean vararg;
  public String modifier;
  protected String name;
  protected String returnType;
  protected Range nameRange;

  private boolean isConstructor;
  private List<String> exceptions = new ArrayList<>();

  public MethodScope(
      final String declaringClass,
      final String name,
      @Nullable final Range nameRange,
      final int pos,
      final Range range,
      final boolean isConstructor) {
    super(pos, range);
    this.declaringClass = declaringClass;
    this.name = name;
    this.nameRange = nameRange;
    this.isConstructor = isConstructor;
  }

  void addMethodParameter(final String fqcn) {
    this.parameters.add(fqcn);
  }

  void addException(final String fqcn) {
    this.exceptions.add(fqcn);
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

  public MemberDescriptor toMemberDescriptor() {
    int size = this.parameters.size();
    List<MethodParameter> mps = new ArrayList<>(size);
    int i = 0;
    for (String type : parameters) {
      i++;
      boolean v = this.vararg && i == size;
      MethodParameter mp = new MethodParameter(type, "arg" + i, v);
      mps.add(mp);
    }
    String[] exceptions = getExceptions();
    return new MethodDescriptor(
        this.declaringClass,
        this.name,
        this.modifier,
        mps,
        exceptions,
        this.returnType,
        false,
        CandidateUnit.MemberType.METHOD);
  }

  private String[] getExceptions() {
    return this.exceptions.toArray(new String[0]);
  }
}
