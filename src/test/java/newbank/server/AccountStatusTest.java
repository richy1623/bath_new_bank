package newbank.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AccountStatusTest {

	@TempDir
	Path dir;

	private final CustomerID alice = new CustomerID("alice");
	private final CustomerID bob = new CustomerID("bob");

	private Path file() {
		return dir.resolve("customers.txt");
	}

	// Alice's Savings (00000101) and Bob's Savings (00000201), with these statuses.
	// Closed accounts must be empty, so they start with 0.
	private NewBank bank(Account.Status aliceStatus, Account.Status bobStatus) throws IOException {
		HashMap<String, Customer> customers = new HashMap<>();
		customers.put("alice", customer(1, aliceStatus));
		customers.put("bob", customer(2, bobStatus));
		new CustomerDatabase(file().toString()).save(customers);
		return new NewBank(file().toString());
	}

	private Customer customer(int id, Account.Status status) {
		Customer customer = new Customer(id, "Name", "pw");
		Account savings = new Account("Savings", status == Account.Status.CLOSED ? 0.0 : 100.0);
		savings.setStatus(status);
		customer.addAccount(savings);
		return customer;
	}

	private String balances(NewBank bank) {
		return bank.processRequest(alice, "SHOWMYACCOUNTS") + " / " + bank.processRequest(bob, "SHOWMYACCOUNTS");
	}

	// ---- the four statuses ----

	@Test
	void statusesAreShownAsInTheTicket() {
		assertEquals("InReview", Account.Status.IN_REVIEW.toString());
		assertEquals("Active", Account.Status.ACTIVE.toString());
		assertEquals("Inactive", Account.Status.INACTIVE.toString());
		assertEquals("Closed", Account.Status.CLOSED.toString());
		assertEquals(4, Account.Status.values().length);
	}

	@ParameterizedTest
	@EnumSource(Account.Status.class)
	void everyStatusSurvivesARestart(Account.Status status) throws IOException {
		NewBank bank = bank(status, Account.Status.ACTIVE);
		String amount = status == Account.Status.CLOSED ? "0.0" : "100.0";
		String expected = "Savings | 00000101 | " + status + " | " + amount;
		assertEquals(expected, bank.processRequest(alice, "SHOWACCOUNTDETAILS"));
		assertEquals(status,
				new CustomerDatabase(file().toString()).load().get("alice").getAccount("Savings").getStatus());
	}

	@Test
	void newAccountsStartInReviewAndStayInReviewAfterARestart() {
		NewBank bank = new NewBank(file().toString());
		bank.registerCustomer("Alice", "alice", "pw");
		assertEquals("SUCCESS", bank.processRequest(alice, "NEWACCOUNT Savings"));
		String expected = "Savings | 00000501 | InReview | 0.0";
		assertEquals(expected, bank.processRequest(alice, "SHOWACCOUNTDETAILS"));
		assertEquals(expected, new NewBank(file().toString()).processRequest(alice, "SHOWACCOUNTDETAILS"));
	}

	@Test
	void existingAccountsWithoutAStatusLoadAsInReview() throws IOException {
		Files.writeString(file(), "1,alice,Alice,pw,Savings:100.0\n");
		Account savings = new CustomerDatabase(file().toString()).load().get("alice").getAccount("Savings");
		assertEquals(Account.Status.IN_REVIEW, savings.getStatus());
	}

	// ---- sending and receiving ----

	@ParameterizedTest
	@EnumSource(value = Account.Status.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
	void onlyActiveAccountsCanSend(Account.Status status) throws IOException {
		NewBank bank = bank(status, Account.Status.ACTIVE);
		String before = balances(bank);
		String disk = Files.readString(file());
		assertEquals("FAIL: Your Savings account is " + status + ". Only Active accounts can send money",
				bank.processRequest(alice, "TRANSFER Savings 00000201 1"));
		assertEquals(before, balances(bank));
		assertEquals(disk, Files.readString(file()));
	}

	@ParameterizedTest
	@EnumSource(value = Account.Status.class, names = {"ACTIVE", "INACTIVE"})
	void activeAndInactiveAccountsCanReceive(Account.Status status) throws IOException {
		NewBank bank = bank(Account.Status.ACTIVE, status);
		assertEquals("SUCCESS", bank.processRequest(alice, "TRANSFER Savings 00000201 25.50"));
		assertEquals("Savings: 74.5 / Savings: 125.5", balances(bank));
		assertEquals("Savings | 00000201 | " + status + " | 125.5", bank.processRequest(bob, "SHOWACCOUNTDETAILS"));
	}

	@ParameterizedTest
	@EnumSource(value = Account.Status.class, names = {"IN_REVIEW", "CLOSED"})
	void inReviewAndClosedAccountsCannotReceive(Account.Status status) throws IOException {
		NewBank bank = bank(Account.Status.ACTIVE, status);
		String before = balances(bank);
		String disk = Files.readString(file());
		assertEquals("FAIL: The receiving account cannot receive money",
				bank.processRequest(alice, "TRANSFER Savings 00000201 1"));
		assertEquals(before, balances(bank));
		assertEquals(disk, Files.readString(file()));
	}

	// ---- closing ----

	@Test
	void accountWithMoneyCannotBeClosed() {
		Account account = new Account("Savings", 0.01);
		assertThrows(IllegalArgumentException.class, () -> account.setStatus(Account.Status.CLOSED));
		assertEquals(Account.Status.ACTIVE, account.getStatus());
	}

	@ParameterizedTest
	@EnumSource(Account.Status.class)
	void emptyAccountCanBeClosedFromAnyStatus(Account.Status status) {
		Account account = new Account("Savings", 0.0);
		account.setStatus(status);
		account.setStatus(Account.Status.CLOSED);
		assertEquals(Account.Status.CLOSED, account.getStatus());
	}

	@ParameterizedTest
	@EnumSource(value = Account.Status.class, names = "CLOSED", mode = EnumSource.Mode.EXCLUDE)
	void closedAccountCannotBeReopened(Account.Status status) throws IOException {
		bank(Account.Status.CLOSED, Account.Status.ACTIVE);
		// loaded from the file, so this also shows that closing lasts after a restart
		Account account = new CustomerDatabase(file().toString()).load().get("alice").getAccount("Savings");
		assertThrows(IllegalArgumentException.class, () -> account.setStatus(status));
		assertEquals(Account.Status.CLOSED, account.getStatus());
	}

	// ---- opening a type again after closing it ----

	private NewBank bankFromFile(String aliceAccounts) throws IOException {
		Files.writeString(file(),
				"1,alice,Alice,pw," + aliceAccounts + "\n2,bob,Bob,pw,Savings:100.00:00000201:ACTIVE\n");
		return new NewBank(file().toString());
	}

	@Test
	void closedTypeCanBeOpenedAgainWithANewNumber() throws IOException {
		NewBank bank = bankFromFile("Savings:0.00:00000101:CLOSED");
		assertEquals("SUCCESS", bank.processRequest(alice, "NEWACCOUNT Savings"));
		String expected = "Savings | 00000101 | Closed | 0.0; Savings | 00000102 | InReview | 0.0";
		assertEquals(expected, bank.processRequest(alice, "SHOWACCOUNTDETAILS"));
		assertEquals(expected, new NewBank(file().toString()).processRequest(alice, "SHOWACCOUNTDETAILS"));
		assertEquals("FAIL: You already have a Savings account", bank.processRequest(alice, "NEWACCOUNT Savings"));
	}

	@ParameterizedTest
	@EnumSource(value = Account.Status.class, names = "CLOSED", mode = EnumSource.Mode.EXCLUDE)
	void anyOpenAccountStillBlocksASecondOne(Account.Status status) throws IOException {
		NewBank bank = bankFromFile("Savings:0.00:00000101:" + status.name());
		assertEquals("FAIL: You already have a Savings account", bank.processRequest(alice, "NEWACCOUNT Savings"));
	}

	@Test
	void commandsUseTheOpenAccountWhicheverComesFirst() throws IOException {
		String open = "Savings:50.00:00000102:ACTIVE#2026-11-20~50.0~New";
		String closed = "Savings:0.00:00000101:CLOSED#2026-11-01~0.0~Old";
		for (String accounts : new String[]{closed + ";" + open, open + ";" + closed}) {
			NewBank bank = bankFromFile(accounts);
			assertEquals("50.0 | 2026-11-20 | New", bank.processRequest(alice, "SHOWACTIVITY Savings"));
			assertEquals("SUCCESS", bank.processRequest(alice, "TRANSFER Savings 00000201 10"));
			assertEquals("Savings: 110.0", bank.processRequest(bob, "SHOWMYACCOUNTS"));
			assertEquals("FAIL: The receiving account cannot receive money",
					bank.processRequest(bob, "TRANSFER Savings 00000101 1"));
			assertEquals("SUCCESS", bank.processRequest(bob, "TRANSFER Savings 00000102 1"));
		}
	}

	@Test
	void latestClosedAccountIsShownWhenNoneIsOpen() throws IOException {
		NewBank bank = bankFromFile("Savings:0.00:00000101:CLOSED#2026-11-01~0.0~First;"
				+ "Savings:0.00:00000102:CLOSED#2026-11-02~0.0~Second");
		assertEquals("0.0 | 2026-11-02 | Second", bank.processRequest(alice, "SHOWACTIVITY Savings"));
		assertEquals("FAIL: Your Savings account is Closed. Only Active accounts can send money",
				bank.processRequest(alice, "TRANSFER Savings 00000201 1"));
	}

	// ---- SHOWMYACCOUNTS ----

	@Test
	void closedAccountsAreHiddenFromShowMyAccounts() throws IOException {
		NewBank bank = bankFromFile("Savings:0.00:00000101:CLOSED;Cheque:5.00:00000102:ACTIVE;"
				+ "Investment:0.00:00000103:CLOSED;Savings:7.00:00000104:ACTIVE");
		assertEquals("Cheque: 5.0, Savings: 7.0", bank.processRequest(alice, "SHOWMYACCOUNTS"));
		assertEquals(
				"Savings | 00000101 | Closed | 0.0; Cheque | 00000102 | Active | 5.0; "
						+ "Investment | 00000103 | Closed | 0.0; Savings | 00000104 | Active | 7.0",
				bank.processRequest(alice, "SHOWACCOUNTDETAILS"));
	}

	@Test
	void showMyAccountsSaysNoAccountsWhenEveryAccountIsClosed() throws IOException {
		NewBank bank = bankFromFile("Savings:0.00:00000101:CLOSED");
		assertEquals("No accounts", bank.processRequest(alice, "SHOWMYACCOUNTS"));
	}

	@ParameterizedTest
	@EnumSource(value = Account.Status.class, names = "CLOSED", mode = EnumSource.Mode.EXCLUDE)
	void everyOtherStatusIsStillShown(Account.Status status) throws IOException {
		NewBank bank = bankFromFile("Savings:5.00:00000101:" + status.name());
		assertEquals("Savings: 5.0", bank.processRequest(alice, "SHOWMYACCOUNTS"));
	}

	// ---- limits and damaged files ----

	@Test
	void customerCannotRunOutOfTheirOwnAccountNumbers() throws IOException {
		StringBuilder closed = new StringBuilder();
		for (int i = 1; i <= 98; i++) {
			closed.append(i == 1 ? "" : ";").append(String.format("Savings:0.00:%08d:CLOSED", 100 + i));
		}
		NewBank bank = bankFromFile(closed.toString());
		assertEquals("SUCCESS", bank.processRequest(alice, "NEWACCOUNT Savings"));
		assertEquals("FAIL: You cannot open any more accounts", bank.processRequest(alice, "NEWACCOUNT Cheque"));
		// the file still loads, so no number was used twice
		assertNotNull(new NewBank(file().toString()).checkLogInDetails("bob", "pw"));
	}

	@Test
	void closedAccountWithMoneyInTheFileIsRejected() throws IOException {
		Files.writeString(file(), "1,alice,Alice,pw,Savings:5.00:00000101:CLOSED\n");
		assertThrows(IOException.class, () -> new CustomerDatabase(file().toString()).load());
	}
}
