package poketest;

import java.util.Collection;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.Bag;
import com.pokegoapi.auth.PTCLogin;
import com.pokegoapi.exceptions.LoginFailedException;

import POGOProtos.Inventory.ItemIdOuterClass.ItemId;
import POGOProtos.Map.Pokemon.MapPokemonOuterClass.MapPokemon;
import POGOProtos.Map.Pokemon.WildPokemonOuterClass.WildPokemon;
import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass;
import POGOProtos.Networking.Responses.CatchPokemonResponseOuterClass.CatchPokemonResponse;
import POGOProtos.Networking.Responses.EncounterResponseOuterClass.EncounterResponse;
import POGOProtos.Networking.Responses.EncounterResponseOuterClass.EncounterResponse.Status;
import okhttp3.OkHttpClient;

public class Pokemon {
	static PokemonGo go;
	static final double SPEED = 2*1./5; // m/s
	static double parcouru = 0;

	static final double lats[] = {48.861151, 48.853580, 48.844644, 48.862068};
	static final double lons[] = {2.287344, 2.303664, 2.338218, 2.358983};

	public static void main(String[] args) {
		OkHttpClient http = new OkHttpClient();
		RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo auth = null;
		try {
			auth = new PTCLogin(http).login("CrashkillerSmurf", "reeper47");
			go = new PokemonGo(auth, http);

			go.setLocation(lats[3], lons[3], 0);
			for(int i = 0; i < lats.length; i++){
				run(lats[i], lons[i]);
			}

		} catch (LoginFailedException e) {
			// failed to login, invalid credentials or auth issue.
			e.printStackTrace();
		} 

	}
	public static void run(double lat, double lon){
		double firstLat = go.getLatitude();
		double firstLon = go.getLongitude();
		double dist = distance(lat, firstLat, lon, firstLon);
		int sections = (int) (dist / SPEED);
		double changeLat = lat - firstLat;
		double changeLon = lon - firstLon;

		for(int i = 0; i < sections; i++){
			System.out.println("Il reste " + (int)((sections - i) * SPEED) + " m.  Total parcouru: " + (int)(parcouru) + " m.");
			go.setLocation(firstLat + changeLat * sections, firstLon + changeLon * sections, 0);
			parcouru += SPEED;
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		go.setLocation(lat, lon, 0);
	}

	public static void getPokemons(Collection<MapPokemon> pokemons){
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
