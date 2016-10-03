package meghanada.parser.source;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.github.javaparser.Range;
import com.google.common.base.MoreObjects;
import meghanada.reflect.MemberDescriptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

// Class or Interface
@DefaultSerializer(TypeScopeSerializer.class)
public class TypeScope extends MethodScope {

    private static Logger log = LogManager.getLogger(TypeScope.class);

    public Map<String, Variable> fieldSymbols = new HashMap<>(32);
    public List<MemberDescriptor> memberDescriptors = new ArrayList<>(32);
    String pkg;
    String fqcn;

    private Deque<String> currentMethod = new ArrayDeque<>(8);

    public TypeScope(final String pkg, final String name, final Range range, final Range nameRange) {
        super(name, range, nameRange);
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

    public void addFieldSymbol(final Variable ns) {
        this.fieldSymbols.put(ns.name, ns);
        log.debug("add fieldSymbol name:{}, {}", this.name, ns);
    }

    public void addMemberDescriptor(MemberDescriptor memberDescriptor) {
        this.memberDescriptors.add(memberDescriptor);
    }

    public BlockScope currentBlock() {
        return super.currentScope.peek();
    }

    public BlockScope endBlock() {
        super.innerScopes.add(this.currentBlock());
        return super.currentScope.remove();
    }

    public void startMethod(String name) {
        this.currentMethod.push(name);
    }

    public String currentMethod() {
        return this.currentMethod.peek();
    }

    public String endMethod() {
        return this.currentMethod.remove();
    }

    public Variable getFieldSymbol(String name) {
        return this.fieldSymbols.get(name);
    }

    public Map<String, Variable> getFieldSymbols() {
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
        final Scope scope = getScope(line, super.innerScopes);
        if (scope != null && (scope instanceof BlockScope)) {
            final BlockScope blockScope = (BlockScope) scope;
            final Optional<ExpressionScope> result = blockScope.getExpression(line);
            final Optional<AccessSymbol> accessSymbol = result.flatMap(ExpressionScope::getExpressionReturn);
            return accessSymbol.orElse(null);
        }

        final Optional<ExpressionScope> result = this.getExpression(line);
        final Optional<AccessSymbol> accessSymbol = result.flatMap(ExpressionScope::getExpressionReturn);
        return accessSymbol.orElse(null);
    }

    public List<MemberDescriptor> getMemberDescriptors() {
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
