package poketest;

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
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.main.ServerRequest;

import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import POGOProtos.Inventory.ItemIdOuterClass.ItemId;
import POGOProtos.Networking.Requests.RequestTypeOuterClass.RequestType;
import POGOProtos.Networking.Requests.Messages.PlayerUpdateMessageOuterClass;
import POGOProtos.Networking.Responses.CatchPokemonResponseOuterClass.CatchPokemonResponse.CatchStatus;
import POGOProtos.Networking.Responses.EncounterResponseOuterClass.EncounterResponse.Status;

public class PokeBot {

	private PokemonGo go;
	private boolean log;

	private final int SPEED = 24; // m/s
	private final int RANGE = 3;

	private int xpEarned = 0;
	private int pokemonTransfered = 0;
	private int pokemonCatched = 0;

	public PokeBot(PokemonGo go) throws LoginFailedException, RemoteServerException{
		this(go, false);
	}

	public PokeBot(PokemonGo go, boolean log) throws LoginFailedException, RemoteServerException {
		this.go = go;
		this.log = log;

		MapObjects objects = go.getMap().getMapObjects(RANGE);
		getPokestops(objects.getPokestops());
	}

	public void transfertAllPokermon() throws LoginFailedException, RemoteServerException{
		Map<PokemonId, Pokemon> pokemons = new HashMap<PokemonId, Pokemon>();
		for(Pokemon pokemon : go.getPokebank().getPokemons()) {

			if (pokemon.getFavorite())
				continue;

			if (pokemons.containsKey(pokemon.getPokemonId())) {
				if (pokemon.getCp() <= pokemons.get(pokemon.getPokemonId()).getCp()) {
					if(log) System.out.println("Transfering pokemon " + pokemon.getPokemonId() + " : " + pokemon.transferPokemon());
				} else {
					if(log) System.out.println("Transfering pokemon " + pokemons.get(pokemon.getPokemonId()).getPokemonId() + " : " + pokemons.get(pokemon.getPokemonId()).transferPokemon());
					pokemons.put(pokemon.getPokemonId(), pokemon);
				}
				pokemonTransfered++;
			}
			else
				pokemons.put(pokemon.getPokemonId(), pokemon);
		}
		if(log) System.out.println();
	}

	public void capturePokemons(List<CatchablePokemon> list) throws LoginFailedException, RemoteServerException{
		for(CatchablePokemon pokemon : list) {
			EncounterResult respondE = pokemon.encounterPokemon();

			if(respondE.getStatus() == Status.ENCOUNTER_SUCCESS){
				go.getPlayerProfile(true);
				Bag bag = go.getBag();

				Pokeball ball = null;
				if(bag.getItem(ItemId.ITEM_MASTER_BALL) != null && bag.getItem(ItemId.ITEM_MASTER_BALL).getCount() > 0)
					ball = Pokeball.MASTERBALL;
				else if(bag.getItem(ItemId.ITEM_ULTRA_BALL) != null && bag.getItem(ItemId.ITEM_ULTRA_BALL).getCount() > 0)
					ball = Pokeball.ULTRABALL;
				else if(bag.getItem(ItemId.ITEM_GREAT_BALL) != null && bag.getItem(ItemId.ITEM_GREAT_BALL).getCount() > 0)
					ball = Pokeball.GREATBALL;
				else if(bag.getItem(ItemId.ITEM_POKE_BALL) != null && bag.getItem(ItemId.ITEM_POKE_BALL).getCount() > 0)
					ball = Pokeball.POKEBALL;

				if(ball != null){
					CatchResult respondC = pokemon.catchPokemon(ball);
					if(log) System.out.println("	" + respondC.getStatus() + ", " + pokemon.getPokemonId().name() + " using " + ball);
					if(respondC.getStatus() == CatchStatus.CATCH_SUCCESS)
						pokemonCatched++;
				}else{
					if(log) System.out.println("	NO POKEBALL for " + pokemon.getPokemonId().name());
				}
			}
		}
	}

	public void getPokestops(Collection<Pokestop> pokestops) throws LoginFailedException, RemoteServerException{
		if(log) System.out.println("Nombre de pokestops: " + pokestops.size());
		int cpt = 0;
		go.getPlayerProfile(true);
		for(Pokestop pokestop : pokestops) {
			cpt++;
			if (!pokestop.canLoot())
				run(pokestop.getLatitude(), pokestop.getLongitude());

			PokestopLootResult result = pokestop.loot();
			capturePokemons(go.getMap().getCatchablePokemon());

			if(log) System.out.println("Pokestop " + cpt + "/" + pokestops.size() + " " + result.getResult() + ", XP: " + result.getExperience() + "\n");
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

		for(Entry<ItemId, Integer> entry : deleteItems.entrySet()){
			int countDelete = go.getBag().getItem(entry.getKey()).getCount() - entry.getValue();
			if(countDelete > 0) {
				go.getBag().removeItem(entry.getKey(), countDelete);
				if(log) System.out.println(countDelete + " " + entry.getKey().name() + " deleted");
			}
		}
		if(log) System.out.println();
	}

	public void showStats(){
		System.out.println("----STATS----");
		System.out.println("Account LVL " + go.getPlayerProfile().getStats().getLevel() + " " + go.getPlayerProfile().getStats().getExperience() + "/" + go.getPlayerProfile().getStats().getNextLevelXp());
		System.out.println("XP Earned: " + xpEarned);
		System.out.println("Pokemon catched: " + pokemonCatched);
		System.out.println("Pokemon transfered: " + pokemonTransfered);
		System.out.println("-------------\n");
	}

	public void run(double lat, double lon) throws LoginFailedException, RemoteServerException{
		double firstLat = go.getLatitude();
		double firstLon = go.getLongitude();
		double dist = distance(lat, firstLat, lon, firstLon);
		int sections = (int) (dist / SPEED);
		double changeLat = lat - firstLat;
		double changeLon = lon - firstLon;

		if(log) System.out.println("Wait " + (int) (dist / SPEED) + " seconds to travel " + (int) (dist) + " m");

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

		final int R = 6371; // Radius of the earth

		Double latDistance = Math.toRadians(lat2 - lat1);
		Double lonDistance = Math.toRadians(lon2 - lon1);
		Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
		Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double distance = R * c * 1000; // convert to meters

		distance = Math.pow(distance, 2);

		return Math.sqrt(distance);
	}
}
