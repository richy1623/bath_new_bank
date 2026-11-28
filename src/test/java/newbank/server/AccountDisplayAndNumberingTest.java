package newbank.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccountDisplayAndNumberingTest {
	@TempDir
	Path dir;

	private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);

	@Test
	void largeBalancesAndAmountsNeverUseScientificNotation() throws IOException {
		Customer a = new Customer(1, "A", "pw");
		a.addAccount(new Account("Savings", 10000000.0));
		Customer b = new Customer(2, "B", "pw");
		b.addAccount(new Account("Savings", 0.0));
		HashMap<String, Customer> customers = new HashMap<>();
		customers.put("a", a);
		customers.put("b", b);
		Path file = dir.resolve("customers.txt");
		new CustomerDatabase(file.toString()).save(customers);

		NewBank bank = new NewBank(file.toString(), clock);
		CustomerID alice = new CustomerID("a");
		CustomerID bob = new CustomerID("b");
		assertEquals("Savings: 10000000.0", bank.processRequest(alice, "SHOWMYACCOUNTS"));
		assertEquals("SUCCESS", bank.processRequest(alice, "TRANSFER Savings 00000201 1234567.89"));
		assertEquals("Savings | 00000101 | Active | 8765432.11", bank.processRequest(alice, "SHOWACCOUNTDETAILS"));
		assertEquals("Savings | 00000201 | Active | 1234567.89", bank.processRequest(bob, "SHOWACCOUNTDETAILS"));
		assertEquals("-1234567.89 | 2026-11-23 | 00000201", bank.processRequest(alice, "SHOWACTIVITY Savings"));
		assertEquals("1234567.89 | 2026-11-23 | 00000101", bank.processRequest(bob, "SHOWACTIVITY Savings"));
	}

	@Test
	void formatKeepsAtLeastOneDecimalPlaceAndNeverUsesExponents() {
		assertEquals("0.0", Account.format(new BigDecimal("0.00")));
		assertEquals("100.1", Account.format(new BigDecimal("100.10")));
		assertEquals("100.0", Account.format(new BigDecimal("100.00")));
		assertEquals("0.01", Account.format(new BigDecimal("0.01")));
		assertEquals("10000000.0", Account.format(new BigDecimal("10000000.00")));
		assertEquals("999999999999.99", Account.format(new BigDecimal("999999999999.99")));
		assertEquals("-1234567.89", Account.format(new BigDecimal("-1234567.89")));
	}

	@Test
	void legacyNumbersSkipSavedNumbersWhenSavedAccountComesFirst() throws IOException {
		Path file = dir.resolve("customers.txt");
		Files.writeString(file, "1,a,A,pw,Savings:5:00000102:ACTIVE;Cheque:0\n");
		CustomerDatabase db = new CustomerDatabase(file.toString());
		Customer loaded = db.load().get("a");
		assertEquals("00000102", loaded.getAccount("Savings").getAccountNumber());
		assertEquals("00000101", loaded.getAccount("Cheque").getAccountNumber());
		HashMap<String, Customer> customers = new HashMap<>();
		customers.put("a", loaded);
		db.save(customers);
		Customer reloaded = db.load().get("a");
		assertEquals("00000102", reloaded.getAccount("Savings").getAccountNumber());
		assertEquals("00000101", reloaded.getAccount("Cheque").getAccountNumber());
	}

	@Test
	void legacyNumbersSkipSavedNumbersWhenLegacyAccountComesFirst() throws IOException {
		Path file = dir.resolve("customers.txt");
		Files.writeString(file, "1,a,A,pw,Cheque:0;Savings:5:00000101:ACTIVE\n");
		Customer loaded = new CustomerDatabase(file.toString()).load().get("a");
		assertEquals("00000101", loaded.getAccount("Savings").getAccountNumber());
		assertEquals("00000102", loaded.getAccount("Cheque").getAccountNumber());
	}

	@Test
	void duplicateSavedNumbersInTheFileAreStillRejected() throws IOException {
		Path file = dir.resolve("customers.txt");
		Files.writeString(file, "1,a,A,pw,Savings:5:00000101:ACTIVE;Cheque:0:00000101:ACTIVE\n");
		assertThrows(IOException.class, () -> new CustomerDatabase(file.toString()).load());
	}

	@Test
	void newAccountsNeverReuseAnExistingNumber() {
		Customer customer = new Customer(1, "A", "pw");
		Account saved = new Account("Savings", 0.0);
		saved.setAccountNumber("00000101");
		customer.addAccount(saved);
		Account added = new Account("Cheque", 0.0);
		customer.addAccount(added);
		assertEquals("00000102", added.getAccountNumber());
		Account third = new Account("Investment", 0.0);
		customer.addAccount(third);
		assertEquals("00000103", third.getAccountNumber());
	}

	@Test
	void decimalConstructorRoundsToWholeCents() {
		assertEquals(new BigDecimal("0.30"), new Account("Savings", 0.1 + 0.2).balanceAmount());
		assertEquals(new BigDecimal("0.13"), new Account("Savings", 0.125).balanceAmount());
		assertEquals(0.3, new Account("Savings", 0.1 + 0.2).getBalance());
		assertEquals("Savings: 0.3", new Account("Savings", 0.1 + 0.2).toString());
	}

	@Test
	void invalidOpeningBalancesAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new Account("Savings", -1.0));
		assertThrows(IllegalArgumentException.class, () -> new Account("Savings", Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> new Account("Savings", Double.POSITIVE_INFINITY));
	}
}
