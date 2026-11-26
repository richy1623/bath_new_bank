package newbank.server.loans.models;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LoanPayment {
	private Long paymentId;
	private BigDecimal amount;
	private LocalDateTime date;
	private Account sourceAccount;
	private LoanRequest loanRequest;

	public LoanPayment(Long paymentId, BigDecimal amount, LocalDateTime date, Account sourceAccount,
			LoanRequest loanRequest) {
		this.paymentId = paymentId;
		this.amount = Money.of(amount);
		this.date = date;
		this.sourceAccount = sourceAccount;
		this.loanRequest = loanRequest;
	}

	public LoanPayment(BigDecimal amount, LocalDateTime date, Account sourceAccount, LoanRequest loanRequest) {
		this.amount = Money.of(amount);
		this.date = date;
		this.sourceAccount = sourceAccount;
		this.loanRequest = loanRequest;
	}

	public Long getPaymentId() {
		return paymentId;
	}

	public BigDecimal getAmount() {
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
