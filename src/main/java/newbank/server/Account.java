package newbank.server;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class Account {

	private Long accountId;
	private String accountName;
	private BigDecimal balance;
	private String accountNumber;
	private Status status = Status.ACTIVE;
	private Customer customer;
	private ArrayList<Transaction> transactions = new ArrayList<>();

	// the constant names are what is saved in the customers file; the labels are
	// what customers see
	public enum Status {
		IN_REVIEW("InReview"), ACTIVE("Active"), INACTIVE("Inactive"), CLOSED("Closed");

		private final String label;

		Status(String label) {
			this.label = label;
		}

		public String toString() {
			return label;
		}
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

	// an account can only be closed once it is empty, and can't be reopened
	// afterwards
	public void setStatus(Status status) {
		java.util.Objects.requireNonNull(status);
		if (this.status == Status.CLOSED && status != Status.CLOSED) {
			throw new IllegalArgumentException("A closed account cannot be reopened");
		}
		if (status == Status.CLOSED && balance.signum() != 0) {
			throw new IllegalArgumentException("An account can only be closed when its balance is 0");
		}
		this.status = status;
	}

	// only Active accounts can deposit, withdraw or send a transfer
	public boolean isActive() {
		return status == Status.ACTIVE;
	}

	// Inactive accounts can still be paid by other customers, but can't send money
	public boolean canReceiveTransfer() {
		return status == Status.ACTIVE || status == Status.INACTIVE;
	}

	// puts back the status an account had before a change that couldn't be saved;
	// skips the rules in setStatus, because it only ever restores an earlier state
	void restoreStatus(Status status) {
		this.status = status;
	}

	public BigDecimal balanceAmount() {
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

	public Account(String accountName, double openingBalance, Customer customer) {
		this(accountName, openingBalance);
		this.customer = customer;
	}

	public Customer getCustomer() {
		return customer;
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

	public Long getAccountId() {
		return accountId;
	}

	public String toString() {
		return (accountName + ": " + format(balance));
	}

}
