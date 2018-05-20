package meghanada.analyze;

import static java.util.Objects.nonNull;

import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.util.Collections;
import java.util.Optional;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.FieldDescriptor;
import meghanada.reflect.MemberDescriptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Variable implements Serializable {

  private static final long serialVersionUID = -6232129655957867573L;
  private static Logger log = LogManager.getLogger(Variable.class);

  public String name;
  public int pos;
  public Range range;
  public String fqcn;

  public boolean isDef;
  public boolean isParameter;
  public boolean isField;
  public int argumentIndex = -1;
  public String modifier;
  public String declaringClass;

  public Variable(final String name, final int pos, final Range range) {
    this.name = name;
    this.pos = pos;
    this.range = range;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("modifier", modifier)
        .add("name", name)
        .add("fqcn", fqcn)
        .add("range", range)
        .add("isField", isField)
        .add("isDef", isDef)
        .add("isParameter", isParameter)
        .add("pos", pos)
        .toString();
  }

  public boolean isDecl() {
    return isDef;
  }

  public CandidateUnit toCandidateUnit() {
    return FieldDescriptor.createVar("", this.name, this.fqcn);
  }

  public Optional<MemberDescriptor> toMemberDescriptor() {
    if (this.isField && nonNull(this.modifier)) {
      FieldDescriptor descriptor =
          new FieldDescriptor(this.declaringClass, this.name, this.modifier, this.fqcn);
      descriptor.setTypeParameters(Collections.emptySet());
      return Optional.of(descriptor);
    }
    return Optional.empty();
  }
}
