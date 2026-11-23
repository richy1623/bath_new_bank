package newbank.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NewBankTest {

	@TempDir
	Path tempDir;

	private String customersFile;
	private NewBank bank;

	@BeforeEach
	void setUp() {
		// a bank using a temporary file, so the real customers_database.txt is never
		// touched
		customersFile = tempDir.resolve("customers_database.txt").toString();
		bank = new NewBank(customersFile);
	}

	// ---- first run and test data ----

	@Test
	void firstRunCreatesFileWithTestCustomers() throws IOException {
		assertTrue(new File(customersFile).exists());
		assertEquals(3, Files.readAllLines(Path.of(customersFile)).size());
	}

	@Test
	void testCustomersLogInWithBlankPassword() {
		assertNotNull(bank.checkLogInDetails("Bhagy", ""));
		assertNull(bank.checkLogInDetails("Bhagy", "anything"));
	}

	@Test
	void unknownUsernameCannotLogIn() {
		assertNull(bank.checkLogInDetails("nobody", ""));
	}

	// ---- REGISTER ----

	@Test
	void registeredCustomerCanLogIn() {
		assertEquals("SUCCESS", bank.registerCustomer("Alice Smith", "asmith", "secret"));
		CustomerID id = bank.checkLogInDetails("asmith", "secret");
		assertNotNull(id);
		assertEquals("asmith", id.getKey());
		assertNull(bank.checkLogInDetails("asmith", "wrong"));
	}

	@Test
	void passwordCanContainSpaces() {
		assertEquals("SUCCESS", bank.registerCustomer("Alice Smith", "asmith", "my secret pass"));
		assertNotNull(bank.checkLogInDetails("asmith", "my secret pass"));
		assertNull(bank.checkLogInDetails("asmith", "pass"));
	}

	@Test
	void newCustomerHasNoAccounts() {
		bank.registerCustomer("Alice Smith", "asmith", "secret");
		assertEquals("", bank.processRequest(new CustomerID("asmith"), "SHOWMYACCOUNTS"));
	}

	@Test
	void duplicateUsernameFailsWithReason() {
		bank.registerCustomer("Alice Smith", "asmith", "secret");
		assertEquals("FAIL: Username asmith is already taken",
				bank.registerCustomer("Another Alice", "asmith", "other"));
		assertEquals("FAIL: Username Bhagy is already taken", bank.registerCustomer("B", "Bhagy", "x"));
	}

	@Test
	void invalidDetailsFailWithReason() {
		assertEquals("FAIL: Full name cannot be empty", bank.registerCustomer("  ", "asmith", "secret"));
		assertEquals("FAIL: Username cannot be empty or contain spaces", bank.registerCustomer("Alice", "", "secret"));
		assertEquals("FAIL: Username cannot be empty or contain spaces",
				bank.registerCustomer("Alice", "a smith", "secret"));
		assertEquals("FAIL: Password cannot be empty", bank.registerCustomer("Alice", "asmith", ""));
		assertEquals("FAIL: Full name, username and password cannot contain file separators (, ; # | ~ :)",
				bank.registerCustomer("Smith, Alice", "asmith", "secret"));
		assertEquals("FAIL: Full name, username and password cannot contain file separators (, ; # | ~ :)",
				bank.registerCustomer("Alice", "asmith", "se,cret"));
		// none of the failed attempts created the customer
		assertNull(bank.checkLogInDetails("asmith", "secret"));
	}

	// ---- saving, IDs and restarts ----

	@Test
	void registeredCustomerSurvivesRestart() {
		bank.registerCustomer("Alice Smith", "asmith", "secret");
		NewBank restarted = new NewBank(customersFile);
		assertNotNull(restarted.checkLogInDetails("asmith", "secret"));
		assertNotNull(restarted.checkLogInDetails("Bhagy", ""));
	}

	@Test
	void idsAreUniqueAndContinueAfterRestart() throws IOException {
		bank.registerCustomer("Alice Smith", "asmith", "secret"); // gets ID 4 (test customers are 1-3)
		NewBank restarted = new NewBank(customersFile);
		restarted.registerCustomer("Tom Lee", "tlee", "pw"); // must get ID 5, not reuse an ID
		String file = Files.readString(Path.of(customersFile));
		assertTrue(file.contains("4,asmith,Alice Smith,secret,"));
		assertTrue(file.contains("5,tlee,Tom Lee,pw,"));
	}

	@Test
	void damagedFileIsNeverOverwritten() throws IOException {
		String damaged = "1,Bhagy,,,Main:1000.0\nthis line is broken\n";
		Files.writeString(Path.of(customersFile), damaged);
		NewBank broken = new NewBank(customersFile);

		assertEquals("FAIL: Customer data could not be loaded, so new customers cannot be saved right now",
				broken.registerCustomer("Alice Smith", "asmith", "secret"));
		// the file still holds exactly what was there, so nothing was lost
		assertEquals(damaged, Files.readString(Path.of(customersFile)));
	}

	@Test
	void failedSaveDoesNotReportSuccess() throws IOException {
		Path file = Path.of(customersFile);
		try {
			Files.delete(file);
			Files.createDirectory(file); // saving over a directory fails on every OS
			assertEquals("FAIL: Your details could not be saved, please try again later",
					bank.registerCustomer("Alice Smith", "asmith", "secret"));
			// the customer was taken back out, so they can't log in to an account that
			// would vanish
			assertNull(bank.checkLogInDetails("asmith", "secret"));
		} finally {
			Files.deleteIfExists(file);
		}
	}

	// ---- NEWACCOUNT ----

	@Test
	void customerCanCreateMultipleAccountTypesWithOpeningBalance() {
		bank.registerCustomer("Alice Smith", "asmith", "secret");
		CustomerID id = new CustomerID("asmith");
		assertEquals("SUCCESS", bank.processRequest(id, "NEWACCOUNT Savings"));
		assertEquals("SUCCESS", bank.processRequest(id, "NEWACCOUNT Cheque"));
		assertEquals("SUCCESS", bank.processRequest(id, "NEWACCOUNT Investment"));
		String accounts = bank.processRequest(id, "SHOWMYACCOUNTS");
		assertTrue(accounts.contains("Savings: 0.0"));
		assertTrue(accounts.contains("Cheque: 0.0"));
		assertTrue(accounts.contains("Investment: 0.0"));
	}

	@Test
	void newAccountRejectsInvalidOrDuplicateType() {
		bank.registerCustomer("Alice Smith", "asmith", "secret");
		CustomerID id = new CustomerID("asmith");
		assertEquals("FAIL: Account type must be Savings, Cheque or Investment",
				bank.processRequest(id, "NEWACCOUNT Main"));
		assertEquals("SUCCESS", bank.processRequest(id, "NEWACCOUNT Savings"));
		assertEquals("FAIL: You already have a Savings account", bank.processRequest(id, "NEWACCOUNT Savings"));
		assertEquals("FAIL: Type NEWACCOUNT <Type>, for example NEWACCOUNT Savings",
				bank.processRequest(id, "NEWACCOUNT"));
	}

}
