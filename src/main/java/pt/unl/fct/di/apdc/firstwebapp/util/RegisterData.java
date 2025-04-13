package pt.unl.fct.di.apdc.firstwebapp.util;

public class RegisterData {

	public String username;
	public String password;
	public String confirmation;
	public String email;
	public String name;
	public String profile;
	public String phone;

	public String cc_number;
	public String nif;
	public String job_entity;
	public String job;
	public String address;
	public String job_entity_nif;

	public RegisterData() {

	}

	public RegisterData(String username, String password, String confirmation, String email, String name,
						String profile, String phone, String cc_number, String nif, String job_entity, String job,
						String address, String job_entity_nif) {
		this.username = username;
		this.password = password;
		this.confirmation = confirmation;
		this.email = email;
		this.name = name;
		this.profile = profile;
		this.phone = phone;
		this.cc_number = cc_number;
		this.nif = nif;
		this.job_entity = job_entity;
		this.job = job;
		this.address = address;
		this.job_entity_nif = job_entity_nif;
	}

	private boolean nonEmptyOrBlankField(String field) {
		return field != null && !field.isBlank();
	}

	public boolean validRegistration() {
		return nonEmptyOrBlankField(username) &&
				nonEmptyOrBlankField(password) &&
				nonEmptyOrBlankField(email) &&
				nonEmptyOrBlankField(name) &&
				nonEmptyOrBlankField(profile) &&
				nonEmptyOrBlankField(phone) &&
				email.contains("@") &&
				password.equals(confirmation) &&
				(profile.equals("público") || profile.equals("privado"));
	}
}
