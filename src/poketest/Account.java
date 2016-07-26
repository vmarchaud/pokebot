package poketest;

public class Account {
	
	private EnumProvider 	provider;
	private String 			username;
	private String 			password;
	private String			token;
	private boolean			logging;
	
	public EnumProvider getProvider() {
		return provider;
	}
	public String getUsername() {
		return username;
	}
	public String getPassword() {
		return password;
	}
	public boolean isLoggingEnabled() {
		return logging;
	}
	public String getToken() {
		return token;
	}
	public void setToken(String token) {
		this.token = token;
	}
	
	public enum EnumProvider {
		GOOGLE, PTC;
	}
}
