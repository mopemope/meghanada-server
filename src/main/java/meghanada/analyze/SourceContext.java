package meghanada.analyze;

import com.sun.tools.javac.tree.EndPosTable;

public class SourceContext {

    private final Source source;
    private boolean isParameter;
    private String parameterFQCN;
    private EndPosTable endPosTable;

    public SourceContext(final Source source) {
        this.source = source;
    }

    public String getParameterFQCN() {
        return parameterFQCN;
    }

    public void setParameterFQCN(String parameterFQCN) {
        if (isParameter) {
            this.parameterFQCN = parameterFQCN;
        } else {
            this.parameterFQCN = "";
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
}
