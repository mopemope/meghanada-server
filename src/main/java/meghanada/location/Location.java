package meghanada.location;

import com.google.common.base.MoreObjects;

public class Location {
    String path;
    int line;
    int column;

    public Location(String path, int line, int column) {
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
