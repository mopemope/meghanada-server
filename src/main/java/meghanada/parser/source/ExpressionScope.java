package meghanada.parser.source;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.google.common.base.MoreObjects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

@DefaultSerializer(ExpressionScopeSerializer.class)
public class ExpressionScope extends Scope {

    private static Logger log = LogManager.getLogger(ExpressionScope.class);
    AccessSymbol expressionReturn;

    public ExpressionScope(final String name, final Range range) {
        super(name, range);
    }

    public Optional<AccessSymbol> getExpressionReturn() {
        log.traceEntry("expressionReturn={}", this.expressionReturn);
        final Optional<Variable> var = this.nameSymbols
                .stream()
                .filter(Variable::isDeclaration)
                .findFirst();

        if (var.isPresent()) {
            return log.traceExit(Optional.empty());
        }

        final Optional<AccessSymbol> aReturn = Optional.ofNullable(this.expressionReturn);
        return log.traceExit(aReturn);
    }

    @Override
    public MethodCallSymbol addMethodCall(final MethodCallSymbol mcs) {
        final Integer endCol = super.range.end.column;
        final Integer endLine = super.range.end.line;
        final Position mcsEnd = mcs.getRange().end;

        if (mcsEnd.column + 1 == endCol && mcsEnd.line == endLine) {
            log.trace("expressionName:{} endCol:{} endLine:{} mcsPos:{}", this.name, endCol, endLine, mcsEnd);
            this.expressionReturn = mcs;
        }
        return super.addMethodCall(mcs);
    }

    @Override
    public FieldAccessSymbol addFieldAccess(FieldAccessSymbol fas) {
        final Integer endCol = super.range.end.column;
        final Integer endLine = super.range.end.line;
        final Position fasEnd = fas.getRange().end;

        if (fasEnd.column + 1 == endCol && fasEnd.line == endLine) {
            log.trace("expressionName:{} endCol:{} endLine:{} fasPos:{}", this.name, endCol, endLine, fasEnd);
            this.expressionReturn = fas;
        }
        return super.addFieldAccess(fas);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("range", this.range)
                .add("methodCalls", this.methodCalls)
                .add("fieldAccesses", this.fieldAccesses)
                .add("expressionReturn", expressionReturn)
                .toString();
    }
}
