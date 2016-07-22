package poketest;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.Bag;
import com.pokegoapi.api.inventory.Pokeball;
import com.pokegoapi.api.map.MapObjects;
import com.pokegoapi.api.map.Pokemon.CatchResult;
import com.pokegoapi.api.map.Pokemon.CatchablePokemon;
import com.pokegoapi.api.map.Pokemon.EncounterResult;
import com.pokegoapi.api.map.fort.Pokestop;
import com.pokegoapi.api.map.fort.PokestopLootResult;
import com.pokegoapi.api.pokemon.Pokemon;
import com.pokegoapi.auth.GoogleLogin;
import com.pokegoapi.auth.PTCLogin;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.main.ServerRequest;

import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import POGOProtos.Inventory.ItemIdOuterClass.ItemId;
import POGOProtos.Networking.Requests.RequestTypeOuterClass.RequestType;
import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo;
import POGOProtos.Networking.Requests.Messages.PlayerUpdateMessageOuterClass;
import POGOProtos.Networking.Responses.CatchPokemonResponseOuterClass.CatchPokemonResponse.CatchStatus;
import POGOProtos.Networking.Responses.EncounterResponseOuterClass.EncounterResponse.Status;
import okhttp3.OkHttpClient;
import poketest.Account.EnumProvider;

public class PokeBot implements Runnable {

	private PokemonGo		go;
	private Account 		account;
	private CustomConfig 	config;
	private CustomLogger	logger;
	
	
	public SecureRandom 	rand = new SecureRandom();
	private OkHttpClient 	http = new OkHttpClient();

	private int xpEarned = 0;
	private int pokemonTransfered = 0;
	private int pokemonCatched = 0;

	public PokeBot(Account account, CustomConfig config) {
		this.account = account;
		this.config = config;
		this.logger = new CustomLogger(account);
	}
	
	public void run() {
		int		failedLoginCount = 0;
		while ( true ) {
			try {
				auth();
			} catch (LoginFailedException e1) {
				logger.important("Cant log into account attempt #" + failedLoginCount);
				
				// if we failed 3 times, wait 10 min
				if (failedLoginCount == 3) {
					logger.important("Will sleep 10 minutes to try for login again");
					try {
						Thread.sleep(10 * 60 * 1000);
					} catch (InterruptedException e) { }
					failedLoginCount = 0;
				}
				else
					failedLoginCount++;
			}
			
			try {
				MapObjects objects = go.getMap().getMapObjects(config.getMap_radius());
				getPokestops(objects.getPokestops());
			} catch (Exception e) {
				logger.important("Got error " + e.getMessage());
				logger.important("Rebooting in 1 minutes ..");
			}
			// sleep to avoid spamming
			try {
				Thread.sleep(60 * 1000);
			} catch (InterruptedException e) { }
		}
	}
	
	public void auth() throws LoginFailedException {
		AuthInfo auth = null;
		if (account.getProvider() == EnumProvider.GOOGLE) 
			auth = new GoogleLogin(http).login("", "");
		else 
			auth = new PTCLogin(http).login(account.getUsername(), account.getPassword());
		
		go = new PokemonGo(auth, http);
		logger.important("Logged into pokemon go with fresh instance");
		Location location = config.getSpawns().get(rand.nextInt(config.getSpawns().size()));
		logger.important("Location choosen : " + Core.gson.toJson(location));
		go.setLocation(location.getLattitude(), location.getLongitude(), 0);
	}

	public void transfertAllPokermon() throws LoginFailedException, RemoteServerException{
		Map<PokemonId, Pokemon> pokemons = new HashMap<PokemonId, Pokemon>();
		for(Pokemon pokemon : go.getPokebank().getPokemons()) {

			if (pokemon.getFavorite())
				continue;

			if (pokemons.containsKey(pokemon.getPokemonId())) {
				if (pokemon.getCp() <= pokemons.get(pokemon.getPokemonId()).getCp()) {
					logger.log("Transfering pokemon " + pokemon.getPokemonId() + " : " + pokemon.transferPokemon());
				} else {
					logger.log("Transfering pokemon " + pokemons.get(pokemon.getPokemonId()).getPokemonId() + " : " + pokemons.get(pokemon.getPokemonId()).transferPokemon());
					pokemons.put(pokemon.getPokemonId(), pokemon);
				}
				pokemonTransfered++;
			}
			else
				pokemons.put(pokemon.getPokemonId(), pokemon);
		}
	}

	public void capturePokemons(List<CatchablePokemon> list) throws LoginFailedException, RemoteServerException{
		for(CatchablePokemon pokemon : list) {
			EncounterResult respondE = pokemon.encounterPokemon();

			if (respondE.getStatus() == Status.ENCOUNTER_SUCCESS){
				go.getPlayerProfile(true);
				Bag bag = go.getBag();

				Pokeball ball = null;
				if (bag.getItem(ItemId.ITEM_MASTER_BALL) != null && bag.getItem(ItemId.ITEM_MASTER_BALL).getCount() > 0)
					ball = Pokeball.MASTERBALL;
				else if (bag.getItem(ItemId.ITEM_ULTRA_BALL) != null && bag.getItem(ItemId.ITEM_ULTRA_BALL).getCount() > 0)
					ball = Pokeball.ULTRABALL;
				else if (bag.getItem(ItemId.ITEM_GREAT_BALL) != null && bag.getItem(ItemId.ITEM_GREAT_BALL).getCount() > 0)
					ball = Pokeball.GREATBALL;
				else if (bag.getItem(ItemId.ITEM_POKE_BALL) != null && bag.getItem(ItemId.ITEM_POKE_BALL).getCount() > 0)
					ball = Pokeball.POKEBALL;

				if (ball != null){
					CatchResult respondC = pokemon.catchPokemon(ball);
					
					logger.log("	" + respondC.getStatus() + ", " + pokemon.getPokemonId().name() + " using " + ball);
					
					if (respondC.getStatus() == CatchStatus.CATCH_SUCCESS)
						pokemonCatched++;
				}
				else
					logger.log("	NO POKEBALL for " + pokemon.getPokemonId().name());
			}
		}
	}

	public void getPokestops(Collection<Pokestop> pokestops) throws LoginFailedException, RemoteServerException{
		logger.log("Pokestop found : " + pokestops.size());
		int cpt = 0;
		
		for(Pokestop pokestop : pokestops) {
			cpt++;
			if (!pokestop.canLoot())
				run(pokestop.getLatitude(), pokestop.getLongitude());

			PokestopLootResult result = pokestop.loot();
			capturePokemons(go.getMap().getCatchablePokemon());

			logger.log("Pokestop " + cpt + "/" + pokestops.size() + " " + result.getResult() + ", XP: " + result.getExperience());
			xpEarned += result.getExperience();

			if(cpt % 50 == 0)
				transfertAllPokermon();
			if(cpt % 20 == 0)
				deleteUselessitem();
			if(cpt % 10 == 0)
				showStats();
		}
		transfertAllPokermon();
	}

	public void deleteUselessitem() throws RemoteServerException, LoginFailedException{
		go.getPlayerProfile(true);

		Map<ItemId, Integer> deleteItems = new HashMap<ItemId, Integer>();
		deleteItems.put(ItemId.ITEM_RAZZ_BERRY, 0);
		deleteItems.put(ItemId.ITEM_POTION, 0);
		deleteItems.put(ItemId.ITEM_SUPER_POTION, 0);
		deleteItems.put(ItemId.ITEM_HYPER_POTION, 30);
		deleteItems.put(ItemId.ITEM_REVIVE, 30);
		deleteItems.put(ItemId.ITEM_POKE_BALL, 30);
		deleteItems.put(ItemId.ITEM_GREAT_BALL, 50);
		deleteItems.put(ItemId.ITEM_ULTRA_BALL, 50);

		for(Entry<ItemId, Integer> entry : deleteItems.entrySet()){
			int countDelete = go.getBag().getItem(entry.getKey()).getCount() - entry.getValue();
			if(countDelete > 0) {
				go.getBag().removeItem(entry.getKey(), countDelete);
				logger.log(countDelete + " " + entry.getKey().name() + " deleted from inventory");
			}
		}
	}

	public void showStats(){
		long playerLvlXP = go.getPlayerProfile().getStats().getNextLevelXp() - go.getPlayerProfile().getStats().getExperience();
		long LvlXP = go.getPlayerProfile().getStats().getNextLevelXp() - go.getPlayerProfile().getStats().getPrevLevelXp();
		
		logger.important("----STATS----");
		logger.important("Account LVL " + go.getPlayerProfile().getStats().getLevel() + ", Next LVL in " + playerLvlXP + " XP ("
				+ (int)(100 - (playerLvlXP * 1. / LvlXP) * 100) + "%)");
		logger.important("XP Earned: " + xpEarned);
		logger.important("Pokemon catched: " + pokemonCatched);
		logger.important("Pokemon transfered: " + pokemonTransfered);
		logger.important("--------------");
	}

	public void run(double lat, double lon) throws LoginFailedException, RemoteServerException{
		double firstLat = go.getLatitude();
		double firstLon = go.getLongitude();
		double dist = distance(lat, firstLat, lon, firstLon);
		int sections = (int) (dist / config.getSpeed());
		double changeLat = lat - firstLat;
		double changeLon = lon - firstLon;

		logger.log("Waiting " + (int) (dist / config.getSpeed()) + " seconds to travel " + (int) (dist) + " m");

		for(int i = 0; i < sections; i++) {
			go.setLocation(firstLat + changeLat * sections, firstLon + changeLon * sections, 0);
			PlayerUpdateMessageOuterClass.PlayerUpdateMessage request =  PlayerUpdateMessageOuterClass.PlayerUpdateMessage.newBuilder()
					.setLatitude(go.getLatitude()).setLongitude(go.getLongitude()).build();

			go.getRequestHandler().request(new ServerRequest(RequestType.PLAYER_UPDATE, request));
			go.getRequestHandler().sendServerRequests();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}

		}
		go.setLocation(lat, lon, 0);
	}

	public double distance(double lat1, double lat2, double lon1, double lon2) {

		Double latDistance = Math.toRadians(lat2 - lat1);
		Double lonDistance = Math.toRadians(lon2 - lon1);
		Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
		
		return Math.sqrt(Math.pow(6371 * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))) * 1000, 2));
	}
}
