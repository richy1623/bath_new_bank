package newbank.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccountActivityTest {
	@TempDir
	Path tempDir;

	private String file() {
		return tempDir.resolve("customers.txt").toString();
	}

	@Test
	void activityIsIsolatedByAccountAndCustomerAndSurvivesRestart() throws IOException {
		Customer alice = new Customer(1, "Alice", "pw");
		Account savings = new Account("Savings", 12.34);
		savings.addTransaction(new Transaction("Savings", 12.34,
				LocalDate.of(2026, 1, 2), "John"));
		savings.addTransaction(new Transaction("Savings", -1.0,
				LocalDate.of(2026, 1, 3), "Mary"));
		alice.addAccount(savings);
		alice.addAccount(new Account("Cheque", 0));
		Customer bob = new Customer(2, "Bob", "pw");
		Account hidden = new Account("Investment", 99);
		hidden.addTransaction(new Transaction("Investment", 99,
				LocalDate.of(2026, 1, 1), "PRIVATE"));
		bob.addAccount(hidden);
		HashMap<String, Customer> customers = new HashMap<>();
		customers.put("alice", alice);
		customers.put("bob", bob);
		new CustomerDatabase(file()).save(customers);

		String expected = "Savings | 12.34 | 2026-01-02 | John\n"
				+ "Savings | -1.0 | 2026-01-03 | Mary";
		for(int restart = 0; restart < 2; restart++) {
			NewBank bank = new NewBank(file());
			CustomerID id = bank.checkLogInDetails("alice", "pw");
			assertNotNull(id);
			assertEquals(expected, bank.processRequest(id, "SHOWACTIVITY Savings"));
			assertEquals("No transactions", bank.processRequest(id, "SHOWACTIVITY Cheque"));
			assertTrue(bank.processRequest(id, "SHOWACTIVITY Investment").startsWith("FAIL"));
			assertTrue(bank.processRequest(id, "SHOWACTIVITY").startsWith("FAIL"));
			assertEquals("FAIL", bank.processRequest(new CustomerID("unknown"), "SHOWACTIVITY Savings"));
		}
	}

	@Test
	void openingRecordAndZeroBalanceSurviveRestart() {
		NewBank bank = new NewBank(file());
		assertEquals("SUCCESS", bank.registerCustomer("Alice", "alice", "pw"));
		CustomerID id = bank.checkLogInDetails("alice", "pw");
		assertEquals("SUCCESS", bank.processRequest(id, "NEWACCOUNT Savings"));
		String activity = bank.processRequest(id, "SHOWACTIVITY Savings");
		assertEquals("Savings | 0.0 | " + LocalDate.now() + " | Opening", activity);
		NewBank restarted = new NewBank(file());
		assertEquals("Savings: 0.0", restarted.processRequest(id, "SHOWMYACCOUNTS"));
		assertEquals(activity, restarted.processRequest(id, "SHOWACTIVITY Savings"));
		assertTrue(restarted.processRequest(id, "NEWACCOUNT savings").startsWith("FAIL"));
	}

	@Test
	void failedOpeningPreservesMemoryAndSavedFile() throws IOException {
		NewBank bank = new NewBank(file());
		assertEquals("SUCCESS", bank.registerCustomer("Alice", "alice", "pw"));
		CustomerID id = bank.checkLogInDetails("alice", "pw");
		String before = Files.readString(Path.of(file()));
		File directory = tempDir.toFile();
		assertTrue(directory.setWritable(false));
		try {
			assertTrue(bank.processRequest(id, "NEWACCOUNT Savings").startsWith("FAIL"));
		} finally {
			directory.setWritable(true);
		}
		assertEquals("", bank.processRequest(id, "SHOWMYACCOUNTS"));
		assertTrue(bank.processRequest(id, "SHOWACTIVITY Savings").startsWith("FAIL"));
		assertEquals(before, Files.readString(Path.of(file())));
		assertEquals("SUCCESS", bank.processRequest(id, "NEWACCOUNT Savings"));
	}

	@Test
	void malformedTransactionDateIsRejected() throws IOException {
		Files.writeString(Path.of(file()), "1,alice,Alice,pw,Savings:1.0#invalid~1.0~John\n");
		assertThrows(IOException.class, () -> new CustomerDatabase(file()).load());
	}
}
