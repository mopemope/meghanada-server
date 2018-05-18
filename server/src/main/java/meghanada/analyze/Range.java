package meghanada.analyze;

import com.google.common.base.MoreObjects;
import java.io.Serializable;

public class Range implements Serializable {

  private static final long serialVersionUID = 4664505910001855783L;

  public final Position begin;
  public final Position end;

  public Range(final int beginCol, int beginLine, final int endCol, int endLine) {
    this(new Position(beginCol, beginLine), new Position(endCol, endLine));
  }

  public Range(final Position begin, final Position end) {
    this.begin = begin;
    this.end = end;
  }

  public static Range create(final Source src, final int beginPos, final int endPos) {
    final Position begin = src.getPos(beginPos);
    final Position end = src.getPos(endPos);
    return new Range(begin, end);
  }

  public boolean containsLine(final int line) {
    final long start = this.begin.line;
    final long end = this.end.line;
    return start <= line && line <= end;
  }

  public boolean containsColumn(final int col) {
    if (this.begin.line != this.end.line) {
      return false;
    }
    final long start = this.begin.column;
    final long end = this.end.column;
    return start <= col && col <= end;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("b", begin).add("e", end).toString();
  }
}
