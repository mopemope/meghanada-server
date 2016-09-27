package meghanada.completion;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import meghanada.utils.ClassNameUtils;

import java.util.List;

public class LocalVariable {

    private final String returnType;
    private final List<String> candidates;

    public LocalVariable(final String returnType, List<String> candidates) {
        this.returnType = returnType;
        this.candidates = candidates;
    }

    public String getReturnFQCN() {
        return returnType;
    }

    public String getReturnType() {
        return ClassNameUtils.getAllSimpleName(returnType);
    }

    public List<String> getCandidates() {
        return candidates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LocalVariable that = (LocalVariable) o;
        return Objects.equal(returnType, that.returnType)
                && Objects.equal(candidates, that.candidates);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(returnType, candidates);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("returnType", returnType)
                .add("candidates", candidates)
                .toString();
    }
}
