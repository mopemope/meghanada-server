package meghanada.reflect.asm;

import com.google.common.base.MoreObjects;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InheritanceInfo {

  final String targetClass;
  final Map<File, List<String>> classFileMap = new HashMap<>(8);
  List<String> inherit = new ArrayList<>(8);

  public InheritanceInfo(String targetClass) {
    this.targetClass = targetClass;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("targetClass", targetClass)
        .add("inherit", inherit)
        .add("classFileMap", classFileMap)
        .toString();
  }
}
