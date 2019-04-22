package meghanada.reflect;

public interface CandidateUnit {

  String getName();

  String getType();

  String getDeclaration();

  String getDisplayDeclaration();

  String getReturnType();

  String getExtra();

  enum MemberType {
    FIELD,
    METHOD,
    CONSTRUCTOR,
    VARIABLE,
    CLASS,
    IMPORT,
    PACKAGE
  }
}
