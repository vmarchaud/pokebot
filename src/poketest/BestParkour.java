package poketest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jgrapht.alg.HamiltonianCycle;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import com.pokegoapi.api.map.fort.Pokestop;

public class BestParkour {

	public static List<Location> getBestParkour(List<Location> locations, Location start){
		locations.add(start);
		
		SimpleWeightedGraph<Location, DefaultWeightedEdge> graph = new SimpleWeightedGraph<Location, DefaultWeightedEdge>(DefaultWeightedEdge.class);
		
		for(int i = 0; i < locations.size(); i++){
			graph.addVertex(locations.get(i));
		}
		for(int i = 0; i < locations.size(); i++){
			for(int j = i + 1; j < locations.size(); j++){
				graph.setEdgeWeight(graph.addEdge(locations.get(i), locations.get(j)), distance(locations.get(i), locations.get(j)));
			}
		}

		List<Location> result = HamiltonianCycle.getApproximateOptimalForCompleteGraph(graph);
		
		return result;
	}
	
	public static double getTotalParkour(List<Location> locations){
		double result = 0;
		for(int i = 0; i < locations.size() - 1; i++){
			result += distance(locations.get(i).getLattitude(), locations.get(i + 1).getLattitude(), locations.get(i).getLongitude(), locations.get(i + 1).getLongitude());
		}
		return result;
	}
	
	public static List<Location> buildLocationArrayFromPokestops(Collection<Pokestop> pokestops){
		List<Location> locs = new ArrayList<Location>();
		for(Pokestop pokestop : pokestops){
			locs.add(new Location(pokestop.getLatitude(), pokestop.getLongitude()));
		}
		return locs;
	}

	public static double distance(Location loc1, Location loc2) {
		return distance(loc1.getLattitude(), loc2.getLattitude(), loc1.getLongitude(), loc2.getLongitude());
	}
	
	public static double distance(double lat1, double lat2, double lon1, double lon2) {

		Double latDistance = Math.toRadians(lat2 - lat1);
		Double lonDistance = Math.toRadians(lon2 - lon1);
		Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
		
		return Math.sqrt(Math.pow(6371 * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))) * 1000, 2));
	}

	public static Collection<Pokestop> buildPokestopCollection(List<Location> bestParkour, Collection<Pokestop> pokestops) {
		Collection<Pokestop> resultPokestop = new ArrayList<Pokestop>();
		for(Location loc : bestParkour){
			for(Pokestop pokestop : pokestops){
				if(pokestop.getLongitude() == loc.getLongitude() && pokestop.getLatitude() == loc.getLattitude()){
					resultPokestop.add(pokestop);
					break;
				}
			}
		}
		return resultPokestop;
	}
}
