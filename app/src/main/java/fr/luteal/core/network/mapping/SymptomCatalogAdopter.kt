package fr.luteal.core.network.mapping

import fr.luteal.core.model.Symptom
import fr.luteal.core.network.contract.models.SymptomDefinitionData

/**
 * Reconciles a local symptom catalog with definitions arriving from sync.
 *
 * NOT currently wired into production code: the app renders
 * [Symptom.DEFAULT_SYMPTOMS] and symptom definitions are not yet part of the
 * synced slice (see docs/architecture/BACKEND_INTEGRATION.md).
 *
 * The original premise no longer holds. The backend used to seed each account
 * with a built-in catalog and act as the authority for it; under end-to-end
 * encryption it cannot, because it cannot create records it has no key for.
 * The catalog is now owned by the client, and definitions would arrive sealed
 * like any other record.
 *
 * The reconciliation rule survives that change and is why this is kept: a
 * definition arriving from sync is authoritative for its key, and local
 * symptoms whose key is not among them are user customs and are preserved.
 */
object SymptomCatalogAdopter {

    fun adopt(serverDefs: List<SymptomDefinitionData>, localSymptoms: List<Symptom>): List<Symptom> {
        val activeDefs = serverDefs.filter { it.active && (it.deletedAt == null) }
        val serverByKey = activeDefs.associateBy { it.key }

        val adopted = activeDefs.map { def ->
            Symptom(
                id = def.key,
                category = def.category.toDomain(),
                // The contract carries no icon; reuse the key as the icon name,
                // which matches how DEFAULT_SYMPTOMS names most icons.
                iconName = def.key,
            )
        }

        val customs = localSymptoms.filter { local -> local.id !in serverByKey }

        return adopted + customs
    }
}
