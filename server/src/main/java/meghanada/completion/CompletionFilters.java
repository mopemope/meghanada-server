package meghanada.completion;

import static meghanada.reflect.CandidateUnit.MemberType;

import meghanada.reflect.MemberDescriptor;
import meghanada.utils.StringUtils;

public class CompletionFilters {

  private CompletionFilters() {}

  public static boolean publicMemberFilter(final MemberDescriptor descriptor, final String target) {
    final String name = descriptor.getName();
    final boolean matched = StringUtils.getInstance().isMatch(name, target);
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
      final String target) {

    final String name = descriptor.getName();
    final boolean matched = StringUtils.getInstance().isMatch(name, target);
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
      final String target) {

    final String name = descriptor.getName();
    final boolean matched = StringUtils.getInstance().isMatch(name, target);
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
      final MemberDescriptor md, final boolean withCONSTRUCTOR, final String target) {
    final String name = md.getName();
    final boolean matched = StringUtils.getInstance().isMatch(name, target);
    return !(md.getMemberType().equals(MemberType.FIELD) && name.startsWith("this$"))
        && !(!target.isEmpty() && !matched)
        && (withCONSTRUCTOR || !md.getMemberType().equals(MemberType.CONSTRUCTOR));
  }
}
