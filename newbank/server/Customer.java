package newbank.server;

import java.util.ArrayList;

public class Customer {
	
	private ArrayList<Account> accounts;
	private int id;
	private String fullName;
	private String password;

	public Customer() {
		accounts = new ArrayList<>();
	}

	// used by REGISTER and when loading the customers file: a customer with a unique ID,
	// full name and password, and no accounts yet
	public Customer(int id, String fullName, String password) {
		this();
		this.id = id;
		this.fullName = fullName;
		this.password = password;
	}

	public int getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	// true if the attempt matches this customer's password.
	// Test customers have no password (null, or "" once loaded from the file),
	// so for them a blank attempt (just pressing Enter) matches.
	public boolean checkPassword(String attempt) {
		if(attempt == null) {
			return false;
		}
		String saved = (password == null) ? "" : password;
		return saved.equals(attempt);
	}
	
	public String accountsToString() {
		String s = "";
		for(Account a : accounts) {
			s += a.toString();
		}
		return s;
	}

	public void addAccount(Account account) {
		accounts.add(account);
	}

	// used when saving to the customers file; test customers have no password, so this can be null
	public String getPassword() {
		return password;
	}

	public ArrayList<Account> getAccounts() {
		return accounts;
	}
}
