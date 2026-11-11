package ma.emsi.chafii.tp2web.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.enterprise.context.Dependent;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Client CDI propre à une vue JSF ; la clé et le proxy ne sont jamais sérialisés. */
@Dependent
public class LlmClient implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(LlmClient.class.getName());
    public static final String ROLE_PAR_DEFAUT = "Tu es un assistant utile. Réponds en français de façon claire.";
    private String systemRole = ROLE_PAR_DEFAUT;
    private transient Assistant assistant;
    private transient ChatMemory chatMemory;

    public LlmClient() {
        chatMemory = MessageWindowChatMemory.withMaxMessages(20);
        chatMemory.add(SystemMessage.from(systemRole));
        String cle = cleApi();
        // La page reste consultable si le serveur n’a pas encore reçu de clé.
        if (cle != null && !cle.isBlank()) connecter(creerModele(cle));
    }

    LlmClient(ChatModel modele) {
        chatMemory = MessageWindowChatMemory.withMaxMessages(20);
        chatMemory.add(SystemMessage.from(systemRole));
        connecter(modele);
    }

    void connecter(ChatModel modele) {
        assistant = AiServices.builder(Assistant.class)
                .chatModel(modele)
                .chatMemory(chatMemory)
                .build();
    }

    public synchronized void setSystemRole(String role) {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("Indique un rôle pour l’assistant.");
        systemRole = role.trim();
        chatMemory.clear();
        chatMemory.add(SystemMessage.from(systemRole));
    }

    public String getSystemRole() { return systemRole; }

    public synchronized String envoyer(String question) {
        if (question == null || question.isBlank()) throw new LlmException("Écris une question avant de l’envoyer.");
        if (assistant == null) {
            String cle = cleApi();
            if (cle == null || cle.isBlank()) throw new LlmException("La clé Gemini n’est pas configurée sur le serveur.");
            connecter(creerModele(cle));
        }
        List<ChatMessage> avant = new ArrayList<>(chatMemory.messages());
        try {
            String reponse = assistant.chat(question.trim());
            if (reponse == null || reponse.isBlank()) throw new LlmException("Le modèle n’a pas renvoyé de réponse. Reformule ta question.");
            return reponse;
        } catch (RuntimeException erreur) {
            // Ne pas conserver une question restée sans réponse après une erreur réseau ou de quota.
            chatMemory.clear();
            avant.forEach(chatMemory::add);
            LOG.warning("Échec de l’appel Gemini : " + erreur.getClass().getSimpleName());
            if (erreur instanceof LlmException connue) throw connue;
            if (erreur instanceof RateLimitException) throw new LlmException("Le quota Gemini est atteint. Réessaie plus tard.", erreur);
            throw new LlmException("Impossible d’obtenir une réponse de Gemini. Vérifie la connexion et la configuration du serveur.", erreur);
        }
    }

    private static String cleApi() {
        String cle = System.getenv("GEMINI_API_KEY");
        return cle == null || cle.isBlank() ? System.getenv("GEMINI_KEY") : cle;
    }

    private static GoogleGenAiChatModel creerModele(String cle) {
        String nom = System.getenv("GEMINI_MODEL");
        var builder = GoogleGenAiChatModel.builder()
                .apiKey(cle)
                .modelName(nom == null || nom.isBlank() ? "gemini-3.5-flash" : nom)
                .temperature(0.7)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(0)
                .logRequestsAndResponses(false);
        // Point d’accès facultatif pour une vérification locale avec une API simulée.
        String endpoint = System.getenv("GEMINI_API_ENDPOINT");
        if (endpoint != null && !endpoint.isBlank()) builder.apiEndpoint(endpoint);
        return builder.build();
    }

    @Serial
    private synchronized void writeObject(ObjectOutputStream sortie) throws IOException {
        sortie.defaultWriteObject();
        var messages = chatMemory.messages();
        sortie.writeInt(messages.size());
        for (ChatMessage message : messages) {
            sortie.writeObject(message.type().name());
            String texte = switch (message) {
                case SystemMessage systeme -> systeme.text();
                case UserMessage utilisateur -> utilisateur.singleText();
                case AiMessage ia -> ia.text();
                default -> throw new IOException("Type de message non pris en charge dans ce chat.");
            };
            sortie.writeObject(texte);
        }
    }

    @Serial
    private void readObject(ObjectInputStream entree) throws IOException, ClassNotFoundException {
        entree.defaultReadObject();
        chatMemory = MessageWindowChatMemory.withMaxMessages(20);
        int nombre = entree.readInt();
        if (nombre < 0 || nombre > 20) throw new InvalidObjectException("Historique de taille invalide.");
        for (int i = 0; i < nombre; i++) {
            String type = (String) entree.readObject();
            String texte = (String) entree.readObject();
            chatMemory.add(switch (type) {
                case "SYSTEM" -> SystemMessage.from(texte);
                case "USER" -> UserMessage.from(texte);
                case "AI" -> AiMessage.from(texte);
                default -> throw new InvalidObjectException("Type de message invalide.");
            });
        }
        // Le proxy sera recréé au prochain envoi avec la configuration du serveur.
    }
}
