package meghanada.analyze;

public class LineRange {

  public int startPos;
  public int endPos;

  public LineRange() {}

  public LineRange(final int startPos, final int endPos) {
    this.startPos = startPos;
    this.endPos = endPos;
  }

  public boolean contains(int pos) {
    return this.startPos <= pos && pos <= this.endPos;
  }

  public int getEndPos() {
    return endPos;
  }
}
