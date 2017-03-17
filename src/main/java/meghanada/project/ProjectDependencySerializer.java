package meghanada.project;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class ProjectDependencySerializer extends Serializer<ProjectDependency> {

    private static final Logger log = LogManager.getLogger(ProjectDependencySerializer.class);

    @Override
    public void write(final Kryo kryo, final Output output, final ProjectDependency pd) {
        try {
            output.writeString(pd.getId());
            output.writeString(pd.getScope());
            output.writeString(pd.getVersion());
            output.writeString(pd.getFile().getCanonicalPath());
            output.writeString(pd.getType().toString());
        } catch (IOException ex) {
            log.catching(ex);
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public ProjectDependency read(final Kryo kryo, Input input, final Class<ProjectDependency> type) {
        final String id = input.readString();
        final String scope = input.readString();
        final String version = input.readString();
        final String path = input.readString();
        final String typeStr = input.readString();
        final ProjectDependency.Type depType = ProjectDependency.Type.valueOf(typeStr);
        return new ProjectDependency(id, scope, version, new File(path), depType);
    }

}
