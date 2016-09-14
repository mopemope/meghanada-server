package meghanada.project;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.io.File;

@DefaultSerializer(ProjectDependencySerializer.class)
public class ProjectDependency {

    private final String id;
    private final String scope;
    private final String version;
    private final File file;

    public ProjectDependency(final String id, final String scope, final String version, final File file) {
        this.id = id;
        this.scope = scope.toUpperCase();
        this.version = version;
        this.file = file;
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public String getScope() {
        return scope;
    }

    public File getFile() {
        return file;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProjectDependency)) {
            return false;
        }
        ProjectDependency that = (ProjectDependency) o;
        return Objects.equal(id, that.id)
                && Objects.equal(scope, that.scope)
                && Objects.equal(version, that.version)
                && Objects.equal(file, that.file);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, scope, version, file);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("scope", scope)
                .add("version", version)
                .add("file", file)
                .toString();
    }
}
