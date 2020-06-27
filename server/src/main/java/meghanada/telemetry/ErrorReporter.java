package meghanada.telemetry;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.errorreporting.v1beta1.ReportErrorsServiceClient;
import com.google.cloud.errorreporting.v1beta1.ReportErrorsServiceSettings;
import com.google.common.io.Resources;
import com.google.devtools.clouderrorreporting.v1beta1.ErrorContext;
import com.google.devtools.clouderrorreporting.v1beta1.ProjectName;
import com.google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent;
import java.io.IOException;
import java.net.URL;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ErrorReporter {

  private static final Logger log = LogManager.getLogger(TelemetryUtils.class);
  private static ProjectName projectName = ProjectName.of(TelemetryUtils.PROJECT_ID);
  private static ReportErrorsServiceSettings reportErrorsServiceSettings = null;

  public static void report(Throwable throwable) {
    try {
      if (TelemetryUtils.enableTelemetry()) {
        ReportErrorsServiceSettings reportErrorsServiceSettings = getReportErrorsServiceSettings();

        try (ReportErrorsServiceClient reportErrorsServiceClient =
            ReportErrorsServiceClient.create(reportErrorsServiceSettings)) {
          String message = convert(throwable);
          ErrorContext context = ErrorContext.newBuilder().setUser(TelemetryUtils.getUID()).build();
          ReportedErrorEvent errorEvent =
              ReportedErrorEvent.getDefaultInstance()
                  .toBuilder()
                  .setContext(context)
                  .setMessage(message)
                  .build();
          reportErrorsServiceClient.reportErrorEvent(projectName, errorEvent);
        }
      }
    } catch (Throwable t) {
      log.catching(t);
    }
  }

  private static String convert(Throwable throwable) {
    return ExceptionUtils.getStackTrace(throwable);
  }

  private static synchronized ReportErrorsServiceSettings getReportErrorsServiceSettings()
      throws IOException {
    if (reportErrorsServiceSettings != null) {
      return reportErrorsServiceSettings;
    }

    URL url = Resources.getResource(TelemetryUtils.CREDENTIALS_JSON);
    ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(url.openStream());
    ReportErrorsServiceSettings settings =
        ReportErrorsServiceSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
            .build();
    reportErrorsServiceSettings = settings;
    return reportErrorsServiceSettings;
  }
}
