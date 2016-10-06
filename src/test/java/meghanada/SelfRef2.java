package meghanada;

import java.lang.ref.PhantomReference;

public class SelfRef2 {

    private Ref head = new Ref(null);

    private final class Ref extends PhantomReference<Object> {
        private Ref next;
        private Ref prev;

        Ref(Object referent) {
            super(referent, null);
            if (referent != null) {
                synchronized (head) {
                    prev = head;
                    next = head.next;
                    head.next.prev = this;
                    head.next = this;
                }
            }
        }
    }
}

