package meghanada.analyze;

import com.sun.tools.javac.tree.EndPosTable;

class SourceContext {

  final Source source;
  boolean parameter;
  boolean argument;
  String argumentFQCN;
  int argumentIndex = -1;
  EndPosTable endPosTable;

  SourceContext(final Source source) {
    this.source = source;
  }

  SourceContext(final Source source, final EndPosTable endPosTable) {
    this.source = source;
    this.endPosTable = endPosTable;
  }

  void setArgumentFQCN(final String argumentFQCN) {
    if (this.argument) {
      this.argumentFQCN = argumentFQCN;
    }
  }

  void setArgumentIndex(int argumentIndex) {
    this.argumentIndex = argumentIndex;
  }

  void incrArgumentIndex() {
    this.argumentIndex++;
  }
}
