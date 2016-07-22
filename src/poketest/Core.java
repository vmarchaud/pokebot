package poketest;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.auth.GoogleLogin;
import com.pokegoapi.auth.PTCLogin;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.sun.net.ssl.internal.ssl.Provider;

import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo;
import okhttp3.OkHttpClient;
import poketest.CustomConfig.EnumProvider;

public class Core {
	
	private static Random rand = new Random();
	
	public static void main(String[] args) throws JsonIOException, JsonSyntaxException, FileNotFoundException {
		Gson gson = new GsonBuilder().create();
		CustomConfig config = gson.fromJson(new JsonReader(new FileReader("config.json")), CustomConfig.class);
		OkHttpClient http = new OkHttpClient();
		
		while(true) {
			try {
				AuthInfo auth = null;
				if (config.getProvider() == EnumProvider.GOOGLE) 
					auth = new GoogleLogin(http).login("", "");
				else 
					auth = new PTCLogin(http).login(config.getUsername(), config.getPassword());
				
				
				PokemonGo go = new PokemonGo(auth, http);
				System.out.println("Logged into pokemon go with fresh instance");
				Location location = config.getSpawns().get(rand.nextInt(config.getSpawns().size()));
				System.out.println("Location choosen : " + gson.toJson(location));
				go.setLocation(location.getLattitude(), location.getLongitude(), 0);

				System.out.println("Starting bot ..");
				
				new PokeBot(go, true);
				
				Thread.sleep(60 * 1000);
			} catch (LoginFailedException | RemoteServerException | InterruptedException e) {
				// failed to login, invalid credentials or auth issue.
				e.printStackTrace();
			} 
		}
	}
}
