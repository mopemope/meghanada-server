package meghanada.reflect;


public interface CandidateUnit {

    String getName();

    String getType();

    String getDeclaration();

    String getDisplayDeclaration();

    String getReturnType();

    enum MemberType {
        FIELD,
        METHOD,
        CONSTRUCTOR,
        VAR,
        CLASS,
        PACKAGE
    }
}
