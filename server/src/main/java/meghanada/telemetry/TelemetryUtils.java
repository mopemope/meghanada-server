package meghanada.telemetry;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.hash.Hashing;
import com.google.common.io.Resources;
import io.opencensus.common.Scope;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.stats.Aggregation;
import io.opencensus.stats.BucketBoundaries;
import io.opencensus.stats.Measure;
import io.opencensus.stats.Stats;
import io.opencensus.stats.StatsRecorder;
import io.opencensus.stats.View;
import io.opencensus.stats.ViewManager;
import io.opencensus.tags.TagContext;
import io.opencensus.tags.TagContextBuilder;
import io.opencensus.tags.TagKey;
import io.opencensus.tags.TagValue;
import io.opencensus.tags.Tagger;
import io.opencensus.tags.Tags;
import io.opencensus.trace.Annotation;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.EndSpanOptions;
import io.opencensus.trace.Sampler;
import io.opencensus.trace.Span;
import io.opencensus.trace.Status;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import java.io.Closeable;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

public class TelemetryUtils {

  private static final Logger log = LogManager.getLogger(TelemetryUtils.class);
  private static final Tagger tagger = Tags.getTagger();
  private static final StatsRecorder statsRecorder = Stats.getStatsRecorder();
  private static final EndSpanOptions END_SPAN_OPTIONS =
      EndSpanOptions.builder().setSampleToLocalSpanStore(true).build();
  private static final String CREDENTIALS_JSON = "credentials.json";
  private static final Tracer tracer = Tracing.getTracer();
  private static final Sampler PROBABILITY_SAMPLER_HIGH = Samplers.probabilitySampler(1 / 10.0);
  private static final Sampler PROBABILITY_SAMPLER_MIDDLE = Samplers.probabilitySampler(1 / 50.0);
  private static final Sampler PROBABILITY_SAMPLER_LOW = Samplers.probabilitySampler(1 / 1000.0);
  private static final Sampler NEVER_SAMPLER = Samplers.neverSample();

  public static final Measure.MeasureLong M_AC_CLASSES =
      Measure.MeasureLong.create("autocomplete class", "The number of class", "1");
  public static final Measure.MeasureLong M_AC_SELECTED =
      Measure.MeasureLong.create(
          "selected member", "The number of selected from completion candidates", "1");
  public static final Measure.MeasureLong M_IMP_CLASSES =
      Measure.MeasureLong.create("import class", "The number of import class", "1");
  public static final TagKey KEY_CLASS = TagKey.create("class");
  public static final TagKey KEY_IMPORT_CLASS = TagKey.create("importClass");
  public static final TagKey KEY_MEMBER = TagKey.create("member");
  public static final TagKey KEY_SRC_FILE = TagKey.create("source");
  public static final TagKey KEY_STATUS = TagKey.create("status");
  public static final TagKey KEY_ERROR = TagKey.create("error");
  public static final String PROJECT_ID = "meghanada-240122";

  private static boolean enabledExporter;
  private static Map<String, AttributeValue> javaAttributeMap = new HashMap<>(8);
  private static Map<String, AttributeValue> osAttributeMap = new HashMap<>(8);

  static {
    Map<String, AttributeValue> javaMap = new HashMap<>(16);
    Map<String, AttributeValue> osMap = new HashMap<>(16);
    String runtimeName = System.getProperty("java.runtime.name");
    javaMap.put("java.runtime.name", AttributeValue.stringAttributeValue(runtimeName));

    String runtimeVersion = System.getProperty("java.runtime.version");
    javaMap.put("java.runtime.version", AttributeValue.stringAttributeValue(runtimeVersion));

    String vmName = System.getProperty("java.vm.name");
    javaMap.put("java.vm.name", AttributeValue.stringAttributeValue(vmName));

    String vmVersion = System.getProperty("java.vm.version");
    javaMap.put("java.vm.version", AttributeValue.stringAttributeValue(vmVersion));

    String osName = System.getProperty("os.name");
    osMap.put("os.name", AttributeValue.stringAttributeValue(osName));

    String osVersion = System.getProperty("os.version");
    osMap.put("os.version", AttributeValue.stringAttributeValue(osVersion));

    SystemInfo si = new SystemInfo();
    HardwareAbstractionLayer hal = si.getHardware();
    CentralProcessor processor = hal.getProcessor();
    String cpuCount = Integer.toString(processor.getLogicalProcessorCount());
    osMap.put("cpu.count", AttributeValue.stringAttributeValue(cpuCount));
    String memoryTotal = Long.toString(hal.getMemory().getTotal());
    osMap.put("memory.total", AttributeValue.stringAttributeValue(memoryTotal));

    javaAttributeMap = javaMap;
    osAttributeMap = osMap;
  }

  public static boolean enableTelemetry() {
    String enable = System.getProperty("meghanada.telemetry.enable");
    return nonNull(enable) && enable.equalsIgnoreCase("true");
  }

  public static boolean setupExporter() {
    boolean enable = setupStackdriverTraceExporter();
    setupStackdriverStatsExporter();
    registerAllViews();
    if (enable) {
      TraceConfig traceConfig = Tracing.getTraceConfig();
      traceConfig.updateActiveTraceParams(
          traceConfig
              .getActiveTraceParams()
              .toBuilder()
              // .setSampler(Samplers.alwaysSample())
              .setSampler(PROBABILITY_SAMPLER_MIDDLE)
              .build());
      enabledExporter = true;
      System.setProperty("meghanada.stackdriver.enable", "true");
      java.util.logging.LogManager.getLogManager().reset();
    }
    return enable;
  }

  public static boolean startedExporter() {
    return enabledExporter;
  }

  public static boolean setupStackdriverTraceExporter() {
    if (enableTelemetry()) {
      try {
        URL url = Resources.getResource(CREDENTIALS_JSON);
        StackdriverTraceExporter.createAndRegister(
            StackdriverTraceConfiguration.builder()
                .setProjectId(PROJECT_ID)
                .setCredentials(ServiceAccountCredentials.fromStream(url.openStream()))
                .build());
        log.info("enable stackdriver trace exporter");
        return true;
      } catch (Throwable e) {
        log.warn("{}", e.getMessage());
      }
    }
    return false;
  }

  public static boolean setupStackdriverStatsExporter() {
    if (enableTelemetry()) {
      try {
        URL url = Resources.getResource(CREDENTIALS_JSON);
        StackdriverStatsExporter.createAndRegister(
            StackdriverStatsConfiguration.builder()
                .setProjectId(PROJECT_ID)
                .setCredentials(ServiceAccountCredentials.fromStream(url.openStream()))
                .build());
        log.info("enable stackdriver stats exporter");
        return true;
      } catch (Throwable e) {
        log.warn("{}", e.getMessage());
      }
    }
    return false;
  }

  private static void registerAllViews() {
    Aggregation latencyDistribution =
        Aggregation.Distribution.create(
            BucketBoundaries.create(
                Arrays.asList(
                    0.0, // >=0ms
                    25.0, // >=25ms
                    50.0, // >=50ms
                    100.0, // >=100ms
                    200.0, // >=200ms
                    400.0, // >=400ms
                    800.0, // >=800ms
                    1000.0, // >=1s
                    2000.0, // >=2s
                    5000.0, // >=5s
                    8000.0, // >=8s
                    10000.0 // >=10s
                    )));
    //
    //    Aggregation lengthsDistribution =
    //        Aggregation.Distribution.create(
    //            BucketBoundaries.create(
    //                Arrays.asList(
    //                    0.0, // >=0B
    //                    5.0, // >=5B
    //                    10.0, // >=10B
    //                    20.0, // >=20B
    //                    40.0, // >=40B
    //                    80.0, // >=80B
    //                    100.0, // >=100B
    //                    200.0, // >=200B
    //                    500.0, // >=500B
    //                    800.0, // >=800B
    //                    1000.0 // >=1000B
    //                    )));
    //
    //    Aggregation sizeDistribution =
    //        Aggregation.Distribution.create(
    //            BucketBoundaries.create(
    //                Arrays.asList(
    //                    0.0, // >=0KB
    //                    1024.0, // >=1KB
    //                    2048.0, // >=2KB
    //                    4096.0, // >=4KB
    //                    8152.0, // >=8KB
    //                    16304.0, // >=16KB
    //                    32608.0, // >=32KB
    //                    65216.0, // >=64KB
    //                    130432.0, // >=128KB
    //                    260864.0, // >=256KB
    //                    521728.0, // >=512KB
    //                    1043456.0, // >=1MB
    //                    2086912.0, // >=2MB
    //                    4173824.0, // >=4MB
    //                    8347648.0, // >=8MB
    //                    16695296.0 // >=16MB
    //                    )));

    Aggregation classCountAggregation = Aggregation.Count.create();
    Aggregation impClassAggregation = Aggregation.Count.create();
    Aggregation selectedCountAggregation = Aggregation.Count.create();

    View[] views =
        new View[] {
          View.create(
              View.Name.create("meghanada/autocomplete"),
              "The number of classes completed by autocomplete",
              M_AC_CLASSES,
              classCountAggregation,
              Collections.unmodifiableList(Arrays.asList(KEY_CLASS))),
          View.create(
              View.Name.create("meghanada/autocomplete-selected"),
              "The number of member selected from autocomplete",
              M_AC_SELECTED,
              selectedCountAggregation,
              Collections.unmodifiableList(Arrays.asList(KEY_MEMBER))),
          View.create(
              View.Name.create("meghanada/importClass"),
              "The number of classes being imported",
              M_IMP_CLASSES,
              impClassAggregation,
              Collections.unmodifiableList(Arrays.asList(KEY_IMPORT_CLASS, KEY_CLASS))),
        };

    ViewManager vmgr = Stats.getViewManager();
    for (View view : views) {
      vmgr.registerView(view);
    }
  }

  public static void recordStat(Measure.MeasureLong ml, Long n) {
    TagContext tctx = tagger.emptyBuilder().build();
    try (Scope ss = tagger.withTagContext(tctx)) {
      statsRecorder.newMeasureMap().put(ml, n).record();
    }
  }

  public static void recordTaggedStat(TagKey key, String value, Measure.MeasureLong ml, Long n) {
    TagContext tctx = tagger.emptyBuilder().putLocal(key, TagValue.create(value)).build();
    try (Scope ss = tagger.withTagContext(tctx)) {
      statsRecorder.newMeasureMap().put(ml, n).record();
    }
  }

  public static void recordTaggedStat(
      TagKey key, String value, Measure.MeasureDouble md, Double d) {
    TagContext tctx = tagger.emptyBuilder().putLocal(key, TagValue.create(value)).build();
    try (Scope ss = tagger.withTagContext(tctx)) {
      statsRecorder.newMeasureMap().put(md, d).record();
    }
  }

  public static void recordTaggedStat(
      TagKey[] keys, String[] values, Measure.MeasureDouble md, Double d) {
    TagContextBuilder builder = tagger.emptyBuilder();
    for (int i = 0; i < keys.length; i++) {
      builder.putLocal(keys[i], TagValue.create(values[i]));
    }
    TagContext tctx = builder.build();
    try (Scope ss = tagger.withTagContext(tctx)) {
      statsRecorder.newMeasureMap().put(md, d).record();
    }
  }

  public static void recordTaggedStat(
      TagKey[] keys, String[] values, Measure.MeasureLong md, Long n) {
    TagContextBuilder builder = tagger.emptyBuilder();
    for (int i = 0; i < keys.length; i++) {
      builder.putLocal(keys[i], TagValue.create(values[i]));
    }
    TagContext tctx = builder.build();
    try (Scope ss = tagger.withTagContext(tctx)) {
      statsRecorder.newMeasureMap().put(md, n).record();
    }
  }

  public static void recordAutocompleteStat(String className) {
    TelemetryUtils.recordTaggedStat(
        TelemetryUtils.KEY_CLASS, className, TelemetryUtils.M_AC_CLASSES, 1L);
  }

  public static void recordImportStat(String className) {
    TelemetryUtils.recordTaggedStat(
        TelemetryUtils.KEY_CLASS, className, TelemetryUtils.M_IMP_CLASSES, 1L);
  }

  public static void recordSelectedCompletion(String memberDesc) {
    TelemetryUtils.recordTaggedStat(
        TelemetryUtils.KEY_MEMBER, memberDesc, TelemetryUtils.M_AC_SELECTED, 1L);
  }

  public static ParentSpan startExplicitParentSpan(String name) {
    Span span;
    if (enabledExporter) {
      span = tracer.spanBuilderWithExplicitParent(name, null).startSpan();
    } else {
      span = tracer.spanBuilderWithExplicitParent(name, null).setRecordEvents(true).startSpan();
    }

    String version = System.getProperty("meghanada-server.version", "");
    String uid = System.getProperty("meghanada-server.uid", "");
    Map<String, AttributeValue> m = new HashMap<>(2);
    m.put("meghanada-server.version", AttributeValue.stringAttributeValue(version));
    m.put("meghanada-server.uid", AttributeValue.stringAttributeValue(uid));
    span.addAnnotation(Annotation.fromDescriptionAndAttributes("meghanada properties", m));
    Annotation vmAnno =
        Annotation.fromDescriptionAndAttributes("java.vm properties", javaAttributeMap);
    span.addAnnotation(vmAnno);
    Annotation osAnno = Annotation.fromDescriptionAndAttributes("os properties", osAttributeMap);
    span.addAnnotation(osAnno);
    return new ParentSpan(span);
  }

  public static ScopedSpan withSpan(Span span) {
    Scope scope = tracer.withSpan(span);
    return new ScopedSpan(scope);
  }

  private static void addAnnotaion(Annotation annotation) {
    Span current = tracer.getCurrentSpan();
    current.addAnnotation(annotation);
  }

  private static void addAnnotaion(String message) {
    Span current = tracer.getCurrentSpan();
    current.addAnnotation(message);
  }

  public static void setStatus(Status status) {
    Span current = tracer.getCurrentSpan();
    current.setStatus(status);
  }

  public static void setStatusINTERNAL(String message) {
    TelemetryUtils.setStatus(Status.INTERNAL.withDescription(message));
  }

  public static ScopedSpan startScopedSpan(String name) {
    Scope scope = tracer.spanBuilder(name).startScopedSpan();
    return new ScopedSpan(scope);
  }

  private static ScopedSpan startScopedSpan(String name, Sampler sampler) {
    Scope scope = tracer.spanBuilder(name).setSampler(sampler).startScopedSpan();
    return new ScopedSpan(scope);
  }

  public static ScopedSpan startScopedSpanHigh(String name) {
    return startScopedSpan(name, PROBABILITY_SAMPLER_HIGH);
  }

  public static ScopedSpan startScopedSpanMiddle(String name) {
    return startScopedSpan(name, PROBABILITY_SAMPLER_MIDDLE);
  }

  public static ScopedSpan startScopedSpanLow(String name) {
    return startScopedSpan(name, PROBABILITY_SAMPLER_LOW);
  }

  public static AnnotationBuilder annotationBuilder() {
    return new AnnotationBuilder();
  }

  public static String getUID() {
    String osName = System.getProperty("os.name");
    String osVersion = System.getProperty("os.version");
    String userHome = System.getProperty("user.home");
    String userLang = System.getProperty("user.language");
    String userName = System.getProperty("user.name");

    return Hashing.sha256()
        .newHasher()
        .putString(osName, StandardCharsets.UTF_8)
        .putString(osVersion, StandardCharsets.UTF_8)
        .putString(userHome, StandardCharsets.UTF_8)
        .putString(userLang, StandardCharsets.UTF_8)
        .putString(userName, StandardCharsets.UTF_8)
        .hash()
        .toString();
  }

  public static class ParentSpan implements Closeable {

    private final Span span;

    ParentSpan(Span span) {
      this.span = span;
    }

    public void setStatusINTERNAL(String message) {
      span.setStatus(Status.INTERNAL.withDescription(message));
    }

    public Span getSpan() {
      return span;
    }

    public void end() {
      if (enabledExporter) {
        span.end();
      } else {
        span.end(END_SPAN_OPTIONS);
      }
    }

    @Override
    public void close() {
      this.end();
    }
  }

  public static class ScopedSpan implements Closeable {

    private final Scope scope;

    ScopedSpan(Scope scope) {
      this.scope = scope;
    }

    public void addAnnotation(Annotation annotation) {
      TelemetryUtils.addAnnotaion(annotation);
    }

    public void addAnnotation(String annotation) {
      TelemetryUtils.addAnnotaion(annotation);
    }

    public void setStatusINTERNAL(String message) {
      TelemetryUtils.setStatusINTERNAL(message);
    }

    @Override
    public void close() {
      scope.close();
    }
  }

  public static class AnnotationBuilder {

    private Map<String, AttributeValue> map = new HashMap<>(8);

    public boolean isMask() {
      return enabledExporter;
    }

    public AnnotationBuilder put(String key, String val) {
      if (isMask()) {
        val = "xxxxxxxx";
      }
      if (isNull(val)) {
        val = "null";
      }
      this.map.put(key, AttributeValue.stringAttributeValue(val));
      return this;
    }

    public AnnotationBuilder put(String key, long val) {
      this.map.put(key, AttributeValue.longAttributeValue(val));
      return this;
    }

    public AnnotationBuilder put(String key, boolean val) {
      this.map.put(key, AttributeValue.booleanAttributeValue(val));
      return this;
    }

    public AnnotationBuilder put(String key, double val) {
      this.map.put(key, AttributeValue.doubleAttributeValue(val));
      return this;
    }

    public Annotation build(String description) {
      return Annotation.fromDescriptionAndAttributes(description, this.map);
    }
  }
}
