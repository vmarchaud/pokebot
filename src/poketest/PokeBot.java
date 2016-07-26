package poketest;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.EggIncubator;
import com.pokegoapi.api.inventory.ItemBag;
import com.pokegoapi.api.inventory.Pokeball;
import com.pokegoapi.api.map.MapObjects;
import com.pokegoapi.api.map.fort.Pokestop;
import com.pokegoapi.api.map.fort.PokestopLootResult;
import com.pokegoapi.api.map.pokemon.CatchResult;
import com.pokegoapi.api.map.pokemon.CatchablePokemon;
import com.pokegoapi.api.pokemon.EggPokemon;
import com.pokegoapi.api.pokemon.HatchedEgg;
import com.pokegoapi.api.pokemon.Pokemon;
import com.pokegoapi.api.pokemon.PokemonMetaRegistry;
import com.pokegoapi.auth.GoogleLogin;
import com.pokegoapi.auth.PtcLogin;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.main.ServerRequest;

import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;
import POGOProtos.Networking.Requests.RequestTypeOuterClass.RequestType;
import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo;
import POGOProtos.Networking.Requests.Messages.LevelUpRewardsMessageOuterClass.LevelUpRewardsMessage;
import POGOProtos.Networking.Requests.Messages.PlayerUpdateMessageOuterClass.PlayerUpdateMessage;
import POGOProtos.Networking.Responses.CatchPokemonResponseOuterClass.CatchPokemonResponse.CatchStatus;
import POGOProtos.Networking.Responses.EncounterResponseOuterClass.EncounterResponse.Status;
import POGOProtos.Networking.Responses.LevelUpRewardsResponseOuterClass.LevelUpRewardsResponse;
import POGOProtos.Networking.Responses.UseItemEggIncubatorResponseOuterClass.UseItemEggIncubatorResponse;
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
	private int cachedLvl	= 0;
	
	private int[] requiredXP = { 0, 1000, 3000, 6000, 10000, 15000, 21000, 28000, 36000, 45000, 55000, 65000, 75000,
            85000, 100000, 120000, 140000, 160000, 185000, 210000, 260000, 335000, 435000, 560000, 710000, 900000, 1100000,
            1350000, 1650000, 2000000, 2500000, 3000000, 3750000, 4750000, 6000000, 7500000, 9500000, 12000000, 15000000, 20000000 };

	public PokeBot(Account account, CustomConfig config) {
		this.account = account;
		this.config = config;
		this.logger = new CustomLogger(account);
	}
	
	public void run() {
		int		failedLoginCount = 0;
		
		try {
			try {
				auth();
			} catch (RemoteServerException e) {
				e.printStackTrace();
			}
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
		
		while ( true ) {
			try {
				MapObjects objects = go.getMap().getMapObjects(config.getMap_radius());
				getPokestops(objects.getPokestops());
			} catch (Exception e) {
				e.printStackTrace();
				logger.important("Got error " + e.getMessage());
				logger.important("Rebooting in 1 minutes ..");
			}
			// sleep to avoid spamming
			try {
				Thread.sleep(60 * 1000);
			} catch (InterruptedException e) { }
		}
	}
	
	public void auth() throws LoginFailedException, RemoteServerException {
		AuthInfo auth = null;
		if (account.getProvider() == EnumProvider.GOOGLE) 
			auth = new GoogleLogin(http).login();
		else 
			auth = new PtcLogin(http).login(account.getUsername(), account.getPassword());
		
		go = new PokemonGo(auth, http);
		cachedLvl = go.getPlayerProfile().getStats().getLevel();
		logger.important("Logged into pokemon go with fresh instance");
		Location location = config.getSpawns().get(rand.nextInt(config.getSpawns().size()));
		logger.important(String.format("Location choosen, lat : %s and long : %s", location.getLattitude(), location.getLongitude() ));
		go.setLocation(location.getLattitude(), location.getLongitude(), 0);
	}

	public void transfertAllPokermon() throws LoginFailedException, RemoteServerException{
		Map<PokemonId, Pokemon> pokemons = new HashMap<PokemonId, Pokemon>();
		for(Pokemon pokemon : go.getInventories().getPokebank().getPokemons()) {

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
		
		// Faudrait choisir un peu plus si on evolve ou pas le pokemon (si il a un bon CP etc) ?
		/*
		for(Pokemon pokemon : go.getInventories().getPokebank().getPokemons()){
			PokemonId hightestPokemonId = PokemonMetaRegistry.getHightestForFamily(pokemon.getPokemonFamily());
			
			if(hightestPokemonId != pokemon.getPokemonId()){
				if(go.getInventories().getCandyjar().getCandies(pokemon.getPokemonFamily()) >= PokemonMetaRegistry.getMeta(pokemon.getPokemonId()).getCandiesToEvolve())
					if(!pokemons.containsKey(hightestPokemonId)){
						Evolution
						logger.log("Evolving pokemon " + pokemon.getPokemonId() + " into " + pokemon.evolve().getEvolvedPokemon().getPokemonId() + " " + pokemon.evolve().getResult());
					}
					else if (pokemons.get(hightestPokemonId).getCp() < pokemon.getCp() * pokemon.getCpMultiplier()){
						logger.log("Evolving pokemon " + pokemon.getPokemonId() + " into " + pokemon.evolve().getEvolvedPokemon().getPokemonId() + " " + pokemon.evolve().getResult());
					}
			}
		}*/
	}

	public void capturePokemons(List<CatchablePokemon> list) throws LoginFailedException, RemoteServerException{
		for(CatchablePokemon pokemon : list) {

			if (pokemon.encounterPokemon().getStatus() == Status.ENCOUNTER_SUCCESS){
				go.getInventories().updateInventories(true);
				
				ItemBag bag = go.getInventories().getItemBag();

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

			if(cpt % 30 == 0) {
				transfertAllPokermon();
			}
			if (cpt % 10 == 0) {
				showStats();
				deleteUselessitem();
				manageEggs();
				if (go.getPlayerProfile().getStats().getLevel() != cachedLvl)
					getRewards(++cachedLvl);
			}
		}
	}
	
	
	public void deleteUselessitem() throws RemoteServerException, LoginFailedException{
		go.getInventories().updateInventories(true);

		Map<ItemId, Integer> deleteItems = new HashMap<ItemId, Integer>();
		deleteItems.put(ItemId.ITEM_RAZZ_BERRY, 0);
		deleteItems.put(ItemId.ITEM_POTION, 0);
		deleteItems.put(ItemId.ITEM_SUPER_POTION, 0);
		deleteItems.put(ItemId.ITEM_HYPER_POTION, 15);
		deleteItems.put(ItemId.ITEM_REVIVE, 15);
		deleteItems.put(ItemId.ITEM_MAX_REVIVE, 15);
		deleteItems.put(ItemId.ITEM_POKE_BALL, 30);
		deleteItems.put(ItemId.ITEM_GREAT_BALL, 50);
		deleteItems.put(ItemId.ITEM_ULTRA_BALL, 50);
		deleteItems.put(ItemId.ITEM_MAX_POTION, 50);


		for(Entry<ItemId, Integer> entry : deleteItems.entrySet()){
			int countDelete = go.getInventories().getItemBag().getItem(entry.getKey()).getCount() - entry.getValue();
			if(countDelete > 0) {
				go.getInventories().getItemBag().removeItem(entry.getKey(), countDelete);
				logger.log(countDelete + " " + entry.getKey().name() + " deleted from inventory");
			}
		}
	}
	
	public void manageEggs() throws LoginFailedException, RemoteServerException {
		go.getInventories().updateInventories(true);
		
		for(HatchedEgg egg : go.getInventories().getHatchery().queryHatchedEggs()) {
			Pokemon pk = go.getInventories().getPokebank().getPokemonById(egg.getId());
			if (pk == null)
				logger.log("A egg has hetched");
			else
				logger.log(String.format("A egg has hetched : %s with cp : %d", pk.getPokemonId(), pk.getCp()));
		}
		
		go.getInventories().getHatchery().getEggs().stream()
		.filter(egg -> egg.isIncubate())
		.forEach(egg -> 
			logger.log(String.format("Egg %s is at %d/%d", Long.toUnsignedString(egg.getId()), (int)egg.getEggKmWalkedStart(),(int) egg.getEggKmWalkedTarget())));
		
		List<EggIncubator> incubators = go.getInventories().getIncubators().stream()
				.filter(incubator -> !incubator.isInUse())
				.collect(Collectors.toCollection(ArrayList::new));
		logger.log("Currently have " + incubators.size() + " incubators available to incube eggs.");
		if (incubators.size() == 0)
			return ;
		
		List<EggPokemon> eggs = go.getInventories().getHatchery().getEggs().stream()
				.filter(egg -> egg.getEggIncubatorId() == null || egg.getEggIncubatorId().length() == 0)
				.sorted((left, right) -> Double.compare(left.getEggKmWalkedTarget(), right.getEggKmWalkedTarget()))
				.collect(Collectors.toCollection(ArrayList::new));
		logger.log("Currently have " + eggs.size() + "eggs available to be incubate.");
		if (eggs.size() == 0)
			return ;
		
		for(int i = 0; i < incubators.size(); i++) {
			UseItemEggIncubatorResponse.Result result = incubators.get(i).hatchEgg(eggs.get(i));
			logger.log("Trying to put an egg " + eggs.get(i).getEggKmWalkedTarget()  + " into the incubators result : " + result);
		}
	}

	public void showStats() {
		int lvl = go.getPlayerProfile().getStats().getLevel();
        int nextXP = requiredXP[lvl] - requiredXP[lvl - 1];
        int curLevelXP = (int)go.getPlayerProfile().getStats().getExperience() - requiredXP[lvl - 1];
        int ratio = (int) ((double)curLevelXP / (double)nextXP * 100.0);
		
		logger.important("----STATS----");
		logger.important(String.format("Account lvl %d : %d/%d (%d%%)", lvl, curLevelXP, nextXP, ratio));
		logger.important("XP Earned: " + xpEarned);
		logger.important("Pokemon catched: " + pokemonCatched);
		logger.important("Pokemon transfered: " + pokemonTransfered);
		logger.important("--------------");
	}
	
	public void getRewards(int cachedLvl) throws RemoteServerException, LoginFailedException {
		
		LevelUpRewardsMessage msg = LevelUpRewardsMessage.newBuilder().setLevel(cachedLvl).build(); 
		ServerRequest serverRequest = new ServerRequest(RequestType.LEVEL_UP_REWARDS, msg);
		go.getRequestHandler().sendServerRequests(serverRequest);
		
		LevelUpRewardsResponse response = null;
		try {
			response = LevelUpRewardsResponse.parseFrom(serverRequest.getData());
		} catch (InvalidProtocolBufferException e) {
			throw new RemoteServerException(e);
		}
		
		logger.log("Getting award for lvl " + (cachedLvl) + " with result : " + response.getResult());
		
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
			PlayerUpdateMessage request =  PlayerUpdateMessage.newBuilder()
					.setLatitude(go.getLatitude()).setLongitude(go.getLongitude()).build();

			go.getRequestHandler().sendServerRequests(new ServerRequest(RequestType.PLAYER_UPDATE, request));
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
