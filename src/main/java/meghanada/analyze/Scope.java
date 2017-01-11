package meghanada.analyze;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public abstract class Scope {

    private static Logger log = LogManager.getLogger(Scope.class);
    public int pos;
    public Range range;

    public Set<Variable> variables = new HashSet<>(16);
    public List<FieldAccess> fieldAccesses = new ArrayList<>(16);
    public List<MethodCall> methodCalls = new ArrayList<>(16);

    Scope() {

    }

    Scope(final int pos, final Range range) {
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
                if (scope instanceof BlockScope) {
                    final BlockScope bs = (BlockScope) scope;
                    final Scope inScope = Scope.getInnerScope(line, bs.scopes);
                    if (inScope != null) {
                        return inScope;
                    }
                }
                return scope;
            }
        }
        return null;
    }

    public FieldAccess addFieldAccess(final FieldAccess fieldAccess) {
        this.fieldAccesses.add(fieldAccess);
        log.debug("add fieldAccess={} to range={} {}", fieldAccess, this.range, this.getClassName());
        return fieldAccess;
    }

    public MethodCall addMethodCall(final MethodCall methodCall) {
        this.methodCalls.add(methodCall);
        log.debug("add methodCall={} to range={} {}", methodCall, this.range, this.getClassName());
        return methodCall;
    }

    public Variable addVariable(final Variable variable) {
        this.variables.add(variable);
        log.debug("add variable={} to range={} {}", variable, this.range, this.getClassName());
        return variable;
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
            if (!v.name.equals("NULL_LITERAL") && v.fqcn == null) {
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
        if (className.equals("ClassScope")) {
            return "Class";
        } else if (className.equals("MethodScope")) {
            final MethodScope methodScope = (MethodScope) this;
            return methodScope.isConstructor ? "Constructor" : "Method";
        } else {
            return "Block";
        }
    }

    public List<MethodCall> getMethodCall(final int line) {
        log.traceEntry("line={}", line);

        final List<MethodCall> result = this.methodCalls
                .stream()
                .filter(mc -> mc.range.begin.line == line)
                .collect(Collectors.toList());
        return log.traceExit(result);
    }

    public List<FieldAccess> getFieldAccess(final int line) {
        return this.fieldAccesses
                .stream()
                .filter(fa -> fa.range.begin.line == line)
                .collect(Collectors.toList());
    }

    public Map<String, Variable> getDeclaratorMap() {
        final Map<String, Variable> result = new HashMap<>(32);
        variables.stream()
                .filter(Variable::isDecl)
                .forEach(v -> result.putIfAbsent(v.name, v));
        return result;
    }

    public Map<String, Variable> getDeclaratorMap(final int line) {
        if (this.contains(line)) {
            final Map<String, Variable> result = new HashMap<>(32);
            variables.stream()
                    .filter(Variable::isDecl)
                    .forEach(v -> result.putIfAbsent(v.name, v));
            return result;
        }
        return Collections.emptyMap();
    }
}