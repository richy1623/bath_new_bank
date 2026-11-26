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

class TransferTest {
	@TempDir
	Path dir;
	private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);
	private final CustomerID alice = new CustomerID("alice");
	private final CustomerID bob = new CustomerID("bob");
	private Path file() {
		return dir.resolve("customers.txt");
	}

	private NewBank bank(Account.Status source, Account.Status destination) throws Exception {
		Customer a = new Customer(1, "Alice", "pw");
		Account savings = new Account("Savings", 100.3);
		savings.setStatus(source);
		a.addAccount(savings);
		a.addAccount(new Account("Cheque", 0));
		Customer b = new Customer(2, "Bob", "pw");
		Account receiving = new Account("Savings", 0.1);
		receiving.setStatus(destination);
		b.addAccount(receiving);
		HashMap<String, Customer> map = new HashMap<>();
		map.put("alice", a);
		map.put("bob", b);
		new CustomerDatabase(file().toString()).save(map);
		return new NewBank(file().toString(), clock);
	}

	private NewBank bank() throws Exception {
		return bank(Account.Status.ACTIVE, Account.Status.ACTIVE);
	}

	private String snapshot(NewBank bank) {
		return bank.processRequest(alice, "SHOWMYACCOUNTS") + bank.processRequest(bob, "SHOWMYACCOUNTS")
				+ bank.processRequest(alice, "SHOWACTIVITY Savings") + bank.processRequest(bob, "SHOWACTIVITY Savings");
	}

	@Test
	void successfulTransferPersistsBothBalancesAndHistories() throws Exception {
		NewBank bank = bank();
		assertNotNull(bank.checkLogInDetails("alice", "pw"));
		assertEquals("SUCCESS", bank.processRequest(alice, "TRANSFER Savings 00000201 0.20"));
		assertEquals("Savings: 100.1, Cheque: 0.0", bank.processRequest(alice, "SHOWMYACCOUNTS"));
		assertEquals("Savings: 0.3", bank.processRequest(bob, "SHOWMYACCOUNTS"));
		assertEquals("-0.2 | 2026-11-23 | 00000201", bank.processRequest(alice, "SHOWACTIVITY Savings"));
		assertEquals("0.2 | 2026-11-23 | 00000101", bank.processRequest(bob, "SHOWACTIVITY Savings"));
		NewBank restarted = new NewBank(file().toString(), clock);
		assertEquals(snapshot(bank), snapshot(restarted));
		assertEquals(bank.processRequest(alice, "SHOWACCOUNTDETAILS"),
				restarted.processRequest(alice, "SHOWACCOUNTDETAILS"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "NaN", "Infinity", "text", "0.001", "0.100000001", "0.099999999", "1e400",
			"1e-400", "1e-100000000", "1e1", "+5", "1.000", "1000000000000"})
	void invalidAmountsLeaveAllDataUnchanged(String amount) throws Exception {
		NewBank bank = bank();
		String before = snapshot(bank);
		String disk = Files.readString(file());
		assertTrue(bank.processRequest(alice, "TRANSFER Savings 00000201 " + amount).startsWith("FAIL"));
		assertEquals(before, snapshot(bank));
		assertEquals(disk, Files.readString(file()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"TRANSFER", "TRANSFER Savings 00000201", "TRANSFER Savings 00000201 1 extra",
			"TRANSFER Investment 00000201 1", "TRANSFER Savings 99999999 1", "TRANSFER Savings 00000101 1",
			"TRANSFER Savings 00000201 100.31"})
	void invalidRequestsLeaveAllDataUnchanged(String command) throws Exception {
		NewBank bank = bank();
		String before = snapshot(bank);
		assertTrue(bank.processRequest(alice, command).startsWith("FAIL"));
		assertEquals(before, snapshot(bank));
	}

	@Test
	void extremeExponentAmountIsRejectedQuickly() throws Exception {
		NewBank bank = bank();
		assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertTrue(
				bank.processRequest(alice, "TRANSFER Savings 00000201 1e-100000000").startsWith("FAIL")));
	}

	@Test
	void customerCanTransferBetweenOwnAccounts() throws Exception {
		NewBank bank = bank();
		assertEquals("SUCCESS", bank.processRequest(alice, "TRANSFER Savings 00000102 10.00"));
		assertEquals("Savings: 90.3, Cheque: 10.0", bank.processRequest(alice, "SHOWMYACCOUNTS"));
	}

	@Test
	void inactiveOrClosedAccountsAreRejected() throws Exception {
		for (Account.Status status : new Account.Status[]{Account.Status.INACTIVE, Account.Status.CLOSED}) {
			for (boolean source : new boolean[]{true, false}) {
				NewBank bank = bank(source ? status : Account.Status.ACTIVE, source ? Account.Status.ACTIVE : status);
				String before = snapshot(bank);
				assertEquals("FAIL: Both accounts must be Active",
						bank.processRequest(alice, "TRANSFER Savings 00000201 1"));
				assertEquals(before, snapshot(bank));
			}
		}
	}

	@Test
	void unknownOrMissingSessionCannotTransfer() throws Exception {
		NewBank bank = bank();
		String before = snapshot(bank);
		assertTrue(bank.processRequest(null, "TRANSFER Savings 00000201 1").startsWith("FAIL"));
		assertTrue(bank.processRequest(new CustomerID("nobody"), "TRANSFER Savings 00000201 1").startsWith("FAIL"));
		assertEquals(before, snapshot(bank));
	}

	@Test
	void failedSaveRestoresBothBalancesAndCompleteHistories() throws Exception {
		NewBank bank = bank();
		assertEquals("SUCCESS", bank.processRequest(alice, "TRANSFER Savings 00000201 0.1"));
		String before = snapshot(bank);
		String disk = Files.readString(file());
		Files.delete(file());
		Files.createDirectory(file());
		try {
			assertTrue(bank.processRequest(alice, "TRANSFER Savings 00000201 0.2").startsWith("FAIL"));
			assertEquals(before, snapshot(bank));
		} finally {
			Files.delete(file());
			Files.writeString(file(), disk);
		}
		assertEquals(before, snapshot(new NewBank(file().toString(), clock)));
		assertEquals("SUCCESS", bank.processRequest(alice, "TRANSFER Savings 00000201 0.2"));
	}

	@Test
	void concurrentRequestsCannotOverdraw() throws Exception {
		NewBank bank = bank();
		var pool = Executors.newFixedThreadPool(2);
		try {
			var first = pool.submit(() -> bank.processRequest(alice, "TRANSFER Savings 00000201 75"));
			var second = pool.submit(() -> bank.processRequest(alice, "TRANSFER Savings 00000201 75"));
			assertNotEquals(first.get().equals("SUCCESS"), second.get().equals("SUCCESS"));
			var loaded = new CustomerDatabase(file().toString()).load();
			assertEquals(new BigDecimal("25.30"), loaded.get("alice").getAccount("Savings").balanceAmount());
			assertEquals(new BigDecimal("75.10"), loaded.get("bob").getAccount("Savings").balanceAmount());
			assertEquals(1, loaded.get("alice").getAccount("Savings").getTransactions().size());
			assertEquals(1, loaded.get("bob").getAccount("Savings").getTransactions().size());
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void decimalTransfersAndExactBalancePaymentConserveMoney() throws Exception {
		NewBank bank = bank();
		assertEquals("SUCCESS", bank.processRequest(alice, "TRANSFER Savings 00000201 0.1"));
		assertEquals("SUCCESS", bank.processRequest(alice, "TRANSFER Savings 00000201 0.2"));
		assertEquals("SUCCESS", bank.processRequest(alice, "TRANSFER Savings 00000201 100"));
		var loaded = new CustomerDatabase(file().toString()).load();
		assertEquals(new BigDecimal("0.00"), loaded.get("alice").getAccount("Savings").balanceAmount());
		assertEquals(new BigDecimal("100.40"), loaded.get("bob").getAccount("Savings").balanceAmount());
		String before = snapshot(bank);
		assertEquals("FAIL: Not enough money", bank.processRequest(alice, "TRANSFER Savings 00000201 0.01"));
		assertEquals(before, snapshot(bank));
	}
}
