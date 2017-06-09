package meghanada.analyze;

import static java.util.Objects.nonNull;

import java.util.Collections;
import java.util.List;
import jetbrains.exodus.entitystore.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MethodCall extends AccessSymbol {

  public static final String ENTITY_TYPE = "MethodCall";
  public static final String ARGS_ENTITY_TYPE = "Arguments";
  private static final long serialVersionUID = -2434830191914184294L;
  private static final Logger log = LogManager.getLogger(MethodCall.class);
  public Range nameRange;
  public List<String> arguments = Collections.emptyList();

  public MethodCall(final String name, final int pos, final Range nameRange, final Range range) {
    super(name, pos, range);
    this.nameRange = nameRange;
  }

  public MethodCall(
      final String scope,
      final String name,
      final int pos,
      final Range nameRange,
      final Range range) {
    super(name, pos, range);
    this.nameRange = nameRange;
    if (scope.length() <= SCOPE_LIMIT) {
      super.scope = scope;
    } else {
      super.scope = name;
    }
  }

  public List<String> getArguments() {
    if (nonNull(this.arguments)) {
      return this.arguments;
    }
    return Collections.emptyList();
  }

  public void setArguments(final List<String> arguments) {
    if (nonNull(arguments)) {
      this.arguments = arguments;
    }
  }

  @Override
  public boolean match(int line, int column) {
    return this.range.end.line == line && this.range.end.column == column;
  }

  public boolean nameContains(final int column) {
    return this.nameRange.begin.column <= column && column <= this.nameRange.end.column;
  }

  void setEntityProps(Entity entity) {

    Range range = this.nameRange;

    if (nonNull(this.declaringClass)) {
      entity.setProperty("declaringClass", this.declaringClass);
    }
    entity.setProperty("name", this.name);
    if (nonNull(this.returnType)) {
      entity.setProperty("returnType", this.returnType);
    }
    entity.setProperty("beginLine", range.begin.line);
    entity.setProperty("beginColumn", range.begin.column);

    entity.setProperty("endLine", range.end.line);
    entity.setProperty("endColumn", range.end.column);
  }
}
