# TP 2 — Application Web LangChain4j

Camel Chafii — EMSI CASA-IA G2.

Deuxième partie du [TP 2 de Richard Grin](http://richard.grin.free.fr/emsi/casablanca-ia/tp/tp2-langchain4j/index.html) : chat JSF avec Jakarta EE 11, PrimeFaces et LangChain4j. Le projet des sept tests en console est dans [un autre dépôt](https://github.com/Camelchafii691/tp2-tests-chafii).

## Lancer l’application

Prérequis : JDK 25, Maven et une clé Google Gemini valide avec du quota. Sous macOS, le script peut utiliser le Maven fourni avec IntelliJ IDEA. Une connexion Internet est nécessaire au premier lancement pour les dépendances et Payara, puis pour les appels Gemini.

Dans le dossier du projet :

```sh
./lancer.sh
```

Le script compile le projet, exécute les cinq tests, demande la clé avec une **saisie masquée dans le terminal**, télécharge Payara Micro 7.2026.11 depuis Maven Central puis démarre l’application. La clé n’est pas écrite dans le projet.

Attendre le message « Payara Micro … ready », puis ouvrir [le chat local](http://localhost:18084/tp2-web-chafii/). Arrêt : `Ctrl+C` dans le terminal.

Le script accepte aussi une clé déjà présente dans `GEMINI_API_KEY` ou `GEMINI_KEY`. Ne pas placer de clé dans Git, dans une capture ou dans l’URL du navigateur.

Si le port est occupé :

```sh
TP2_PORT=18085 ./lancer.sh
```

Le modèle par défaut est `gemini-3.5-flash`. Pour utiliser un autre modèle disponible sur le compte :

```sh
GEMINI_MODEL=nom-du-modele ./lancer.sh
```

Variables facultatives : `JAVA_HOME`, `MVN_BIN` (exécutable Maven), `PAYARA_MICRO_JAR` (chemin d’un Payara Micro 7 déjà téléchargé).

Pour compiler et vérifier sans clé et sans démarrer de serveur :

```sh
./lancer.sh --verifier
```

Dans IntelliJ, ouvrir `pom.xml` comme projet Maven et choisir le JDK 25. Le fichier déployable produit est `target/tp2-web-chafii.war`. Un déploiement manuel nécessite un serveur Jakarta EE 11, et la clé doit être configurée dans l’environnement **du serveur**.

## Utilisation

1. Choisir un rôle dans la liste ou saisir une consigne personnelle.
2. Écrire une question puis cliquer sur « Envoyer la question ».
3. Lire la réponse à gauche et la conversation à droite. Le rôle est verrouillé après le premier échange réussi.
4. Cliquer sur « Nouveau chat » pour vider la mémoire et pouvoir choisir un autre rôle.

Exemple demandé par le TP : choisir « Traducteur », envoyer `bonjour`, puis vérifier la traduction anglaise. Les boutons permettent de copier les textes. « Effacer question et réponse » vide les champs affichés ; « Nouveau chat » réinitialise la conversation côté serveur.

## Organisation

| Fichier / classe | Fonction |
| --- | --- |
| `index.xhtml` | Interface JSF avec PrimeFaces, reprise du TP 1 sans le panneau JSON de débogage |
| `resources/css/mycsslayout.css` | Présentation en deux colonnes et adaptation aux petits écrans |
| `resources/js/script.js` | Copie et effacement des champs |
| `Bb` | Bean `@ViewScoped`, rôle, question, réponse et historique affiché |
| `llm.Assistant` | Interface de service implémentée par `AiServices` |
| `llm.LlmClient` | Bean CDI `@Dependent`, connexion Gemini et mémoire de 20 messages |
| `LlmClientTest` | Tests de mémoire, changement de rôle, erreurs et sérialisation |
| `IA.md` | Déclaration de l’aide reçue de l’IA |

Le rôle est un message système. Le changement de rôle efface la mémoire et réinsère ce message. Chaque vue JSF possède son client et sa conversation. Le client restaure les messages après sérialisation, sans sérialiser la clé ou le proxy du service. En cas d’échec, la question sans réponse est retirée de la mémoire et un message lisible est affiché.

## Vérifications réalisées

- Compilation et création du WAR réussies avec JDK 25 et Maven.
- **5 tests automatisés réussis** : rôle et échanges précédents, effacement après changement de rôle, restauration de la mémoire après échec, limite de la mémoire et conservation du message système, sérialisation sans clé ni proxy.
- Déploiement réussi sur Payara Micro 7.2026.11 ; page JSF et ressources accessibles.
- Dans le navigateur, avec une API Gemini **simulée localement** : envoi, réponse, historique, verrouillage du rôle, mémoire du nom, nouveau chat et absence de l’ancien historique dans la requête suivante.
- Scénario « Traducteur / bonjour » : réponse simulée `Hello.` affichée dans la réponse et la conversation.
- Erreur HTTP 429 simulée : message de quota affiché ; l’échange en échec n’apparaît pas dans la conversation. Le SDK Google a effectué plusieurs tentatives avant de rendre l’erreur.
- Question vide refusée ; le rôle reste modifiable tant qu’aucun échange n’a réussi.

**Limite : aucun appel réel à Google Gemini n’a été exécuté pour cette application Web lors de cette vérification.** La clé réelle n’était pas disponible. Il reste à lancer l’application avec une clé valide et à confirmer une réponse réelle du modèle. Les réponses de la vérification locale ne sont pas présentées comme des résultats Google.

La variable `GEMINI_API_ENDPOINT` permet de cibler une API de vérification. Ne pas la définir pour l’utilisation normale avec Google. Si elle désigne `localhost` ou `127.0.0.1`, une bannière indique explicitement la simulation. L’API simulée utilisée pour les essais ne fait pas partie de l’application livrée.

Le comptage des tokens et le streaming, facultatifs dans l’énoncé, ne sont pas implémentés.
