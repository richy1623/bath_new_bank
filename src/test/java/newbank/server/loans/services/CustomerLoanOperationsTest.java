package newbank.server.loans.services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import newbank.server.loans.models.Account;
import newbank.server.loans.models.Customer;
import newbank.server.loans.models.LoanPayment;
import newbank.server.loans.models.LoanRequest;

public class CustomerLoanOperationsTest {

	private static final double DELTA = 0.001;

	private CustomerLoanOperations operator;

	private Customer alice;
	private Account aliceAccount;
	private LoanRequest aliceApprovedLoan;
	private LoanRequest aliceRejectedLoan;

	private Customer bob;
	private Account bobAccount;
	private LoanRequest bobLoan;

	private Customer carol;
	private Account carolAccount;

	@BeforeEach
	public void setUp() {
		// --- Initializing testing objects ---
		alice = new Customer(1L, "Alice Smith", "NI-AS-001", new ArrayList<>(), new ArrayList<>());
		aliceAccount = new Account(1001L, alice, 2000.00);
		alice.addAccount(aliceAccount);

		aliceApprovedLoan = new LoanRequest(101L, alice, 1000.00, 800.00, 0.05, true, true, false, new ArrayList<>());
		aliceApprovedLoan.getLoanPayments()
				.add(new LoanPayment(1L, 200.00, LocalDateTime.of(2026, 9, 1, 10, 0), aliceAccount, aliceApprovedLoan));
		aliceRejectedLoan = new LoanRequest(102L, alice, 3000.00, 0.00, 0.05, false, false, false, new ArrayList<>());

		alice.addLoanRequest(aliceApprovedLoan);
		alice.addLoanRequest(aliceRejectedLoan);

		bob = new Customer(2L, "Bob Jones", "NI-BJ-002", new ArrayList<>(), new ArrayList<>());
		bobAccount = new Account(2001L, bob, 5000.00);
		bob.addAccount(bobAccount);

		bobLoan = new LoanRequest(201L, bob, 5000.00, 4000.00, 0.04, true, true, false, new ArrayList<>());
		bob.addLoanRequest(bobLoan);

		carol = new Customer(3L, "Carol White", "NI-CW-003", new ArrayList<>(), new ArrayList<>());
		carolAccount = new Account(3001L, carol, 150.00);
		carol.addAccount(carolAccount);

		operator = new LoanService(new ArrayList<Customer>(List.of(alice, bob, carol)));
	}

	// --- newLoanRequest ---

	@Test
	public void newLoanRequestReturnsRequestWithGivenDetails() {
		LoanRequest request = request(alice, 1500.00, 0.07);

		assertNotNull(request.getLoanRequestId(), "new request id");
		assertSame(alice, request.getCustomer());
		assertEquals(1500.00, request.getRequestedAmount(), DELTA);
		assertEquals(0.07, request.getInterestRate(), DELTA);
	}

	@Test
	public void newLoanRequestIsPendingAndNotYetApprovedOrActive() {
		LoanRequest request = request(alice, 1500.00, 0.07);

		assertTrue(request.isPending(), "new request pending");
		assertFalse(request.isApproved(), "new request approved");
		assertFalse(request.isActive(), "new request active");
	}

	@Test
	public void newLoanRequestStartsWithEmptyPaymentHistory() {
		LoanRequest request = request(alice, 1500.00, 0.07);

		assertNotNull(request.getLoanPayments(), "payment history of new request");
		assertTrue(request.getLoanPayments().isEmpty());
	}

	@Test
	public void newLoanRequestIsAddedToCustomerLoans() {
		LoanRequest request = request(alice, 1500.00, 0.07);

		assertEquals(3, alice.getLoanRequests().size());
		assertTrue(alice.getLoanRequests().contains(request));
	}

	@Test
	public void newLoanRequestForCustomerWithoutLoans() {
		LoanRequest request = request(carol, 100.00, 0.10);

		assertEquals(List.of(request), carol.getLoanRequests());
		assertTrue(request.isPending());
	}

	@Test
	public void newLoanRequestDoesNotAffectOtherCustomers() {
		request(alice, 1500.00, 0.07);

		assertEquals(List.of(bobLoan), bob.getLoanRequests());
		assertTrue(carol.getLoanRequests().isEmpty());
	}

	@Test
	public void newLoanRequestDoesNotChangeExistingLoans() {
		request(alice, 1500.00, 0.07);

		assertEquals(800.00, aliceApprovedLoan.getCurrentDebt(), DELTA);
		assertTrue(aliceApprovedLoan.isActive());
		assertTrue(aliceApprovedLoan.isApproved());
		assertFalse(aliceRejectedLoan.isApproved());
		assertFalse(aliceRejectedLoan.isActive());
	}

	@Test
	public void newLoanRequestDoesNotMoveMoney() {
		request(alice, 1500.00, 0.07);

		assertEquals(2000.00, aliceAccount.getBalance(), DELTA);
	}

	@Test
	public void newLoanRequestsGetUniqueIds() {
		LoanRequest first = request(alice, 1500.00, 0.07);
		LoanRequest second = request(alice, 1500.00, 0.07);
		LoanRequest third = request(bob, 700.00, 0.03);

		assertNotEquals(first.getLoanRequestId(), second.getLoanRequestId());
		assertNotEquals(first.getLoanRequestId(), third.getLoanRequestId());
		assertNotEquals(second.getLoanRequestId(), third.getLoanRequestId());
	}

	@Test
	public void newLoanRequestIdsDoNotClashWithExistingLoans() {
		Long id = request(bob, 700.00, 0.03).getLoanRequestId();

		assertNotEquals(101L, id);
		assertNotEquals(102L, id);
		assertNotEquals(201L, id);
	}

	@Test
	public void newLoanRequestRejectsMissingCustomer() {
		assertThrows(IllegalArgumentException.class, () -> operator.newLoanRequest(null, 1500.00, 0.07));
	}

	@Test
	public void newLoanRequestRejectsMissingAmount() {
		assertThrows(IllegalArgumentException.class, () -> operator.newLoanRequest(alice, null, 0.07));

		assertEquals(2, alice.getLoanRequests().size());
	}

	@Test
	public void newLoanRequestRejectsZeroOrNegativeAmount() {
		assertThrows(IllegalArgumentException.class, () -> operator.newLoanRequest(alice, 0.00, 0.07));
		assertThrows(IllegalArgumentException.class, () -> operator.newLoanRequest(alice, -500.00, 0.07));

		assertEquals(2, alice.getLoanRequests().size());
	}

	@Test
	public void newLoanRequestRejectsMissingInterestRate() {
		assertThrows(IllegalArgumentException.class, () -> operator.newLoanRequest(alice, 1500.00, null));

		assertEquals(2, alice.getLoanRequests().size());
	}

	@Test
	public void newLoanRequestRejectsNegativeInterestRate() {
		assertThrows(IllegalArgumentException.class, () -> operator.newLoanRequest(alice, 1500.00, -0.01));

		assertEquals(2, alice.getLoanRequests().size());
	}

	// --- studyCredit ---

	@Test
	public void studyCreditReturnsADecision() {
		LoanRequest request = request(alice, 1500.00, 0.07);

		assertNotNull(operator.studyCredit(request.getLoanRequestId()), "studyCredit decision");
	}

	@Test
	public void studyCreditClearsPendingFlag() {
		LoanRequest request = request(alice, 1500.00, 0.07);

		operator.studyCredit(request.getLoanRequestId());

		assertFalse(request.isPending(), "request pending after study");
	}

	@Test
	public void studyCreditDecisionMatchesApprovedFlag() {
		LoanRequest request = request(alice, 1500.00, 0.07);

		Boolean decision = operator.studyCredit(request.getLoanRequestId());

		assertEquals(decision, request.isApproved());
	}

	@Test
	public void studyCreditRejectedRequestIsNotActive() {
		LoanRequest request = request(carol, 100000.00, 0.20);

		Boolean decision = operator.studyCredit(request.getLoanRequestId());

		if (!decision) {
			assertFalse(request.isActive(), "rejected request active");
		}
	}

	@Test
	public void studyCreditOnlyUpdatesDecision() {
		LoanRequest request = request(alice, 1500.00, 0.07);
		Long id = request.getLoanRequestId();
		Double debtBefore = request.getCurrentDebt();

		operator.studyCredit(id);

		assertEquals(id, request.getLoanRequestId());
		assertSame(alice, request.getCustomer());
		assertEquals(1500.00, request.getRequestedAmount(), DELTA);
		assertEquals(0.07, request.getInterestRate(), DELTA);
		assertEquals(debtBefore, request.getCurrentDebt(), "current debt after study");
		assertTrue(request.getLoanPayments().isEmpty());
	}

	@Test
	public void studyCreditDoesNotMoveMoney() {
		LoanRequest request = request(alice, 1500.00, 0.07);

		operator.studyCredit(request.getLoanRequestId());

		assertEquals(2000.00, aliceAccount.getBalance(), DELTA);
	}

	@Test
	public void studyCreditOnlyAffectsTheStudiedRequest() {
		LoanRequest studied = request(alice, 1500.00, 0.07);
		LoanRequest untouched = request(alice, 600.00, 0.05);

		operator.studyCredit(studied.getLoanRequestId());

		assertTrue(untouched.isPending(), "other request pending");
		assertFalse(untouched.isApproved(), "other request approved");
		assertFalse(untouched.isActive(), "other request active");
		assertTrue(aliceApprovedLoan.isActive());
		assertEquals(800.00, aliceApprovedLoan.getCurrentDebt(), DELTA);
	}

	@Test
	public void studyCreditKeepsRequestInCustomerLoans() {
		LoanRequest request = request(alice, 1500.00, 0.07);

		operator.studyCredit(request.getLoanRequestId());

		assertEquals(3, alice.getLoanRequests().size());
		assertTrue(alice.getLoanRequests().contains(request));
	}

	@Test
	public void studyCreditRejectsUnknownLoan() {
		assertThrows(IllegalArgumentException.class, () -> operator.studyCredit(999L));
	}

	@Test
	public void studyCreditRejectsMissingId() {
		assertThrows(IllegalArgumentException.class, () -> operator.studyCredit(null));
	}

	@Test
	public void studyCreditRejectsAlreadyApprovedLoan() {
		assertThrows(IllegalArgumentException.class, () -> operator.studyCredit(101L));

		assertTrue(aliceApprovedLoan.isApproved());
		assertTrue(aliceApprovedLoan.isActive());
	}

	@Test
	public void studyCreditRejectsAlreadyRejectedLoan() {
		assertThrows(IllegalArgumentException.class, () -> operator.studyCredit(102L));

		assertFalse(aliceRejectedLoan.isApproved());
		assertFalse(aliceRejectedLoan.isActive());
	}

	@Test
	public void studyCreditCannotStudyTheSameRequestTwice() {
		LoanRequest request = request(alice, 1500.00, 0.07);
		Long id = request.getLoanRequestId();

		Boolean decision = operator.studyCredit(id);

		assertThrows(IllegalArgumentException.class, () -> operator.studyCredit(id));
		assertEquals(decision, request.isApproved(), "decision kept after second study attempt");
	}

	// --- Helpers ---

	private LoanRequest request(Customer customer, Double amount, Double interestRate) {
		LoanRequest request = operator.newLoanRequest(customer, amount, interestRate);
		assertNotNull(request,
				"newLoanRequest(customer " + customer.getCustomerId() + ", " + amount + ") returned null");
		return request;
	}
}
