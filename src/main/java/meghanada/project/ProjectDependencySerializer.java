package meghanada.project;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class ProjectDependencySerializer extends Serializer<ProjectDependency> {

    @Override
    public void write(Kryo kryo, Output output, ProjectDependency pd) {
        try {
            final String id = pd.getId();
            kryo.writeClassAndObject(output, id);
            final String scope = pd.getScope();
            kryo.writeClassAndObject(output, scope);
            final String version = pd.getVersion();
            kryo.writeClassAndObject(output, version);
            final String path = pd.getFile().getCanonicalPath();
            kryo.writeClassAndObject(output, path);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public ProjectDependency read(Kryo kryo, Input input, Class<ProjectDependency> type) {
        final String id = (String) kryo.readClassAndObject(input);
        final String scope = (String) kryo.readClassAndObject(input);
        final String version = (String) kryo.readClassAndObject(input);
        final String path = (String) kryo.readClassAndObject(input);
        return new ProjectDependency(id, scope, version, new File(path));
    }
}
