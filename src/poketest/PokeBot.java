package poketest;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.gson.JsonIOException;
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
import com.pokegoapi.api.map.pokemon.EvolutionResult;
import com.pokegoapi.api.pokemon.EggPokemon;
import com.pokegoapi.api.pokemon.HatchedEgg;
import com.pokegoapi.api.pokemon.Pokemon;
import com.pokegoapi.api.pokemon.PokemonMetaRegistry;
import com.pokegoapi.auth.CredentialProvider;
import com.pokegoapi.auth.GoogleUserCredentialProvider;
import com.pokegoapi.auth.PtcCredentialProvider;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.main.ServerRequest;
import com.pokegoapi.util.SystemTimeImpl;

import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;
import POGOProtos.Networking.Requests.RequestTypeOuterClass.RequestType;
import POGOProtos.Networking.Requests.Messages.CheckAwardedBadgesMessageOuterClass.CheckAwardedBadgesMessage;
import POGOProtos.Networking.Requests.Messages.LevelUpRewardsMessageOuterClass.LevelUpRewardsMessage;
import POGOProtos.Networking.Requests.Messages.PlayerUpdateMessageOuterClass.PlayerUpdateMessage;
import POGOProtos.Networking.Requests.Messages.UseIncenseMessageOuterClass.UseIncenseMessage;
import POGOProtos.Networking.Requests.Messages.UseItemXpBoostMessageOuterClass.UseItemXpBoostMessage;
import POGOProtos.Networking.Responses.CatchPokemonResponseOuterClass.CatchPokemonResponse.CatchStatus;
import POGOProtos.Networking.Responses.EncounterResponseOuterClass.EncounterResponse.Status;
import POGOProtos.Networking.Responses.FortSearchResponseOuterClass.FortSearchResponse.Result;
import POGOProtos.Networking.Responses.LevelUpRewardsResponseOuterClass.LevelUpRewardsResponse;
import POGOProtos.Networking.Responses.UseIncenseResponseOuterClass.UseIncenseResponse;
import POGOProtos.Networking.Responses.UseItemEggIncubatorResponseOuterClass.UseItemEggIncubatorResponse;
import POGOProtos.Networking.Responses.UseItemXpBoostResponseOuterClass.UseItemXpBoostResponse;
import okhttp3.OkHttpClient;
import poketest.Account.EnumProvider;

public class PokeBot implements Runnable {

	static final int showStatTime = 60000;

	private PokemonGo		go;
	private Account 		account;
	private CustomConfig 	config;
	private CustomLogger	logger;

	public SecureRandom 	rand = new SecureRandom();
	private OkHttpClient 	http = new OkHttpClient();

	private int cachedLvl = 0;

	private BotStats stats;

	public PokeBot(Account account, CustomConfig config) {
		this.account = account;
		this.config = config;
		this.logger = new CustomLogger(account);
		this.stats = new BotStats(logger);

		Timer t = new Timer(); 
		GregorianCalendar gc = new GregorianCalendar(); 
		gc.add(Calendar.SECOND, 10); 
		t.scheduleAtFixedRate(this.stats, gc.getTime(), config.getStatsTimer() * 1000);
	}

	public void run() {
		int	failedLoginCount = 0;

		while ( true ) {
			boolean authok = false;
			while(!authok){
				try {
					auth();
					authok = true;
				} catch (RemoteServerException e) {
					e.printStackTrace();
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
			}
			try {
				Thread.currentThread().setName("Thread-" + account.getUsername());

				if(stats.getXpStart() == 0)
					stats.setXpStart(go.getPlayerProfile().getStats().getExperience());

				stats.updateStat(go.getPlayerProfile().getStats().getLevel(), go.getPlayerProfile().getStats().getExperience());

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
		CredentialProvider auth = null;
		// loggin with PTC with credentials
		if (account.getProvider() == EnumProvider.PTC)
			auth = new PtcCredentialProvider(http, account.getUsername(), account.getPassword());
		// loggin with google with token to put into url
		else if (account.getProvider() == EnumProvider.GOOGLE && account.getToken() == null) {
			auth = new GoogleUserCredentialProvider(http);

			try {
				Desktop.getDesktop().browse(new URI(GoogleUserCredentialProvider.LOGIN_URL));
			} catch (IOException | URISyntaxException e) {  }

			logger.important("Enter authorisation code:");

			@SuppressWarnings("resource")
			String access = new Scanner(System.in).nextLine();
			((GoogleUserCredentialProvider)auth).login(access);
			account.setToken(((GoogleUserCredentialProvider)auth).getRefreshToken());
			try {
				config.save();
			} catch (JsonIOException | IOException e) {
				e.printStackTrace();
			}
		}
		// loggin with google refresh token
		else if (account.getProvider() == EnumProvider.GOOGLE && account.getToken().length() > 0)
			auth = new GoogleUserCredentialProvider(http, account.getToken(), new SystemTimeImpl());


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

			if (pokemon.isFavorite())
				continue;

			if (pokemons.containsKey(pokemon.getPokemonId())) {
				if (pokemon.getCp() <= pokemons.get(pokemon.getPokemonId()).getCp()) {
					logger.log("Transfering pokemon " + pokemon.getPokemonId() + " : " + pokemon.transferPokemon());
				} else {
					logger.log("Transfering pokemon " + pokemons.get(pokemon.getPokemonId()).getPokemonId() + " : " + pokemons.get(pokemon.getPokemonId()).transferPokemon());
					pokemons.put(pokemon.getPokemonId(), pokemon);
				}
				stats.addPokemonTransfered();
			}
			else
				pokemons.put(pokemon.getPokemonId(), pokemon);
		}

		for(Pokemon pokemon : go.getInventories().getPokebank().getPokemons()){
			PokemonId hightestPokemonId = PokemonMetaRegistry.getHightestForFamily(pokemon.getPokemonFamily());

			if (hightestPokemonId != pokemon.getPokemonId() && PokemonMetaRegistry.getMeta(pokemon.getPokemonId()) != null &&
					go.getInventories().getCandyjar().getCandies(pokemon.getPokemonFamily()) >= PokemonMetaRegistry.getMeta(pokemon.getPokemonId()).getCandyToEvolve()) {

				if(!pokemons.containsKey(hightestPokemonId) || pokemons.get(hightestPokemonId).getCp() < pokemon.getCp() * pokemon.getCpMultiplier()){
					EvolutionResult result = pokemon.evolve();
					logger.log("Evolving pokemon " + pokemon.getPokemonId() + " into " + result.getEvolvedPokemon().getPokemonId() + " " + result.getResult());
					stats.addPokemonEvolved();
				}
			}
		}
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
						stats.addPokemonCatched();
				}
				else
					logger.log("	NO POKEBALL for " + pokemon.getPokemonId().name());
			}
		}
	}

	public void getPokestops(Collection<Pokestop> pokestops) throws LoginFailedException, RemoteServerException{
		logger.log("Pokestop found : " + pokestops.size());

		List<Location> parkour = Parkour.buildLocationArrayFromPokestops(pokestops);

		double rawDistance = Parkour.getTotalParkour(parkour);
		logger.log("Raw parkour: " + (int)(rawDistance) + " m in " + (int)(rawDistance / config.getSpeed()) + " secs");

		List<Location> bestParkour = Parkour.getBestParkour(Parkour.buildLocationArrayFromPokestops(pokestops));
		double optimisedDistance = Parkour.getTotalParkour(bestParkour);
		logger.log("Optimised parkour: " + (int)(optimisedDistance) + " m in " + (int)(optimisedDistance / config.getSpeed()) + " secs");
		pokestops = Parkour.buildPokestopCollection(bestParkour, pokestops);

		useXpBoost();
		useIncense();
		getBadges();

		int cpt = 0;
		for(Pokestop pokestop : pokestops) {
			cpt++;

			if (!pokestop.canLoot())
				run(pokestop.getLatitude(), pokestop.getLongitude());

			int tryPokestop = 0;
			PokestopLootResult result = null;
			do{
				tryPokestop++;
				result = pokestop.loot();
			}while(result.getResult() == Result.SUCCESS && result.getExperience() == 0);

			capturePokemons(go.getMap().getCatchablePokemon());

			logger.log("Pokestop " + cpt + "/" + pokestops.size() + " " + result.getResult() + ", XP: " + result.getExperience() + ", try: " + tryPokestop);
			stats.addPokestopVisited();

			if(cpt % 30 == 0) {
				evolveUselessPokemon();
				transfertAllPokermon();
			}
			if (cpt % 10 == 0) {
				deleteUselessitem();
				manageEggs();
				if (go.getPlayerProfile().getStats().getLevel() != cachedLvl)
					getRewards(++cachedLvl);
			}

			stats.updateStat(go.getPlayerProfile().getStats().getLevel(), go.getPlayerProfile().getStats().getExperience());
		}
	}

	public void useXpBoost() throws RemoteServerException, LoginFailedException{
		UseItemXpBoostMessage xpBoost =  UseItemXpBoostMessage.newBuilder().setItemId(ItemId.ITEM_LUCKY_EGG).setItemIdValue(ItemId.ITEM_LUCKY_EGG_VALUE).build();

		ServerRequest request = new ServerRequest(RequestType.USE_ITEM_XP_BOOST, xpBoost);
		go.getRequestHandler().sendServerRequests(request);
		UseItemXpBoostResponse xpBoostResponse = null;
		try {
			xpBoostResponse = UseItemXpBoostResponse.parseFrom(request.getData());
		} catch (InvalidProtocolBufferException e) {
			e.printStackTrace();
		}

		logger.log("Use XP boost: " + xpBoostResponse.getResult());
	}

	public void useIncense() throws RemoteServerException, LoginFailedException{
		UseIncenseMessage xpBoost =  UseIncenseMessage.newBuilder().setIncenseType(ItemId.ITEM_INCENSE_ORDINARY).setIncenseTypeValue(ItemId.ITEM_INCENSE_ORDINARY_VALUE).build();

		ServerRequest request = new ServerRequest(RequestType.USE_INCENSE, xpBoost);
		go.getRequestHandler().sendServerRequests(request);
		UseIncenseResponse xpBoostResponse = null;
		try {
			xpBoostResponse = UseIncenseResponse.parseFrom(request.getData());
		} catch (InvalidProtocolBufferException e) {
			e.printStackTrace();
		}

		logger.log("Use incense: " + xpBoostResponse.getResult());
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
		{
			try {
				logger.log(String.format("Egg %s is at %4.3f/%4.3f", Long.toUnsignedString(egg.getId()), egg.getEggKmWalked(), egg.getEggKmWalkedTarget()));
			} catch (LoginFailedException | RemoteServerException e) {
				e.printStackTrace();
			}
		});

		List<EggIncubator> incubators = go.getInventories().getIncubators().stream()
				.filter(incubator -> {
					try {
						return !incubator.isInUse();
					} catch (LoginFailedException | RemoteServerException e) {
						e.printStackTrace();
					}
					return false;
				})
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
			if (i >= eggs.size())
				break ;
			UseItemEggIncubatorResponse.Result result = incubators.get(i).hatchEgg(eggs.get(i));
			logger.log("Trying to put an egg " + eggs.get(i).getEggKmWalkedTarget()  + " into the incubators result : " + result);
		}
	}

	public void getBadges() throws LoginFailedException, RemoteServerException{
		CheckAwardedBadgesMessage msg = CheckAwardedBadgesMessage.newBuilder().build();
		ServerRequest serverRequest = new ServerRequest(RequestType.CHECK_AWARDED_BADGES, msg);
		go.getRequestHandler().sendServerRequests(serverRequest);
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

	public void evolveUselessPokemon() throws LoginFailedException, RemoteServerException{
		List<PokemonId> uselessPokemonId = new ArrayList<PokemonId>();
		uselessPokemonId.add(PokemonId.RATTATA);
		uselessPokemonId.add(PokemonId.PIDGEY);
		uselessPokemonId.add(PokemonId.SPEAROW);
		uselessPokemonId.add(PokemonId.ZUBAT);

		for(PokemonId pokeid : uselessPokemonId){
			for(Pokemon pokemon : go.getInventories().getPokebank().getPokemonByPokemonId(pokeid))
			{
				if(go.getInventories().getCandyjar().getCandies(pokemon.getPokemonFamily()) > PokemonMetaRegistry.getMeta(pokemon.getPokemonId()).getCandyToEvolve()) {
					EvolutionResult result = pokemon.evolve();
					logger.log("Evolving pokemon " + pokemon.getPokemonId() + " into " + result.getEvolvedPokemon().getPokemonId() + " " + result.getResult());
					stats.addPokemonEvolved();
				}
			}
		}
	}

	public void run(double lat, double lon) throws LoginFailedException, RemoteServerException{
		double firstLat = go.getLatitude();
		double firstLon = go.getLongitude();
		double dist = Parkour.distance(lat, firstLat, lon, firstLon);
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
}
