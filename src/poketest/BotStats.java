package poketest;

import java.util.TimerTask;

public class BotStats extends TimerTask{
	
	private CustomLogger logger;
	
	private long xpStart;
	private long currentXp;
	private int lvl;
	private int pokemonTransfered 	= 0;
	private int pokemonCatched		= 0;
	private int pokemonEvolved		= 0;
	private int pokestopVisited 	= 0;
	private int xpPerHour 			= 0;
	private long startTime;
	
	private int[] requiredXP = { 0, 1000, 3000, 6000, 10000, 15000, 21000, 28000, 36000, 45000, 55000, 65000, 75000,
			85000, 100000, 120000, 140000, 160000, 185000, 210000, 260000, 335000, 435000, 560000, 710000, 900000, 1100000,
			1350000, 1650000, 2000000, 2500000, 3000000, 3750000, 4750000, 6000000, 7500000, 9500000, 12000000, 15000000, 20000000 };
	
	public BotStats(CustomLogger logger){
		this.logger = logger;
		this.xpStart = this.currentXp = 0;
		this.lvl = -1;
		this.startTime = System.currentTimeMillis();
	}

	public void updateStat(int lvl, long xp){
		this.currentXp = xp;
		this.lvl = lvl;
		this.xpPerHour = (int) ((this.currentXp - this.xpStart) / ((System.currentTimeMillis() - startTime) * 1.f / (1000 * 60 * 60)));
	}
	
	public void showStats(){
		int nextXP = 0;
		int curLevelXP = (int)this.currentXp;
		int ratio = 0;
		if(this.lvl > 0){
			nextXP = requiredXP[this.lvl] - requiredXP[this.lvl - 1];
			curLevelXP = (int)this.currentXp - requiredXP[this.lvl - 1];
			ratio = (int) ((double)curLevelXP / (double)nextXP * 100.0);
		}

		logger.important("----STATS----");
		logger.important(String.format("Account lvl %d : %d/%d (%d%%)", this.lvl, curLevelXP, nextXP, ratio));
		logger.important("XP Earned: " + (this.currentXp - this.xpStart));
		logger.important("Pokestop visited: " + this.pokestopVisited);
		logger.important("Pokemon catched: " + this.pokemonCatched);
		logger.important("Pokemon transfered: " + this.pokemonTransfered);
		logger.important("Pokemon evolved: " + this.pokemonEvolved);
		logger.important("XP per hour: " + this.xpPerHour);
		logger.important("-------------");
	}
	
	public void showStats(int lvl, long xp) {
		updateStat(lvl, xp);
		showStats();
	}
	
	public int addPokemonTransfered(){
		return pokemonTransfered++;
	}
	
	public int addPokemonCatched(){
		return pokemonCatched++;
	}
	
	public int addPokemonEvolved(){
		return pokemonEvolved++;
	}
	
	public int addPokestopVisited(){
		return pokestopVisited++;
	}
	
	public void setXpStart(long xpStart) {
		this.xpStart = xpStart;
	}
	
	public long getXpStart() {
		return xpStart;
	}

	@Override
	public void run() {
		showStats(this.lvl, this.currentXp);
	}
}
