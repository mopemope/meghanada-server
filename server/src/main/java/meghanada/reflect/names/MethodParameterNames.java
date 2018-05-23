package meghanada.reflect.names;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodParameterNames implements Serializable {

  private static final long serialVersionUID = 6556924934040150075L;
  // key = methodName: val = Set
  public final Map<String, List<List<ParameterName>>> names = new HashMap<>();
  public String className;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MethodParameterNames that = (MethodParameterNames) o;
    return Objects.equal(className, that.className) && Objects.equal(names, that.names);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(className, names);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("className", className)
        .add("names", names)
        .toString();
  }
}
