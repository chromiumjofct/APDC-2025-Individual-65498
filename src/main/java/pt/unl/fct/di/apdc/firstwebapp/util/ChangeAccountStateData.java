package pt.unl.fct.di.apdc.firstwebapp.util;


public class ChangeAccountStateData {

    private String targetUsername;
    private String newState;

    public ChangeAccountStateData() {

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



    public boolean isValidState() {
        if (newState == null) {
            return false;
        }
        String state = newState.trim().toUpperCase();
        return state.equals("ATIVADA") || state.equals("SUSPENSA") || state.equals("DESATIVADA");
    }
}
