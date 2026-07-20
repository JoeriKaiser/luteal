package fr.luteal.core.model

enum class SyncMode(val description: String) {
    OFFLINE_LOCAL("Mode Hors-ligne (Stockage local uniquement, pas de permissions réseau requises)"),
    ONLINE_CLOUD("Mode En Ligne (Synchronisation chiffrée avec le serveur backend)")
}
