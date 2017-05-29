package meghanada.analyze;

import java.io.Serializable;

public class LineRange implements Serializable {

  private static final long serialVersionUID = 4068103819277244238L;

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
