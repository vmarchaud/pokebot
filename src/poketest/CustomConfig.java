package poketest;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Paths;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;

public class CustomConfig {
	private	List<Account>	accounts;
	private List<Location>	spawns;
	private int				speed;
	private int				map_radius;

	private transient static Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	
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
	
	public static CustomConfig load() throws IOException {
		return gson.fromJson(new FileReader(Paths.get("config.json").toFile()), CustomConfig.class);
	}
	
	public void save() throws JsonIOException, IOException {
		Writer writer = new FileWriter(Paths.get("config.json").toFile());
		writer.write(gson.toJson(this));
		writer.close();
	}
}
