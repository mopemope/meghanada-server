package meghanada.store;

import java.util.Map;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityId;
import jetbrains.exodus.entitystore.StoreTransaction;

@SuppressWarnings("rawtypes")
public interface Storable {

  default EntityId getEntityId() {
    return null;
  }

  String getStoreId();

  String getEntityType();

  Map<String, Comparable> getSaveProperties();

  default void storeExtraData(StoreTransaction txn, Entity mainEntity) {}

  default void onSuccess(Entity entity) {}
}
