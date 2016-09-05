package meghanada.project.meghanada;

import com.esotericsoftware.kryo.DefaultSerializer;
import meghanada.project.Project;
import meghanada.project.ProjectParseException;
import meghanada.project.ProjectSerializer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@DefaultSerializer(ProjectSerializer.class)
public class MeghanadaProject extends Project {

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
