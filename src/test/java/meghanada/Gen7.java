package meghanada;

import java.util.concurrent.Future;

public class Gen7<V, F extends Future<V>> {

    public void operationComplete(F future) throws Exception {
        if (future.isDone()) {
            V result = future.get();
        }
    }
}
