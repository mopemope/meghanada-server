package meghanada.project.maven;

import com.google.common.base.Strings;
import meghanada.project.ProjectParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.model.*;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

class POMParser {
    private static final Logger log = LogManager.getLogger(POMParser.class);

    private final File projectRoot;

    POMParser(File projectRoot) {
        this.projectRoot = projectRoot;
    }

    private static String getPOMProperties(final POMInfo pomInfo, @Nullable String value) {
        if (value != null && value.contains("$")) {
            int startIdx = value.indexOf('$');
            int endIdx = value.indexOf('}');
            String key = value.substring(startIdx + 2, endIdx);
            String replace = value.substring(startIdx, endIdx + 1);
            String newValue = pomInfo.properties.getProperty(key);
            if (newValue != null) {
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
            if (val != null) {
                pomInfo.properties.setProperty(key, val);
            }
        }
    }

    POMInfo parsePom(File pom) throws ProjectParseException {

        try {
            if (!pom.isAbsolute()) {
                pom = pom.getCanonicalFile();
            }

            final ModelBuildingRequest req = new DefaultModelBuildingRequest();
            req.setPomFile(pom);

            final DefaultModelBuilderFactory factory = new DefaultModelBuilderFactory();
            final Model mavenModel = factory.newInstance().build(req).getEffectiveModel();
            final POMInfo pomInfo = new POMInfo(pom.getParent());

            final Parent parent = mavenModel.getParent();
            if (parent != null) {
                final String relativePath = parent.getRelativePath();
                final File parentDir = new File(pom.getParent(), relativePath).getCanonicalFile();
                final File parentPom = new File(parentDir, MavenProject.MVN_PROJECT_FILE);
                if (parentDir.exists() && parentPom.exists()) {
                    log.debug("start  parent {}", parentPom);
                    POMInfo parentPOMInfo = parsePom(parentPom);
                    log.debug("finish parent {}", parentPom);
                    // add all
                    pomInfo.putAll(parentPOMInfo);
                }
            }

            final String groupId = mavenModel.getGroupId();
            if (groupId != null) {
                pomInfo.groupId = groupId;
            }
            final String artifactId = mavenModel.getArtifactId();
            if (artifactId != null) {
                pomInfo.artifactId = artifactId;
            }
            final String version = mavenModel.getVersion();
            if (version != null) {
                pomInfo.version = version;
            }

            final Properties modelProperties = mavenModel.getProperties();
            if (modelProperties != null) {
                if (groupId != null) {
                    modelProperties.put("project.groupId", groupId);
                }
                if (artifactId != null) {
                    modelProperties.put("project.artifactId", artifactId);
                }
                if (version != null) {
                    modelProperties.put("project.version", version);
                }

                for (final String key : modelProperties.stringPropertyNames()) {
                    String value = modelProperties.getProperty(key);
                    if (value != null) {
                        pomInfo.properties.setProperty(key, value);
                    }
                }
                POMParser.replacePOMProperties(pomInfo);
            }

            final Build build = mavenModel.getBuild();
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
        if (build != null) {
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
                    if (conf != null && conf instanceof Xpp3Dom) {
                        Xpp3Dom confDom = (Xpp3Dom) conf;
                        Xpp3Dom sources = confDom.getChild("sources");
                        if (sources != null) {
                            Xpp3Dom[] children = sources.getChildren();
                            if (children != null) {
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
                if (conf != null && conf instanceof Xpp3Dom) {
                    Xpp3Dom confDom = (Xpp3Dom) conf;
                    Xpp3Dom source = confDom.getChild("source");
                    if (source != null) {
                        pomInfo.compileSource = source.getValue();
                    }

                    Xpp3Dom target = confDom.getChild("target");
                    if (target != null) {
                        pomInfo.compileTarget = target.getValue();
                    }

                }
            }
        }
    }

}
