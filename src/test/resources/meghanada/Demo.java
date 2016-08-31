package meghanada;

import com.google.common.base.Strings;
import meghanada.reflect.CandidateUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;


public class Demo {

    private static Logger log = LogManager.getLogger(Demo.class);

    public static void completion(Session session) throws Exception {
        List<String> list = new ArrayList<>();
        Map<String, Object> map = new HashMap<>();

        String setFile = "src/java/meghanada/Demo.java";
        // session.openSourceFile("/tmp/test.java");
        log.debug(Strings.repeat("=", 160));
        // completionAt symbol
        {
            Collection<? extends CandidateUnit> methodMembers = session.completionAt(setFile, 22, 0, "se");

            for (CandidateUnit member : methodMembers) {
                log.debug(member.toString());
            }
        }

        log.debug(Strings.repeat("=", 160));

        {
            // completionAt methods
            Collection<? extends CandidateUnit> methodMembers = session.completionAt(setFile, 22, 0, "@session");
            for (CandidateUnit member : methodMembers) {
                log.debug(member.toString());
            }
        }

        log.debug(Strings.repeat("=", 160));

        {
            // completionAt class static
            Collection<? extends CandidateUnit> methodMembers = session.completionAt(setFile, 22, 0, "@Logger");
            for (CandidateUnit member : methodMembers) {
                log.debug(member.toString());
            }
        }

        log.debug(Strings.repeat("=", 160));

        {
            // completionAt class
            Collection<? extends CandidateUnit> methodMembers = session.completionAt(setFile, 22, 0, "Log");
            for (CandidateUnit member : methodMembers) {
                log.debug(member.toString());
            }
        }

        log.debug(Strings.repeat("=", 160));

        {
            // completionAt class
            Collection<? extends CandidateUnit> methodMembers = session.completionAt(setFile, 22, 0, "log");
            for (CandidateUnit member : methodMembers) {
                log.debug(member.toString());
            }
        }

        log.debug(Strings.repeat("=", 160));
    }

    public static void completionOther(Session session) throws Exception {

        String setFile = "src/java/meghanada/Demo.java";

        // session.openSourceFile("/tmp/test.java");

        log.debug(Strings.repeat("=", 160));

        {
            Collection<? extends CandidateUnit> methodMembers = session.completionAt(setFile, 21, 34, "@method");
            log.debug("size {}", methodMembers.size());
            for (CandidateUnit member : methodMembers) {
                log.debug(member.toString());
            }
        }

        log.debug(Strings.repeat("=", 160));
    }

    public static void main(String args[]) throws Exception {
        Session session = Session.createSession("./");
        // autobuild
        session.start();
        Thread.sleep(1000 * 20);
//        completionOther(session);
        session.shutdown(5);

    }

}
