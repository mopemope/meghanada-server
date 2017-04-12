package meghanada;

import com.sun.tools.javac.util.JCDiagnostic;

import java.util.ArrayList;
import java.util.List;

import static meghanada.utils.FunctionUtils.wrapIOConsumer;

public class Opt {

    public Opt() {
        JCDiagnostic.DiagnosticPosition pos = null;
        List<String> list = new ArrayList<>();
        list.forEach(wrapIOConsumer(s -> {
        }));
    }
}
