package newbank.server.loans.services;

import java.math.BigDecimal;
import java.util.List;

import newbank.server.loans.models.Customer;
import newbank.server.loans.models.Account;
import newbank.server.loans.models.LoanRequest;
import newbank.server.loans.models.LoanPayment;

public interface LoanPaymentOperation {
	Boolean paymentValidation(BigDecimal amount, Account sourceAccount);
	Boolean activeLoan(Long loanRequestId);
	LoanRequest getLoanRequest(Long loanRequestId);
	LoanPayment makePayment(Long loanRequestId, BigDecimal amount, Account sourceAccount);
	List<LoanPayment> getPaymentsHistory(Long loanRequestId);
	List<LoanRequest> getActiveLoans(Customer customer);
	BigDecimal getCurrentLoanDebt(Long loanRequestId);
}
