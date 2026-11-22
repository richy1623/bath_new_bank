package newbank.server;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class Account {

	private String accountName;
	private BigDecimal balance;
	private String accountNumber;
	private Status status = Status.ACTIVE;
	private ArrayList<Transaction> transactions = new ArrayList<>();

	public enum Status {
		ACTIVE, INACTIVE, CLOSED
	}

	public Account(String accountName, double openingBalance) {
		this(accountName, BigDecimal.valueOf(openingBalance).setScale(2, RoundingMode.HALF_UP));
	}

	Account(String accountName, BigDecimal openingBalance) {
		this.accountName = accountName;
		if (openingBalance.signum() < 0) {
			throw new IllegalArgumentException("Balance cannot be negative");
		}
		this.balance = openingBalance.setScale(2, RoundingMode.UNNECESSARY);
	}

	// plain decimal text with at least one decimal place, e.g. 0.0, 100.1, 12.34
	static String format(BigDecimal amount) {
		BigDecimal shown = amount.stripTrailingZeros();
		if (shown.scale() < 1) {
			shown = shown.setScale(1);
		}
		return shown.toPlainString();
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	void setAccountNumber(String accountNumber) {
		if (!accountNumber.matches("[0-9]+")) {
			throw new IllegalArgumentException("Account number must contain digits");
		}
		this.accountNumber = accountNumber;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = java.util.Objects.requireNonNull(status);
	}

	BigDecimal balanceAmount() {
		return balance;
	}

	void restore(BigDecimal balance, int transactionCount) {
		this.balance = balance;
		transactions.subList(transactionCount, transactions.size()).clear();
	}

	void recordTransfer(BigDecimal amount, java.time.LocalDate date, String counterparty) {
		balance = balance.add(amount);
		transactions.add(new Transaction(amount, date, counterparty));
	}

	public String getAccountName() {
		return accountName;
	}

	public double getBalance() {
		return balance.doubleValue();
	}

	public List<Transaction> getTransactions() {
		return java.util.Collections.unmodifiableList(transactions);
	}

	public void addTransaction(Transaction transaction) {
		transactions.add(transaction);
	}

	public String toString() {
		return (accountName + ": " + format(balance));
	}

}
