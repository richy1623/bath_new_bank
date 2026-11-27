package newbank.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApproveAccountsTest {

	@TempDir
	Path dir;

	private final CustomerID staff = new CustomerID("Staff");
	private final CustomerID alice = new CustomerID("alice");
	private final CustomerID bob = new CustomerID("bob");

	private Path file() {
		return dir.resolve("customers.txt");
	}

	// Alice has an InReview Savings (00000101) and an Active Cheque (00000102);
	// Bob has an InReview Investment (00000201) and an Active Savings (00000202)
	// with 100 in it
	private NewBank bank() throws IOException {
		Files.writeString(file(), "1,alice,Alice,pw,Savings:0.00:00000101:IN_REVIEW;Cheque:0.00:00000102:ACTIVE\n"
				+ "2,bob,Bob,pw,Investment:0.00:00000201:IN_REVIEW;Savings:100.00:00000202:ACTIVE\n");
		return new NewBank(file().toString());
	}

	private String details(NewBank bank, CustomerID who) {
		return bank.processRequest(who, "SHOWACCOUNTDETAILS");
	}

	// ---- new accounts start as InReview ----

	@Test
	void newAccountIsPending() throws IOException {
		NewBank bank = bank();
		bank.processRequest(alice, "NEWACCOUNT Investment");
		assertEquals("00000101 | alice | Savings; 00000103 | alice | Investment; 00000201 | bob | Investment",
				bank.processRequest(staff, "PENDING"));
	}

	// ---- PENDING ----

	@Test
	void pendingListsInReviewAccountsSortedByNumber() throws IOException {
		assertEquals("00000101 | alice | Savings; 00000201 | bob | Investment",
				bank().processRequest(staff, "PENDING"));
	}

	@Test
	void pendingSaysWhenThereAreNone() throws IOException {
		NewBank bank = bank();
		bank.processRequest(staff, "APPROVE 00000101");
		bank.processRequest(staff, "REJECT 00000201");
		assertEquals("No pending accounts", bank.processRequest(staff, "PENDING"));
	}

	@Test
	void pendingTakesNoArguments() throws IOException {
		assertEquals("FAIL: Type PENDING", bank().processRequest(staff, "PENDING all"));
	}

	// ---- APPROVE ----

	@Test
	void approvedAccountIsActiveAndCanBeUsed() throws IOException {
		NewBank bank = bank();
		assertEquals("SUCCESS", bank.processRequest(staff, "APPROVE 00000101"));
		assertEquals("Savings | 00000101 | Active | 0.0; Cheque | 00000102 | Active | 0.0", details(bank, alice));
		assertEquals("SUCCESS", bank.processRequest(bob, "TRANSFER Savings 00000101 10"));
		assertEquals("00000201 | bob | Investment", bank.processRequest(staff, "PENDING"));
	}

	@Test
	void approvalSurvivesARestart() throws IOException {
		bank().processRequest(staff, "APPROVE 00000101");
		assertEquals("Savings | 00000101 | Active | 0.0; Cheque | 00000102 | Active | 0.0",
				details(new NewBank(file().toString()), alice));
	}

	// ---- REJECT ----

	@Test
	void rejectedAccountIsDeletedButTheCustomerStays() throws IOException {
		NewBank bank = bank();
		assertEquals("SUCCESS", bank.processRequest(staff, "REJECT 00000201"));
		assertEquals("Savings | 00000202 | Active | 100.0", details(bank, bob));
		assertFalse(Files.readString(file()).contains("00000201"));
		NewBank restarted = new NewBank(file().toString());
		assertNotNull(restarted.checkLogInDetails("bob", "pw"));
		assertEquals("Savings | 00000202 | Active | 100.0", details(restarted, bob));
	}

	@Test
	void customerCanApplyAgainAfterARejection() throws IOException {
		NewBank bank = bank();
		bank.processRequest(staff, "REJECT 00000201");
		assertEquals("SUCCESS", bank.processRequest(bob, "NEWACCOUNT Investment"));
		assertEquals("00000101 | alice | Savings; 00000201 | bob | Investment", bank.processRequest(staff, "PENDING"));
	}

	@Test
	void rejectingACustomersOnlyAccountLeavesNoAccounts() throws IOException {
		Files.writeString(file(), "1,carol,Carol,pw,Savings:0.00:00000101:IN_REVIEW\n");
		NewBank bank = new NewBank(file().toString());
		assertEquals("SUCCESS", bank.processRequest(staff, "REJECT 00000101"));
		CustomerID carol = new CustomerID("carol");
		assertEquals("No accounts", bank.processRequest(carol, "SHOWMYACCOUNTS"));
		assertNotNull(new NewBank(file().toString()).checkLogInDetails("carol", "pw"));
	}

	@Test
	void olderAccountWithMoneyCannotBeRejected() throws IOException {
		// saved before statuses existed, so it loads as InReview
		Files.writeString(file(), "1,carol,Carol,pw,Savings:250.00\n");
		NewBank bank = new NewBank(file().toString());
		assertEquals("FAIL: Account 00000101 still has money in it, so it cannot be rejected",
				bank.processRequest(staff, "REJECT 00000101"));
		assertEquals("Savings | 00000101 | InReview | 250.0",
				new NewBank(file().toString()).processRequest(new CustomerID("carol"), "SHOWACCOUNTDETAILS"));
		assertEquals("SUCCESS", bank.processRequest(staff, "APPROVE 00000101"));
	}

	// ---- rules for APPROVE and REJECT ----

	@ParameterizedTest
	@ValueSource(strings = {"APPROVE", "REJECT"})
	void onlyInReviewAccountsCanBeReviewed(String command) throws IOException {
		NewBank bank = bank();
		assertEquals("FAIL: Account 00000202 is Active. Only InReview accounts can be approved or rejected",
				bank.processRequest(staff, command + " 00000202"));
		assertEquals("Investment | 00000201 | InReview | 0.0; Savings | 00000202 | Active | 100.0", details(bank, bob));
	}

	@ParameterizedTest
	@ValueSource(strings = {"APPROVE", "REJECT"})
	void unknownAccountIsRejected(String command) throws IOException {
		assertEquals("FAIL: Account 99999999 not found", bank().processRequest(staff, command + " 99999999"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"APPROVE", "REJECT", "APPROVE 00000101 extra", "REJECT 00000101 extra"})
	void wrongFormatIsExplained(String request) throws IOException {
		String command = request.split(" ")[0];
		assertEquals("FAIL: Type " + command + " <AccountNumber>, for example " + command + " 00000101",
				bank().processRequest(staff, request));
	}

	@ParameterizedTest
	@ValueSource(strings = {"APPROVE 00000101", "REJECT 00000101"})
	void failedSaveChangesNothing(String request) throws IOException {
		NewBank bank = bank();
		Files.delete(file());
		Files.createDirectory(file()); // saving now fails on every OS
		assertEquals("FAIL: Your details could not be saved, please try again later",
				bank.processRequest(staff, request));
		assertEquals("Savings | 00000101 | InReview | 0.0; Cheque | 00000102 | Active | 0.0", details(bank, alice));
	}

	// ---- customers can't review accounts ----

	@ParameterizedTest
	@ValueSource(strings = {"PENDING", "APPROVE 00000101", "REJECT 00000101"})
	void customersCannotReviewAccounts(String request) throws IOException {
		NewBank bank = bank();
		assertEquals("FAIL: Only staff can review new accounts", bank.processRequest(alice, request));
		assertEquals("Savings | 00000101 | InReview | 0.0; Cheque | 00000102 | Active | 0.0", details(bank, alice));
	}
}
