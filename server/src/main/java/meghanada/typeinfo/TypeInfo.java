package meghanada.typeinfo;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class TypeInfo {

  private final String fqcn;
  private final List<String> hierarchy;
  private final List<String> interfaces;
  private final List<Pair<String, String>> members = new ArrayList<>(1);

  public TypeInfo(String fqcn, List<String> hierarchy, List<String> interfaces) {
    this.fqcn = fqcn;
    this.hierarchy = hierarchy;
    this.interfaces = interfaces;
  }

  public TypeInfo(String fqcn, List<String> hierarchy) {
    this(fqcn, hierarchy, Collections.emptyList());
  }

  public TypeInfo(String fqcn) {
    this(fqcn, new ArrayList<>(1), Collections.emptyList());
    this.hierarchy.add(fqcn);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("classes", hierarchy)
        .add("interfaces", interfaces)
        .toString();
  }

  public String getFqcn() {
    return fqcn;
  }

  public List<String> getHierarchy() {
    return this.hierarchy;
  }

  public List<String> getInterfaces() {
    return this.interfaces;
  }

  public List<Pair<String, String>> getMembers() {
    return this.members;
  }

  public void addMember(String name, String member) {
    this.members.add(Pair.of(name, member));
  }
}
