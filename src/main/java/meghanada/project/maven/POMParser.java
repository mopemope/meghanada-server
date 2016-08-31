package meghanada.project.maven;

import com.google.common.base.Strings;
import meghanada.project.ProjectDependency;
import meghanada.project.ProjectParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.model.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class POMParser {
    private static Logger log = LogManager.getLogger(POMParser.class);

    private RepositorySystem system;
    private RepositorySystemSession session;
    private List<RemoteRepository> repositories;
    private File projectRoot;

    public POMParser(File projectRoot) {
        this.system = getRepositorySystem();
        this.session = getRepositorySystemSession(system);
        this.repositories = getRepositories(system, session);
        this.projectRoot = projectRoot;
    }

    POMInfo parsePom(File pom) throws ProjectParseException {

        try {
            if (!pom.isAbsolute()) {
                pom = pom.getCanonicalFile();
            }
            try (FileReader reader = new FileReader(pom)) {
                POMInfo pomInfo = new POMInfo(pom.getParent());

                final MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
                final Model mavenModel = xpp3Reader.read(reader);

                Parent parent = mavenModel.getParent();
                if (parent != null) {
                    String relativePath = parent.getRelativePath();
                    File parentPom = new File(pom.getParent(), relativePath).getCanonicalFile();

                    if (parentPom.exists()) {
                        log.debug("start  parent {}", parentPom);
                        POMInfo parentPOMInfo = parsePom(parentPom);
                        log.debug("finish parent {}", parentPom);
                        // add all
                        pomInfo.putAll(parentPOMInfo);
                    }
                }

                String groupId = mavenModel.getGroupId();
                if (groupId != null) {
                    pomInfo.groupId = groupId;
                }
                String artifactId = mavenModel.getArtifactId();
                if (artifactId != null) {
                    pomInfo.artifactId = artifactId;
                }
                String version = mavenModel.getVersion();
                if (version != null) {
                    pomInfo.version = version;
                }

                Properties modelProperties = mavenModel.getProperties();
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

                    for (String key : modelProperties.stringPropertyNames()) {
                        String value = modelProperties.getProperty(key);
                        if (value != null) {
                            pomInfo.properties.setProperty(key, value);
                        }
                    }
                    replacePOMProperties(pomInfo);
                }

                for (Repository repository : mavenModel.getRepositories()) {
                    // String url = repository.getUrl();
                    // log.debug("repository url {}", url);
                    final RemoteRepository remoteRepository = new RemoteRepository.Builder(
                            repository.getId(),
                            repository.getName(),
                            repository.getUrl()).build();
                    repositories.add(remoteRepository);
                }

                Build build = mavenModel.getBuild();
                this.parseBuild(pomInfo, build);

                DependencyManagement dependencyManagement = mavenModel.getDependencyManagement();
                if (dependencyManagement != null) {
                    List<org.apache.maven.model.Dependency> dependencies = dependencyManagement.getDependencies();
                    this.parseDependencies(pomInfo, dependencies);
                }

                List<org.apache.maven.model.Dependency> dependencies = mavenModel.getDependencies();
                this.parseDependencies(pomInfo, dependencies);

                return pomInfo;
            }
        } catch (IOException | XmlPullParserException | ArtifactDescriptorException | ArtifactResolutionException e) {
            throw new ProjectParseException(e);
        }
    }

    private void parseDependencies(POMInfo pomInfo, List<org.apache.maven.model.Dependency> dependencies) throws ArtifactResolutionException, ArtifactDescriptorException {
        for (org.apache.maven.model.Dependency d : dependencies) {
            String groupId = getPOMProperties(pomInfo, d.getGroupId());
            String artifactId = getPOMProperties(pomInfo, d.getArtifactId());
            String version = getPOMProperties(pomInfo, d.getVersion());
            if (version == null) {
                // dependencies skip
                continue;
            }

            String code = groupId + ":" + artifactId + ":" + d.getType() + ":" + version;
            // log.debug("dependency: {}", code);
            // resolve self
            Artifact resolved = this.resolveArtifactFromCode(code);
            String scope = d.getScope();
            if (Strings.isNullOrEmpty(scope)) {
                scope = "compile";
            }

            this.toDependency(pomInfo, resolved, scope);
            this.resolveDependencies(pomInfo, code, false);
        }
    }

    private String getPOMProperties(POMInfo pomInfo, String value) {
        if (value != null && value.contains("$")) {
            int startIdx = value.indexOf("$");
            int endIdx = value.indexOf("}");
            String key = value.substring(startIdx + 2, endIdx);
            String replace = value.substring(startIdx, endIdx + 1);
            if (pomInfo.properties != null) {
                String newValue = pomInfo.properties.getProperty(key);
                if (newValue != null) {
                    value = value.replace(replace, newValue);
                }
            }
        }
        return value;
    }

    private void replacePOMProperties(POMInfo pomInfo) {
        if (pomInfo.properties != null) {
            for (String key : pomInfo.properties.stringPropertyNames()) {
                String val = pomInfo.properties.getProperty(key);
                val = this.getPOMProperties(pomInfo, val);
                val = this.getPOMProperties(pomInfo, val);
                if (val != null) {
                    pomInfo.properties.setProperty(key, val);
                }
            }
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

    private void parseBuild(POMInfo pomInfo, Build build) {
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

    private void resolveDependencies(POMInfo pomInfo, String code, boolean includeTest) throws ArtifactDescriptorException {

        Artifact artifact = new DefaultArtifact(code);

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(artifact);
        descriptorRequest.setRepositories(this.repositories);
        ArtifactDescriptorResult descriptorResult = this.system.readArtifactDescriptor(this.session, descriptorRequest);

        for (Dependency dependency : descriptorResult.getDependencies()) {

            boolean isOptional = dependency.isOptional();
            if (!isOptional) {
                String scope = dependency.getScope();

                if (scope.toLowerCase().equals("provided")) {
                    continue;
                }
                if (!includeTest && scope.toLowerCase().equals("test")) {
                    continue;
                }
                try {
                    Artifact resolved = resolveArtifact(dependency.getArtifact());
                    this.toDependency(pomInfo, resolved, scope);
                    String newCode = resolved.getGroupId() + ":" + resolved.getArtifactId() + ":" + resolved.getExtension() + ":" + resolved.getVersion();
                    this.resolveDependencies(pomInfo, newCode, false);
                } catch (ArtifactResolutionException e) {
                    // ignore download old jar
                }
            }
        }
    }

    private void toDependency(POMInfo pomInfo, Artifact resolved, String scope) {
        String id = String.format("%s:%s:%s",
                resolved.getGroupId(),
                resolved.getArtifactId(),
                resolved.getVersion());
        if (Strings.isNullOrEmpty(scope)) {
            scope = "compile";
        }

        ProjectDependency resolvedDependency = new ProjectDependency(id,
                scope,
                resolved.getVersion(),
                resolved.getFile());

        String key = resolved.getGroupId() + ":" + resolved.getArtifactId();

        if (pomInfo.dependencyHashMap.containsKey(key)) {
            ProjectDependency prev = pomInfo.dependencyHashMap.get(key);
            ComparableVersion prevVersion = new ComparableVersion(prev.getVersion());
            ComparableVersion comparableVersion = new ComparableVersion(resolved.getVersion());

            if (comparableVersion.compareTo(prevVersion) > 0) {
                pomInfo.dependencyHashMap.put(key, resolvedDependency);
            }
        } else {
            pomInfo.dependencyHashMap.put(key, resolvedDependency);
        }
    }

    private Artifact resolveArtifactFromCode(String code) throws ArtifactResolutionException {
        Artifact artifact = new DefaultArtifact(code);
        return this.resolveArtifact(artifact);
    }

    private Artifact resolveArtifact(Artifact artifact) throws ArtifactResolutionException {

        // download
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact);
        artifactRequest.setRepositories(this.repositories);
        ArtifactResult result = this.system.resolveArtifact(this.session, artifactRequest);
        return result.getArtifact();
    }

    private List<RemoteRepository> getRepositories(RepositorySystem system, RepositorySystemSession session) {
        return new ArrayList<>(Collections.singletonList(newCentralRepository()));
    }

    private RemoteRepository newCentralRepository() {
        return new RemoteRepository.Builder("central", "default", "http://central.maven.org/maven2/").build();
    }

    private RepositorySystemSession getRepositorySystemSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        final String home = System.getProperty("user.home");

        File repo = new File(new File(home, ".m2"), "repository");

        LocalRepository localRepo = new LocalRepository(repo);

        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        session.setTransferListener(new ConsoleTransferListener());
        session.setRepositoryListener(new ConsoleRepositoryListener());

        return session;

    }

    private RepositorySystem getRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                log.error(exception.getMessage());
            }
        });

        return locator.getService(RepositorySystem.class);
    }

    private class ConsoleTransferListener extends AbstractTransferListener {
        private PrintStream out;

        private Map<TransferResource, Long> downloads = new ConcurrentHashMap<>();

        private int lastLength;

        public ConsoleTransferListener() {
            this(null);
        }

        public ConsoleTransferListener(PrintStream out) {
            this.out = (out != null) ? out : System.out;
        }

        @Override
        public void transferInitiated(TransferEvent event) {
            String message = event.getRequestType() == TransferEvent.RequestType.PUT ? "Uploading" : "Downloading";

            out.println(message + ": " + event.getResource().getRepositoryUrl() + event.getResource().getResourceName());
        }

        @Override
        public void transferProgressed(TransferEvent event) {
            TransferResource resource = event.getResource();
            downloads.put(resource, event.getTransferredBytes());

//            StringBuilder buffer = new StringBuilder(64);
//
//            for (Map.Entry<TransferResource, Long> entry : downloads.entrySet()) {
//                long total = entry.getKey().getContentLength();
//                long complete = entry.getValue();
//
//                buffer.append(getStatus(complete, total)).append("  ");
//            }
//
//            int pad = lastLength - buffer.length();
//            lastLength = buffer.length();
//            pad(buffer, pad);
//            buffer.append('\r');
//
//            out.print(buffer);
        }

        private String getStatus(long complete, long total) {
            if (total >= 1024) {
                return toKB(complete) + "/" + toKB(total) + " KB ";
            } else if (total >= 0) {
                return complete + "/" + total + " B ";
            } else if (complete >= 1024) {
                return toKB(complete) + " KB ";
            } else {
                return complete + " B ";
            }
        }

        private void pad(StringBuilder buffer, int spaces) {
            String block = "                                        ";
            while (spaces > 0) {
                int n = Math.min(spaces, block.length());
                buffer.append(block, 0, n);
                spaces -= n;
            }
        }

        @Override
        public void transferSucceeded(TransferEvent event) {
            transferCompleted(event);

            TransferResource resource = event.getResource();
            long contentLength = event.getTransferredBytes();
            if (contentLength >= 0) {
                String type = (event.getRequestType() == TransferEvent.RequestType.PUT ? "Uploaded" : "Downloaded");
                String len = contentLength >= 1024 ? toKB(contentLength) + " KB" : contentLength + " B";

                String throughput = "";
                long duration = System.currentTimeMillis() - resource.getTransferStartTime();
                if (duration > 0) {
                    long bytes = contentLength - resource.getResumeOffset();
                    DecimalFormat format = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.ENGLISH));
                    double kbPerSec = (bytes / 1024.0) / (duration / 1000.0);
                    throughput = " at " + format.format(kbPerSec) + " KB/sec";
                }

                out.println(type + ": " + resource.getRepositoryUrl() + resource.getResourceName() + " (" + len
                        + throughput + ")");
            }
        }

        @Override
        public void transferFailed(TransferEvent event) {
            transferCompleted(event);

            if (!(event.getException() instanceof MetadataNotFoundException)) {
                log.error(event.getException().getMessage());
            }
        }

        private void transferCompleted(TransferEvent event) {
            downloads.remove(event.getResource());

            StringBuilder buffer = new StringBuilder(64);
            pad(buffer, lastLength);
            buffer.append('\r');
            out.print(buffer);
        }

        public void transferCorrupted(TransferEvent event) {
            log.error(event.getException().getMessage());
        }

        protected long toKB(long bytes) {
            return (bytes + 1023) / 1024;
        }
    }

    private class ConsoleRepositoryListener extends AbstractRepositoryListener {

    }

}
