package pt.unl.fct.di.apdc.firstwebapp.util;

import java.util.Date;

public class CreateWorkSheetData {

    // Atributos obrigatórios
    public String reference;       // Exemplo: O234/CM/2024
    public String description;     // Frase sumária da descrição da obra
    public String targetType;      // "Propriedade Pública" ou "Propriedade Privada"
    public String awardingStatus;  // "ADJUDICADO" ou "NÃO ADJUDICADO"

    // Atributos opcionais (preenchidos apenas se awardingStatus = "ADJUDICADO")
    public Date awardingDate;
    public Date startDate;
    public Date endDate;
    public String partnerAccount;      // Conta do PARTNER que executará a obra
    public String awardingEntity;      // Nome da Empresa (ex. "Reflorestação Inteligente S.A.")
    public String awardingNif;         // NIF da empresa (ex. 511876234)
    public String workState;           // "NÃO INICIADO", "EM CURSO" ou "CONCLUÍDO"
    public String observations;        // Observações sobre a obra

    public CreateWorkSheetData() {
    }

}
