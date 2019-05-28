package meghanada.debugger;

import static java.util.Objects.nonNull;

import com.google.common.base.Objects;
import java.io.File;

public class Breakpoint {

  private final Integer line;
  private File file;
  private String className;

  public Breakpoint(File file, Integer line) {
    this.file = file;
    this.line = line;
  }

  public Breakpoint(String className, Integer line) {
    this.className = className;
    this.line = line;
  }

  public boolean hasFile() {
    return nonNull(this.file);
  }

  public Integer getLine() {
    return line;
  }

  public String getClassName() {
    return className;
  }

  public File getFile() {
    return file;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Breakpoint)) return false;
    Breakpoint that = (Breakpoint) o;
    return Objects.equal(line, that.line)
        && Objects.equal(file, that.file)
        && Objects.equal(className, that.className);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(line, file, className);
  }
}
