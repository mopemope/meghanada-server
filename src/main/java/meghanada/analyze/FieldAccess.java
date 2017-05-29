package meghanada.analyze;

public class FieldAccess extends AccessSymbol {

  private static final long serialVersionUID = -1933640313689982694L;

  public boolean isEnum;

  public FieldAccess(final String name, final int pos, final Range range) {
    super(name, pos, range);
  }

  @Override
  public boolean match(int line, int column) {
    return this.range.begin.line == line && this.containsColumn(column);
  }
}
