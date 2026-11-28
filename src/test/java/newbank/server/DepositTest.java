package newbank.server;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DepositTest {
	@TempDir
	Path dir;

	private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);
	private final CustomerID alice = new CustomerID("alice");
	private final CustomerID bob = new CustomerID("bob");

	private Path file() {
		return dir.resolve("customers.txt");
	}

	private NewBank bank() throws Exception {
		Customer a = new Customer(1, "Alice", "pw");
		a.addAccount(new Account("Savings", 100.3));
		a.addAccount(new Account("Cheque", 0));
		Customer b = new Customer(2, "Bob", "pw");
		b.addAccount(new Account("Savings", 25.0));
		HashMap<String, Customer> map = new HashMap<>();
		map.put("alice", a);
		map.put("bob", b);
		new CustomerDatabase(file().toString()).save(map);
		return new NewBank(file().toString(), clock);
	}

	private String snapshot(NewBank bank) {
		return bank.processRequest(alice, "SHOWMYACCOUNTS") + bank.processRequest(bob, "SHOWMYACCOUNTS")
				+ bank.processRequest(alice, "SHOWACTIVITY Savings") + bank.processRequest(alice, "SHOWACTIVITY Cheque")
				+ bank.processRequest(bob, "SHOWACTIVITY Savings");
	}

	@Test
	void successfulDepositUpdatesBalanceHistoryAndPersistsAcrossRestart() throws Exception {
		NewBank bank = bank();
		assertNotNull(bank.checkLogInDetails("alice", "pw"));
		assertEquals("SUCCESS: New balance is 150.55", bank.processRequest(alice, "DEPOSIT Savings 50.25"));
		assertEquals("Savings: 150.55, Cheque: 0.0", bank.processRequest(alice, "SHOWMYACCOUNTS"));
		assertEquals("Savings: 25.0", bank.processRequest(bob, "SHOWMYACCOUNTS"));
		assertEquals("50.25 | 2026-11-23 | Deposit", bank.processRequest(alice, "SHOWACTIVITY Savings"));

		NewBank restarted = new NewBank(file().toString(), clock);
		assertEquals(snapshot(bank), snapshot(restarted));
		assertEquals(bank.processRequest(alice, "SHOWACCOUNTDETAILS"),
				restarted.processRequest(alice, "SHOWACCOUNTDETAILS"));
	}

	@Test
	void depositIntoSecondAccountIsCaseInsensitiveAndLeavesOtherAccountsUnchanged() throws Exception {
		NewBank bank = bank();
		assertEquals("SUCCESS: New balance is 25.0", bank.processRequest(alice, "DEPOSIT cheque 25"));
		assertEquals("Savings: 100.3, Cheque: 25.0", bank.processRequest(alice, "SHOWMYACCOUNTS"));
		assertEquals("No transactions", bank.processRequest(alice, "SHOWACTIVITY Savings"));
		assertEquals("25.0 | 2026-11-23 | Deposit", bank.processRequest(alice, "SHOWACTIVITY Cheque"));
	}

	@Test
	void multipleDecimalDepositsAccumulateExactCents() throws Exception {
		NewBank bank = bank();
		assertEquals("SUCCESS: New balance is 0.1", bank.processRequest(alice, "DEPOSIT Cheque 0.1"));
		assertEquals("SUCCESS: New balance is 0.3", bank.processRequest(alice, "DEPOSIT Cheque 0.2"));
		assertEquals("SUCCESS: New balance is 100.0", bank.processRequest(alice, "DEPOSIT Cheque 99.70"));

		var loaded = new CustomerDatabase(file().toString()).load();
		assertEquals(new BigDecimal("100.00"), loaded.get("alice").getAccount("Cheque").balanceAmount());
		assertEquals(3, loaded.get("alice").getAccount("Cheque").getTransactions().size());
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "-0.01", "NaN", "Infinity", "text", "0.001", "0.100000001", "0.099999999",
			"1e400", "1e-400", "1e-100000000", "1e1", "+5", "1.000", "1000000000000"})
	void invalidAmountsLeaveAllDataUnchanged(String amount) throws Exception {
		NewBank bank = bank();
		String before = snapshot(bank);
		String disk = Files.readString(file());
		assertTrue(bank.processRequest(alice, "DEPOSIT Savings " + amount).startsWith("FAIL:"));
		assertEquals(before, snapshot(bank));
		assertEquals(disk, Files.readString(file()));
	}

	@Test
	void extremeExponentAmountIsRejectedQuickly() throws Exception {
		NewBank bank = bank();
		assertTimeoutPreemptively(Duration.ofSeconds(1),
				() -> assertTrue(bank.processRequest(alice, "DEPOSIT Savings 1e-100000000").startsWith("FAIL:")));
	}

	@ParameterizedTest
	@ValueSource(strings = {"DEPOSIT", "DEPOSIT Savings", "DEPOSIT Savings 10 extra", "DEPOSIT Investment 10",
			"DEPOSIT Unknown 10", "deposit Savings 10"})
	void invalidRequestsLeaveAllDataUnchanged(String command) throws Exception {
		NewBank bank = bank();
		String before = snapshot(bank);
		String disk = Files.readString(file());
		assertTrue(bank.processRequest(alice, command).startsWith("FAIL:"));
		assertEquals(before, snapshot(bank));
		assertEquals(disk, Files.readString(file()));
	}

	@Test
	void unknownOrMissingSessionCannotDeposit() throws Exception {
		NewBank bank = bank();
		String before = snapshot(bank);
		assertEquals("FAIL: No active customer session", bank.processRequest(null, "DEPOSIT Savings 10"));
		assertEquals("FAIL: No active customer session",
				bank.processRequest(new CustomerID("nobody"), "DEPOSIT Savings 10"));
		assertEquals(before, snapshot(bank));
	}

	@Test
	void failedSaveRestoresBalanceAndHistory() throws Exception {
		NewBank bank = bank();
		assertEquals("SUCCESS: New balance is 100.4", bank.processRequest(alice, "DEPOSIT Savings 0.1"));
		String before = snapshot(bank);
		String disk = Files.readString(file());
		Files.delete(file());
		Files.createDirectory(file());
		try {
			assertTrue(bank.processRequest(alice, "DEPOSIT Savings 20").startsWith("FAIL:"));
			assertEquals(before, snapshot(bank));
		} finally {
			Files.delete(file());
			Files.writeString(file(), disk);
		}
		assertEquals(before, snapshot(new NewBank(file().toString(), clock)));
		assertEquals("SUCCESS: New balance is 120.4", bank.processRequest(alice, "DEPOSIT Savings 20"));
	}

	@Test
	void concurrentDepositsAreSerializedWithoutLostUpdates() throws Exception {
		NewBank bank = bank();
		var pool = Executors.newFixedThreadPool(2);
		try {
			var first = pool.submit(() -> bank.processRequest(alice, "DEPOSIT Savings 25.10"));
			var second = pool.submit(() -> bank.processRequest(alice, "DEPOSIT Savings 50.20"));
			assertTrue(first.get().startsWith("SUCCESS: New balance is "));
			assertTrue(second.get().startsWith("SUCCESS: New balance is "));
			var loaded = new CustomerDatabase(file().toString()).load();
			assertEquals(new BigDecimal("175.60"), loaded.get("alice").getAccount("Savings").balanceAmount());
			assertEquals(2, loaded.get("alice").getAccount("Savings").getTransactions().size());
		} finally {
			pool.shutdownNow();
		}
	}
}
