package meghanada.analyze;

import com.sun.tools.javac.tree.EndPosTable;

public class SourceContext {

    private final Source source;
    private boolean isParameter;
    private boolean isArgument;
    private String argumentFQCN;
    private EndPosTable endPosTable;

    public SourceContext(final Source source) {
        this.source = source;
    }

    public String getArgumentFQCN() {
        return argumentFQCN;
    }

    public void setArgumentFQCN(String argumentFQCN) {
        if (isArgument) {
            this.argumentFQCN = argumentFQCN;
        } else {
            this.argumentFQCN = "";
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
        isArgument = argument;
    }
}
