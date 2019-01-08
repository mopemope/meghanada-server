package meghanada.reflect;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.io.Serializable;
import meghanada.utils.ClassNameUtils;

public class MethodParameter implements Serializable {

  private static final long serialVersionUID = -5993679147139308884L;

  public final String type;
  public final String name;
  public final boolean varargs;

  public MethodParameter(final String type, final String name, final boolean varargs) {
    this.type = type;
    this.name = name;
    this.varargs = varargs;
  }

  public String getParameter(boolean simple) {
    if (simple) {
      if (this.varargs) {
        String t = ClassNameUtils.removeTypeAndArray(type);
        return t + "... " + this.name;
      }
      String t = ClassNameUtils.getSimpleName(type);
      return t + ' ' + this.name;
    }
    return type + ' ' + name;
  }

  public String getType() {
    return type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MethodParameter)) return false;
    MethodParameter that = (MethodParameter) o;
    return varargs == that.varargs
        && Objects.equal(type, that.type)
        && Objects.equal(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type, name, varargs);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("type", type)
        .add("name", name)
        .add("varargs", varargs)
        .toString();
  }
}
