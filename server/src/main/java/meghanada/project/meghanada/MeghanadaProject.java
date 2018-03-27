package meghanada.project.meghanada;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import meghanada.project.Project;
import meghanada.project.ProjectParseException;

public class MeghanadaProject extends Project {

  private static final long serialVersionUID = 1L;

  public MeghanadaProject(File projectRoot) throws IOException {
    super(projectRoot);
  }

  @Override
  public Project parseProject() throws ProjectParseException {
    return this;
  }

  @Override
  public InputStream runTask(List<String> args) throws IOException {
    throw new UnsupportedOperationException("Not support");
  }
}
