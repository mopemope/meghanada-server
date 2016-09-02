package meghanada;

import java.util.List;

public class Gen5 {

    public String name;

    <T> T getFormalClass(String s, Class<T> type) {
        return (T) type;
    }

    public String getName() {
        return this.name;
    }

    public List<String> getList() {
        return null;
    }

}
