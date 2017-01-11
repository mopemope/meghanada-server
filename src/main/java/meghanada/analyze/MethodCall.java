package meghanada.analyze;

public class MethodCall extends AccessSymbol {

    public Range nameRange;

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
