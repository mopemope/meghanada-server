package meghanada;

public class Method {

    public Method(String m){
    }

    public static Method getMethod(java.lang.reflect.Method m) {
        return new Method(m.getName());
    }
}
