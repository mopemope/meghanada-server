package meghanada.analyze;

import java.util.ArrayList;
import java.util.List;

public class MethodCall extends AccessSymbol {

    public Range nameRange;
    public List<String> arguments = new ArrayList<>(0);

    public MethodCall() {
        super();
    }

    public MethodCall(final String name, final int pos, final Range nameRange, final Range range) {
        super(name, pos, range);
        this.nameRange = nameRange;
    }

    public MethodCall(final String scope, final String name, final int pos, final Range nameRange, final Range range) {
        super(name, pos, range);
        this.nameRange = nameRange;
        super.scope = scope;
    }

    @Override
    public boolean match(int line, int column) {
        return this.range.end.line == line && this.range.end.column == column;
    }

    public boolean nameContains(final int column) {
        return this.nameRange.begin.column <= column && column <= this.nameRange.end.column;
    }

}
