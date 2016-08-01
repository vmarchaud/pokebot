package poketest;

public class BotStats {
	
	private long xpStart;
	private long currentXp;
	private int pokemonTransfered 	= 0;
	private int pokemonCatched		= 0;
	private int pokemonEvolved		= 0;
	private int pokestopVisited 	= 0;
	private int lvl;
	
	private int[] requiredXP = { 0, 1000, 3000, 6000, 10000, 15000, 21000, 28000, 36000, 45000, 55000, 65000, 75000,
			85000, 100000, 120000, 140000, 160000, 185000, 210000, 260000, 335000, 435000, 560000, 710000, 900000, 1100000,
			1350000, 1650000, 2000000, 2500000, 3000000, 3750000, 4750000, 6000000, 7500000, 9500000, 12000000, 15000000, 20000000 };
	
	public BotStats(long currentXp, int lvl){
		this.xpStart = this.currentXp = currentXp;
		this.lvl = lvl;
	}

	public void updateStat(int lvl, long xp){
		this.currentXp = xp;
		this.lvl = lvl;
	}
	
	public void showStats(CustomLogger logger){
		int nextXP = requiredXP[this.lvl] - requiredXP[this.lvl - 1];
		int curLevelXP = (int)this.currentXp - requiredXP[this.lvl - 1];
		int ratio = (int) ((double)curLevelXP / (double)nextXP * 100.0);

		logger.important("----STATS----");
		logger.important(String.format("Account lvl %d : %d/%d (%d%%)", this.lvl, curLevelXP, nextXP, ratio));
		logger.important("XP Earned: " + (this.currentXp - this.xpStart));
		logger.important("Pokestop visited: " + this.pokestopVisited);
		logger.important("Pokemon catched: " + this.pokemonCatched);
		logger.important("Pokemon transfered: " + this.pokemonTransfered);
		logger.important("Pokemon evolved: " + this.pokemonEvolved);
		logger.important("--------------");
	}
	
	public void showStats(CustomLogger logger, int lvl, long xp) {
		updateStat(lvl, xp);
		showStats(logger);
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
}
