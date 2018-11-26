package meghanada.analyze;

import com.sun.tools.javac.tree.EndPosTable;

public class SourceContext {

  private final Source source;
  private boolean assign;
  private boolean isParameter;
  private boolean isArgument;
  private String argumentFQCN;
  private int argumentIndex = -1;
  private EndPosTable endPosTable;

  public SourceContext(final Source source) {
    this.source = source;
  }

  public SourceContext(final Source source, final EndPosTable endPosTable) {
    this.source = source;
    this.endPosTable = endPosTable;
  }

  public String getArgumentFQCN() {
    return argumentFQCN;
  }

  public void setArgumentFQCN(final String argumentFQCN) {
    if (isArgument) {
      this.argumentFQCN = argumentFQCN;
    }
  }

  public boolean isParameter() {
    return isParameter;
  }

  public void setParameter(boolean parameter) {
    isParameter = parameter;
  }

  public Source getSource() {
    return source;
  }

  public EndPosTable getEndPosTable() {
    return endPosTable;
  }

  public void setEndPosTable(EndPosTable endPosTable) {
    this.endPosTable = endPosTable;
  }

  public boolean isArgument() {
    return isArgument;
  }

  public void setArgument(boolean argument) {
    this.isArgument = argument;
  }

  public int getArgumentIndex() {
    return argumentIndex;
  }

  public void setArgumentIndex(int argumentIndex) {
    this.argumentIndex = argumentIndex;
  }

  public void incrArgumentIndex() {
    this.argumentIndex++;
  }

  public boolean isAssign() {
    return assign;
  }

  public void setAssign(boolean assign) {
    this.assign = assign;
  }
}
