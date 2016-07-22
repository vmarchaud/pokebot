package poketest;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

public class Core {
	
	private static Map<Account, Thread>	bots = new HashMap<Account, Thread>();
	public static Gson gson = new GsonBuilder().serializeNulls().create();
	
	public static void main(String[] args) throws JsonIOException, JsonSyntaxException, FileNotFoundException, InterruptedException {
		// Load config
		CustomConfig config = gson.fromJson(new JsonReader(new FileReader("config.json")), CustomConfig.class);
		
		// Start a bot foreach account in his own thread
		for(Account account : config.getAccounts()) {
			PokeBot bot = new PokeBot(account, config);
			bots.put(account, new Thread(bot));
			bots.get(account).start();
			
			Thread.sleep(100);
		}
	}
}
