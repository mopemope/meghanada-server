package meghanada.location;

import com.google.common.base.MoreObjects;

public class Location {
    private final String path;
    private final int line;
    private final int column;

    public Location(final String path, final int line, final int column) {
        this.path = path;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("path", path)
                .add("line", line)
                .add("column", column)
                .toString();
    }

    public String getPath() {
        return path;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
