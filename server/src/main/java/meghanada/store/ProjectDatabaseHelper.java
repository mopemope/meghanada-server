package meghanada.store;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static meghanada.store.ProjectDatabase.ID;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityId;
import jetbrains.exodus.entitystore.EntityIterable;
import meghanada.analyze.CompileResult;
import meghanada.analyze.Source;
import meghanada.project.Project;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProjectDatabaseHelper {

  private static final String PROP_DECLARATION = "declaration";
  private static final String PROP_FILE_PATH = "filePath";
  private static final String BLOB_PROP_MEMBERS = "members";
  private static final String BLOB_PROP_CHECKSUM = "checksum";
  private static final String BLOB_PROP_CALLER = "caller";
  private static final Logger log = LogManager.getLogger(ProjectDatabaseHelper.class);
  private static int indexTTL = 60 * 60;

  public static void saveClassIndexes(Collection<ClassIndex> indexes, boolean allowUpdate) {
    ProjectDatabase projectDatabase = ProjectDatabase.getInstance();
    if (!indexes.isEmpty()) {
      projectDatabase.asyncStoreObjects(indexes, allowUpdate);
    }
  }

  public static boolean getLoadJar(String filePath) {
    ProjectDatabase database = ProjectDatabase.getInstance();
    return database.computeInReadonly(
        txn -> {
          EntityIterable it = txn.find(ClassIndex.FILE_ENTITY_TYPE, "filePath", filePath);
          return nonNull(it.getFirst());
        });
  }

  public static void saveLoadJar(String filePath) {
    ProjectDatabase database = ProjectDatabase.getInstance();
    boolean b =
        database.execute(
            txn -> {
              EntityIterable it = txn.find(ClassIndex.FILE_ENTITY_TYPE, "filePath", filePath);
              if (nonNull(it.getFirst())) {
                return false;
              }
              Entity entity = txn.newEntity(ClassIndex.FILE_ENTITY_TYPE);
              entity.setProperty("filePath", filePath);
              return true;
            });
  }

  public static List<ClassIndex> getClassIndexes(String filePath) {
    ProjectDatabase database = ProjectDatabase.getInstance();
    return database.find(
        ClassIndex.ENTITY_TYPE,
        "filePath",
        filePath,
        entity -> {
          try (InputStream in = entity.getBlob(ProjectDatabase.SERIALIZE_KEY)) {
            return Serializer.readObject(in, ClassIndex.class);
          } catch (Exception e) {
            log.warn(e.getMessage());
            return null;
          }
        });
  }

  public static File getClassFile(String fqcn) {
    ProjectDatabase database = ProjectDatabase.getInstance();
    Optional<String> res =
        database.findOne(
            ClassIndex.ENTITY_TYPE,
            PROP_DECLARATION,
            fqcn,
            entity -> (String) entity.getProperty(PROP_FILE_PATH));

    return res.map(File::new).orElse(null);
  }

  public static ClassIndex getClassIndex(String fqcn) throws Exception {
    ProjectDatabase database = ProjectDatabase.getInstance();
    return database.loadObject(ClassIndex.ENTITY_TYPE, fqcn, ClassIndex.class);
  }

  public static void getClassIndexLinks(String fqcn, String linkName, Consumer<EntityIterable> fn)
      throws Exception {
    Map<String, ClassIndex> globalClassIndex =
        CachedASMReflector.getInstance().getGlobalClassIndex();
    if (!globalClassIndex.containsKey(fqcn)) {
      return;
    }
    ClassIndex index = globalClassIndex.get(fqcn);
    EntityId entityId = index.getEntityId();
    ProjectDatabase database = ProjectDatabase.getInstance();

    boolean result =
        database.execute(
            txn -> {
              Entity classEntity = txn.getEntity(entityId);
              EntityIterable iterable = classEntity.getLinks(linkName);
              fn.accept(iterable);
              return true;
            });
  }

  public static boolean saveMemberDescriptors(
      final String fqcn, final List<MemberDescriptor> members) {

    ProjectDatabase database = ProjectDatabase.getInstance();
    return database.execute(
        txn -> {
          EntityIterable it = txn.find(ClassIndex.ENTITY_TYPE, ID, fqcn);
          Entity entity = it.getFirst();

          if (isNull(entity)) {
            return false;
          }

          try {
            ProjectDatabase.setSerializeBlobData(entity, BLOB_PROP_MEMBERS, members);
          } catch (IOException e) {
            log.catching(e);
            txn.abort();
            return false;
          }

          // txn.saveEntity(entity);
          return true;
        });
  }

  public static Optional<List<MemberDescriptor>> getMemberDescriptors(String fqcn) {

    ProjectDatabase database = ProjectDatabase.getInstance();
    return database.computeInReadonly(
        txn -> {
          EntityIterable it =
              txn.find(ClassIndex.ENTITY_TYPE, ID, fqcn)
                  .intersect(txn.findWithBlob(ClassIndex.ENTITY_TYPE, BLOB_PROP_MEMBERS));
          Entity entity = it.getFirst();

          if (isNull(entity)) {
            return Optional.empty();
          }

          try (InputStream in = entity.getBlob(BLOB_PROP_MEMBERS)) {
            @SuppressWarnings("unchecked")
            List<MemberDescriptor> res = Serializer.readObject(in, ArrayList.class);
            return Optional.ofNullable(res);
          } catch (Exception e) {
            log.catching(e);
            return Optional.empty();
          }
        });
  }

  public static boolean deleteMemberDescriptors(String fqcn) {

    ProjectDatabase database = ProjectDatabase.getInstance();
    return database.execute(
        txn -> {
          EntityIterable it = txn.find(ClassIndex.ENTITY_TYPE, ID, fqcn);
          Entity entity = it.getFirst();

          if (isNull(entity)) {
            return false;
          }
          boolean result = entity.deleteBlob(BLOB_PROP_MEMBERS);
          // txn.saveEntity(entity);
          return result;
        });
  }

  public static void saveProject(Project project, boolean async) {
    ProjectDatabase database = ProjectDatabase.getInstance();
    if (async) {
      database.asyncStoreObject(project, true);
      return;
    }
    long l = database.storeObject(project, true);
  }

  public static Project loadProject(String projectRoot) throws Exception {
    ProjectDatabase database = ProjectDatabase.getInstance();
    return database.loadObject(Project.ENTITY_TYPE, projectRoot, Project.class);
  }

  public static void saveSource(Source source) {
    ProjectDatabase database = ProjectDatabase.getInstance();
    database.asyncStoreObject(source, true);
  }

  public static void saveSources(Collection<Source> sources, boolean async) {
    ProjectDatabase database = ProjectDatabase.getInstance();
    if (async) {
      database.asyncStoreObjects(sources, true);
      return;
    }
    long l = database.storeObjects(sources, true);
  }

  public static List<Source> getAllSources() {
    ProjectDatabase database = ProjectDatabase.getInstance();
    return database.computeInReadonly(
        txn -> {
          EntityIterable iterable = txn.getAll(Source.ENTITY_TYPE);
          List<Source> result = new ArrayList<>(8);
          for (Entity entity : iterable) {
            try (InputStream in = entity.getBlob(ProjectDatabase.SERIALIZE_KEY)) {
              Source s = Serializer.readObject(in, Source.class);
              if (nonNull(s)) {
                result.add(s);
              }
            } catch (Exception e) {
              log.warn(e.getMessage());
            }
          }
          return result;
        });
  }

  public static Source loadSource(String filePath) throws Exception {
    ProjectDatabase database = ProjectDatabase.getInstance();
    return database.loadObject(Source.ENTITY_TYPE, filePath, Source.class);
  }

  public static boolean deleteSource(String filePath) throws Exception {
    ProjectDatabase database = ProjectDatabase.getInstance();
    return database.deleteObject(Source.ENTITY_TYPE, filePath);
  }

  @Nonnull
  @SuppressWarnings("unchecked")
  public static Map<String, String> getChecksumMap(String projectRoot) {
    ProjectDatabase database = ProjectDatabase.getInstance();

    Optional<Map<String, String>> result =
        database.computeInReadonly(
            txn -> {
              EntityIterable entities =
                  txn.find(Project.ENTITY_TYPE, ID, projectRoot)
                      .intersect(txn.findWithBlob(Project.ENTITY_TYPE, BLOB_PROP_CHECKSUM));
              Entity entity = entities.getFirst();
              if (isNull(entity)) {
                return Optional.empty();
              }
              try (InputStream in = entity.getBlob(BLOB_PROP_CHECKSUM)) {
                return Optional.ofNullable(Serializer.readObject(in, ConcurrentHashMap.class));
              } catch (Exception e) {
                log.catching(e);
                return Optional.empty();
              }
            });
    return result.orElse(new ConcurrentHashMap<>(32));
  }

  public static boolean saveChecksumMap(String projectRoot, Map<String, String> map) {
    ProjectDatabase database = ProjectDatabase.getInstance();

    return database.execute(
        txn -> {
          EntityIterable entities = txn.find(Project.ENTITY_TYPE, ID, projectRoot);

          Entity entity = entities.getFirst();
          if (isNull(entity)) {
            return false;
          }
          try {
            ProjectDatabase.setSerializeBlobData(entity, BLOB_PROP_CHECKSUM, map);
          } catch (IOException e) {
            log.catching(e);
            txn.abort();
            return false;
          }
          // txn.saveEntity(entity);
          return true;
        });
  }

  @Nonnull
  @SuppressWarnings("unchecked")
  public static Map<String, Set<String>> getCallerMap(String projectRoot) {
    ProjectDatabase database = ProjectDatabase.getInstance();

    Optional<Map<String, Set<String>>> result =
        database.computeInReadonly(
            txn -> {
              EntityIterable entities =
                  txn.find(Project.ENTITY_TYPE, ID, projectRoot)
                      .intersect(txn.findWithBlob(Project.ENTITY_TYPE, BLOB_PROP_CALLER));
              Entity entity = entities.getFirst();
              if (isNull(entity)) {
                return Optional.empty();
              }
              try (InputStream in = entity.getBlob(BLOB_PROP_CALLER)) {
                return Optional.ofNullable(Serializer.readObject(in, ConcurrentHashMap.class));
              } catch (Exception e) {
                log.warn(e.getMessage());
                return Optional.empty();
              }
            });

    return result.orElse(new ConcurrentHashMap<>(32));
  }

  public static boolean saveCallerMap(String projectRoot, Map<String, Set<String>> map) {
    ProjectDatabase database = ProjectDatabase.getInstance();

    return database.execute(
        txn -> {
          EntityIterable entities = txn.find(Project.ENTITY_TYPE, ID, projectRoot);

          Entity entity = entities.getFirst();
          if (isNull(entity)) {
            return false;
          }
          try {
            ProjectDatabase.setSerializeBlobData(entity, BLOB_PROP_CALLER, map);
          } catch (IOException e) {
            log.catching(e);
            txn.abort();
            return false;
          }
          // txn.saveEntity(entity);
          return true;
        });
  }

  public static void saveCompileResult(CompileResult result) {
    ProjectDatabase database = ProjectDatabase.getInstance();
    database.asyncStoreObject(result, false);
  }

  public static boolean deleteUnunsedSource(Project p) {
    ProjectDatabase database = ProjectDatabase.getInstance();
    return database.execute(
        txn -> {
          EntityIterable all = txn.getAll(Source.ENTITY_TYPE);
          boolean result = false;
          for (Entity entity : all) {

            String path = (String) entity.getProperty("filePath");
            if (!Files.exists(Paths.get(path))) {
              entity.delete();
              try {
                FileUtils.getClassFile(path, p.getSources(), p.getOutput()).ifPresent(File::delete);
                FileUtils.getClassFile(path, p.getTestSources(), p.getTestOutput())
                    .ifPresent(File::delete);

              } catch (IOException e) {
                log.catching(e);
              }
              result = true;
            }
          }
          return result;
        });
  }

  public static boolean saveIndexedFile(String path) {
    ProjectDatabase database = ProjectDatabase.getInstance();
    return database.execute(
        txn -> {
          Entity entity = txn.newEntity("IndexedFile");
          entity.setProperty(ID, path);
          Long timestamp = Instant.now().getEpochSecond();
          entity.setProperty("path", path);
          entity.setProperty("lastUpdate", timestamp);
          return true;
        });
  }

  public static boolean isIndexedFile(String path) {
    ProjectDatabase database = ProjectDatabase.getInstance();

    return database.computeInReadonly(
        txn -> {
          EntityIterable entities = txn.find("IndexedFile", ID, path);
          Entity entity = entities.getFirst();
          if (isNull(entity)) {
            return false;
          }

          Long old = (Long) entity.getProperty("lastUpdate");
          Long now = Instant.now().getEpochSecond();
          if ((now - old) > getIndexTTL()) {
            return false;
          }
          return true;
        });
  }

  private static int getIndexTTL() {
    return indexTTL;
  }

  public static void setIndexTTL(int ttl) {
    indexTTL = ttl;
  }

  public static void reset() {
    ProjectDatabase.reset();
  }

  public static void shutdown() {
    ProjectDatabase database = ProjectDatabase.getInstance();
    database.shutdown();
  }
}
