package meghanada;


import meghanada.reflect.MemberDescriptor;

public class Variable1 {

    private MemberDescriptor[] children;

    public Variable1() {

        for (int i = 0; i < 10; i++) {
            try {
            } catch (Exception e) {
                throw new IllegalStateException("failed to create a child event loop", e);
            } finally {
                for (int j = 0; j < i; j++) {
                    MemberDescriptor e = children[j];
                    while (!(e.getName() != null)) {
                        e.getType();
                    }
                }
            }
        }
    }
}


