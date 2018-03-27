import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class MissingImport1 extends File {

  Date date;
  java.util.Queue queue;

  public List<String> testSimple() {
    Map<String, Long> foo = new HashMap<>();
    return new ArrayList<String>();
  }

  public Properties getProp(Set<MemberDescriptor> set) throws IOException {
    List<FileDescriptor> fileDescriptors = new ArrayList<FileDescriptor>();
    return null;
  }

  public List<MethodCall> getMethodCalls() throws IOException {
    return null;
  }
}
