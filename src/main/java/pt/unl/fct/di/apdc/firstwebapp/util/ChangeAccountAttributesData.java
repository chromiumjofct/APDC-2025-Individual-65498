package pt.unl.fct.di.apdc.firstwebapp.util;

public class ChangeAccountAttributesData {

    // Indica qual conta (username ou outro identificador) se quer modificar
    private String targetUsername;

    // Atributos que podem ser atualizados - esses campos podem ser opcionais
    private String newUserName;         // Correspondente a "user_name" (nome do utilizador)
    private String newEmail;            // Correspondente a "user_email"
    private String newPhone;            // "user_phone"
    private String newAccountProfile;   // "account_profile"
    private String newCcNumber;         // Número de cartão do cidadão
    private String newNif;              // NIF do utilizador
    private String newJobEntity;        // Entidade empregadora, ex: "Smart Forest S.A."
    private String newJob;              // Função, ex: "Professor", "Estudante", etc.
    private String newAdress;           // Morada, ex: “Rua dos alunos de APDC2324, No 100, Piso 2, Porta 116”
    private String newJobEntityNif;     // NIF da entidade empregadora
    private String newRole;             // NOVO role (apenas ADMIN pode alterar)
    private String newAccountStatus;    // Novo estado da conta (apenas ADMIN pode alterar)

    // Getters e setters
    public String getTargetUsername() {
        return targetUsername;
    }

    public void setTargetUsername(String targetUsername) {
        this.targetUsername = targetUsername;
    }

    public String getNewUserName() {
        return newUserName;
    }

    public void setNewUserName(String newUserName) {
        this.newUserName = newUserName;
    }

    public String getNewEmail() {
        return newEmail;
    }

    public void setNewEmail(String newEmail) {
        this.newEmail = newEmail;
    }

    public String getNewPhone() {
        return newPhone;
    }

    public void setNewPhone(String newPhone) {
        this.newPhone = newPhone;
    }

    public String getNewAccountProfile() {
        return newAccountProfile;
    }

    public void setNewAccountProfile(String newAccountProfile) {
        this.newAccountProfile = newAccountProfile;
    }

    public String getNewCcNumber() {
        return newCcNumber;
    }

    public void setNewCcNumber(String newCcNumber) {
        this.newCcNumber = newCcNumber;
    }

    public String getNewNif() {
        return newNif;
    }

    public void setNewNif(String newNif) {
        this.newNif = newNif;
    }

    public String getNewJobEntity() {
        return newJobEntity;
    }

    public void setNewJobEntity(String newJobEntity) {
        this.newJobEntity = newJobEntity;
    }

    public String getNewJob() {
        return newJob;
    }

    public void setNewJob(String newJob) {
        this.newJob = newJob;
    }

    public String getNewAdress() {
        return newAdress;
    }

    public void setNewAdress(String newAdress) {
        this.newAdress = newAdress;
    }

    public String getNewJobEntityNif() {
        return newJobEntityNif;
    }

    public void setNewJobEntityNif(String newJobEntityNif) {
        this.newJobEntityNif = newJobEntityNif;
    }

    public String getNewRole() {
        return newRole;
    }

    public void setNewRole(String newRole) {
        this.newRole = newRole;
    }

    public String getNewAccountStatus() {
        return newAccountStatus;
    }

    public void setNewAccountStatus(String newAccountStatus) {
        this.newAccountStatus = newAccountStatus;
    }
}
