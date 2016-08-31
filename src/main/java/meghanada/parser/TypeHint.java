package meghanada.parser;

import com.google.common.base.MoreObjects;
import meghanada.reflect.MemberDescriptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

class TypeHint {

    private static Logger log = LogManager.getLogger(TypeHint.class);

    List<MemberDescriptor> hintDescriptors;
    int parameterHintIndex;
    int narrowingHintIndex;
    private Deque<String> lambdaReturnTypes = new ArrayDeque<>();
    private MemberDescriptor lambdaMethod;

    void setHintDescriptors(final List<MemberDescriptor> hints) {
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

    boolean maybeResolved() {
        return this.hintDescriptors != null && this.hintDescriptors.size() == 1;
    }

    void clearHintDescriptors() {
        log.traceEntry();
        this.hintDescriptors = null;
        this.narrowingHintIndex = 0;
        this.parameterHintIndex = 0;
        log.traceExit();
    }

    boolean hasHintDescriptors() {
        return this.hintDescriptors != null && this.hintDescriptors.size() > 0;
    }

    String getLambdaReturnType() {
        log.traceEntry("lambdaReturnType={}", this.lambdaReturnTypes);
        if (this.lambdaReturnTypes.size() > 0) {
            final String returnType = this.lambdaReturnTypes.pop();
            return log.traceExit(returnType);
        }
        log.traceExit();
        return null;
    }

    String peekLambdaReturnType() {
        log.traceEntry("lambdaReturnType={}", this.lambdaReturnTypes);
        if (this.lambdaReturnTypes.size() > 0) {
            final String returnType = this.lambdaReturnTypes.peek();
            return log.traceExit(returnType);
        }
        log.traceExit();
        return null;
    }

    void addLambdaReturnType(final String fqcn) {
        log.traceEntry("fqcn={}", fqcn);
        this.lambdaReturnTypes.push(fqcn);
        log.traceExit();
    }

    void clearLambdaReturnType() {
        this.lambdaReturnTypes.clear();
    }

    boolean hasLambdaReturn() {
        return this.lambdaReturnTypes.size() > 0;
    }

    MemberDescriptor getLambdaMethod() {
        return lambdaMethod;
    }

    MemberDescriptor setLambdaMethod(MemberDescriptor lambdaMethod) {
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
