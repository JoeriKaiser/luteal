package fr.luteal.core.network.mapping

import fr.luteal.core.model.Symptom
import fr.luteal.core.network.contract.models.SymptomDefinitionData

/**
 * Reconciles the local symptom catalog with the server's authoritative
 * definitions. The backend seeds each account with a built-in catalog; the
 * client must ADOPT those (matched by key) rather than create its own rows,
 * because the backend enforces a unique live (account_id, key) index.
 *
 * Rule: active server definitions are authoritative for their key; local
 * symptoms whose key is not an active server definition are treated as
 * user customs and preserved.
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
