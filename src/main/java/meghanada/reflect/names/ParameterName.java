package meghanada.reflect.names;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.io.Serializable;

public class ParameterName implements Serializable {

    private static final long serialVersionUID = -4661482854311698261L;

    public String type;
    public String name;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ParameterName that = (ParameterName) o;
        return Objects.equal(type, that.type)
                && Objects.equal(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, name);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("type", type)
                .add("name", name)
                .toString();
    }
}
