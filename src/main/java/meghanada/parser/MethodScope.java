package meghanada.parser;

import com.github.javaparser.Range;
import com.google.common.base.MoreObjects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MethodScope extends BlockScope {

    private static Logger log = LogManager.getLogger(MethodScope.class);
    final Range nameRange;

    MethodScope(final String name, final Range range, final Range nameRange) {
        super(name, range);
        this.nameRange = nameRange;
    }

    MethodScope startBlock(final String name, final Range range, final Range nameRange) {
        // add method
        MethodScope scope = new MethodScope(name, range, nameRange);
        super.startBlock(scope);
        return scope;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("scope", name)
                .add("nameRange", nameRange)
                .toString();
    }

    public Range getNameRange() {
        return nameRange;
    }
}
