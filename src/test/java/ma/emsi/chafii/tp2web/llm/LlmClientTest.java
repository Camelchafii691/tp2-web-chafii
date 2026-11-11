package ma.emsi.chafii.tp2web.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LlmClientTest {
    private static class ModeleTemoin implements ChatModel {
        final List<ChatRequest> requetes = new ArrayList<>();
        RuntimeException erreur;
        @Override public ChatResponse doChat(ChatRequest requete) {
            requetes.add(requete);
            if (erreur != null) throw erreur;
            return ChatResponse.builder().aiMessage(AiMessage.from("Réponse " + requetes.size())).build();
        }
    }

    @Test void transmetLeRoleEtLesEchangesPrecedents() {
        var modele = new ModeleTemoin();
        var client = new LlmClient(modele);
        client.setSystemRole("Traduis en anglais.");
        client.envoyer("bonjour");
        client.envoyer("Comment ai-je commencé ?");
        var messages = modele.requetes.getLast().messages();
        assertEquals(4, messages.size());
        assertEquals("Traduis en anglais.", ((SystemMessage) messages.get(0)).text());
        assertEquals("bonjour", ((UserMessage) messages.get(1)).singleText());
        assertEquals("Réponse 1", ((AiMessage) messages.get(2)).text());
    }

    @Test void unNouveauRoleEffaceLAncienHistorique() {
        var modele = new ModeleTemoin();
        var client = new LlmClient(modele);
        client.envoyer("Je m’appelle Camel.");
        client.setSystemRole("Réponds brièvement.");
        client.envoyer("bonjour");
        var messages = modele.requetes.getLast().messages();
        assertEquals(2, messages.size());
        assertEquals("Réponds brièvement.", ((SystemMessage) messages.getFirst()).text());
        assertTrue(messages.stream().noneMatch(m -> m instanceof UserMessage u && u.singleText().contains("Camel")));
    }

    @Test void uneErreurNePolluePasLaConversationEtNeDivulguePasSesDetails() {
        var modele = new ModeleTemoin();
        var client = new LlmClient(modele);
        client.envoyer("Message réussi");
        modele.erreur = new IllegalStateException("détail confidentiel factice");
        var erreur = assertThrows(LlmException.class, () -> client.envoyer("Message échoué"));
        assertFalse(erreur.getMessage().contains("confidentiel"));
        modele.erreur = null;
        client.envoyer("Reprise");
        var messages = modele.requetes.getLast().messages();
        assertEquals(4, messages.size());
        assertTrue(messages.stream().noneMatch(m -> m instanceof UserMessage u && u.singleText().equals("Message échoué")));
    }

    @Test void laMemoireResteBorneeEtConserveLeRoleSysteme() {
        var modele = new ModeleTemoin();
        var client = new LlmClient(modele);
        client.setSystemRole("Rôle conservé");
        for (int i = 0; i < 25; i++) client.envoyer("Question " + i);
        var messages = modele.requetes.getLast().messages();
        // AiServices assemble les vingt messages mémorisés puis la question courante.
        assertTrue(messages.size() <= 21);
        assertEquals("Rôle conservé", ((SystemMessage) messages.getFirst()).text());
        assertTrue(messages.stream().noneMatch(m -> m instanceof UserMessage u && u.singleText().equals("Question 0")));
    }

    @Test void laSerialisationConserveLeChatSansSerialiserLaCleNiLeProxy() throws Exception {
        var client = new LlmClient(new ModeleTemoin());
        client.setSystemRole("Rôle à restaurer");
        client.envoyer("Une question conservée");
        client.connecter(GoogleGenAiChatModel.builder().apiKey("secret-factice-ne-pas-serialiser")
                .modelName("gemini-3.5-flash").build());
        var octets = new ByteArrayOutputStream();
        try (var sortie = new ObjectOutputStream(octets)) { sortie.writeObject(client); }
        assertFalse(octets.toString(StandardCharsets.UTF_8).contains("secret-factice-ne-pas-serialiser"));
        LlmClient restaure;
        try (var entree = new ObjectInputStream(new ByteArrayInputStream(octets.toByteArray()))) {
            restaure = (LlmClient) entree.readObject();
        }
        assertEquals("Rôle à restaurer", restaure.getSystemRole());
        var modele = new ModeleTemoin();
        restaure.connecter(modele);
        restaure.envoyer("Suite de la conversation");
        var messages = modele.requetes.getFirst().messages();
        assertEquals(4, messages.size());
        assertEquals("Une question conservée", ((UserMessage) messages.get(1)).singleText());
        assertEquals("Réponse 1", ((AiMessage) messages.get(2)).text());
    }
}
