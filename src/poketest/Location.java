package poketest;

public class Location {
	private double lattitude;
	private double longitude;
	
	public Location(double latitude, double longitude) {
		this.lattitude = latitude;
		this.longitude = longitude;
	}
	public double getLongitude() {
		return longitude;
	}
	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}
	public double getLattitude() {
		return lattitude;
	}
	public void setLattitude(double lattitude) {
		this.lattitude = lattitude;
	}
}
