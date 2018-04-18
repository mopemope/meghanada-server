package meghanada.reflect;

import com.google.common.base.MoreObjects;
import java.io.Serializable;
import meghanada.utils.ClassNameUtils;

public class MethodParameter implements Serializable {

  private static final long serialVersionUID = 2931973575424068754L;

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
}
