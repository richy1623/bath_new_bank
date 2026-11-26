package newbank.server;

import java.util.ArrayList;
import java.util.List;

public class Account {

	private Long accountId;
	private String accountName;
	private double openingBalance;
	private Customer customer;
	private ArrayList<Transaction> transactions = new ArrayList<>();

	public Account(String accountName, double openingBalance) {
		this.accountName = accountName;
		this.openingBalance = openingBalance;
	}

	public Account(String accountName, double openingBalance, Customer customer) {
		this.accountName = accountName;
		this.openingBalance = openingBalance;
		this.customer = customer;
	}
	
	public Customer getCustomer() {
		return customer;
	}

	public String getAccountName() {
		return accountName;
	}

	public double getBalance() {
		return openingBalance;
	}

	public List<Transaction> getTransactions() {
		return java.util.Collections.unmodifiableList(transactions);
	}

	public void addTransaction(Transaction transaction) {
		transactions.add(transaction);
	}

	public Long getAccountId() {
		return accountId;
	}

	public String toString() {
		return (accountName + ": " + openingBalance);
	}

}
