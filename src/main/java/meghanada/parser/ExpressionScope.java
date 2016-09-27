package meghanada.parser;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

public class ExpressionScope extends Scope {

    private static Logger log = LogManager.getLogger(ExpressionScope.class);
    private AccessSymbol expressionReturn;

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
    MethodCallSymbol addMethodCall(final MethodCallSymbol mcs) {
        final Integer endCol = super.range.end.column;
        final Integer endLine = super.range.end.line;
        final Position end = mcs.getRange().end;

        if (end.column + 1 == endCol && end.line == endLine) {
            log.trace("expressionName:{} endCol:{} endLine:{} mcsPos:{}", this.name, endCol, endLine, end);
            this.expressionReturn = mcs;
        }
        return super.addMethodCall(mcs);
    }

    @Override
    FieldAccessSymbol addFieldAccess(FieldAccessSymbol fas) {
        final Integer endCol = super.range.end.column;
        final Integer endLine = super.range.end.line;
        final Position end = fas.getRange().end;

        if (end.column + 1 == endCol && end.line == endLine) {
            log.trace("expressionName:{} endCol:{} endLine:{} fasPos:{}", this.name, endCol, endLine, end);
            this.expressionReturn = fas;
        }
        return super.addFieldAccess(fas);
    }


}
