package newbank.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StaffTest {

	@TempDir
	Path dir;

	private final CustomerID staff = new CustomerID("Staff");
	private final CustomerID alice = new CustomerID("alice");
	private final CustomerID bob = new CustomerID("bob");

	private Path file() {
		return dir.resolve("customers.txt");
	}

	// Alice has an InReview Savings (00000101) and an empty Active Cheque
	// (00000102); Bob has an Active Savings (00000201) with 100 in it
	private NewBank bank() throws IOException {
		Files.writeString(file(), "1,alice,Alice,pw,Savings:0.00:00000101:IN_REVIEW;Cheque:0.00:00000102:ACTIVE\n"
				+ "2,bob,Bob,pw,Savings:100.00:00000201:ACTIVE\n");
		return new NewBank(file().toString());
	}

	private String details(NewBank bank, CustomerID who) {
		return bank.processRequest(who, "SHOWACCOUNTDETAILS");
	}

	// ---- the default staff user ----

	@Test
	void firstRunAddsTheDefaultStaffUser() throws IOException {
		NewBank bank = new NewBank(file().toString());
		assertNotNull(bank.checkLogInDetails("Staff", "staff123"));
		assertTrue(Files.readString(file()).contains("4,Staff,Staff,staff123,,STAFF"));
	}

	@Test
	void olderFileWithoutStaffGetsOneAndKeepsItsCustomers() throws IOException {
		Files.writeString(file(), "7,alice,Alice,pw,Savings:5.00:00000701:ACTIVE\n");
		NewBank bank = new NewBank(file().toString());
		assertNotNull(bank.checkLogInDetails("Staff", "staff123"));
		assertNotNull(bank.checkLogInDetails("alice", "pw"));
		Customer loaded = new CustomerDatabase(file().toString()).load().get("Staff");
		assertEquals(8, loaded.getId());
		assertTrue(loaded.isStaff());
	}

	@Test
	void staffUserIsNotAddedTwice() throws IOException {
		new NewBank(file().toString());
		new NewBank(file().toString());
		long staffLines = Files.readAllLines(file()).stream().filter(l -> l.endsWith(",STAFF")).count();
		assertEquals(1, staffLines);
	}

	@Test
	void customerAlreadyCalledStaffIsLeftAlone() throws IOException {
		Files.writeString(file(), "1,Staff,Someone,pw,\n");
		NewBank bank = new NewBank(file().toString());
		assertNull(bank.checkLogInDetails("Staff", "staff123"));
		assertFalse(new CustomerDatabase(file().toString()).load().get("Staff").isStaff());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "wrong", "Staff123"})
	void staffNeedsTheRightPassword(String password) {
		NewBank bank = new NewBank(file().toString());
		assertNull(bank.checkLogInDetails("Staff", password));
	}

	// ---- roles in the file ----

	@Test
	void linesWithoutARoleLoadAsCustomers() throws IOException {
		Files.writeString(file(), "1,alice,Alice,pw,Savings:1.0\n");
		assertEquals(Customer.Role.CUSTOMER, new CustomerDatabase(file().toString()).load().get("alice").getRole());
	}

	@Test
	void unknownRoleMeansTheFileIsDamaged() throws IOException {
		Files.writeString(file(), "1,alice,Alice,pw,,ADMIN\n");
		assertThrows(IOException.class, () -> new CustomerDatabase(file().toString()).load());
	}

	@Test
	void registerAlwaysCreatesACustomer() throws IOException {
		NewBank bank = bank();
		assertEquals("SUCCESS", bank.registerCustomer("Carol", "carol", "pw"));
		assertEquals("FAIL: Only staff can change account statuses",
				bank.processRequest(new CustomerID("carol"), "SETSTATUS 00000101 Active"));
		assertFalse(new CustomerDatabase(file().toString()).load().get("carol").isStaff());
	}

	// ---- SETSTATUS ----

	@Test
	void staffCanApproveANewAccountSoItCanBeUsed() throws IOException {
		NewBank bank = bank();
		assertEquals("FAIL: The receiving account cannot receive money",
				bank.processRequest(bob, "TRANSFER Savings 00000101 10"));
		assertEquals("SUCCESS", bank.processRequest(staff, "SETSTATUS 00000101 Active"));
		assertEquals("SUCCESS", bank.processRequest(bob, "TRANSFER Savings 00000101 10"));
		assertEquals("Savings | 00000101 | Active | 10.0; Cheque | 00000102 | Active | 0.0", details(bank, alice));
	}

	@Test
	void statusChangeSurvivesARestart() throws IOException {
		bank().processRequest(staff, "SETSTATUS 00000201 Inactive");
		assertEquals("Savings | 00000201 | Inactive | 100.0", details(new NewBank(file().toString()), bob));
	}

	@ParameterizedTest
	@ValueSource(strings = {"Inactive", "inactive", "INACTIVE"})
	void statusNamesIgnoreCase(String status) throws IOException {
		NewBank bank = bank();
		assertEquals("SUCCESS", bank.processRequest(staff, "SETSTATUS 00000201 " + status));
		assertEquals("Savings | 00000201 | Inactive | 100.0", details(bank, bob));
	}

	@Test
	void staffCanCloseAnEmptyAccount() throws IOException {
		NewBank bank = bank();
		assertEquals("SUCCESS", bank.processRequest(staff, "SETSTATUS 00000102 Closed"));
		assertEquals("Savings: 0.0", bank.processRequest(alice, "SHOWMYACCOUNTS"));
	}

	@Test
	void accountWithMoneyCannotBeClosed() throws IOException {
		NewBank bank = bank();
		assertEquals("FAIL: An account can only be closed when its balance is 0",
				bank.processRequest(staff, "SETSTATUS 00000201 Closed"));
		assertEquals("Savings | 00000201 | Active | 100.0", details(bank, bob));
	}

	@Test
	void closedAccountCannotBeReopened() throws IOException {
		NewBank bank = bank();
		bank.processRequest(staff, "SETSTATUS 00000102 Closed");
		assertEquals("FAIL: A closed account cannot be reopened",
				bank.processRequest(staff, "SETSTATUS 00000102 Active"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"SETSTATUS", "SETSTATUS 00000101", "SETSTATUS 00000101 Active extra"})
	void wrongFormatIsExplained(String request) throws IOException {
		assertEquals("FAIL: Type SETSTATUS <AccountNumber> <Status>, for example SETSTATUS 00000102 Active",
				bank().processRequest(staff, request));
	}

	@ParameterizedTest
	@ValueSource(strings = {"Open", "IN_REVIEW", "Dormant", "1"})
	void unknownStatusIsRejected(String status) throws IOException {
		assertEquals("FAIL: Status must be InReview, Active, Inactive or Closed",
				bank().processRequest(staff, "SETSTATUS 00000101 " + status));
	}

	@Test
	void unknownAccountIsRejected() throws IOException {
		assertEquals("FAIL: Account 99999999 not found", bank().processRequest(staff, "SETSTATUS 99999999 Active"));
	}

	@Test
	void failedSaveLeavesTheStatusUnchanged() throws IOException {
		NewBank bank = bank();
		Files.delete(file());
		Files.createDirectory(file()); // saving now fails on every OS
		assertEquals("FAIL: Your details could not be saved, please try again later",
				bank.processRequest(staff, "SETSTATUS 00000102 Closed"));
		assertEquals("Savings | 00000101 | InReview | 0.0; Cheque | 00000102 | Active | 0.0", details(bank, alice));
	}

	// ---- staff and customers keep to their own commands ----

	@ParameterizedTest
	@ValueSource(strings = {"SHOWMYACCOUNTS", "SHOWACCOUNTDETAILS", "NEWACCOUNT Savings", "SHOWACTIVITY Savings",
			"TRANSFER Savings 00000201 1"})
	void staffCannotBank(String request) throws IOException {
		assertEquals("FAIL: Staff can only use SETSTATUS", bank().processRequest(staff, request));
	}

	@Test
	void customersCannotChangeStatuses() throws IOException {
		NewBank bank = bank();
		assertEquals("FAIL: Only staff can change account statuses",
				bank.processRequest(alice, "SETSTATUS 00000101 Active"));
		assertEquals("Savings | 00000101 | InReview | 0.0; Cheque | 00000102 | Active | 0.0", details(bank, alice));
	}
}
