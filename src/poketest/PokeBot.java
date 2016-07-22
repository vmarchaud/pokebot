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
import POGOProtos.Networking.Responses.EncounterResponseOuterClass.EncounterResponse.Status;
import okhttp3.OkHttpClient;

public class PokeBot {

	public PokemonGo go;
	public final int SPEED = 24; // m/s
	private OkHttpClient httpClient;

	public PokeBot(PokemonGo go) throws LoginFailedException, RemoteServerException {
		this.go = go;

		MapObjects objects = go.getMap().getMapObjects(3);
		getPokestops(objects.getPokestops());
	}

	public void transfertAllPokermon() throws LoginFailedException, RemoteServerException{
		Map<PokemonId, Pokemon> pokemons = new HashMap<PokemonId, Pokemon>();
		for(Pokemon pokemon : go.getPokebank().getPokemons()) {
			
			if (pokemon.getFavorite())
				continue;

			if (pokemons.containsKey(pokemon.getPokemonId())) {
				if (pokemon.getCp() <= pokemons.get(pokemon.getPokemonId()).getCp()) {
					System.out.println("Transfering pokemon " + pokemon.getPokemonId() + " : " + pokemon.transferPokemon());
				} else {
					System.out.println("Transfering pokemon " + pokemons.get(pokemon.getPokemonId()).getPokemonId() + " : " + pokemons.get(pokemon.getPokemonId()).transferPokemon());
					pokemons.put(pokemon.getPokemonId(), pokemon);
				}
			}
			else
				pokemons.put(pokemon.getPokemonId(), pokemon);
		}
	}

	public void capturePokemons(List<CatchablePokemon> list) throws LoginFailedException, RemoteServerException{
		for(CatchablePokemon pokemon : list) {
			EncounterResult respondE = pokemon.encounterPokemon();

			if(respondE.getStatus() == Status.ENCOUNTER_SUCCESS){
				go.getPlayerProfile(true);
				Bag bag = go.getBag();

				Pokeball ball = Pokeball.POKEBALL;
				if(bag.getItem(ItemId.ITEM_ULTRA_BALL) != null && bag.getItem(ItemId.ITEM_ULTRA_BALL).getCount() > 0)
					ball = Pokeball.ULTRABALL;
				else if(bag.getItem(ItemId.ITEM_GREAT_BALL) != null && bag.getItem(ItemId.ITEM_GREAT_BALL).getCount() > 0)
					ball = Pokeball.GREATBALL;

				CatchResult respondC = pokemon.catchPokemon(ball);
				System.out.println("	" + respondC.getStatus() + ", " + pokemon.getPokemonId().name() + " using " + ball);
			}
		}
	}

	public void getPokestops(Collection<Pokestop> pokestops) throws LoginFailedException, RemoteServerException{
		System.out.println("Nombre de pokestops: " + pokestops.size());
		int cpt = 0;
		go.getPlayerProfile(true);
		for(Pokestop pokestop : pokestops) {
			cpt++;
			if (!pokestop.canLoot())
				run(pokestop.getLatitude(), pokestop.getLongitude());

			PokestopLootResult result = pokestop.loot();
			capturePokemons(go.getMap().getCatchablePokemon());
			
			System.out.println("Pokestop " + cpt + "/" + pokestops.size() + " " + result.getResult() + ", XP: " + result.getExperience());
			
			if(cpt % (pokestops.size() / 2) == 0)
				transfertAllPokermon();
			if(cpt % 10 == 0)
				deleteUselessitem();
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
				System.out.println(countDelete + " " + entry.getKey().name() + " Deleted");
			}
		}
	}

	public void run(double lat, double lon) throws LoginFailedException, RemoteServerException{
		double firstLat = go.getLatitude();
		double firstLon = go.getLongitude();
		double dist = distance(lat, firstLat, lon, firstLon);
		int sections = (int) (dist / SPEED);
		double changeLat = lat - firstLat;
		double changeLon = lon - firstLon;

		System.out.println("Wait " + (int) (dist / SPEED) + " s");

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
