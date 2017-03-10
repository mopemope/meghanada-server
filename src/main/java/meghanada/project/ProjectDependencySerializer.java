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
    public void write(Kryo kryo, Output output, ProjectDependency pd) {
        try {
            output.writeString(pd.getId());
            output.writeString(pd.getScope());
            output.writeString(pd.getVersion());
            output.writeString(pd.getFile().getCanonicalPath());
        } catch (IOException ex) {
            log.catching(ex);
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public ProjectDependency read(final Kryo kryo, final Input input, final Class<ProjectDependency> type) {
        final String id = input.readString();
        final String scope = input.readString();
        final String version = input.readString();
        final String path = input.readString();
        return new ProjectDependency(id, scope, version, new File(path));
    }
}
