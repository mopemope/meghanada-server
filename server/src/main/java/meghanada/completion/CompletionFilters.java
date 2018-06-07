package meghanada.completion;

import static meghanada.reflect.CandidateUnit.MemberType;

import java.util.function.Predicate;
import meghanada.completion.matcher.CompletionMatcher;
import meghanada.reflect.FieldDescriptor;
import meghanada.reflect.MemberDescriptor;

public class CompletionFilters {

  private CompletionFilters() {}

  static Predicate<MemberDescriptor> testThis(
      final CompletionMatcher matcher, final String prefix) {
    return (md -> {
      if (md.isStatic()) {
        return false;
      }
      return privateMemberFilter(md, false, matcher, prefix);
    });
  }

  static Predicate<MemberDescriptor> testThisField(
      final CompletionMatcher matcher, final String prefix) {
    return (md -> {
      if (md.isStatic()) {
        return false;
      }
      return privateMemberFilter(md, false, matcher, prefix)
          && md instanceof FieldDescriptor
          && md.getName().equals(prefix);
    });
  }

  static Predicate<MemberDescriptor> testPrivateStatic(
      final CompletionMatcher matcher, final String prefix) {
    return (md -> {
      if (!md.isStatic()) {
        return false;
      }
      return privateMemberFilter(md, false, matcher, prefix);
    });
  }

  static boolean publicMemberFilter(
      final MemberDescriptor descriptor, final CompletionMatcher matcher, final String prefix) {
    boolean matched = matcher.match(descriptor);
    if (!prefix.isEmpty() && !matched) {
      return false;
    }
    if (descriptor.getMemberType().equals(MemberType.CONSTRUCTOR)) {
      return false;
    }
    return descriptor.isPublic();
  }

  static boolean publicMemberFilter(
      final MemberDescriptor descriptor,
      final boolean staticOnly,
      final boolean ctor,
      final CompletionMatcher matcher,
      final String prefix) {

    final boolean matched = matcher.match(descriptor);
    if (!prefix.isEmpty() && !matched) {
      return false;
    }

    if (!descriptor.isPublic()) {
      return false;
    }
    if (staticOnly) {
      return descriptor.isStatic();
    }
    if (ctor) {
      return !descriptor.isStatic();
    }
    return !descriptor.isStatic() && !descriptor.getMemberType().equals(MemberType.CONSTRUCTOR);
  }

  static boolean packageMemberFilter(
      final MemberDescriptor descriptor,
      final boolean staticOnly,
      final boolean ctor,
      final CompletionMatcher matcher,
      final String target) {

    final boolean matched = matcher.match(descriptor);
    if (!target.isEmpty() && !matched) {
      return false;
    }

    if (descriptor.isPrivate()) {
      return false;
    }
    if (staticOnly) {
      return descriptor.isStatic();
    }
    if (ctor) {
      return !descriptor.isStatic();
    }
    return !descriptor.isStatic() && !descriptor.getMemberType().equals(MemberType.CONSTRUCTOR);
  }

  static boolean privateMemberFilter(
      final MemberDescriptor md,
      final boolean ctor,
      final CompletionMatcher matcher,
      final String prefix) {
    final String name = md.getName();
    final MemberType memberType = md.getMemberType();
    return !(memberType.equals(MemberType.FIELD) && name.startsWith("this$"))
        && !(!prefix.isEmpty() && !matcher.match(md))
        && (ctor || !memberType.equals(MemberType.CONSTRUCTOR));
  }
}
