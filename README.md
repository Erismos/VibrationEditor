# Vibration Editor

Vibration Editor est une application Android permettant de personnaliser le retour tactile de votre téléphone pour chaque application et chaque type de notification.

## Concept

L'application permet de créer des séquences de vibrations personnalisées via un éditeur visuel et de les associer aux applications de votre choix (WhatsApp, Gmail, Instagram, etc.).

## Fonctionnalités

### Studio de Création
*   Editeur d'enveloppe visuel (durée et intensité).
*   Gestion des points (ajout, déplacement, suppression).
*   Test en temps réel du pattern créé.
*   Import et export des patterns au format texte.

### Bibliothèque de Patterns
*   Liste des patterns sauvegardés et modèles par défaut.
*   Recherche de patterns par nom.
*   Actions rapides pour lire, éditer ou supprimer un pattern.

### Gestion des Applications
*   Liste des applications installées sur l'appareil.
*   Mapping personnalisé par canaux de notification.
*   Sauvegarde automatique des réglages de l'utilisateur.

## Installation et Configuration

### Prérequis
*   Android 8.0 (API 26) ou supérieur.
*   Téléphone avec vibreur fonctionnel.

### Permissions requises
1.  Permission VIBRATE pour déclencher les vibrations.
2.  Accès aux notifications pour intercepter les alertes système et appliquer les patterns personnalisés.

## Stack Technique

*   UI : Jetpack Compose et Material 3.
*   Langage : Kotlin.
*   Service : NotificationListenerService pour l'interception des notifications.
*   Sérialisation : kotlinx.serialization pour le stockage JSON.
