package meghanada.reflect;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.io.Serializable;
import meghanada.utils.ClassNameUtils;

public class MethodParameter implements Serializable {

  private static final long serialVersionUID = 5364004286868603967L;
  public final String type;
  public String name;

  public MethodParameter(final String type, final String name) {
    this.type = type;
    this.name = name;
  }

  public String getParameter(boolean simple) {
    if (simple) {
      return ClassNameUtils.getSimpleName(type) + ' ' + name;
    }
    return type + ' ' + name;
  }

  public String getType() {
    return type;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("type", type).add("name", name).toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MethodParameter)) return false;
    MethodParameter that = (MethodParameter) o;
    return Objects.equal(type, that.type) && Objects.equal(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type, name);
  }
}
