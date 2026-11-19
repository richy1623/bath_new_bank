package newbank.server;

import java.time.LocalDate;

public class Transaction {

	private final double amount;
	private final LocalDate date;
	private final String beneficiary;

	public Transaction(double amount, LocalDate date, String beneficiary) {
		this.amount = amount;
		this.date = date;
		this.beneficiary = beneficiary;
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
		return amount + " | " + date + " | " + beneficiary;
	}
}
