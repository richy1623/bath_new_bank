package newbank.server.loans.services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import newbank.server.loans.models.Account;
import newbank.server.loans.models.Customer;
import newbank.server.loans.models.LoanPayment;
import newbank.server.loans.models.LoanRequest;

public class LoanPaymentOperationTest {

	private static final double DELTA = 0.001;

	private LoanPaymentOperation operator;

	private Customer alice;
	private Account aliceAccount;
	private LoanRequest aliceApprovedLoan;
	private LoanRequest aliceRejectedLoanOne;
	private LoanRequest aliceRejectedLoanTwo;

	private Customer bob;
	private Account bobMainAccount;
	private Account bobSecondAccount;
	private LoanRequest bobFirstLoan;
	private LoanRequest bobSecondLoan;

	@BeforeEach
	public void setUp() {
		// --- Initializing testing objects ---
		alice = new Customer(1L, "Alice Smith", "NI-AS-001", new ArrayList<>(), new ArrayList<>());
		aliceAccount = new Account(1001L, alice, 2000.00);
		alice.addAccount(aliceAccount);

		aliceApprovedLoan = new LoanRequest(101L, alice, 1000.00, 800.00, 0.05, true, true, false, new ArrayList<>());
		aliceApprovedLoan.getLoanPayments()
				.add(new LoanPayment(1L, 150.00, LocalDateTime.of(2026, 9, 1, 10, 0), aliceAccount, aliceApprovedLoan));
		aliceApprovedLoan.getLoanPayments().add(
				new LoanPayment(2L, 100.00, LocalDateTime.of(2026, 10, 1, 10, 0), aliceAccount, aliceApprovedLoan));

		aliceRejectedLoanOne = new LoanRequest(102L, alice, 3000.00, 0.00, 0.05, false, false, false,
				new ArrayList<>());
		aliceRejectedLoanTwo = new LoanRequest(103L, alice, 10000.00, 0.00, 0.08, false, false, false,
				new ArrayList<>());

		alice.addLoanRequest(aliceApprovedLoan);
		alice.addLoanRequest(aliceRejectedLoanOne);
		alice.addLoanRequest(aliceRejectedLoanTwo);

		bob = new Customer(2L, "Bob Jones", "NI-BJ-002", new ArrayList<>(), new ArrayList<>());
		bobMainAccount = new Account(2001L, bob, 5000.00);
		bobSecondAccount = new Account(2002L, bob, 300.00);
		bob.addAccount(bobMainAccount);
		bob.addAccount(bobSecondAccount);

		bobFirstLoan = new LoanRequest(201L, bob, 5000.00, 4000.00, 0.04, true, true, false, new ArrayList<>());
		bobFirstLoan.getLoanPayments()
				.add(new LoanPayment(3L, 1000.00, LocalDateTime.of(2026, 8, 15, 9, 30), bobMainAccount, bobFirstLoan));
		bobFirstLoan.getLoanPayments()
				.add(new LoanPayment(4L, 200.00, LocalDateTime.of(2026, 9, 15, 9, 30), bobSecondAccount, bobFirstLoan));

		bobSecondLoan = new LoanRequest(202L, bob, 2000.00, 2000.00, 0.10, true, true, false, new ArrayList<>());
		bobSecondLoan.getLoanPayments()
				.add(new LoanPayment(5L, 200.00, LocalDateTime.of(2026, 10, 20, 14, 0), bobMainAccount, bobSecondLoan));

		bob.addLoanRequest(bobFirstLoan);
		bob.addLoanRequest(bobSecondLoan);

		operator = new LoanService(new ArrayList<Customer>(List.of(alice, bob)));
	}

	@Test
	public void paymentValidationAcceptsAmountWithinBalance() {
		assertTrue(operator.paymentValidation(500.00, aliceAccount), "500.00 from account 1001 (balance 2000.00)");
		assertTrue(operator.paymentValidation(250.00, bobSecondAccount), "250.00 from account 2002 (balance 300.00)");
	}

	@Test
	public void paymentValidationAcceptsAmountEqualToBalance() {
		assertTrue(operator.paymentValidation(300.00, bobSecondAccount), "300.00 from account 2002 (balance 300.00)");
	}

	@Test
	public void paymentValidationRejectsAmountExceedingBalance() {
		assertFalse(operator.paymentValidation(300.01, bobSecondAccount), "300.01 from account 2002 (balance 300.00)");
		assertFalse(operator.paymentValidation(5000.00, aliceAccount), "5000.00 from account 1001 (balance 2000.00)");
	}

	@Test
	public void paymentValidationRejectsZeroOrNegativeAmount() {
		assertFalse(operator.paymentValidation(0.00, aliceAccount), "zero amount");
		assertFalse(operator.paymentValidation(-50.00, aliceAccount), "negative amount");
	}

	@Test
	public void paymentValidationRejectsMissingAmountOrAccount() {
		assertFalse(operator.paymentValidation(null, aliceAccount), "null amount");
		assertFalse(operator.paymentValidation(100.00, null), "null account");
	}

	@Test
	public void paymentValidationDoesNotChangeBalance() {
		operator.paymentValidation(500.00, aliceAccount);
		assertEquals(2000.00, aliceAccount.getBalance(), DELTA);
	}

	@Test
	public void activeLoanIsTrueForApprovedLoans() {
		assertTrue(operator.activeLoan(101L), "loan 101 active");
		assertTrue(operator.activeLoan(201L), "loan 201 active");
		assertTrue(operator.activeLoan(202L), "loan 202 active");
	}

	@Test
	public void activeLoanIsFalseForRejectedLoans() {
		assertFalse(operator.activeLoan(102L), "loan 102 active");
		assertFalse(operator.activeLoan(103L), "loan 103 active");
	}

	@Test
	public void activeLoanIsFalseForUnknownLoan() {
		assertFalse(operator.activeLoan(999L), "unknown loan 999 active");
	}

	@Test
	public void getLoanRequestReturnsMatchingApprovedLoan() {
		LoanRequest loan = loan(101L);

		assertEquals(101L, loan.getLoanRequestId());
		assertEquals(1L, loan.getCustomer().getCustomerId());
		assertEquals(1000.00, loan.getRequestedAmount(), DELTA);
		assertEquals(0.05, loan.getInterestRate(), DELTA);
		assertTrue(loan.isApproved());
	}

	@Test
	public void getLoanRequestReturnsRejectedLoansToo() {
		LoanRequest loan = loan(103L);

		assertEquals(103L, loan.getLoanRequestId());
		assertFalse(loan.isApproved());
		assertFalse(loan.isActive());
	}

	@Test
	public void getLoanRequestFindsLoansAcrossCustomers() {
		assertEquals(2L, loan(201L).getCustomer().getCustomerId());
		assertEquals(2L, loan(202L).getCustomer().getCustomerId());
	}

	@Test
	public void getLoanRequestReturnsNullForUnknownLoan() {
		assertNull(operator.getLoanRequest(999L));
	}

	@Test
	public void makePaymentReducesDebtAndDebitsAccount() {
		LoanRequest updated = pay(101L, 200.00, aliceAccount).getLoanRequest();

		assertEquals(101L, updated.getLoanRequestId());
		assertEquals(600.00, updated.getCurrentDebt(), DELTA, "debt on returned loan 101");
		assertEquals(1800.00, aliceAccount.getBalance(), DELTA);
	}

	@Test
	public void makePaymentRecordsPaymentInHistory() {
		pay(101L, 200.00, aliceAccount);

        LoanRequest updated = operator.getLoanRequest(101L);

		List<LoanPayment> history =updated.getLoanPayments();
		assertEquals(3, history.size());

		LoanPayment latest = history.get(history.size() - 1);
		assertEquals(6L, latest.getPaymentId(), "id of payment recorded in history");
		assertEquals(200.00, latest.getAmount(), DELTA);
		assertEquals(1001L, latest.getSourceAccount().getAccountId());
		assertEquals(101L, latest.getLoanRequest().getLoanRequestId());
		assertNotNull(latest.getDate());
	}

	@Test
	public void makePaymentFromSecondAccountOnlyDebitsThatAccount() {
		LoanRequest updated = pay(202L, 300.00, bobSecondAccount).getLoanRequest();

		assertEquals(1700.00, updated.getCurrentDebt(), DELTA, "debt on returned loan 202");
		assertEquals(0.00, bobSecondAccount.getBalance(), DELTA);
		assertEquals(5000.00, bobMainAccount.getBalance(), DELTA);
	}

	@Test
	public void consecutivePaymentsAccumulate() {
		LoanPayment first = pay(201L, 1000.00, bobMainAccount);
		LoanPayment second = pay(201L, 250.00, bobSecondAccount);
		LoanRequest updated = second.getLoanRequest();

		assertEquals(6L, first.getPaymentId(), "id of first new payment");
		assertEquals(7L, second.getPaymentId(), "id of second new payment");

		assertEquals(2750.00, updated.getCurrentDebt(), DELTA, "debt on returned loan 201");
		assertEquals(4000.00, bobMainAccount.getBalance(), DELTA);
		assertEquals(50.00, bobSecondAccount.getBalance(), DELTA);
		assertEquals(4, updated.getLoanPayments().size());
	}

	@Test
	public void makePaymentForFullDebtClosesTheLoan() {
		LoanRequest updated = pay(202L, 2000.00, bobMainAccount).getLoanRequest();

		assertEquals(0.00, updated.getCurrentDebt(), DELTA, "debt on returned loan 202");
		assertFalse(operator.activeLoan(202L), "loan 202 active after being paid off");
	}

	@Test
	public void makePaymentRejectsAmountExceedingAccountBalance() {
		assertThrows(IllegalArgumentException.class, () -> operator.makePayment(202L, 500.00, bobSecondAccount));

		assertEquals(300.00, bobSecondAccount.getBalance(), DELTA);
	}

	@Test
	public void makePaymentRejectsAmountExceedingOutstandingDebt() {
		assertThrows(IllegalArgumentException.class, () -> operator.makePayment(101L, 900.00, aliceAccount));

		assertEquals(2000.00, aliceAccount.getBalance(), DELTA);
	}

	@Test
	public void makePaymentRejectsZeroOrNegativeAmount() {
		assertThrows(IllegalArgumentException.class, () -> operator.makePayment(101L, 0.00, aliceAccount));
		assertThrows(IllegalArgumentException.class, () -> operator.makePayment(101L, -100.00, aliceAccount));

		assertEquals(2000.00, aliceAccount.getBalance(), DELTA);
	}

	@Test
	public void makePaymentRejectsPaymentOnRejectedLoan() {
		assertThrows(IllegalArgumentException.class, () -> operator.makePayment(102L, 100.00, aliceAccount));

		assertEquals(2000.00, aliceAccount.getBalance(), DELTA);
	}

	@Test
	public void makePaymentRejectsUnknownLoan() {
		assertThrows(IllegalArgumentException.class, () -> operator.makePayment(999L, 100.00, aliceAccount));

		assertEquals(2000.00, aliceAccount.getBalance(), DELTA);
	}

	@Test
	public void makePaymentAssignsNextPaymentId() {
		LoanPayment payment = pay(101L, 200.00, aliceAccount);

		assertNotNull(payment.getPaymentId(), "payment id");
		assertEquals(6L, payment.getPaymentId(), "fixture holds payments 1-5, so the next id is 6");
	}

	@Test
	public void makePaymentIdsAreSequentialAcrossLoansAndCustomers() {
		LoanPayment alicePayment = pay(101L, 100.00, aliceAccount);
		LoanPayment bobPayment = pay(202L, 100.00, bobMainAccount);
		LoanPayment bobOtherLoanPayment = pay(201L, 100.00, bobSecondAccount);

		assertEquals(6L, alicePayment.getPaymentId());
		assertEquals(7L, bobPayment.getPaymentId());
		assertEquals(8L, bobOtherLoanPayment.getPaymentId());
	}

	@Test
	public void makePaymentIdsAreUniqueAcrossAllLoans() {
		pay(101L, 100.00, aliceAccount);
		pay(201L, 100.00, bobMainAccount);
		pay(202L, 100.00, bobMainAccount);

		List<Long> allIds = new ArrayList<>();
		for (Long loanId : List.of(101L, 102L, 103L, 201L, 202L)) {
			allIds.addAll(paymentIds(history(loanId)));
		}

		assertEquals(8, allIds.size());
		assertEquals(allIds.size(), allIds.stream().distinct().count(), "duplicate payment ids: " + allIds);
		assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), allIds.stream().sorted().collect(Collectors.toList()));
	}

	@Test
	public void rejectedPaymentDoesNotConsumeAnId() {
		assertThrows(IllegalArgumentException.class, () -> operator.makePayment(202L, 500.00, bobSecondAccount));
		assertThrows(IllegalArgumentException.class, () -> operator.makePayment(102L, 100.00, aliceAccount));

		assertEquals(6L, pay(101L, 100.00, aliceAccount).getPaymentId());
	}

	@Test
	public void paymentsHistoryForAliceApprovedLoanHasTwoPaymentsInOrder() {
		List<LoanPayment> history = history(101L);

		assertEquals(2, history.size());
		assertEquals(List.of(1L, 2L), paymentIds(history));
		assertEquals(150.00, history.get(0).getAmount(), DELTA);
		assertEquals(100.00, history.get(1).getAmount(), DELTA);
		assertTrue(history.stream().allMatch(p -> p.getSourceAccount().getAccountId() == 1001L));
	}

	@Test
	public void paymentsHistoryForBobLoansHasThreePaymentsInTotal() {
		List<LoanPayment> first = history(201L);
		List<LoanPayment> second = history(202L);

		assertEquals(2, first.size());
		assertEquals(1, second.size());
		assertEquals(3, first.size() + second.size());
		assertEquals(List.of(3L, 4L), paymentIds(first));
		assertEquals(List.of(5L), paymentIds(second));

		assertEquals(2001L, first.get(0).getSourceAccount().getAccountId());
		assertEquals(2002L, first.get(1).getSourceAccount().getAccountId());
		assertEquals(2001L, second.get(0).getSourceAccount().getAccountId());
	}

	@Test
	public void paymentsHistoryForRejectedLoansIsEmpty() {
		assertTrue(history(102L).isEmpty());
		assertTrue(history(103L).isEmpty());
	}

	@Test
	public void activeLoansForBobIncludesBothApprovedLoans() {
		assertEquals(List.of(201L, 202L), activeLoanIds(bob));
	}

	@Test
	public void activeLoansForCustomerWithoutLoansIsEmpty() {
		Customer carol = new Customer(3L, "Carol White", "NI-CW-003", new ArrayList<>(), new ArrayList<>());

		assertTrue(activeLoanIds(carol).isEmpty());
	}

	// --- Helpers ---

	private LoanRequest loan(Long loanRequestId) {
		LoanRequest loan = operator.getLoanRequest(loanRequestId);
		assertNotNull(loan, "getLoanRequest(" + loanRequestId + ") returned null");
		return loan;
	}

	private LoanPayment pay(Long loanRequestId, Double amount, Account sourceAccount) {
		LoanPayment payment = operator.makePayment(loanRequestId, amount, sourceAccount);
		assertNotNull(payment, "makePayment(" + loanRequestId + ", " + amount + ") returned null");
		return payment;
	}

	private List<LoanPayment> history(Long loanRequestId) {
		List<LoanPayment> history = operator.getPaymentsHistory(loanRequestId);
		assertNotNull(history, "getPaymentsHistory(" + loanRequestId + ") returned null");
		return history;
	}

	private List<Long> paymentIds(List<LoanPayment> payments) {
		return payments.stream().map(LoanPayment::getPaymentId).collect(Collectors.toList());
	}

	private List<Long> activeLoanIds(Customer customer) {
		List<LoanRequest> loans = operator.getActiveLoans(customer);
		assertNotNull(loans, "getActiveLoans(customer " + customer.getCustomerId() + ") returned null");
		return loans.stream().map(LoanRequest::getLoanRequestId).sorted().collect(Collectors.toList());
	}
}
