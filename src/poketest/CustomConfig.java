package poketest;

import java.util.List;

public class CustomConfig {
	private	List<Account>	accounts;
	private List<Location>	spawns;
	private int				speed;
	private int				map_radius;
	
	public List<Location> getSpawns() {
		return spawns;
	}
	
	public List<Account> getAccounts() {
		return accounts;
	}

	public int getSpeed() {
		return speed;
	}

	public int getMap_radius() {
		return map_radius;
	}
}
