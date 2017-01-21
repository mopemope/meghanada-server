package meghanada.location;

import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import meghanada.analyze.*;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.asm.CachedASMReflector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class LocationSearcher {

    private static Logger log = LogManager.getLogger(LocationSearcher.class);

    private final Set<File> sources;
    private final LoadingCache<File, Source> sourceCache;
    private final List<LocationSearchFunction> locationSearchFunctions;


    public LocationSearcher(Set<File> sources, LoadingCache<File, Source> sourceCache) {
        this.sources = sources;
        this.sourceCache = sourceCache;
        this.locationSearchFunctions = this.getLocationSearchFunctions();
    }

    public Location searchDeclaration(final File file, final int line, final int column, final String symbol) throws ExecutionException, IOException {
        final Source source = this.sourceCache.get(file.getCanonicalFile());
        log.trace("search symbol {}", symbol);

        return this.locationSearchFunctions.stream()
                .map(f -> f.apply(source, line, column, symbol))
                .filter(Objects::nonNull)
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

    private Location searchMethodCallSymbol(final Source source, final int line, final int col, final String symbol) {
        final EntryMessage entryMessage = log.traceEntry("line={} col={} symbol={}", line, col, symbol);
        final Optional<MethodCall> methodCall = source.getMethodCall(line, col, true);

        final Location result = methodCall.flatMap(mc -> {
            final String methodName = mc.name;
            final String fqcn = mc.declaringClass;

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
                        final Source declaringClassSrc = this.sourceCache.get(f.getCanonicalFile());
                        final String path = declaringClassSrc.getFile().getPath();
                        return declaringClassSrc.getClassScopes()
                                .stream()
                                .flatMap(ts -> ts.getScopes().stream())
                                .filter(bs -> methodName.equals(bs.getName()))
                                .filter(bs -> bs instanceof MethodScope)
                                .map(MethodScope.class::cast)
                                .map(ms -> new Location(path,
                                        ms.getBeginLine(),
                                        ms.getNameRange().begin.column))
                                .findFirst();
                    } catch (ExecutionException e) {
                        throw new UncheckedExecutionException(e);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                if (location.isPresent()) {
                    return location;
                }
            }
            return Optional.empty();
        }).orElse(null);

        log.traceExit(entryMessage);
        return result;
    }

    private Location searchClassOrInterface(final Source source, final int line, final int col, final String symbol) {
        final EntryMessage entryMessage = log.traceEntry("line={} col={} symbol={}", line, col, symbol);
        String fqcn = source.importClass.get(symbol);
        if (fqcn == null) {
            if (source.packageName != null) {
                fqcn = source.packageName + "." + symbol;
            } else {
                fqcn = symbol;
            }
        }
        final String searchFQCN = fqcn;

        final Location location = existsFQCN(this.sources, fqcn).flatMap(f -> {
            try {
                final Source declaringClassSrc = this.sourceCache.get(f);
                final String path = declaringClassSrc.getFile().getPath();
                return declaringClassSrc
                        .getClassScopes()
                        .stream()
                        .filter(cs -> cs.getName().equals(searchFQCN))
                        .map(cs -> new Location(path,
                                cs.getBeginLine(),
                                cs.getNameRange().begin.column))
                        .findFirst();

            } catch (ExecutionException e) {
                throw new UncheckedExecutionException(e);
            }
        }).orElse(null);
        log.traceExit(entryMessage);
        return location;
    }

    private Location searchLocalNameSymbol(final Source source, final int line, final int col, final String symbol) {
        final EntryMessage entryMessage = log.traceEntry("line={} col={} symbol={}", line, col, symbol);

        final Map<String, Variable> symbols = source.getDeclaratorMap(line);
        log.trace("variables={}", symbols);
        final Variable variable = symbols.get(symbol);
        final Location location = Optional.ofNullable(variable)
                .map(ns -> new Location(source.getFile().getPath(),
                        ns.range.begin.line,
                        ns.range.begin.column))
                .orElseGet(() -> {
                    // isField
                    final TypeScope ts = source.getTypeScope(line);
                    if (ts == null) {
                        return null;
                    }
                    final Variable fieldSymbol = ts.getField(symbol);
                    if (fieldSymbol == null) {
                        return null;
                    }
                    return new Location(source.getFile().getPath(),
                            fieldSymbol.range.begin.line,
                            fieldSymbol.range.begin.column);
                });
        if (location != null) {
            log.traceExit(entryMessage);
            return location;
        }
        log.traceExit(entryMessage);
        return null;
    }

    private Location searchFieldAccessSymbol(final Source source, final int line, final int col, final String symbol) {
        final EntryMessage entryMessage = log.traceEntry("line={} col={} symbol={}", line, col, symbol);

        final FieldAccess fieldAccess = source.searchFieldAccess(line, symbol);
        final Location location1 = Optional.ofNullable(fieldAccess)
                .flatMap(fa -> {
                    final String fieldName = fa.name;
                    final String fqcn = fa.declaringClass;

                    final List<String> searchTargets = new ArrayList<>();
                    searchTargets.add(fqcn);

                    final Map<String, ClassIndex> globalClassIndex = CachedASMReflector.getInstance().getGlobalClassIndex();
                    if (globalClassIndex.containsKey(fqcn)) {
                        final List<String> supers = globalClassIndex.get(fqcn).supers;
                        searchTargets.addAll(supers);
                    }

                    for (final String targetFqcn : searchTargets) {
                        final Optional<Location> location = existsFQCN(this.sources, targetFqcn).flatMap(f -> {
                            try {
                                final Source declaringClassSrc = this.sourceCache.get(f);
                                final String path = declaringClassSrc.getFile().getPath();
                                return declaringClassSrc
                                        .getClassScopes()
                                        .stream()
                                        .map(ts -> {
                                            return ts.getField(fieldName);
                                        })
                                        .filter(ns -> ns != null)
                                        .map(ns -> new Location(path,
                                                ns.range.begin.line,
                                                ns.range.begin.column))
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
        log.traceExit(entryMessage);
        return location1;
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
