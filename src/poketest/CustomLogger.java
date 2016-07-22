package poketest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CustomLogger {
	
	private Account 			account;
	
	public CustomLogger(Account account) {
		this.account = account;
	}
	
	public void log(String input) {
		if (!account.isLoggingEnabled()) return ;
		
		String log = String.format("[%s] on [%s] : %s", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm:ss"))
				, account.getUsername(), input);
		System.out.println(log);
	}
	
	public void important(String input) {
		String log = String.format("[%s] on [%s] : %s", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm:ss"))
				, account.getUsername(), input);
		System.out.println(log);
	}
}
