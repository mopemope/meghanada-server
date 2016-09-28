package meghanada.parser.source;

import com.github.javaparser.Range;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

abstract class Scope {

    private static Logger log = LogManager.getLogger(Scope.class);
    protected final String name;
    protected final Range range;

    protected Set<Variable> nameSymbols = new HashSet<>(32);
    protected List<MethodCallSymbol> methodCalls = new ArrayList<>(32);
    protected List<FieldAccessSymbol> fieldAccesses = new ArrayList<>(32);

    Scope(final String name, final Range range) {
        this.name = name;
        this.range = range;
    }

    static Scope getScope(final int line, final List<? extends Scope> scopeList) {
        for (Scope scope : scopeList) {
            if (scope.contains(line)) {
                return scope;
            }
        }
        return null;
    }

    static Scope getInnerScope(final int line, final List<? extends Scope> scopeList) {
        for (Scope scope : scopeList) {
            if (scope.contains(line)) {
                if (scope instanceof BlockScope) {
                    final BlockScope bs = (BlockScope) scope;
                    final Scope scope1 = Scope.getInnerScope(line, bs.getInnerScopes());
                    if (scope1 != null) {
                        return scope1;
                    }
                }
                return scope;
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public int getBeginLine() {
        return this.range.begin.line;
    }

    public Range getRange() {
        return range;
    }

    public int getEndLine() {
        return this.range.end.line;
    }

    public boolean contains(final int line) {
        final int start = this.range.begin.line;
        final int end = this.range.end.line;
        return start <= line && line <= end;
    }

    public boolean containsSymbol(final String name) {
        for (Variable v : nameSymbols) {
            if (v.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public void addNameSymbol(final Variable var) {
        this.nameSymbols.add(var);
        log.debug("add variable scope:{} line:{} {}", this.name, var.getLine(), var);
    }

    public MethodCallSymbol addMethodCall(final MethodCallSymbol mcs) {
        this.methodCalls.add(mcs);
        log.debug("add methodCallSymbol scope:{} line:{} {}", this.name, mcs.getLine(), mcs);
        return mcs;
    }

    public FieldAccessSymbol addFieldAccess(final FieldAccessSymbol fas) {
        this.fieldAccesses.add(fas);
        log.debug("add fieldAccessSymbol scope:{} line:{} {}", this.name, fas.getLine(), fas);
        return fas;
    }

    public Set<Variable> getNameSymbol(final int line) {
        if (this.contains(line)) {
            return nameSymbols;
        }
        return Collections.emptySet();
    }


    public Map<String, Variable> getDeclaratorMap() {
        Map<String, Variable> result = new HashMap<>(32);
        nameSymbols.stream()
                .filter(Variable::isDeclaration)
                .forEach(v -> result.putIfAbsent(v.name, v));
        return result;
    }

    public Map<String, Variable> getDeclaratorMap(final int line) {
        if (this.contains(line)) {
            Map<String, Variable> result = new HashMap<>(32);
            nameSymbols.stream()
                    .filter(Variable::isDeclaration)
                    .forEach(v -> result.putIfAbsent(v.name, v));
            return result;
        }
        return Collections.emptyMap();
    }

    public List<MethodCallSymbol> getMethodCallSymbols(final int line) {
        log.traceEntry("line={}", line);
        final List<MethodCallSymbol> result = this.methodCalls
                .stream()
                .filter(methodCallSymbol -> methodCallSymbol.getRange().begin.line == line)
                .collect(Collectors.toList());
        return log.traceExit(result);
    }

    public List<FieldAccessSymbol> getFieldAccessSymbols(final int line) {
        return this.fieldAccesses.stream()
                .filter(fieldAccessSymbol -> fieldAccessSymbol.getRange().begin.line == line)
                .collect(Collectors.toList());
    }

    public Set<Variable> getNameSymbols() {
        return nameSymbols;
    }

    public List<MethodCallSymbol> getMethodCallSymbols() {
        return this.methodCalls;
    }

    public List<FieldAccessSymbol> getFieldAccessSymbols() {
        return this.fieldAccesses;
    }

}
