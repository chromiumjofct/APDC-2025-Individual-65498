package pt.unl.fct.di.apdc.firstwebapp.util;

public class RemoveUserAccountData {
    private String targetUsername;

    public RemoveUserAccountData() {

    }

    public RemoveUserAccountData(String targetUsername) {
        this.targetUsername = targetUsername;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public void setTargetUsername(String targetUsername) {
        this.targetUsername = targetUsername;
    }
}
