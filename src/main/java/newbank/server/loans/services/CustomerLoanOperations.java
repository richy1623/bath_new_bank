package newbank.server.loans.services;

import java.math.BigDecimal;

import newbank.server.loans.models.Customer;
import newbank.server.loans.models.LoanRequest;

public interface CustomerLoanOperations {
	LoanRequest newLoanRequest(Customer customer, BigDecimal amount, BigDecimal interestRate);
	Boolean studyCredit(Long loanRequestId);
}
