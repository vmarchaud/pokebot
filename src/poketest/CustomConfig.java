package poketest;

import java.util.List;

public class CustomConfig {
	private	String 			username;
	private String 			password;
	private EnumProvider	provider;
	private List<Location>	spawns;
	
	public String getUsername() {
		return username;
	}
	
	public String getPassword() {
		return password;
	}
	
	public List<Location> getSpawns() {
		return spawns;
	}
	public EnumProvider	getProvider() {
		return provider;
	}
	
	public enum EnumProvider {
		GOOGLE, PTC;
	}
}
