package meghanada.reference;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class Reference {

  private final String path;
  private final long line;
  private final long column;
  private final String code;

  public Reference(String path, long line, long column, String code) {
    this.path = path;
    this.line = line;
    this.column = column;
    this.code = code;
  }

  public String getPath() {
    return path;
  }

  public long getLine() {
    return line;
  }

  public long getColumn() {
    return column;
  }

  public String getCode() {
    return code;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("path", path)
        .add("line", line)
        .add("column", column)
        .add("code", code)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Reference reference = (Reference) o;
    return line == reference.line && Objects.equal(path, reference.path);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(path, line);
  }
}
