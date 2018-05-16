package meghanada.project.maven;

import static java.util.Objects.nonNull;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import javax.annotation.Nullable;
import meghanada.config.Config;
import meghanada.project.ProjectParseException;
import meghanada.utils.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Repository;
import org.apache.maven.model.Resource;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.building.ModelSource2;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.codehaus.plexus.util.xml.Xpp3Dom;

class POMParser {

  private static final Logger log = LogManager.getLogger(POMParser.class);

  private final File projectRoot;

  POMParser(File projectRoot) {
    this.projectRoot = projectRoot;
  }

  private static String getPOMProperties(final POMInfo pomInfo, @Nullable String value) {
    if (nonNull(value) && value.contains("$")) {
      int startIdx = value.indexOf('$');
      int endIdx = value.indexOf('}');
      String key = value.substring(startIdx + 2, endIdx);
      String replace = value.substring(startIdx, endIdx + 1);
      String newValue = pomInfo.properties.getProperty(key);
      if (nonNull(newValue)) {
        value = value.replace(replace, newValue);
      }
    }
    return value;
  }

  private static void replacePOMProperties(final POMInfo pomInfo) {
    for (String key : pomInfo.properties.stringPropertyNames()) {
      String val = pomInfo.properties.getProperty(key);
      val = POMParser.getPOMProperties(pomInfo, val);
      val = POMParser.getPOMProperties(pomInfo, val);
      if (nonNull(val)) {
        pomInfo.properties.setProperty(key, val);
      }
    }
  }

  POMInfo parsePom(File pom) throws ProjectParseException {

    try {
      if (!pom.isAbsolute()) {
        pom = pom.getCanonicalFile();
      }

      ModelBuildingRequest req = new DefaultModelBuildingRequest();
      req.setPomFile(pom);
      req.setSystemProperties(System.getProperties());
      req.setModelResolver(new LocalModelResolver(this.projectRoot));
      req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
      req.setProcessPlugins(true);

      DefaultModelBuilderFactory factory = new DefaultModelBuilderFactory();
      DefaultModelBuilder builder = factory.newInstance();
      ModelBuildingResult result = builder.build(req);

      Model mavenModel = result.getEffectiveModel();
      POMInfo pomInfo = new POMInfo(pom.getParent());

      String groupId = mavenModel.getGroupId();
      if (nonNull(groupId)) {
        pomInfo.groupId = groupId;
      }
      String artifactId = mavenModel.getArtifactId();
      if (nonNull(artifactId)) {
        pomInfo.artifactId = artifactId;
      }
      String version = mavenModel.getVersion();
      if (nonNull(version)) {
        pomInfo.version = version;
      }

      Properties modelProperties = mavenModel.getProperties();
      if (nonNull(modelProperties)) {
        if (nonNull(groupId)) {
          modelProperties.put("project.groupId", groupId);
        }
        if (nonNull(artifactId)) {
          modelProperties.put("project.artifactId", artifactId);
        }
        if (nonNull(version)) {
          modelProperties.put("project.version", version);
        }

        for (String key : modelProperties.stringPropertyNames()) {
          String value = modelProperties.getProperty(key);
          if (nonNull(value)) {
            pomInfo.properties.setProperty(key, value);
          }
        }
        POMParser.replacePOMProperties(pomInfo);
      }

      Build build = mavenModel.getBuild();
      this.parseBuild(pomInfo, build);

      return pomInfo;
    } catch (IOException | ModelBuildingException e) {
      throw new ProjectParseException(e);
    }
  }

  private File normalize(POMInfo pomInfo, String src) {
    src = getPOMProperties(pomInfo, src);
    File file = new File(src);
    if (!file.isAbsolute()) {
      file = new File(this.projectRoot, src);
    }
    return file;
  }

  private void parseBuild(POMInfo pomInfo, @Nullable Build build) {
    if (nonNull(build)) {
      {
        String src = build.getSourceDirectory();
        if (!Strings.isNullOrEmpty(src)) {
          pomInfo.sourceDirectory.add(normalize(pomInfo, src));
        }
        for (Resource resource : build.getResources()) {
          pomInfo.resourceDirectories.add(normalize(pomInfo, resource.getDirectory()));
        }
        String out = build.getOutputDirectory();
        if (!Strings.isNullOrEmpty(out)) {
          pomInfo.outputDirectory = normalize(pomInfo, out);
        }
      }
      {
        String src = build.getTestSourceDirectory();
        if (!Strings.isNullOrEmpty(src)) {
          pomInfo.testSourceDirectory.add(normalize(pomInfo, src));
        }
        for (Resource resource : build.getTestResources()) {
          pomInfo.testResourceDirectories.add(normalize(pomInfo, resource.getDirectory()));
        }
        String out = build.getTestOutputDirectory();
        if (!Strings.isNullOrEmpty(out)) {
          pomInfo.testOutputDirectory = normalize(pomInfo, out);
        }
      }

      parsePlugins(pomInfo, build);
    }
  }

  private void parsePlugins(POMInfo pomInfo, Build build) {
    for (Plugin plugin : build.getPlugins()) {
      if (plugin.getArtifactId().equals("build-helper-maven-plugin")) {
        for (PluginExecution pluginExecution : plugin.getExecutions()) {
          Object conf = pluginExecution.getConfiguration();
          if (nonNull(conf) && conf instanceof Xpp3Dom) {
            Xpp3Dom confDom = (Xpp3Dom) conf;
            Xpp3Dom sources = confDom.getChild("sources");
            if (nonNull(sources)) {
              Xpp3Dom[] children = sources.getChildren();
              if (nonNull(children)) {
                for (Xpp3Dom s : sources.getChildren()) {
                  String value = s.getValue();
                  if (!Strings.isNullOrEmpty(value)) {
                    pomInfo.sourceDirectory.add(normalize(pomInfo, value));
                  }
                }
              }
            }
          }
        }
      }
      if (plugin.getArtifactId().equals("maven-compiler-plugin")) {
        Object conf = plugin.getConfiguration();
        if (nonNull(conf) && conf instanceof Xpp3Dom) {
          Xpp3Dom confDom = (Xpp3Dom) conf;
          Xpp3Dom source = confDom.getChild("source");
          if (nonNull(source)) {
            pomInfo.compileSource = source.getValue();
          }

          Xpp3Dom target = confDom.getChild("target");
          if (nonNull(target)) {
            pomInfo.compileTarget = target.getValue();
          }
        }
      }
    }
  }

  static class LocalModelResolver implements ModelResolver {

    private Set<File> loaded = new HashSet<>(8);
    private File projectRoot;

    public LocalModelResolver(final File projectRoot) {
      this.projectRoot = projectRoot;
    }

    @Override
    public ModelSource2 resolveModel(
        final String groupId, final String artifactId, final String version)
        throws UnresolvableModelException {
      final Config config = Config.load();
      final String localRepository = config.getMavenLocalRepository();
      final String parent = StringUtils.replace(groupId, ".", File.separator);
      final String path =
          Joiner.on(File.separator).join(localRepository, parent, artifactId, version);
      final String file = artifactId + '-' + version + ".pom";
      final File pom = new File(path, file);
      final boolean exists = pom.exists();

      if (!loaded.contains(pom)) {
        loaded.add(pom);
      }
      if (exists) {
        return new FileModelSource(pom);
      }
      return null;
    }

    @Override
    public ModelSource2 resolveModel(final Parent parent) throws UnresolvableModelException {
      final String groupId = parent.getGroupId();
      final String artifactId = parent.getArtifactId();
      final String version = parent.getVersion();
      final ModelSource2 model = resolveModel(groupId, artifactId, version);
      if (nonNull(model)) {
        return model;
      }
      String relativePath = parent.getRelativePath();

      if (nonNull(relativePath) && !relativePath.isEmpty()) {
        File pom = new File(this.projectRoot, relativePath);
        if (!relativePath.endsWith("pom.xml")) {
          pom = new File(relativePath, "pom.xml");
        }
        if (!loaded.contains(pom)) {
          loaded.add(pom);
        }
        if (pom.exists()) {
          return new FileModelSource(pom);
        }
      }
      return null;
    }

    @Override
    public ModelSource2 resolveModel(Dependency dependency) throws UnresolvableModelException {
      return null;
    }

    @Override
    public void addRepository(Repository repository) throws InvalidRepositoryException {}

    @Override
    public void addRepository(Repository repository, boolean replace)
        throws InvalidRepositoryException {}

    @Override
    public ModelResolver newCopy() {
      return this;
    }
  }
}
