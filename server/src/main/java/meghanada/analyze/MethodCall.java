package meghanada.analyze;

import static java.util.Objects.nonNull;

import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MethodCall extends AccessSymbol {

  private static final long serialVersionUID = -2434830191914184294L;
  private static final Logger log = LogManager.getLogger(MethodCall.class);
  public Range nameRange;
  public boolean constructor;
  public List<String> arguments = Collections.emptyList();

  public MethodCall(final String name, final int pos, final Range nameRange, final Range range) {
    super(name, pos, range);
    this.nameRange = nameRange;
  }

  public MethodCall(
      final String name,
      final int pos,
      final Range nameRange,
      final Range range,
      boolean constructor) {
    this(name, pos, nameRange, range);
    this.constructor = constructor;
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
}
