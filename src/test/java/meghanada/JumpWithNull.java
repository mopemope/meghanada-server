package meghanada;

public class JumpWithNull {
    String s;

    public static void main(String[] args) {
        return;
    }

    void foo() {
        s = "hello";
        JumpWithNull.main(null);
    }
}
