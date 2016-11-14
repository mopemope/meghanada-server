package meghanada.analyze;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

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
}