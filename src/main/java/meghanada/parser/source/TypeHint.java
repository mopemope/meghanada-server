package meghanada.parser.source;

import com.google.common.base.MoreObjects;
import meghanada.reflect.MemberDescriptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

public class TypeHint {

    private static Logger log = LogManager.getLogger(TypeHint.class);
    public int parameterHintIndex;
    public int narrowingHintIndex;
    List<MemberDescriptor> hintDescriptors;
    private Deque<String> lambdaReturnTypes = new ArrayDeque<>();
    private MemberDescriptor lambdaMethod;

    public void setHintDescriptors(final List<MemberDescriptor> hints) {
        log.traceEntry("hints:{}", hints.size());
        this.clearHintDescriptors();
        this.hintDescriptors = hints;
        this.narrowingHintIndex = 0;
        this.parameterHintIndex = 0;
        log.traceExit();
    }

    public Optional<MemberDescriptor> get() {
        if (this.maybeResolved()) {
            return Optional.of(hintDescriptors.get(0));
        }
        return Optional.empty();
    }

    public boolean maybeResolved() {
        return this.hintDescriptors != null && this.hintDescriptors.size() == 1;
    }

    public void clearHintDescriptors() {
        log.traceEntry();
        this.hintDescriptors = null;
        this.narrowingHintIndex = 0;
        this.parameterHintIndex = 0;
        log.traceExit();
    }

    public boolean hasHintDescriptors() {
        return this.hintDescriptors != null && this.hintDescriptors.size() > 0;
    }

    public String getLambdaReturnType() {
        log.traceEntry("lambdaReturnType={}", this.lambdaReturnTypes);
        if (this.lambdaReturnTypes.size() > 0) {
            final String returnType = this.lambdaReturnTypes.pop();
            return log.traceExit(returnType);
        }
        log.traceExit();
        return null;
    }

    public String peekLambdaReturnType() {
        log.traceEntry("lambdaReturnType={}", this.lambdaReturnTypes);
        if (this.lambdaReturnTypes.size() > 0) {
            final String returnType = this.lambdaReturnTypes.peek();
            return log.traceExit(returnType);
        }
        log.traceExit();
        return null;
    }

    public void addLambdaReturnType(final String fqcn) {
        log.traceEntry("fqcn={}", fqcn);
        this.lambdaReturnTypes.push(fqcn);
        log.traceExit();
    }

    public void clearLambdaReturnType() {
        this.lambdaReturnTypes.clear();
    }

    public boolean hasLambdaReturn() {
        return this.lambdaReturnTypes.size() > 0;
    }

    public MemberDescriptor getLambdaMethod() {
        return lambdaMethod;
    }

    public MemberDescriptor setLambdaMethod(MemberDescriptor lambdaMethod) {
        this.lambdaMethod = lambdaMethod;
        return this.lambdaMethod;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("hintDescriptors", hintDescriptors)
                .add("lambdaReturnTypes", lambdaReturnTypes)
                .add("parameterHintIndex", parameterHintIndex)
                .add("narrowingHintIndex", narrowingHintIndex)
                .toString();
    }
}
