package pt.unl.fct.di.apdc.firstwebapp.util;

/**
 * Classe para mapear os dados recebidos na requisição de mudança de estado de conta.
 * Os valores aceitos para o novo estado são: ATIVADA, SUSPENSA ou DESATIVADA.
 *
 * Exemplo de JSON de input:
 * {
 *   "targetUsername": "jose123",
 *   "newState": "ATIVADA"
 * }
 */
public class ChangeAccountStateData {

    private String targetUsername;
    private String newState; // Valor esperado: "ATIVADA", "SUSPENSA" ou "DESATIVADA"

    public ChangeAccountStateData() {
        // Construtor padrão para deserialização JSON
    }

    public ChangeAccountStateData(String targetUsername, String newState) {
        this.targetUsername = targetUsername;
        this.newState = newState;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public void setTargetUsername(String targetUsername) {
        this.targetUsername = targetUsername;
    }

    public String getNewState() {
        return newState;
    }

    public void setNewState(String newState) {
        this.newState = newState;
    }

    /**
     * Metodo utilitário para verificar se o novo estado é válido.
     *
     * @return true se newState for ATIVADA, SUSPENSA ou DESATIVADA (ignorando maiúsculas/minúsculas); false caso contrário.
     */
    public boolean isValidState() {
        if (newState == null) {
            return false;
        }
        String state = newState.trim().toUpperCase();
        return state.equals("ATIVADA") || state.equals("SUSPENSA") || state.equals("DESATIVADA");
    }
}
