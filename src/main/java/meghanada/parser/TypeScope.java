package meghanada.parser;

import com.github.javaparser.Range;
import com.google.common.base.MoreObjects;
import meghanada.reflect.MemberDescriptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

// Class or Interface
public class TypeScope extends MethodScope {

    private static Logger log = LogManager.getLogger(TypeScope.class);

    Map<String, Variable> fieldSymbols = new HashMap<>(32);
    private List<MemberDescriptor> memberDescriptors = new ArrayList<>(32);
    private Deque<String> currentMethod = new ArrayDeque<>(8);

    private String pkg;
    private String fqcn;

    TypeScope(final String pkg, final String type, final Range range, final Range nameRange) {
        super(type, range, nameRange);
        this.pkg = pkg;
        if (pkg != null) {
            this.fqcn = this.pkg + "." + this.name;
        } else {
            this.fqcn = this.name;
        }
    }

    public String getType() {
        return name;
    }

    public String getFQCN() {
        return this.fqcn;
    }

    void addFieldSymbol(String name, Variable ns) {
        this.fieldSymbols.put(name, ns);
        log.debug("add fieldSymbol name:{}, {}", this.name, ns);
    }

    void addMemberDescriptor(MemberDescriptor cu) {
        this.memberDescriptors.add(cu);
    }

//    void startBlock(String name, int begin, int end, int colStart) {
//        // add method
//        MethodScope scope = new MethodScope(name, begin, end, colStart);
//        super.startBlock(scope);
//    }

    BlockScope currentBlock() {
        return super.currentScope.peek();
    }

    BlockScope endBlock() {
        super.innerScopes.add(this.currentBlock());
        return super.currentScope.remove();
    }

    void startMethod(String name) {
        this.currentMethod.push(name);
    }

    String currentMethod() {
        return this.currentMethod.peek();
    }

    String endMethod() {
        return this.currentMethod.remove();
    }

    public Variable getFieldSymbol(String name) {
        return this.fieldSymbols.get(name);
    }

    Map<String, Variable> getFieldSymbols() {
        return this.fieldSymbols;
    }

    @Override
    public Set<Variable> getNameSymbol(int line) {
        Scope scope = getScope(line, super.innerScopes);
        if (scope != null) {
            return scope.getNameSymbol(line);
        }
        return Collections.emptySet();
    }

    @Override
    public List<MethodCallSymbol> getMethodCallSymbols(int line) {
        Scope scope = getScope(line, super.innerScopes);
        if (scope != null) {
            return scope.getMethodCallSymbols(line);
        }
        return super.methodCalls.stream()
                .filter(methodCallSymbol -> methodCallSymbol.containsLine(line))
                .collect(Collectors.toList());
    }

    @Override
    public List<FieldAccessSymbol> getFieldAccessSymbols(final int line) {
        Scope scope = getScope(line, super.innerScopes);
        if (scope != null) {
            return scope.getFieldAccessSymbols(line);
        }

        // search this
        return super.fieldAccesses
                .stream()
                .filter(fieldAccessSymbol -> fieldAccessSymbol.range.begin.line == line)
                .collect(Collectors.toList());
    }

    public AccessSymbol getExpressionReturn(final int line) {
        Scope scope = getScope(line, super.innerScopes);
        if (scope != null) {
            if (scope instanceof BlockScope) {
                BlockScope blockScope = (BlockScope) scope;
                final Optional<ExpressionScope> result = blockScope.getExpression(line);
                final Optional<AccessSymbol> accessSymbol = result.flatMap(ExpressionScope::getExpressionReturn);
                return accessSymbol.orElse(null);
            }
        }

        final Optional<ExpressionScope> result = this.getExpression(line);
        final Optional<AccessSymbol> accessSymbol = result.flatMap(ExpressionScope::getExpressionReturn);
        return accessSymbol.orElse(null);
    }

    List<MemberDescriptor> getMemberDescriptors() {
        return memberDescriptors;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("pkg", pkg)
                .add("declaringClass", fqcn)
                .add("fieldSymbol", fieldSymbols)
//                .add("memberDescriptors", memberDescriptors)
//                .add("methodCalls", methodCalls)
//                .add("fieldAccesses", fieldAccesses)
                .toString();
    }

    public String getPackage() {
        return pkg;
    }
}
