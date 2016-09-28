package meghanada.location;

import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import meghanada.parser.source.JavaSource;
import meghanada.parser.source.MethodScope;
import meghanada.parser.source.TypeScope;
import meghanada.parser.source.Variable;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.asm.CachedASMReflector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class LocationSearcher {

    private static Logger log = LogManager.getLogger(LocationSearcher.class);

    private final Set<File> sources;
    private final LoadingCache<File, JavaSource> sourceCache;
    private final List<LocationSearchFunction> locationSearchFunctions;


    public LocationSearcher(Set<File> sources, LoadingCache<File, JavaSource> sourceCache) {
        this.sources = sources;
        this.sourceCache = sourceCache;
        this.locationSearchFunctions = this.getLocationSearchFunctions();
    }

    public Location searchDeclaration(final File file, final int line, final int column, final String symbol) throws ExecutionException {
        final JavaSource source = this.sourceCache.get(file);
        log.trace("search symbol {}", symbol);

        return this.locationSearchFunctions.stream()
                .map(f -> f.apply(source, line, column, symbol))
                .filter(l -> l != null)
                .findFirst()
                .orElse(null);
    }

    private List<LocationSearchFunction> getLocationSearchFunctions() {
        List<LocationSearchFunction> list = new ArrayList<>(4);
        list.add(this::searchClassOrInterface);
        list.add(this::searchFieldAccessSymbol);
        list.add(this::searchMethodCallSymbol);
        list.add(this::searchLocalNameSymbol);
        return list;
    }

    private Location searchMethodCallSymbol(final JavaSource source, final int line, final int col, final String symbol) {
        return source.getMethodCallSymbol(line, col, true).flatMap(mc -> {
            final String methodName = mc.getName();
            final String fqcn = mc.getDeclaringClass();

            List<String> searchTargets = new ArrayList<>();
            searchTargets.add(fqcn);
            final Map<String, ClassIndex> globalClassIndex = CachedASMReflector.getInstance().getGlobalClassIndex();
            if (globalClassIndex.containsKey(fqcn)) {
                final List<String> supers = globalClassIndex.get(fqcn).supers;
                searchTargets.addAll(supers);
            }

            for (final String targetFqcn : searchTargets) {
                final Optional<Location> location = existsFQCN(this.sources, targetFqcn).flatMap(f -> {
                    try {
                        final JavaSource declaringClassSrc = this.sourceCache.get(f);
                        final String path = declaringClassSrc.getFile().getPath();
                        return declaringClassSrc.getTypeScopes()
                                .stream()
                                .flatMap(ts -> ts.getInnerScopes().stream())
                                .filter(bs -> methodName.equals(bs.getName()))
                                .filter(bs -> bs instanceof MethodScope)
                                .map(MethodScope.class::cast)
                                .map(ms -> new Location(path,
                                        ms.getBeginLine(),
                                        ms.getNameRange().begin.column))
                                .findFirst();
                    } catch (ExecutionException e) {
                        throw new UncheckedExecutionException(e);
                    }
                });
                if (location.isPresent()) {
                    return location;
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    private Location searchClassOrInterface(final JavaSource source, final int line, final int col, final String symbol) {
        String fqcn = source.importClass.get(symbol);
        if (fqcn == null) {
            if (source.getPkg() != null) {
                fqcn = source.getPkg() + "." + symbol;
            } else {
                fqcn = symbol;
            }
        }
        return existsFQCN(this.sources, fqcn).flatMap(f -> {
            try {
                final JavaSource declaringClassSrc = this.sourceCache.get(f);
                final String path = declaringClassSrc.getFile().getPath();
                return declaringClassSrc
                        .getTypeScopes()
                        .stream()
                        .filter(ts -> ts.getName().equals(symbol))
                        .map(ts -> new Location(path,
                                ts.getBeginLine(),
                                ts.getNameRange().begin.column))
                        .findFirst();

            } catch (ExecutionException e) {
                throw new UncheckedExecutionException(e);
            }
        }).orElse(null);
    }

    private Location searchLocalNameSymbol(final JavaSource source, final int line, final int col, final String symbol) {
        log.traceEntry("line={} col={} symbol={}", line, col, symbol);

        final Map<String, Variable> symbols = source.getDeclaratorMap(line);
        log.trace("symbols={}", symbols);
        final Location location = Optional.ofNullable(symbols.get(symbol))
                .map(ns -> new Location(source.getFile().getPath(),
                        ns.getLine(),
                        ns.getRange().begin.column))
                .orElseGet(() -> {
                    // field
                    final TypeScope ts = source.getTypeScope(line);
                    if (ts == null) {
                        return null;
                    }
                    final Variable fieldSymbol = ts.getFieldSymbol(symbol);
                    if (fieldSymbol == null) {
                        return null;
                    }
                    return new Location(source.getFile().getPath(),
                            fieldSymbol.getLine(),
                            fieldSymbol.getRange().begin.column);
                });
        if (location != null) {
            return log.traceExit(location);
        }
        log.traceExit();
        return null;
    }

    private Location searchFieldAccessSymbol(final JavaSource source, final int line, final int col, final String symbol) {
        return Optional.ofNullable(source.searchFieldAccessSymbol(line, symbol))
                .flatMap(fieldAccessSymbol -> {
                    final String fieldName = fieldAccessSymbol.getName();
                    final String fqcn = fieldAccessSymbol.getDeclaringClass();
                    List<String> searchTargets = new ArrayList<>();
                    searchTargets.add(fqcn);

                    final Map<String, ClassIndex> globalClassIndex = CachedASMReflector.getInstance().getGlobalClassIndex();
                    if (globalClassIndex.containsKey(fqcn)) {
                        final List<String> supers = globalClassIndex.get(fqcn).supers;
                        searchTargets.addAll(supers);
                    }

                    for (final String targetFqcn : searchTargets) {
                        final Optional<Location> location = existsFQCN(this.sources, targetFqcn).flatMap(f -> {
                            try {
                                final JavaSource declaringClassSrc = this.sourceCache.get(f);
                                final String path = declaringClassSrc.getFile().getPath();
                                return declaringClassSrc
                                        .getTypeScopes()
                                        .stream()
                                        .map(ts -> ts.getFieldSymbol(fieldName))
                                        .filter(ns -> ns != null)
                                        .map(ns -> new Location(path,
                                                ns.getLine(),
                                                ns.getRange().begin.column))
                                        .findFirst();
                            } catch (ExecutionException e) {
                                throw new UncheckedExecutionException(e);
                            }
                        });
                        if (location.isPresent()) {
                            return location;
                        }
                    }
                    return Optional.empty();
                }).orElse(null);
    }

    private Optional<File> existsFQCN(final Set<File> roots, final String fqcn) {
        return roots.stream()
                .map(root -> toFile(root, fqcn))
                .filter(File::exists)
                .findFirst();
    }

    private File toFile(final File root, final String fqcn) {
        String path = fqcn.replace(".", File.separator) + ".java";
        return new File(root, path);
    }
}
