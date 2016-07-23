package poketest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

public class Core {
	
	private static Map<Account, Thread>	bots = new HashMap<Account, Thread>();
	public static Moshi moshi = new Moshi.Builder().build();
	
	public static void main(String[] args) throws InterruptedException, IOException {
		// Load config
		JsonAdapter<CustomConfig> jsonAdapter = moshi.adapter(CustomConfig.class);
		CustomConfig config = jsonAdapter.fromJson(new String(Files.readAllBytes(Paths.get("config.json"))));
		
		// Start a bot foreach account in his own thread
		for(Account account : config.getAccounts()) {
			PokeBot bot = new PokeBot(account, config);
			bots.put(account, new Thread(bot));
			bots.get(account).start();
			
			Thread.sleep(100);
		}
	}
}
