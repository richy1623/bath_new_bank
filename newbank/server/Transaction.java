package newbank.server;

import java.time.LocalDate;

public class Transaction {

	private final String accountType;
	private final double amount;
	private final LocalDate date;
	private final String beneficiary;

	public Transaction(String accountType, double amount, LocalDate date, String beneficiary) {
		this.accountType = accountType;
		this.amount = amount;
		this.date = date;
		this.beneficiary = beneficiary;
	}

	public String getAccountType() {
		return accountType;
	}

	public double getAmount() {
		return amount;
	}

	public LocalDate getDate() {
		return date;
	}

	public String getBeneficiary() {
		return beneficiary;
	}

	public String toString() {
		return accountType + " | " + amount + " | " + date + " | " + beneficiary;
	}
}
