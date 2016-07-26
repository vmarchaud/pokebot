package poketest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Core {
	
	private static Map<Account, Thread>	bots = new HashMap<Account, Thread>();
	
	public static void main(String[] args) throws InterruptedException, IOException {
		// Load config
		CustomConfig config = CustomConfig.load();
		
		// Start a bot foreach account in his own thread
		for(Account account : config.getAccounts()) {
			PokeBot bot = new PokeBot(account, config);
			bots.put(account, new Thread(bot));
			bots.get(account).start();
			
			Thread.sleep(100);
		}
	}
}
