package meghanada.analyze;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Range;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Consumer;

public class Source {

    private static Logger log = LogManager.getLogger(Source.class);

    public File file;

    public String packageName;

    // K: className V: FQCN
    public Map<String, String> importClass = new HashMap<>(8);
    public Map<String, String> staticImportClass = new HashMap<>(8);

    public Set<String> imported = new HashSet<>(8);
    public Set<String> unknown = new HashSet<>(8);

    public List<Range<Integer>> lineRange;

    public List<ClassScope> classScopes = new ArrayList<>(1);
    public Deque<ClassScope> currentClassScope = new ArrayDeque<>(1);

    // temp flag
    public boolean isParameter;

    public Source() {
    }

    public Source(final File file) {
        this.file = file;
    }

    public void addImport(final String fqcn) {
        final String className = ClassNameUtils.getSimpleName(fqcn);
        this.importClass.putIfAbsent(className, fqcn);
        this.imported.add(fqcn);
        log.trace("imported class {}", fqcn);
    }

    public void addStaticImport(final String method, final String clazz) {
        final String className = ClassNameUtils.getSimpleName(clazz);
        this.importClass.putIfAbsent(className, clazz);
        this.staticImportClass.putIfAbsent(method, clazz);
        log.trace("static imported class {} {}", clazz, method);
    }

    public void startClass(final ClassScope classScope) {
        this.currentClassScope.push(classScope);
    }

    public Optional<ClassScope> getCurrentClass() {
        final ClassScope classScope = this.currentClassScope.peek();
        if (classScope != null) {
            return classScope.getCurrentClass();
        }
        return Optional.empty();
    }

    public Optional<ClassScope> endClass() {
        return this.getCurrentClass().map(classScope -> {
            this.classScopes.add(classScope);
            return this.currentClassScope.remove();
        });
    }

    public Optional<BlockScope> getCurrentBlock() {
        return this.getCurrentClass().flatMap(TypeScope::getCurrentBlock);
    }

    public Optional<? extends Scope> getCurrentScope() {
        return this.getCurrentClass().flatMap(BlockScope::getCurrentScope);
    }

    public void addClassScope(final ClassScope classScope) {
        this.classScopes.add(classScope);
    }

    private List<Range<Integer>> getRange(final File file) throws IOException {
        if (this.lineRange != null) {
            return this.lineRange;
        }
        int last = 1;
        final List<Range<Integer>> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charset.forName("UTF-8")))) {
            String s;
            while ((s = br.readLine()) != null) {
                final int length = s.length();
                final Range<Integer> range = Range.closed(last, last + length);
                list.add(range);
            }
        }
        this.lineRange = list;
        return this.lineRange;
    }

    Position getPos(int pos) throws IOException {
        int line = 1;
        for (final Range<Integer> r : getRange(this.file)) {
            if (r.contains(pos)) {
                return new Position(line, pos + 1);
            }
            final Integer last = r.upperEndpoint();
            pos -= last;
            line++;
        }
        return new Position(-1, -1);
    }

    public File getFile() {
        return file;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("file", file)
                .toString();
    }

    public Optional<Variable> findVariable(final int pos) {
        for (final ClassScope cs : classScopes) {
            final Optional<Variable> variable = cs.findVariable(pos);
            if (variable.isPresent()) {
                return variable;
            }
        }
        log.warn("Missing element pos={}", pos);
        return Optional.empty();
    }

    public void dumpVariable() {
        final EntryMessage entryMessage = log.traceEntry("{}", Strings.repeat("*", 100));
        for (final ClassScope cs : classScopes) {
            cs.dumpVariable();
        }
        log.traceExit(entryMessage);
    }

    public void dumpFieldAccess() {
        final EntryMessage entryMessage = log.traceEntry("{}", Strings.repeat("*", 100));
        for (final ClassScope cs : classScopes) {
            cs.dumpFieldAccess();
        }
        log.traceExit(entryMessage);
    }

    public void dump() {
        final EntryMessage entryMessage = log.traceEntry("{}", Strings.repeat("*", 100));
        for (final ClassScope cs : classScopes) {
            cs.dump();
        }
        log.traceExit(entryMessage);
    }

    public void inParam(Consumer c) {
    }
}
