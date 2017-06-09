package meghanada.analyze;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import jetbrains.exodus.entitystore.Entity;

public class FieldAccess extends AccessSymbol {

  public static final String ENTITY_TYPE = "FieldAccess";
  private static final long serialVersionUID = -1933640313689982694L;
  public boolean isEnum;

  public FieldAccess(final String name, final int pos, final Range range) {
    super(name, pos, range);
  }

  @Override
  public boolean match(int line, int column) {
    return this.range.begin.line == line && this.containsColumn(column);
  }

  void setEntityProps(Entity entity) {

    requireNonNull(this.returnType, this.toString());
    Range range = this.range;

    entity.setProperty("declaringClass", this.declaringClass);
    entity.setProperty("name", this.name);
    if (nonNull(this.returnType)) {
      entity.setProperty("returnType", this.returnType);
    }

    entity.setProperty("beginLine", range.begin.line);
    entity.setProperty("beginColumn", range.begin.column);

    entity.setProperty("endLine", range.end.line);
    entity.setProperty("endColumn", range.end.column);
  }
}
