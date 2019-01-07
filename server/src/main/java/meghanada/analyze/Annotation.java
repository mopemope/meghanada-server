package meghanada.analyze;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.io.Serializable;

public class Annotation implements Symbol, Serializable {

  private static final long serialVersionUID = 7948194139024600219L;
  public String name;
  public int pos;
  public Range range;
  public String returnType;

  public Annotation(final String name, final int pos, final Range range) {
    this.name = name;
    this.returnType = name;
    this.pos = pos;
    this.range = range;
  }

  public boolean containsLine(final int line) {
    return this.range.containsLine(line);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("pos", pos)
        .add("range", range)
        .add("returnType", returnType)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Annotation)) return false;
    if (this == o) return true;
    Annotation that = (Annotation) o;
    return pos == that.pos
        && Objects.equal(name, that.name)
        && Objects.equal(range, that.range)
        && Objects.equal(returnType, that.returnType);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, pos, range, returnType);
  }

  public boolean containsColumn(final int col) {
    final long start = this.range.begin.column;
    final long end = this.range.end.column;
    return start <= col && col <= end;
  }

  @Override
  public String getFQCN() {
    return this.returnType;
  }
}
