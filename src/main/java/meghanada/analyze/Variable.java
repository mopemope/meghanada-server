package meghanada.analyze;

import com.google.common.base.MoreObjects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Variable {

    private static Logger log = LogManager.getLogger(Variable.class);

    public String name;
    public int pos;
    public Range range;
    public String fqcn;

    public boolean def;
    public boolean parameter;

    public Variable() {

    }

    public Variable(final String name, final int pos, final Range range) {
        this.name = name;
        this.pos = pos;
        this.range = range;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("fqcn", fqcn)
                .add("range", range)
                .add("def", def)
                .add("parameter", parameter)
                .add("pos", pos)
                .toString();
    }
}
