package poketest;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.Bag;
import com.pokegoapi.api.map.MapObjects;
import com.pokegoapi.api.map.fort.Pokestop;
import com.pokegoapi.api.map.fort.PokestopLootResult;
import com.pokegoapi.api.pokemon.Pokemon;
import com.pokegoapi.auth.PTCLogin;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.google.common.geometry.S2LatLng;
import com.pokegoapi.main.ServerRequest;

import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import POGOProtos.Inventory.ItemIdOuterClass.ItemId;
import POGOProtos.Map.Fort.FortDataOuterClass.FortData;
import POGOProtos.Map.Pokemon.MapPokemonOuterClass.MapPokemon;
import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass;
import POGOProtos.Networking.Requests.RequestTypeOuterClass.RequestType;
import POGOProtos.Networking.Requests.Messages.PlayerUpdateMessageOuterClass;
import POGOProtos.Networking.Responses.CatchPokemonResponseOuterClass.CatchPokemonResponse;
import POGOProtos.Networking.Responses.EncounterResponseOuterClass.EncounterResponse;
import POGOProtos.Networking.Responses.EncounterResponseOuterClass.EncounterResponse.Status;
import POGOProtos.Networking.Responses.FortSearchResponseOuterClass.FortSearchResponse;
import okhttp3.OkHttpClient;

public class PokeBot {

	public static PokemonGo go;
	public static final int SPEED = 10; // m/s

	public static void main(String[] args) {
		boolean pos = false;
		OkHttpClient http = new OkHttpClient();
		while(true) {
			pos = !pos;
			RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo auth = null;
			try {
				auth = new PTCLogin(http).login("thisismac42", "30101997");
				go = new PokemonGo(auth, http);
				if(pos)
					go.setLocation(48.857445, 2.295771, 0);
				else
					go.setLocation(48.854989, 2.348024, 0);

				MapObjects mapObj = go.getMap().getMapObjects(go.getLatitude(), go.getLongitude(), 3);
				Collection<Pokestop> pokestops = mapObj.getPokestops();
				getPokestops(pokestops);

			} catch (LoginFailedException | RemoteServerException e) {
				// failed to login, invalid credentials or auth issue.
				e.printStackTrace();
			} 
		}
	}

	public static void transfertAllPokermon() throws LoginFailedException, RemoteServerException{
		Map<PokemonId, Pokemon> pokemons = new HashMap<PokemonId, Pokemon>();
		for(Pokemon pokemon : go.getPokebank().getPokemons()) {

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

	public static void getPokemons(Collection<MapPokemon> pokemons) throws LoginFailedException, RemoteServerException{
		for(MapPokemon pokemon : pokemons){
			EncounterResponse respondE = go.getMap().encounterPokemon(pokemon);

			if(respondE.getStatus() == Status.ENCOUNTER_SUCCESS){
				Bag bag = go.getBag();
				int pokeballid = ItemId.ITEM_POKE_BALL_VALUE;
				
				if(bag.getItem(ItemId.ITEM_MASTER_BALL) != null)
					if(bag.getItem(ItemId.ITEM_MASTER_BALL).getCount() > 0)
						pokeballid = ItemId.ITEM_MASTER_BALL_VALUE;
				
				else if(bag.getItem(ItemId.ITEM_ULTRA_BALL) != null)
					if(bag.getItem(ItemId.ITEM_ULTRA_BALL).getCount() > 0)
						pokeballid = ItemId.ITEM_ULTRA_BALL_VALUE;
				
				else if(bag.getItem(ItemId.ITEM_GREAT_BALL) != null)
					if(bag.getItem(ItemId.ITEM_GREAT_BALL).getCount() > 0)
						pokeballid = ItemId.ITEM_GREAT_BALL_VALUE;
				
				//System.out.println(bag.getItem(ItemId.ITEM_ULTRA_BALL) + "  " + pokeballid);
				
				CatchPokemonResponse respondC = go.getMap().catchPokemon(pokemon, 1.0, 2, 1, pokeballid);
				System.out.println("	" + respondC.getStatus() + ", " + pokemon.getPokemonId().name());
			}
		}
	}

	public static void getPokestops(Collection<Pokestop> pokestops) throws LoginFailedException, RemoteServerException{
		System.out.println("Nombre de pokestops: " + pokestops.size());
		int cpt = 0;
		for(Pokestop pokestop : pokestops) {
			cpt++;
			if (!pokestop.canLoot())
				run(pokestop.getLatitude(), pokestop.getLongitude());
			
			PokestopLootResult result = pokestop.loot();
			getPokemons(go.getMap().getMapObjects(go.getLatitude(), go.getLongitude()).getCatchablePokemons());
			System.out.println("Pokestop " + cpt + "/" + pokestops.size() + " " + result.getResult() + ", XP: " + result.getExperience());
			if(cpt % (pokestops.size() / 2) == 0)
				transfertAllPokermon();
		}
		transfertAllPokermon();
	}

	public static void run(double lat, double lon) throws LoginFailedException, RemoteServerException{
		double firstLat = go.getLatitude();
		double firstLon = go.getLongitude();
		double dist = distance(lat, firstLat, lon, firstLon);
		int sections = (int) (dist / SPEED);
		double changeLat = lat - firstLat;
		double changeLon = lon - firstLon;

		System.out.println("Attente de " + timeInSec(dist, SPEED) + " s");
		
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

	public static double distance(double lat1, double lat2, double lon1, double lon2) {

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

	public static int timeInSec(double distance, double vitesse){
		return (int) (distance / vitesse);
	}
}
