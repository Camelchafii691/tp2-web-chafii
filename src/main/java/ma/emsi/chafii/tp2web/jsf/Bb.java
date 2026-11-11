package ma.emsi.chafii.tp2web.jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ma.emsi.chafii.tp2web.llm.LlmClient;
import ma.emsi.chafii.tp2web.llm.LlmException;

/** Une instance par page de chat : rôle, affichage et conversation restent associés à la vue. */
@Named("bb")
@ViewScoped
public class Bb implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    @Inject private LlmClient llmClient;
    private String roleSysteme = LlmClient.ROLE_PAR_DEFAUT;
    private boolean roleSystemeChangeable = true;
    private String question = "";
    private String reponse = "";
    private final List<Echange> echanges = new ArrayList<>();

    public synchronized void envoyer() {
        if (question == null || question.isBlank()) {
            erreur("Écris une question avant de l’envoyer.");
            return;
        }
        if (roleSysteme == null || roleSysteme.isBlank()) {
            erreur("Choisis un rôle pour l’assistant.");
            return;
        }
        try {
            if (roleSystemeChangeable) llmClient.setSystemRole(roleSysteme);
            String texte = question.trim();
            String nouvelleReponse = llmClient.envoyer(texte);
            reponse = nouvelleReponse;
            echanges.add(new Echange(texte, nouvelleReponse));
            roleSystemeChangeable = false;
        } catch (LlmException erreur) {
            erreur(erreur.getMessage());
        }
    }

    public synchronized String nouveauChat() {
        echanges.clear();
        question = "";
        reponse = "";
        roleSysteme = LlmClient.ROLE_PAR_DEFAUT;
        roleSystemeChangeable = true;
        llmClient.setSystemRole(roleSysteme);
        // Nouvelle vue pour vider également les valeurs locales des composants JSF.
        return "index?faces-redirect=true";
    }

    private void erreur(String message) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, message, message));
    }

    public Map<String, String> getRolesSysteme() {
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put("Assistant général", LlmClient.ROLE_PAR_DEFAUT);
        roles.put("Traducteur", "Tu es un traducteur vers l’anglais. Traduis uniquement les messages de l’utilisateur en anglais, sans explication.");
        roles.put("Professeur de Java", "Tu es un professeur de Java. Explique en français, avec des exemples simples et exacts.");
        roles.put("Réponses courtes", "Réponds en français en une ou deux phrases, de manière précise.");
        return roles;
    }

    public String getConversation() {
        StringBuilder texte = new StringBuilder();
        for (Echange echange : echanges) {
            texte.append("Question : ").append(echange.question()).append("\n")
                    .append("Réponse : ").append(echange.reponse()).append("\n\n");
        }
        return texte.toString();
    }

    public String getRoleSysteme() { return roleSysteme; }
    public void setRoleSysteme(String roleSysteme) { this.roleSysteme = roleSysteme; }
    public boolean isRoleSystemeChangeable() { return roleSystemeChangeable; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getReponse() { return reponse; }
    public boolean isVerificationLocale() {
        String endpoint = System.getenv("GEMINI_API_ENDPOINT");
        return endpoint != null && (endpoint.startsWith("http://127.0.0.1:")
                || endpoint.startsWith("http://localhost:"));
    }
    private record Echange(String question, String reponse) implements Serializable { }
}
