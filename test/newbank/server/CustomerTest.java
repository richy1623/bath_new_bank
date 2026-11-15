package newbank.server;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CustomerTest {

	@Test
	void newCustomerHasDetailsAndNoAccounts() {
		Customer c = new Customer(7, "Alice Smith", "secret");
		assertEquals(7, c.getId());
		assertEquals("Alice Smith", c.getFullName());
		assertTrue(c.getAccounts().isEmpty());
	}

	@Test
	void correctPasswordMatches() {
		Customer c = new Customer(1, "Alice Smith", "my secret");
		assertTrue(c.checkPassword("my secret"));
	}

	@Test
	void wrongPasswordDoesNotMatch() {
		Customer c = new Customer(1, "Alice Smith", "secret");
		assertFalse(c.checkPassword("Secret"));
		assertFalse(c.checkPassword(""));
		assertFalse(c.checkPassword(null));
	}

	@Test
	void customerWithoutPasswordOnlyMatchesBlank() {
		// test customers have no password (null in addTestData, "" once loaded from the file)
		assertTrue(new Customer(1, "", null).checkPassword(""));
		assertTrue(new Customer(1, "", "").checkPassword(""));
		assertFalse(new Customer(1, "", null).checkPassword("anything"));
	}
}
