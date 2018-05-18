package meghanada.analyze;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class Scope implements Serializable {

  private static final long serialVersionUID = -4765110551444765813L;
  private static final Logger log = LogManager.getLogger(Scope.class);

  public final Set<Variable> variables = new HashSet<>(16);
  public final List<FieldAccess> fieldAccesses = new ArrayList<>(16);
  public final List<MethodCall> methodCalls = new ArrayList<>(16);
  public final int pos;
  public Range range;

  public Scope(final int pos, final Range range) {
    this.pos = pos;
    this.range = range;
  }

  public static Scope getScope(final int line, final List<? extends Scope> scopeList) {
    for (final Scope scope : scopeList) {
      if (scope.contains(line)) {
        return scope;
      }
    }
    return null;
  }

  public static Scope getInnerScope(final int line, final List<? extends Scope> scopeList) {
    for (Scope scope : scopeList) {
      if (scope.contains(line)) {
        if (scope instanceof ClassScope) {
          final ClassScope cs = (ClassScope) scope;
          final Scope inScope = Scope.getInnerScope(line, cs.classScopes);
          if (nonNull(inScope)) {
            return inScope;
          }
        }
        if (scope instanceof BlockScope) {
          final BlockScope bs = (BlockScope) scope;
          final Scope inScope = Scope.getInnerScope(line, bs.scopes);
          if (nonNull(inScope)) {
            return inScope;
          }
        }
        return scope;
      }
    }
    return null;
  }

  public void addFieldAccess(final FieldAccess fieldAccess) {
    this.fieldAccesses.add(fieldAccess);
    log.trace("add fieldAccess={} to range={} {}", fieldAccess, this.range, this.getClassName());
  }

  public void addMethodCall(final MethodCall methodCall) {
    this.methodCalls.add(methodCall);
    log.trace("add methodCall={} to range={} {}", methodCall, this.range, this.getClassName());
  }

  public void addVariable(final Variable variable) {
    assert variable.fqcn != null;
    this.variables.add(variable);
    log.trace("add variable={} to range={} {}", variable, this.range, this.getClassName());
  }

  public boolean contains(final int line) {
    return this.range.containsLine(line);
  }

  public Optional<Variable> findVariable(final int pos) {
    for (final Variable v : this.variables) {
      if (v.pos == pos) {
        return Optional.of(v);
      }
    }
    return Optional.empty();
  }

  public void dump() {
    this.dumpVariable(log);
    this.dumpFieldAccess(log);
  }

  public void dump(final Logger logger) {
    this.dumpVariable(logger);
    this.dumpFieldAccess(logger);
  }

  public void dumpVariable() {
    this.dumpVariable(log);
  }

  public void dumpVariable(final Logger logger) {
    for (final Variable v : this.variables) {
      if (!v.name.equals("NULL_LITERAL") && isNull(v.fqcn)) {
        logger.warn("missing fqcn {}", v);
      } else {
        logger.trace("# {}", v);
      }
    }
  }

  public void dumpFieldAccess() {
    this.dumpFieldAccess(log);
  }

  public void dumpFieldAccess(final Logger logger) {
    for (final FieldAccess fa : this.fieldAccesses) {
      if (fa.returnType == null) {
        logger.warn("missing returnType {}", fa);
      } else {
        logger.trace("# {}", fa);
      }
    }
    for (final MethodCall mc : this.methodCalls) {
      if (mc.returnType == null) {
        logger.warn("missing returnType {}", mc);
      } else {
        logger.trace("# {}", mc);
      }
    }
  }

  public String getClassName() {
    return getClass().getSimpleName();
  }

  protected String getScopeType() {
    final String className = this.getClassName();
    switch (className) {
      case "ClassScope":
        return "Class";
      case "MethodScope":
        final MethodScope methodScope = (MethodScope) this;
        return methodScope.isConstructor() ? "Constructor" : "Method";
      default:
        return "Block";
    }
  }

  public List<MethodCall> getMethodCall(final int line) {
    return this.methodCalls
        .stream()
        .filter(mc -> mc.range.begin.line == line)
        .collect(Collectors.toList());
  }

  public List<FieldAccess> getFieldAccess(final int line) {
    return this.fieldAccesses
        .stream()
        .filter(fa -> fa.range.begin.line == line)
        .collect(Collectors.toList());
  }

  public Map<String, Variable> getDeclaratorMap() {
    final Map<String, Variable> result = new HashMap<>(32);
    variables.stream().filter(Variable::isDecl).forEach(v -> result.putIfAbsent(v.name, v));
    return result;
  }

  public Map<String, Variable> getVariableMap() {
    final Map<String, Variable> result = new HashMap<>(32);
    getVariables()
        .forEach(
            v -> {
              if (v.isDecl()) {
                result.put(v.name, v);
              } else {
                result.putIfAbsent(v.name, v);
              }
            });
    return result;
  }

  public Set<Variable> getVariables() {
    return this.variables;
  }

  public Collection<FieldAccess> getFieldAccesses() {
    return this.fieldAccesses;
  }

  public Collection<MethodCall> getMethodCalls() {
    return this.methodCalls;
  }

  public Collection<AccessSymbol> getAccessSymbols() {
    final List<AccessSymbol> result = new ArrayList<>(this.fieldAccesses);
    result.addAll(this.methodCalls);
    return result;
  }
}
