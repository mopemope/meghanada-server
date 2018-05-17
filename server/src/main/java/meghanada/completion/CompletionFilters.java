package meghanada.completion;

import static meghanada.reflect.CandidateUnit.MemberType;

import meghanada.completion.matcher.CompletionMatcher;
import meghanada.reflect.MemberDescriptor;

public class CompletionFilters {

  private CompletionFilters() {}

  public static boolean publicMemberFilter(
      final MemberDescriptor descriptor, final CompletionMatcher matcher, final String target) {
    boolean matched = matcher.match(descriptor);
    if (!target.isEmpty() && !matched) {
      return false;
    }
    if (descriptor.getMemberType().equals(MemberType.CONSTRUCTOR)) {
      return false;
    }
    return descriptor.isPublic();
  }

  public static boolean publicMemberFilter(
      final MemberDescriptor descriptor,
      final boolean isStatic,
      final boolean withCONSTRUCTOR,
      final CompletionMatcher matcher,
      final String target) {

    final boolean matched = matcher.match(descriptor);
    if (!target.isEmpty() && !matched) {
      return false;
    }

    if (!descriptor.isPublic()) {
      return false;
    }
    if (isStatic) {
      return descriptor.isStatic();
    }
    if (withCONSTRUCTOR) {
      return !descriptor.isStatic();
    }
    return !descriptor.isStatic() && !descriptor.getMemberType().equals(MemberType.CONSTRUCTOR);
  }

  public static boolean packageMemberFilter(
      final MemberDescriptor descriptor,
      final boolean isStatic,
      final boolean withCONSTRUCTOR,
      final CompletionMatcher matcher,
      final String target) {

    final boolean matched = matcher.match(descriptor);
    if (!target.isEmpty() && !matched) {
      return false;
    }

    if (descriptor.isPrivate()) {
      return false;
    }
    if (isStatic) {
      return descriptor.isStatic();
    }
    if (withCONSTRUCTOR) {
      return !descriptor.isStatic();
    }
    return !descriptor.isStatic() && !descriptor.getMemberType().equals(MemberType.CONSTRUCTOR);
  }

  public static boolean privateMemberFilter(
      final MemberDescriptor md,
      final boolean withCONSTRUCTOR,
      final CompletionMatcher matcher,
      final String target) {
    final String name = md.getName();
    final boolean matched = matcher.match(md);
    return !(md.getMemberType().equals(MemberType.FIELD) && name.startsWith("this$"))
        && !(!target.isEmpty() && !matched)
        && (withCONSTRUCTOR || !md.getMemberType().equals(MemberType.CONSTRUCTOR));
  }
}
