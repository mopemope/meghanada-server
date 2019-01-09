package meghanada.store;

import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityId;
import jetbrains.exodus.entitystore.StoreTransaction;

public interface Storable {

  default EntityId getEntityId() {
    return null;
  }

  String getStoreId();

  String getEntityType();

  void store(StoreTransaction txn, Entity mainEntity);

  default void onSuccess(Entity entity) {}
}
