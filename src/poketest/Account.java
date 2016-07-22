package poketest;

public class Account {
	
	private EnumProvider 	provider;
	private String 			username;
	private String 			password;
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
	
	public enum EnumProvider {
		GOOGLE, PTC;
	}
}
