package meghanada.server;

import meghanada.analyze.CompileResult;
import meghanada.completion.LocalVariable;
import meghanada.docs.declaration.Declaration;
import meghanada.location.Location;
import meghanada.reflect.CandidateUnit;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface OutputFormatter {

    String changeProject(boolean result);

    String compile(CompileResult compileResult, String path);

    String compileProject(CompileResult compileResult);

    String diagnostics(CompileResult compileResult, String path);

    String autocomplete(Collection<? extends CandidateUnit> units);

    String parse(boolean result);

    String addImport(boolean result, String fqcn);

    String optimizeImport(String path);

    String importAll(Map<String, List<String>> result);

    String switchTest(String openPath);

    String jumpDeclaration(Location location);

    String clearCache(boolean result);

    String localVariable(LocalVariable lv);

    String formatCode(String path);

    String showDeclaration(Declaration declaration);
}
