package newbank.server.loans.models;
import java.time.LocalDateTime;

public class LoanPayment {
	private Long paymentId;
	private Double amount;
	private LocalDateTime date;
	private Account sourceAccount;
	private LoanRequest loanRequest;

	public LoanPayment(Long paymentId, Double amount, LocalDateTime date, Account sourceAccount,
			LoanRequest loanRequest) {
		this.paymentId = paymentId;
		this.amount = amount;
		this.date = date;
		this.sourceAccount = sourceAccount;
		this.loanRequest = loanRequest;
	}

	public LoanPayment(Double amount, LocalDateTime date, Account sourceAccount, LoanRequest loanRequest) {
		this.amount = amount;
		this.date = date;
		this.sourceAccount = sourceAccount;
		this.loanRequest = loanRequest;
	}

	public Long getPaymentId() {
		return paymentId;
	}

	public Double getAmount() {
		return amount;
	}

	public LocalDateTime getDate() {
		return date;
	}

	public Account getSourceAccount() {
		return sourceAccount;
	}

	public LoanRequest getLoanRequest() {
		return loanRequest;
	}

}
