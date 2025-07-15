import java.sql.*;
import java.util.*;

public class TrainReservationSystem
{
    private Connection conn;
    private Scanner sc;
    private int currentWaitingNumber; // Add this field to store the current waiting number

    // Constructor to initialize the connection and scanner
    public TrainReservationSystem(Connection conn) {
        this.conn = conn;
        sc = new Scanner(System.in);
    }

    // Method to generate a random PNR number
    private int generateRandomPNR() throws SQLException {
        Random random = new Random();
        int maxAttempts = 5; // Maximum attempts to generate unique PNR
        int attempts = 0;
        
        while (attempts < maxAttempts) {
            int pnr = 1000 + random.nextInt(999999000); // Generate number between 1000 and 1000000000
            
            // Check if PNR already exists
            String checkPNRQuery = "SELECT pr FROM pnr WHERE pr=?";
            PreparedStatement pst = conn.prepareStatement(checkPNRQuery);
            pst.setInt(1, pnr);
            ResultSet rs = pst.executeQuery();
            
            if (!rs.next()) {
                return pnr; // Return PNR if it's unique
            }
            
            attempts++;
        }
        
        throw new SQLException("Unable to generate unique PNR. Please try booking again.");
    }

    // Method to handle the payment via UPI
    private boolean pay(int amount) throws SQLException {
        System.out.println("Enter your UPI ID:");
        String upiId = sc.nextLine();

        // Query to get balance for the entered UPI ID
        String checkBalanceQuery = "SELECT balance FROM upi WHERE upi_id=?";
        PreparedStatement checkBalanceStmt = conn.prepareStatement(checkBalanceQuery);
        checkBalanceStmt.setString(1, upiId);
        ResultSet rs = checkBalanceStmt.executeQuery();

        if (rs.next()) {
            int balance = rs.getInt("balance");

            // Check if the user has enough balance
            if (balance >= amount) {
                // Deduct the amount from the balance
                int newBalance = balance - amount;
                String updateBalanceQuery = "UPDATE upi SET balance=? WHERE upi_id=?";
                PreparedStatement updateBalanceStmt = conn.prepareStatement(updateBalanceQuery);
                updateBalanceStmt.setInt(1, newBalance);
                updateBalanceStmt.setString(2, upiId);
                updateBalanceStmt.executeUpdate();

                System.out.println("Payment Successful! Your new balance is: " + newBalance);
                return true;
            } else {
                // Insufficient balance
                System.out.println("Insufficient balance. You need " + (amount - balance) + " more.");
                return false;
            }
        } else {
            System.out.println("UPI ID not found in the system.");
            return false;
        }
    }

    // Method to book a ticket
    public void bookTicket(String username) throws SQLException {
        System.out.println("Enter the start city:");
        String startCity = sc.nextLine();
        System.out.println("Enter the destination city:");
        String endCity = sc.nextLine();

        String query = "SELECT * FROM train WHERE start_city=? AND end_city=?";
        PreparedStatement pst = conn.prepareStatement(query);
        pst.setString(1, startCity);
        pst.setString(2, endCity);
        ResultSet rs = pst.executeQuery();

        if (!rs.next()) {
            System.out.println("No trains available for the given route.");
            return;
        }

        // Show available trains
        System.out.println("Available trains:");
        do {
            System.out.println("Train No: " + rs.getInt("train_no") + ", Start Time: " + rs.getString("start_time") +
                    ", Total Time: " + rs.getString("total_time") + ", Available Seats: " +
                    rs.getInt("available_seats") + ", Amount: " + rs.getInt("amount"));
        } while (rs.next());

        System.out.println("Enter Train No to book:");
        int trainNo = sc.nextInt();
        sc.nextLine();  // consume newline

        // Check if train exists
        String checkTrain = "SELECT * FROM train WHERE train_no=?";
        PreparedStatement checkStmt = conn.prepareStatement(checkTrain);
        checkStmt.setInt(1, trainNo);
        ResultSet checkRs = checkStmt.executeQuery();
        if (!checkRs.next()) {
            System.out.println("No such train number exists.");
            return;
        }

        System.out.println("Enter the number of tickets you want to book:");
        int requestedSeats = sc.nextInt();
        sc.nextLine();  // consume newline

        int availableSeats = checkRs.getInt("available_seats");
        int amount = checkRs.getInt("amount") * requestedSeats;  // Amount based on seats

        int pnr = generateRandomPNR(); // Generate single PNR for the entire booking
        
        if (requestedSeats <= availableSeats) {
            System.out.println("Booking " + requestedSeats + " seats.");
            try {
                if (pay(amount)) {
                    bookSeats(trainNo, requestedSeats, username, "Booked", null, pnr);
                    System.out.println("Your PNR is: " + pnr);
                }
            } catch (SQLException e) {
                if (e.getMessage().contains("Unable to generate unique PNR")) {
                    System.out.println(e.getMessage());
                    return;
                }
                throw e;
            }
        } else if (availableSeats == 0) {
            System.out.println("No seats available. Adding " + requestedSeats + " seats to waiting list.");
            try {
                if (pay(amount)) {
                    conn.setAutoCommit(false);
                    try {
                        // Get current waiting list count
                        String waitQuery = "SELECT waiting_list FROM wait WHERE train_no=?";
                        PreparedStatement waitStmt = conn.prepareStatement(waitQuery);
                        waitStmt.setInt(1, trainNo);
                        ResultSet waitRs = waitStmt.executeQuery();
                        int currentWaitingCount = 0;
                        
                        String waitUpdateQuery;
                        PreparedStatement waitUpdateStmt;
                        
                        if (waitRs.next()) {
                            // Train already has waiting list - update existing count
                            currentWaitingCount = waitRs.getInt("waiting_list");
                            waitUpdateQuery = "UPDATE wait SET waiting_list = waiting_list + ? WHERE train_no = ?";
                            waitUpdateStmt = conn.prepareStatement(waitUpdateQuery);
                            waitUpdateStmt.setInt(1, requestedSeats);
                            waitUpdateStmt.setInt(2, trainNo);
                        } else {
                            // First waiting list entry for this train
                            waitUpdateQuery = "INSERT INTO wait (train_no, waiting_list) VALUES (?, ?)";
                            waitUpdateStmt = conn.prepareStatement(waitUpdateQuery);
                            waitUpdateStmt.setInt(1, trainNo);
                            waitUpdateStmt.setInt(2, requestedSeats);
                        }
                        waitUpdateStmt.executeUpdate();

                        // Insert PNR record with waiting list range
                        String pnrQuery = "INSERT INTO pnr (user_name, train_no, status, waiting_list, pr) VALUES (?, ?, ?, ?, ?)";
                        PreparedStatement pnrStmt = conn.prepareStatement(pnrQuery);
                        pnrStmt.setString(1, username);
                        pnrStmt.setInt(2, trainNo);
                        pnrStmt.setString(3, "Waiting List");
                        pnrStmt.setString(4, (currentWaitingCount + 1) + "-" + (currentWaitingCount + requestedSeats));
                        pnrStmt.setInt(5, pnr);
                        pnrStmt.executeUpdate();

                        // Add passengers to waiting list
                        for (int i = 0; i < requestedSeats; i++) {
                            System.out.println("Enter waiting passenger " + (i + 1) + " details:");
                            System.out.println("Name:");
                            String name = sc.nextLine();
                            System.out.println("Age:");
                            int age = sc.nextInt();
                            sc.nextLine();
                            System.out.println("Gender:");
                            String gender = sc.nextLine();

                            String insertPassengerQuery = "INSERT INTO passenger (pnr, name, age, gender, status) VALUES (?, ?, ?, ?, ?)";
                            PreparedStatement passengerStmt = conn.prepareStatement(insertPassengerQuery);
                            passengerStmt.setInt(1, pnr);
                            passengerStmt.setString(2, name);
                            passengerStmt.setInt(3, age);
                            passengerStmt.setString(4, gender);
                            passengerStmt.setString(5, "Waiting List");
                            passengerStmt.executeUpdate();
                        }

                        conn.commit();
                        System.out.println("Booking completed. Your PNR is: " + pnr);
                        System.out.println("All " + requestedSeats + " seats are in waiting list positions " + 
                                         (currentWaitingCount + 1) + " to " + (currentWaitingCount + requestedSeats));

                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(true);
                    }
                }
            } catch (SQLException e) {
                if (e.getMessage().contains("Unable to generate unique PNR")) {
                    System.out.println(e.getMessage());
                    return;
                }
                throw e;
            }
        } else {
            System.out.println("Only " + availableSeats + " seats available.");
            System.out.println("Booking " + availableSeats + " seats and adding " + (requestedSeats - availableSeats) + " to waiting list.");
            try {
                if (pay(amount)) {
                    conn.setAutoCommit(false);
                    try {
                        // First book available seats
                        if (availableSeats > 0) {
                            // Update train seats
                            String updateSeatsQuery = "UPDATE train SET available_seats = 0 WHERE train_no = ?";
                            PreparedStatement updateSeatsStmt = conn.prepareStatement(updateSeatsQuery);
                            updateSeatsStmt.setInt(1, trainNo);
                            updateSeatsStmt.executeUpdate();

                            // Insert PNR record
                            String pnrQuery = "INSERT INTO pnr (user_name, train_no, status, pr) VALUES (?, ?, ?, ?)";
                            PreparedStatement pnrStmt = conn.prepareStatement(pnrQuery);
                            pnrStmt.setString(1, username);
                            pnrStmt.setInt(2, trainNo);
                            pnrStmt.setString(3, "Partially Booked");
                            pnrStmt.setInt(4, pnr);
                            pnrStmt.executeUpdate();

                            // Add confirmed passengers
                            for (int i = 0; i < availableSeats; i++) {
                                System.out.println("Enter confirmed passenger " + (i + 1) + " details:");
                                System.out.println("Name:");
                                String name = sc.nextLine();
                                System.out.println("Age:");
                                int age = sc.nextInt();
                                sc.nextLine();
                                System.out.println("Gender:");
                                String gender = sc.nextLine();

                                String insertPassengerQuery = "INSERT INTO passenger (pnr, name, age, gender, status) VALUES (?, ?, ?, ?, ?)";
                                PreparedStatement passengerStmt = conn.prepareStatement(insertPassengerQuery);
                                passengerStmt.setInt(1, pnr);
                                passengerStmt.setString(2, name);
                                passengerStmt.setInt(3, age);
                                passengerStmt.setString(4, gender);
                                passengerStmt.setString(5, "Confirmed");
                                passengerStmt.executeUpdate();
                            }
                        }

                        // Handle waiting list for remaining seats
                        int waitingSeats = requestedSeats - availableSeats;
                        if (waitingSeats > 0) {
                            // Get current waiting list count
                            String waitQuery = "SELECT COALESCE(SUM(waiting_list), 0) as total_waiting FROM wait WHERE train_no=?";
                            PreparedStatement waitStmt = conn.prepareStatement(waitQuery);
                            waitStmt.setInt(1, trainNo);
                            ResultSet waitRs = waitStmt.executeQuery();
                            int currentWaitingCount = 0;
                            if (waitRs.next()) {
                                currentWaitingCount = waitRs.getInt("total_waiting");
                            }

                            // Update waiting list
                            String updateWaitQuery = "INSERT INTO wait (train_no, waiting_list) VALUES (?, ?) " +
                                                   "ON DUPLICATE KEY UPDATE waiting_list = waiting_list + ?";
                            PreparedStatement updateWaitStmt = conn.prepareStatement(updateWaitQuery);
                            updateWaitStmt.setInt(1, trainNo);
                            updateWaitStmt.setInt(2, waitingSeats);
                            updateWaitStmt.setInt(3, waitingSeats);
                            updateWaitStmt.executeUpdate();

                            // Add waiting list passengers
                            for (int i = 0; i < waitingSeats; i++) {
                                System.out.println("Enter waiting passenger " + (i + 1) + " details:");
                                System.out.println("Name:");
                                String name = sc.nextLine();
                                System.out.println("Age:");
                                int age = sc.nextInt();
                                sc.nextLine();
                                System.out.println("Gender:");
                                String gender = sc.nextLine();

                                String insertPassengerQuery = "INSERT INTO passenger (pnr, name, age, gender, status) VALUES (?, ?, ?, ?, ?)";
                                PreparedStatement passengerStmt = conn.prepareStatement(insertPassengerQuery);
                                passengerStmt.setInt(1, pnr);
                                passengerStmt.setString(2, name);
                                passengerStmt.setInt(3, age);
                                passengerStmt.setString(4, gender);
                                passengerStmt.setString(5, "Waiting List");
                                passengerStmt.executeUpdate();
                            }
                        }

                        conn.commit();
                        System.out.println("Booking completed. Your PNR is: " + pnr);
                        if (availableSeats > 0) {
                            System.out.println(availableSeats + " seats confirmed");
                        }
                        if (requestedSeats - availableSeats > 0) {
                            System.out.println((requestedSeats - availableSeats) + " seats in waiting list");
                        }
                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(true);
                    }
                }
            } catch (SQLException e) {
                if (e.getMessage().contains("Unable to generate unique PNR")) {
                    System.out.println(e.getMessage());
                    return;
                }
                throw e;
            }
        }
    }

    // Method to book seats and assign a PNR number
    private void bookSeats(int trainNo, int seatsToBook, String username, String status, String waitingListNumber, int pnr) throws SQLException {
        conn.setAutoCommit(false);
        try {
            // First update available seats
            String updateSeatsQuery = "UPDATE train SET available_seats = available_seats - ? WHERE train_no = ?";
            PreparedStatement updateSeatsStmt = conn.prepareStatement(updateSeatsQuery);
            updateSeatsStmt.setInt(1, seatsToBook);
            updateSeatsStmt.setInt(2, trainNo);
            int updatedRows = updateSeatsStmt.executeUpdate();
            
            if (updatedRows == 0) {
                throw new SQLException("Failed to update available seats");
            }

            // Insert PNR record
            String insertPNRQuery = "INSERT INTO pnr (user_name, train_no, status, waiting_list, pr) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement insertPNRStmt = conn.prepareStatement(insertPNRQuery);
            insertPNRStmt.setString(1, username);
            insertPNRStmt.setInt(2, trainNo);
            insertPNRStmt.setString(3, status);
            insertPNRStmt.setString(4, waitingListNumber);
            insertPNRStmt.setInt(5, pnr);
            insertPNRStmt.executeUpdate();

            // Insert passenger details
            for (int i = 0; i < seatsToBook; i++) {
                System.out.println("Enter passenger " + (i + 1) + " name:");
                String name = sc.nextLine();
                System.out.println("Enter age:");
                int age = sc.nextInt();
                sc.nextLine();
                System.out.println("Enter gender:");
                String gender = sc.nextLine();

                String insertPassengerQuery = "INSERT INTO passenger (pnr, name, age, gender, status) VALUES (?, ?, ?, ?, ?)";
                PreparedStatement insertPassengerStmt = conn.prepareStatement(insertPassengerQuery);
                insertPassengerStmt.setInt(1, pnr);
                insertPassengerStmt.setString(2, name);
                insertPassengerStmt.setInt(3, age);
                insertPassengerStmt.setString(4, gender);
                insertPassengerStmt.setString(5, status);
                insertPassengerStmt.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // Method to update waiting list
    private void updateWaitingList(int trainNo, int newWaitingCount) throws SQLException {
        // First, get the current maximum waiting list number for this train
        String getMaxWaitingQuery = "SELECT MAX(CAST(waiting_list AS SIGNED)) as max_waiting FROM pnr WHERE train_no=? AND status='Waiting List'";
        PreparedStatement maxStmt = conn.prepareStatement(getMaxWaitingQuery);
        maxStmt.setInt(1, trainNo);
        ResultSet maxRs = maxStmt.executeQuery();
        
        int startWaitingNumber = 1;
        if (maxRs.next() && maxRs.getString("max_waiting") != null) {
            startWaitingNumber = maxRs.getInt("max_waiting") + 1;
        }

        // Update the total waiting count in wait table
        String checkWaitingListQuery = "SELECT waiting_list FROM wait WHERE train_no=?";
        PreparedStatement checkStmt = conn.prepareStatement(checkWaitingListQuery);
        checkStmt.setInt(1, trainNo);
        ResultSet rs = checkStmt.executeQuery();

        if (rs.next()) {
            int currentWaitingCount = rs.getInt("waiting_list");
            String updateWaitingListQuery = "UPDATE wait SET waiting_list=? WHERE train_no=?";
            PreparedStatement updateStmt = conn.prepareStatement(updateWaitingListQuery);
            updateStmt.setInt(1, currentWaitingCount + newWaitingCount);
            updateStmt.setInt(2, trainNo);
            updateStmt.executeUpdate();
        } else {
            String insertWaitingListQuery = "INSERT INTO wait (train_no, waiting_list) VALUES (?, ?)";
            PreparedStatement insertStmt = conn.prepareStatement(insertWaitingListQuery);
            insertStmt.setInt(1, trainNo);
            insertStmt.setInt(2, newWaitingCount);
            insertStmt.executeUpdate();
        }

        // Store the starting waiting number to use in addToWaitingList method
        this.currentWaitingNumber = startWaitingNumber;
    }

    // Method to add passengers to the waiting list
    private void addToWaitingList(int trainNo, int waitingSeats, String username, int pnr) throws SQLException {
        try {
            conn.setAutoCommit(false);
            
            for (int i = 0; i < waitingSeats; i++) {
                System.out.println("Enter waiting passenger " + (i + 1) + " name:");
                String name = sc.nextLine();
                System.out.println("Enter age:");
                int age = sc.nextInt();
                sc.nextLine();
                System.out.println("Enter gender:");
                String gender = sc.nextLine();

                String insertPassengerQuery = "INSERT INTO passenger (pnr, name, age, gender, status) VALUES (?, ?, ?, ?, ?)";
                PreparedStatement insertPassengerStmt = conn.prepareStatement(insertPassengerQuery);
                insertPassengerStmt.setInt(1, pnr);
                insertPassengerStmt.setString(2, name);
                insertPassengerStmt.setInt(3, age);
                insertPassengerStmt.setString(4, gender);
                insertPassengerStmt.setString(5, "Waiting List");
                insertPassengerStmt.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // Method to check PNR status
    public void checkPNRStatus() throws SQLException {
        System.out.println("Enter your PNR number:");
        int pnr = sc.nextInt();
        sc.nextLine();

        String query = "SELECT p.*, t.train_no, t.start_city, t.end_city, t.start_time " +
                      "FROM pnr p JOIN train t ON p.train_no = t.train_no WHERE p.pr=?";
        PreparedStatement pst = conn.prepareStatement(query);
        pst.setInt(1, pnr);
        ResultSet rs = pst.executeQuery();

        if (rs.next()) {
            System.out.println("\nPNR Status:");
            System.out.println("Train No: " + rs.getString("train_no"));
            System.out.println("From: " + rs.getString("start_city"));
            System.out.println("To: " + rs.getString("end_city"));
            System.out.println("Start Time: " + rs.getString("start_time"));
            System.out.println("Status: " + rs.getString("status"));
            
            if (rs.getString("status").equals("Waiting List")) {
                System.out.println("Waiting List Numbers: " + rs.getString("waiting_list"));
            }
            
            // Show passenger details
            String passengerQuery = "SELECT name, age, gender FROM passenger WHERE pnr=?";
            PreparedStatement passengerStmt = conn.prepareStatement(passengerQuery);
            passengerStmt.setInt(1, pnr);
            ResultSet passengerRs = passengerStmt.executeQuery();
            
            System.out.println("\nPassenger Details:");
            while (passengerRs.next()) {
                System.out.println("Name: " + passengerRs.getString("name"));
                System.out.println("Age: " + passengerRs.getInt("age"));
                System.out.println("Gender: " + passengerRs.getString("gender"));
                System.out.println("------------------------");
            }
        } else {
            System.out.println("No booking found with the provided PNR number.");
        }
    }

    // Method for user signup
    public void signUp() throws SQLException {
        String username, password, name, gmailId, phoneNo, gender;

        while (true) {
            System.out.println("Enter your username:");
            username = sc.nextLine();

            System.out.println("Enter your password (at least 4 characters):");
            password = sc.nextLine();
            if (password.length() < 4) {
                System.out.println("Password must be at least 4 characters long. Please try again.");
                continue;
            }

            System.out.println("Enter your name:");
            name = sc.nextLine();

            System.out.println("Enter your Gmail ID (must end with @gmail.com, @yahoo.com, .in, or .ac.in):");
            gmailId = sc.nextLine();
            if (!gmailId.matches(".@(gmail\\.com|yahoo\\.com|.\\.in|.*\\.ac\\.in)$")) {
                System.out.println("Invalid Gmail ID. Please enter a valid email ending with @gmail.com, @yahoo.com, .in, or .ac.in.");
                continue;
            }

            System.out.println("Enter your phone number (10 digits):");
            phoneNo = sc.nextLine();
            if (!phoneNo.matches("\\d{10}")) {
                System.out.println("Phone number must be exactly 10 digits. Please try again.");
                continue;
            }

            System.out.println("Enter your gender:");
            gender = sc.nextLine();

            // If all validations pass, break the loop
            break;
        }

        // Insert the user into the database
        String insertUserQuery = "INSERT INTO user (uname, pass, name, gmail_id, phone_no, gender) VALUES (?, ?, ?, ?, ?, ?)";
        PreparedStatement pst = conn.prepareStatement(insertUserQuery);
        pst.setString(1, username);
        pst.setString(2, password);
        pst.setString(3, name);
        pst.setString(4, gmailId);
        pst.setString(5, phoneNo);
        pst.setString(6, gender);
        pst.executeUpdate();

        System.out.println("User registered successfully!");
    }

    // Method to verify the user
    public boolean verifyUser(String username, String password) throws SQLException {
        String query = "SELECT * FROM user WHERE uname=? AND pass=?";
        PreparedStatement pst = conn.prepareStatement(query);
        pst.setString(1, username);
        pst.setString(2, password);
        ResultSet rs = pst.executeQuery();

        return rs.next();  // If user is found, return true
    }

    // Method to cancel a ticket
    public void cancelTicket() throws SQLException {
        System.out.println("Enter PNR number to cancel:");
        int pnr = sc.nextInt();
        sc.nextLine();

        conn.setAutoCommit(false);
        try {
            // Get ticket and train details
            String pnrQuery = "SELECT p.*, t.train_no, t.start_city, t.end_city, t.start_time, t.available_seats " +
                             "FROM pnr p JOIN train t ON p.train_no = t.train_no WHERE p.pr=?";
            PreparedStatement pnrStmt = conn.prepareStatement(pnrQuery);
            pnrStmt.setInt(1, pnr);
            ResultSet pnrRs = pnrStmt.executeQuery();

            if (!pnrRs.next()) {
                System.out.println("PNR not found.");
                return;
            }

            int trainNo = pnrRs.getInt("train_no");

            // Count confirmed seats being cancelled
            String passengerQuery = "SELECT COUNT(*) as count FROM passenger WHERE pnr=? AND status='Confirmed'";
            PreparedStatement countStmt = conn.prepareStatement(passengerQuery);
            countStmt.setInt(1, pnr);
            ResultSet countRs = countStmt.executeQuery();
            int confirmedCount = 0;
            if (countRs.next()) {
                confirmedCount = countRs.getInt("count");
            }

            // Show ticket details
            displayTicketDetails(pnr, pnrRs);

            System.out.println("\nDo you want to cancel this ticket? (Y/N)");
            String confirm = sc.nextLine();

            if (confirm.equalsIgnoreCase("Y")) {
                // Process refund
                processRefund(pnr, pnrRs);

                // Delete all passenger records
                String deletePassengerQuery = "DELETE FROM passenger WHERE pnr=?";
                PreparedStatement deletePassengerStmt = conn.prepareStatement(deletePassengerQuery);
                deletePassengerStmt.setInt(1, pnr);
                deletePassengerStmt.executeUpdate();

                // Update PNR status to Cancelled
                String updatePnrQuery = "UPDATE pnr SET status='Cancelled', waiting_list=NULL WHERE pr=?";
                PreparedStatement updatePnrStmt = conn.prepareStatement(updatePnrQuery);
                updatePnrStmt.setInt(1, pnr);
                updatePnrStmt.executeUpdate();

                // If there were confirmed seats, promote waiting list passengers
                if (confirmedCount > 0) {
                    promoteWaitingListPassengers(trainNo, confirmedCount);
                }

                conn.commit();
                System.out.println("Ticket cancelled successfully.");
            } else {
                System.out.println("Cancellation aborted.");
            }
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private void promoteWaitingListPassengers(int trainNo, int seatsAvailable) throws SQLException {
        // Get current available seats
        String seatQuery = "SELECT available_seats FROM train WHERE train_no=?";
        PreparedStatement seatStmt = conn.prepareStatement(seatQuery);
        seatStmt.setInt(1, trainNo);
        ResultSet seatRs = seatStmt.executeQuery();
        seatRs.next();
        int currentAvailableSeats = seatRs.getInt("available_seats");

        // Check waiting list
        String waitCountQuery = "SELECT waiting_list FROM wait WHERE train_no=?";
        PreparedStatement waitCountStmt = conn.prepareStatement(waitCountQuery);
        waitCountStmt.setInt(1, trainNo);
        ResultSet waitCountRs = waitCountStmt.executeQuery();

        if (!waitCountRs.next() || waitCountRs.getInt("waiting_list") == 0) {
            // No waiting list passengers, just add seats back
            String updateSeatsQuery = "UPDATE train SET available_seats = ? WHERE train_no = ?";
            PreparedStatement updateSeatsStmt = conn.prepareStatement(updateSeatsQuery);
            updateSeatsStmt.setInt(1, currentAvailableSeats + seatsAvailable);
            updateSeatsStmt.setInt(2, trainNo);
            updateSeatsStmt.executeUpdate();
            System.out.println("No waiting list passengers. Available seats updated to: " + (currentAvailableSeats + seatsAvailable));
            return;
        }

        int currentWaitingCount = waitCountRs.getInt("waiting_list");
        System.out.println("Current waiting list count: " + currentWaitingCount);
        System.out.println("Seats being freed: " + seatsAvailable);

        // Get waiting list passengers in order
        String waitingQuery = "SELECT p.pr, p.waiting_list FROM pnr p " +
                             "WHERE p.train_no=? AND p.status='Waiting List' " +
                             "ORDER BY CAST(SUBSTRING_INDEX(waiting_list, '-', 1) AS SIGNED)";
        PreparedStatement waitingStmt = conn.prepareStatement(waitingQuery);
        waitingStmt.setInt(1, trainNo);
        ResultSet waitingRs = waitingStmt.executeQuery();

        int promotedCount = 0;
        int remainingSeats = seatsAvailable;

        while (waitingRs.next() && remainingSeats > 0) {
            int waitingPnr = waitingRs.getInt("pr");

            // Get waiting list passengers for this PNR
            String passengerCountQuery = "SELECT COUNT(*) as count FROM passenger WHERE pnr=? AND status='Waiting List'";
            PreparedStatement countStmt = conn.prepareStatement(passengerCountQuery);
            countStmt.setInt(1, waitingPnr);
            ResultSet countRs = countStmt.executeQuery();
            countRs.next();
            int passengersToPromote = Math.min(countRs.getInt("count"), remainingSeats);

            if (passengersToPromote > 0) {
                // Update passenger status to confirmed
                String updatePassengerQuery = "UPDATE passenger SET status='Confirmed' " +
                                              "WHERE pnr=? AND status='Waiting List' " +
                                              "ORDER BY name LIMIT ?";
                PreparedStatement updatePassengerStmt = conn.prepareStatement(updatePassengerQuery);
                updatePassengerStmt.setInt(1, waitingPnr);
                updatePassengerStmt.setInt(2, passengersToPromote);
                updatePassengerStmt.executeUpdate();

                // Update PNR status
                boolean allConfirmed = (passengersToPromote == countRs.getInt("count"));
                String updatePnrQuery = "UPDATE pnr SET status=?, waiting_list=? WHERE pr=?";
                PreparedStatement updatePnrStmt = conn.prepareStatement(updatePnrQuery);
                updatePnrStmt.setString(1, allConfirmed ? "Confirmed" : "Partially Confirmed");
                updatePnrStmt.setString(2, allConfirmed ? null : 
                                        (currentWaitingCount - promotedCount) + "-" + 
                                        (currentWaitingCount - promotedCount + countRs.getInt("count") - passengersToPromote));
                updatePnrStmt.setInt(3, waitingPnr);
                updatePnrStmt.executeUpdate();

                promotedCount += passengersToPromote;
                remainingSeats -= passengersToPromote;
            }
        }

        // Update wait table
        int newWaitingCount = currentWaitingCount - promotedCount;
        if (newWaitingCount <= 0) {
            // Delete wait record if no more waiting passengers
            String deleteWaitQuery = "DELETE FROM wait WHERE train_no=?";
            PreparedStatement deleteWaitStmt = conn.prepareStatement(deleteWaitQuery);
            deleteWaitStmt.setInt(1, trainNo);
            deleteWaitStmt.executeUpdate();
        } else {
            // Update wait count
            String updateWaitQuery = "UPDATE wait SET waiting_list=? WHERE train_no=?";
            PreparedStatement updateWaitStmt = conn.prepareStatement(updateWaitQuery);
            updateWaitStmt.setInt(1, newWaitingCount);
            updateWaitStmt.setInt(2, trainNo);
            updateWaitStmt.executeUpdate();
        }

        // Final update of available seats
        String finalUpdateSeatsQuery = "UPDATE train SET available_seats=? WHERE train_no=?";
        PreparedStatement finalUpdateSeatsStmt = conn.prepareStatement(finalUpdateSeatsQuery);
        finalUpdateSeatsStmt.setInt(1, currentAvailableSeats + seatsAvailable - promotedCount);
        finalUpdateSeatsStmt.setInt(2, trainNo);
        finalUpdateSeatsStmt.executeUpdate();

        System.out.println("\nFinal Status:");
        System.out.println("- Promoted passengers: " + promotedCount);
        System.out.println("- Remaining in waiting list: " + newWaitingCount);
        System.out.println("- Available seats: " + (currentAvailableSeats + seatsAvailable - promotedCount));
    }

    private void updateWaitingListNumbers(int trainNo) throws SQLException {
        // Get all waiting list PNRs in order
        String waitingQuery = "SELECT pr FROM pnr WHERE train_no=? AND status='Waiting List' " +
                             "ORDER BY CAST(SUBSTRING_INDEX(waiting_list, '-', 1) AS SIGNED)";
        PreparedStatement waitingStmt = conn.prepareStatement(waitingQuery);
        waitingStmt.setInt(1, trainNo);
        ResultSet waitingRs = waitingStmt.executeQuery();

        int newWaitingNumber = 1;
        while (waitingRs.next()) {
            int waitingPnr = waitingRs.getInt("pr");
            
            // Get number of passengers in this PNR
            String countQuery = "SELECT COUNT(*) as count FROM passenger WHERE pnr=?";
            PreparedStatement countStmt = conn.prepareStatement(countQuery);
            countStmt.setInt(1, waitingPnr);
            ResultSet countRs = countStmt.executeQuery();
            countRs.next();
            int passengerCount = countRs.getInt("count");

            // Update waiting list range
            String updateQuery = "UPDATE pnr SET waiting_list=? WHERE pr=?";
            PreparedStatement updateStmt = conn.prepareStatement(updateQuery);
            updateStmt.setString(1, newWaitingNumber + "-" + (newWaitingNumber + passengerCount - 1));
            updateStmt.setInt(2, waitingPnr);
            updateStmt.executeUpdate();

            newWaitingNumber += passengerCount;
        }
    }

    private void displayTicketDetails(int pnr, ResultSet pnrRs) throws SQLException {
        System.out.println("\nTicket Details:");
        System.out.println("Train No: " + pnrRs.getInt("train_no"));
        System.out.println("From: " + pnrRs.getString("start_city"));
        System.out.println("To: " + pnrRs.getString("end_city"));
        System.out.println("Start Time: " + pnrRs.getString("start_time"));
        System.out.println("Status: " + pnrRs.getString("status"));

        // Show passenger details
        String passengerQuery = "SELECT * FROM passenger WHERE pnr=?";
        PreparedStatement passengerStmt = conn.prepareStatement(passengerQuery);
        passengerStmt.setInt(1, pnr);
        ResultSet passengerRs = passengerStmt.executeQuery();

        System.out.println("\nPassenger Details:");
        while (passengerRs.next()) {
            System.out.println("Name: " + passengerRs.getString("name"));
            System.out.println("Age: " + passengerRs.getInt("age"));
            System.out.println("Gender: " + passengerRs.getString("gender"));
            System.out.println("Status: " + passengerRs.getString("status"));
            System.out.println("------------------------");
        }
    }

    private void processRefund(int pnr, ResultSet pnrRs) throws SQLException {
        System.out.println("Enter your UPI ID for refund:");
        String upiId = sc.nextLine();

        // Get the train number from the PNR record
        int trainNo = pnrRs.getInt("train_no");

        // Fetch the ticket price (amount) from the train table
        String amountQuery = "SELECT amount FROM train WHERE train_no=?";
        PreparedStatement amountStmt = conn.prepareStatement(amountQuery);
        amountStmt.setInt(1, trainNo);
        ResultSet amountRs = amountStmt.executeQuery();
        if (!amountRs.next()) {
            throw new SQLException("Train not found for train number: " + trainNo);
        }
        int amountPerTicket = amountRs.getInt("amount");

        // Get the total number of passengers for the PNR
        String countQuery = "SELECT COUNT(*) as count FROM passenger WHERE pnr=?";
        PreparedStatement countStmt = conn.prepareStatement(countQuery);
        countStmt.setInt(1, pnr);
        ResultSet countRs = countStmt.executeQuery();
        countRs.next();
        int passengerCount = countRs.getInt("count");

        // Calculate the total refund amount
        int refundAmount = passengerCount * amountPerTicket;

        // Verify UPI ID and process refund
        String checkUpiQuery = "SELECT balance FROM upi WHERE upi_id=?";
        PreparedStatement checkUpiStmt = conn.prepareStatement(checkUpiQuery);
        checkUpiStmt.setString(1, upiId);
        ResultSet upiRs = checkUpiStmt.executeQuery();

        if (upiRs.next()) {
            // Process refund
            int currentBalance = upiRs.getInt("balance");
            int newBalance = currentBalance + refundAmount;

            String refundQuery = "UPDATE upi SET balance = ? WHERE upi_id=?";
            PreparedStatement refundStmt = conn.prepareStatement(refundQuery);
            refundStmt.setInt(1, newBalance);
            refundStmt.setString(2, upiId);
            refundStmt.executeUpdate();

            System.out.println("Refund of Rs. " + refundAmount + " processed to UPI ID: " + upiId);
            System.out.println("New balance: Rs. " + newBalance);
        } else {
            throw new SQLException("Invalid UPI ID provided for refund");
        }
    }

    // Main method to run the system
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/project";
        String user = "root";
        String password = "Tejeshwar@2012"; // change to your DB password

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            Scanner sc = new Scanner(System.in);
            TrainReservationSystem trs = new TrainReservationSystem(conn);

            System.out.println("Welcome to the Train Reservation System!");
            while (true) {
                System.out.println("1. Login");
                System.out.println("2. Sign Up");
                System.out.println("3. Exit");
                int choice = sc.nextInt();
                sc.nextLine();  // consume newline

                if (choice == 1) {
                    System.out.println("Enter username:");
                    String uname = sc.nextLine();
                    System.out.println("Enter password:");
                    String pass = sc.nextLine();

                    if (trs.verifyUser(uname, pass)) {
                        boolean loggedIn = true;
                        while (loggedIn) {
                            System.out.println("\n1. Book a Ticket");
                            System.out.println("2. Cancel a Ticket");
                            System.out.println("3. Check PNR Status");
                            System.out.println("4. Logout");
                            int option = sc.nextInt();
                            sc.nextLine();

                            switch (option) {
                                case 1:
                                    trs.bookTicket(uname);
                                    break;
                                case 2:
                                    trs.cancelTicket();
                                    break;
                                case 3:
                                    trs.checkPNRStatus();
                                    break;
                                case 4:
                                    loggedIn = false;
                                    System.out.println("Logged out successfully");
                                    break;
                                default:
                                    System.out.println("Invalid option");
                            }
                        }
                    } else {
                        System.out.println("Invalid credentials. Please try again.");
                    }
                } else if (choice == 2) {
                    trs.signUp();
                } else if (choice == 3) {
                    System.out.println("Exiting...");
                    break;
                }
            }

        } 
        catch (SQLException e)
         {
            e.printStackTrace();
}
}
}