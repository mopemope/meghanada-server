package meghanada.server.lsp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public final class Server
    implements LanguageServer, LanguageClientAware, meghanada.server.Launcher {

  private static final Logger log = LogManager.getLogger(Server.class);
  private static final String WORKSPACE_FOLDERS_CAPABILITY_ID = UUID.randomUUID().toString();

  private ServerSocket serverSocket;
  private final String projectRoot;
  private String host;
  private int port;
  private long id;
  private LanguageClient client;

  public Server(final String projectRoot) throws IOException {
    this.projectRoot = projectRoot;
  }

  public Server(final String host, final int port, final String projectRoot) throws IOException {
    final InetAddress address = InetAddress.getByName(host);
    this.host = host;
    this.port = port;
    this.serverSocket = new ServerSocket(port, 0, address);
    this.projectRoot = projectRoot;
    System.setProperty("meghanada.server.port", Integer.toString(this.serverSocket.getLocalPort()));
  }

  @Override
  public void connect(LanguageClient client) {
    this.client = client;
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    client.logMessage(new MessageParams(MessageType.Info, "hello, meghanada server"));

    List<WorkspaceFolder> workspaceFolders = getWorkspaceFolders(params);
    if (!workspaceFolders.isEmpty()) {
      log.info("workspace {}", workspaceFolders);
      // this.getWorkspaceService().setWorkspaceFolders(workspaceFolders);
    }

    ServerCapabilities capabilities = new ServerCapabilities();
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);

    WorkspaceServerCapabilities workspaceServerCapabilities = new WorkspaceServerCapabilities();
    WorkspaceFoldersOptions foldersOptions = new WorkspaceFoldersOptions();
    foldersOptions.setSupported(true);
    foldersOptions.setChangeNotifications(WORKSPACE_FOLDERS_CAPABILITY_ID);
    workspaceServerCapabilities.setWorkspaceFolders(foldersOptions);
    capabilities.setWorkspace(workspaceServerCapabilities);

    CompletionOptions completionOptions = new CompletionOptions();
    completionOptions.setResolveProvider(true);
    capabilities.setCompletionProvider(completionOptions);

    InitializeResult result = new InitializeResult(capabilities);
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    return null;
  }

  @Override
  public void exit() {}

  @Override
  public TextDocumentService getTextDocumentService() {
    return null;
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return null;
  }

  private List<WorkspaceFolder> getWorkspaceFolders(InitializeParams params) {
    List<WorkspaceFolder> initialFolders = new ArrayList<>();

    Object initOptions = params.getInitializationOptions();
    if (initOptions != null && initOptions instanceof JsonObject) {
      JsonObject initializationOptions = (JsonObject) initOptions;
      JsonElement folders = initializationOptions.get("workspaceFolders");
      if (folders != null && folders instanceof JsonArray) {
        JsonArray workspaceFolders = (JsonArray) folders;
        for (JsonElement object : workspaceFolders) {
          String folderUri = object.getAsString();
          String folderName = null;

          int folderNameStart = folderUri.lastIndexOf("/");
          if (folderNameStart > 0) {
            folderName = folderUri.substring(folderUri.lastIndexOf("/") + 1);
          }

          WorkspaceFolder folder = new WorkspaceFolder();
          folder.setName(folderName);
          folder.setUri(folderUri);

          initialFolders.add(folder);
        }
      }
    }

    return initialFolders;
  }

  public void startServer() throws IOException {
    Launcher<LanguageClient> launcher = null;
    if (Objects.isNull(this.serverSocket)) {
      launcher = LSPLauncher.createServerLauncher(this, System.in, System.out);
    } else {
      Socket socket = serverSocket.accept();
      launcher =
          LSPLauncher.createServerLauncher(this, socket.getInputStream(), socket.getOutputStream());
    }
    LanguageClient client = launcher.getRemoteProxy();
    this.connect(client);
    launcher.startListening();
  }
}
