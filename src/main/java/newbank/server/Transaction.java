package newbank.server;

import java.time.LocalDate;
import java.math.BigDecimal;

public class Transaction {

	private final BigDecimal amount;
	private final LocalDate date;
	private final String beneficiary;

	public Transaction(double amount, LocalDate date, String beneficiary) {
		this(BigDecimal.valueOf(amount), date, beneficiary);
	}

	public Transaction(BigDecimal amount, LocalDate date, String beneficiary) {
		this.amount = amount;
		this.date = date;
		this.beneficiary = beneficiary;
	}

	public double getAmount() {
		return amount.doubleValue();
	}

	BigDecimal amountValue() {
		return amount;
	}

	public LocalDate getDate() {
		return date;
	}

	public String getBeneficiary() {
		return beneficiary;
	}

	public String toString() {
		return Account.format(amount) + " | " + date + " | " + beneficiary;
	}
}
