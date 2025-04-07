package pt.unl.fct.di.apdc.firstwebapp.util;

import com.google.cloud.Timestamp;
import java.util.UUID;

public class AuthToken {

	public String username;
	public String role;
	public Validity validity;

	public static final long EXPIRATION_TIME = 1000 * 60 * 60 * 2; // 2 horas de validade

	public AuthToken() { }

	public AuthToken(String username, String role) {
		this.username = username;
		this.role = role;
		Timestamp now = Timestamp.now();
		Timestamp validTo = Timestamp.of(new java.util.Date(now.toDate().getTime() + EXPIRATION_TIME));
		this.validity = new Validity(now, validTo, UUID.randomUUID().toString());
	}

	public static class Validity {
		public Timestamp valid_from;
		public Timestamp valid_to;
		public String verificator;

		public Validity(Timestamp valid_from, Timestamp valid_to, String verificator) {
			this.valid_from = valid_from;
			this.valid_to = valid_to;
			this.verificator = verificator;
		}
	}
}
