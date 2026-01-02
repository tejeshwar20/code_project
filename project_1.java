import java.sql.*;
import java.util.*;

public class TrainReservationSystem {
    private Connection conn;
    private Scanner sc;
    private int currentWaitingNumber;

    public TrainReservationSystem(Connection conn) {
        this.conn = conn;
        sc = new Scanner(System.in);
    }

    private int generateRandomPNR() throws SQLException {
        Random random = new Random();
        int maxAttempts = 5;
        int attempts = 0;

        while (attempts < maxAttempts) {
            int pnr = 1000 + random.nextInt(999999000);

            String checkPNRQuery = "SELECT pr FROM pnr WHERE pr=?";
            PreparedStatement pst = conn.prepareStatement(checkPNRQuery);
            pst.setInt(1, pnr);
            ResultSet rs = pst.executeQuery();

            if (!rs.next()) {
                return pnr;
            }

            attempts++;
        }

        throw new SQLException("Unable to generate unique PNR. Please try booking again.");
    }

    private boolean pay(int amount) throws SQLException {
        System.out.println("Enter your UPI ID:");
        String upiId = sc.nextLine();

        String checkBalanceQuery = "SELECT balance FROM upi WHERE upi_id=?";
        PreparedStatement checkBalanceStmt = conn.prepareStatement(checkBalanceQuery);
        checkBalanceStmt.setString(1, upiId);
        ResultSet rs = checkBalanceStmt.executeQuery();

        if (rs.next()) {
            int balance = rs.getInt("balance");

            if (balance >= amount) {
                int newBalance = balance - amount;
                String updateBalanceQuery = "UPDATE upi SET balance=? WHERE upi_id=?";
                PreparedStatement updateBalanceStmt = conn.prepareStatement(updateBalanceQuery);
                updateBalanceStmt.setInt(1, newBalance);
                updateBalanceStmt.setString(2, upiId);
                updateBalanceStmt.executeUpdate();

                System.out.println("Payment Successful! Your new balance is: " + newBalance);
                return true;
            } else {
                System.out.println("Insufficient balance. You need " + (amount - balance) + " more.");
                return false;
            }
        } else {
            System.out.println("UPI ID not found in the system.");
            return false;
        }
    }

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

        System.out.println("Available trains:");
        do {
            System.out.println("Train No: " + rs.getInt("train_no") + ", Start Time: " + rs.getString("start_time") +
                    ", Total Time: " + rs.getString("total_time") + ", Available Seats: " +
                    rs.getInt("available_seats") + ", Amount: " + rs.getInt("amount"));
        } while (rs.next());

        System.out.println("Enter Train No to book:");
        int trainNo = sc.nextInt();
        sc.nextLine();

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
        sc.nextLine();

        int availableSeats = checkRs.getInt("available_seats");
        int amount = checkRs.getInt("amount") * requestedSeats;

        int pnr = generateRandomPNR();

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
                        String waitQuery = "SELECT waiting_list FROM wait WHERE train_no=?";
                        PreparedStatement waitStmt = conn.prepareStatement(waitQuery);
                        waitStmt.setInt(1, trainNo);
                        ResultSet waitRs = waitStmt.executeQuery();
                        int currentWaitingCount = 0;

                        String waitUpdateQuery;
                        PreparedStatement waitUpdateStmt;

                        if (waitRs.next()) {
                            currentWaitingCount = waitRs.getInt("waiting_list");
                            waitUpdateQuery = "UPDATE wait SET waiting_list = waiting_list + ? WHERE train_no = ?";
                            waitUpdateStmt = conn.prepareStatement(waitUpdateQuery);
                            waitUpdateStmt.setInt(1, requestedSeats);
                            waitUpdateStmt.setInt(2, trainNo);
                        } else {
                            waitUpdateQuery = "INSERT INTO wait (train_no, waiting_list) VALUES (?, ?)";
                            waitUpdateStmt = conn.prepareStatement(waitUpdateQuery);
                            waitUpdateStmt.setInt(1, trainNo);
                            waitUpdateStmt.setInt(2, requestedSeats);
                        }
                        waitUpdateStmt.executeUpdate();

                        String pnrQuery = "INSERT INTO pnr (user_name, train_no, status, waiting_list, pr) VALUES (?, ?, ?, ?, ?)";
                        PreparedStatement pnrStmt = conn.prepareStatement(pnrQuery);
                        pnrStmt.setString(1, username);
                        pnrStmt.setInt(2, trainNo);
                        pnrStmt.setString(3, "Waiting List");
                        pnrStmt.setString(4, (currentWaitingCount + 1) + "-" + (currentWaitingCount + requestedSeats));
                        pnrStmt.setInt(5, pnr);
                        pnrStmt.executeUpdate();

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
                        if (availableSeats > 0) {
                            String updateSeatsQuery = "UPDATE train SET available_seats = 0 WHERE train_no = ?";
                            PreparedStatement updateSeatsStmt = conn.prepareStatement(updateSeatsQuery);
                            updateSeatsStmt.setInt(1, trainNo);
                            updateSeatsStmt.executeUpdate();

                            String pnrQuery = "INSERT INTO pnr (user_name, train_no, status, pr) VALUES (?, ?, ?, ?)";
                            PreparedStatement pnrStmt = conn.prepareStatement(pnrQuery);
                            pnrStmt.setString(1, username);
                            pnrStmt.setInt(2, trainNo);
                            pnrStmt.setString(3, "Partially Booked");
                            pnrStmt.setInt(4, pnr);
                            pnrStmt.executeUpdate();

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

                        int waitingSeats = requestedSeats - availableSeats;
                        if (waitingSeats > 0) {
                            String waitQuery = "SELECT COALESCE(SUM(waiting_list), 0) as total_waiting FROM wait WHERE train_no=?";
                            PreparedStatement waitStmt = conn.prepareStatement(waitQuery);
                            waitStmt.setInt(1, trainNo);
                            ResultSet waitRs = waitStmt.executeQuery();
                            int currentWaitingCount = 0;
                            if (waitRs.next()) {
                                currentWaitingCount = waitRs.getInt("total_waiting");
                            }

                            String updateWaitQuery = "INSERT INTO wait (train_no, waiting_list) VALUES (?, ?) " +
                                    "ON DUPLICATE KEY UPDATE waiting_list = waiting_list + ?";
                            PreparedStatement updateWaitStmt = conn.prepareStatement(updateWaitQuery);
                            updateWaitStmt.setInt(1, trainNo);
                            updateWaitStmt.setInt(2, waitingSeats);
                            updateWaitStmt.setInt(3, waitingSeats);
                            updateWaitStmt.executeUpdate();

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

    private void bookSeats(int trainNo, int seatsToBook, String username, String status, String waitingListNumber, int pnr) throws SQLException {
        conn.setAutoCommit(false);
        try {
            String updateSeatsQuery = "UPDATE train SET available_seats = available_seats - ? WHERE train_no = ?";
            PreparedStatement updateSeatsStmt = conn.prepareStatement(updateSeatsQuery);
            updateSeatsStmt.setInt(1, seatsToBook);
            updateSeatsStmt.setInt(2, trainNo);
            int updatedRows = updateSeatsStmt.executeUpdate();

            if (updatedRows == 0) {
                throw new SQLException("Failed to update available seats");
            }

            String insertPNRQuery = "INSERT INTO pnr (user_name, train_no, status, waiting_list, pr) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement insertPNRStmt = conn.prepareStatement(insertPNRQuery);
            insertPNRStmt.setString(1, username);
            insertPNRStmt.setInt(2, trainNo);
            insertPNRStmt.setString(3, status);
            insertPNRStmt.setString(4, waitingListNumber);
            insertPNRStmt.setInt(5, pnr);
            insertPNRStmt.executeUpdate();

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
}
