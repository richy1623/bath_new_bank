package newbank.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class NewBankClientHandler extends Thread{
	
	private NewBank bank;
	private BufferedReader in;
	private PrintWriter out;
	
	
	public NewBankClientHandler(Socket s) throws IOException {
		bank = NewBank.getBank();
		in = new BufferedReader(new InputStreamReader(s.getInputStream()));
		out = new PrintWriter(s.getOutputStream(), true);
	}
	
	// REGISTER: asks for each detail on its own line, so the full name and password can contain
	// spaces, then asks the bank to create the customer and sends back its reply.
	// Returns false if the client disconnected part way through.
	private boolean register() throws IOException {
		out.println("Enter Full Name");
		String fullName = in.readLine();
		if(fullName == null) {
			return false;
		}
		out.println("Enter Username");
		String userName = in.readLine();
		if(userName == null) {
			return false;
		}
		out.println("Enter Password");
		String password = in.readLine();
		if(password == null) {
			return false;
		}
		out.println(bank.registerCustomer(fullName, userName, password));
		return true;
	}

	// asks for a username and password until the customer logs in.
	// Returns their CustomerID, or null if the client disconnects.
	private CustomerID logIn() throws IOException {
		CustomerID customer = null;
		while(customer == null) {
			// ask for user name; the user can type REGISTER here instead to create a new customer
			out.println("Enter Username");
			String userName = in.readLine();
			if(userName == null) {
				return null; // client disconnected
			}
			if(userName.trim().equals("REGISTER")) {
				if(!register()) {
					return null; // client disconnected
				}
				continue;
			}
			if(userName.startsWith("REGISTER ")) {
				// the old one-line format; point the customer to the new prompts
				out.println("FAIL: Type REGISTER on its own and you will be asked for each detail");
				continue;
			}
			// ask for password
			out.println("Enter Password");
			String password = in.readLine();
			if(password == null) {
				return null; // client disconnected
			}
			out.println("Checking Details...");
			// authenticate user and get customer ID token from bank for use in subsequent requests
			customer = bank.checkLogInDetails(userName, password);
			if(customer == null) {
				out.println("Log In Failed. Please try again.");
			}
		}
		return customer;
	}

	public void run() {
		// keep getting requests from the client and processing them
		try {
			// each pass of this loop is one login session; LOGOUT goes back to the top to log in again
			while(true) {
				CustomerID customer = logIn();
				if(customer == null) {
					break; // client disconnected
				}
				// the user is authenticated, so get requests from the user and process them
				out.println("Log In Successful. What do you want to do?");
				while(true) {
					String request = in.readLine();
					if(request == null) {
						return; // client disconnected; the finally block closes the streams
					}
					System.out.println("Request from " + customer.getKey());
					// LOGOUT ends this session (handled here, not in NewBank, because it's about the
					// connection rather than the customer's data)
					if("LOGOUT".equals(request)) {
						out.println("Log Out Successful. Goodbye " + customer.getKey() + ".");
						break;
					}
					String responce = bank.processRequest(customer, request);
					out.println(responce);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			try {
				in.close();
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
				Thread.currentThread().interrupt();
			}
		}
	}

}
