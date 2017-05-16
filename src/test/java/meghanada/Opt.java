package meghanada;

import static meghanada.utils.FunctionUtils.wrapIOConsumer;

import com.sun.tools.javac.util.JCDiagnostic;
import java.util.ArrayList;
import java.util.List;

public class Opt {

  public Opt() {
    JCDiagnostic.DiagnosticPosition pos = null;
    List<String> list = new ArrayList<>();
    list.forEach(wrapIOConsumer(s -> {}));
  }
}
