package meghanada.analyze;

public class MethodCall extends AccessSymbol {

    public MethodCall() {
        super();
    }

    public MethodCall(final String name, final int pos, final Range range) {
        super(name, pos, range);
    }

}
