package pt.unl.fct.di.apdc.firstwebapp.util;

public class ChangeRoleData {
    public String targetUsername;
    public String newRole;

    public ChangeRoleData() { }

    public ChangeRoleData(String targetUsername, String newRole) {
        this.targetUsername = targetUsername;
        this.newRole = newRole;
    }
}

