package meghanada.analyze;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static meghanada.utils.FunctionUtils.wrapIOConsumer;

public class ElementAnalyzer {
    private static Logger log = LogManager.getLogger(ElementAnalyzer.class);

    public void analyze(final Iterable<? extends Element> analyzed, final Map<File, Source> analyzedMap) {
        analyzed.forEach(wrapIOConsumer(element -> {

            final ElementKind kind = element.getKind();
            final javax.lang.model.element.Name simpleName = element.getSimpleName();

            log.trace("@@@ kind={} asType={} class={} {}", kind, element.asType(), element.getClass(), simpleName);

            if (kind.equals(ElementKind.CLASS)) {
                final Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) element;
                final URI uri = classSymbol.sourcefile.toUri();
                final File file = new File(uri.normalize());

                if (analyzedMap.containsKey(file)) {
                    final Source source = analyzedMap.get(file);
                    // TODO apply class
                    this.analyzeElement(element, source);
                } else {
                    log.trace("???");
                }
            } else {
                log.trace("???");
            }
        }));
    }

    private void analyzeElement(final Element targetElement, final Source src) throws IOException {
        targetElement.getEnclosedElements().forEach(el -> {
            final ElementKind kind = el.getKind();
            final TypeMirror typeMirror = el.asType();
            final TypeKind typeMirrorKind = typeMirror.getKind();

            log.trace("ElementKind={} {} {}", kind, el.getClass(), typeMirrorKind);

            if (kind.equals(ElementKind.FIELD)) {
                final Symbol.VarSymbol varSymbol = (Symbol.VarSymbol) el;
                final int pos = varSymbol.pos;
                final String fqcn = typeMirror.toString();
                src.findVariable(pos).ifPresent(variable -> {
                    variable.fqcn = fqcn;
                });
            } else if (kind.equals(ElementKind.CONSTRUCTOR)) {
                final Symbol.MethodSymbol methodSymbol = (Symbol.MethodSymbol) el;

                // Resolve.instance()
                methodSymbol.getParameters().forEach(varSymbol -> {
                    final int pos = varSymbol.pos;
                    final String fqcn = varSymbol.asType().toString();
                    log.trace("fqcn={} pos={}", fqcn, pos);
                    src.findVariable(pos).ifPresent(variable -> {
                        variable.fqcn = fqcn;
                    });

                });
                final Type returnType = methodSymbol.getReturnType();

            } else {
                log.trace("??? {} {} {}", kind, el.getClass(), typeMirror.getKind());
            }
        });
    }

}
