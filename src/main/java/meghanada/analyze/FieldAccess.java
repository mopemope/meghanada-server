package meghanada.analyze;

public class FieldAccess extends AccessSymbol {

    public boolean isEnum;

    public FieldAccess() {
        super();
    }

    public FieldAccess(final String name, final int pos, final Range range) {
        super(name, pos, range);
    }

}
