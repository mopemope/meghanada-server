package meghanada.parser.source;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.HashBiMap;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JavaSourceSerializer extends Serializer<JavaSource> {

    @Override
    public void write(Kryo kryo, Output output, JavaSource source) {
        // 1. file
        final String path = source.file.getPath();
        output.writeString(path);

        // 2. importClass
        Map<String, String> map = new HashMap<>();
        source.importClass.forEach(map::put);
        kryo.writeClassAndObject(output, map);

        // 3. staticImp
        kryo.writeClassAndObject(output, source.staticImp);

        // 4. typeScopes
        kryo.writeClassAndObject(output, source.typeScopes);

        // 5. pkg
        output.writeString(source.pkg);

        // 6. unusedClass
        kryo.writeClassAndObject(output, source.unusedClass);

        // 7. unknownClass
        kryo.writeClassAndObject(output, source.unknownClass);

    }

    @Override
    public JavaSource read(Kryo kryo, Input input, Class<JavaSource> type) {
        // 1. file
        final String path = input.readString();
        final JavaSource source = new JavaSource(new File(path));

        // 2. importClass
        @SuppressWarnings("unchecked")
        final Map<String, String> map = (Map<String, String>) kryo.readClassAndObject(input);
        source.importClass = HashBiMap.create(map);

        // 3. staticImp
        @SuppressWarnings("unchecked")
        final Map<String, String> staticImp = (Map<String, String>) kryo.readClassAndObject(input);
        source.staticImp = staticImp;

        // 4. typeScopes
        @SuppressWarnings("unchecked")
        final List<TypeScope> typeScopes = (List<TypeScope>) kryo.readClassAndObject(input);
        source.typeScopes = typeScopes;

        // 5. pkg
        source.pkg = input.readString();

        // 6. unusedClass
        @SuppressWarnings("unchecked")
        final Map<String, String> unusedClass = (Map<String, String>) kryo.readClassAndObject(input);
        source.unusedClass = unusedClass;

        // 7. unknownClass
        @SuppressWarnings("unchecked")
        final Set<String> unknownClass = (Set<String>) kryo.readClassAndObject(input);
        source.unknownClass = unknownClass;

        return source;
    }
}
