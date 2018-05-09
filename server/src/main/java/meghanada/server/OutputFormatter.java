package meghanada.server;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import meghanada.analyze.CompileResult;
import meghanada.completion.LocalVariable;
import meghanada.docs.declaration.Declaration;
import meghanada.index.SearchResults;
import meghanada.location.Location;
import meghanada.reference.Reference;
import meghanada.reflect.CandidateUnit;
import meghanada.typeinfo.TypeInfo;

public interface OutputFormatter {

  String changeProject(long id, boolean result);

  String compile(long id, CompileResult compileResult, String path);

  String compileProject(long id, CompileResult compileResult);

  String diagnostics(long id, CompileResult compileResult, String path);

  String autocomplete(long id, Collection<? extends CandidateUnit> units);

  String parse(long id, boolean result);

  String addImport(long id, boolean result, String fqcn);

  String optimizeImport(long id, String path);

  String importAll(long id, Map<String, List<String>> result);

  String switchTest(long id, String openPath);

  String jumpDeclaration(long id, Location location);

  String clearCache(long id, boolean result);

  String ping(long id, String ping);

  String localVariable(long id, LocalVariable lv);

  String formatCode(long id, String path);

  String showDeclaration(long id, Declaration declaration);

  String error(long id, Throwable t);

  String references(long id, List<Reference> references);

  String typeInfo(long id, TypeInfo typeInfo);

  String killRunningProcess(long id);

  String searchEverywhere(long id, SearchResults r);

  String showProject(long id, String s);

  String completionResolve(long id, boolean b);

  String importAtPoint(long id, Map<String, List<String>> result);
}
