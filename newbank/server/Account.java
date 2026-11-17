package newbank.server;

import java.util.ArrayList;
import java.util.List;

public class Account {
	
	private String accountName;
	private double openingBalance;
	private ArrayList<Transaction> transactions = new ArrayList<>();

	public Account(String accountName, double openingBalance) {
		this.accountName = accountName;
		this.openingBalance = openingBalance;
	}
	
	public String getAccountName() {
		return accountName;
	}

	public double getBalance() {
		return openingBalance;
	}

	public List<Transaction> getTransactions() {
		return transactions;
	}

	public void addTransaction(Transaction transaction) {
		transactions.add(transaction);
	}

	public String toString() {
		return (accountName + ": " + openingBalance);
	}

}
