

# Train Reservation System

This project implements a console-based Train Reservation System using Java and MySQL.
The system enables users to register, log in, book train tickets, check PNR status, and cancel booked tickets.
It also maintains waiting lists, processes UPI-based mock payments, and handles automatic promotion of passengers from the waiting list upon cancellation.

---

## 1. Overview

The Train Reservation System is designed to simulate the core functionalities of a railway ticket booking system. It demonstrates:

* database connectivity using JDBC
* transaction handling
* input validation
* exception management
* waiting list and PNR management mechanisms

The application operates entirely through a command-line interface.

---

## 2. Features

### User Services

* New user registration
* User login authentication

### Ticket Booking

* Search trains by source and destination
* Automatic PNR generation
* Booking based on seat availability
* Partial booking with waiting list assignment
* Passenger-level status handling

### Payment Handling

* UPI-based simulated payment validation
* Balance verification before booking
* Refund processing during cancellation

### PNR Status Enquiry

* Current booking status
* Waiting list position (if applicable)
* Passenger details retrieval

### Ticket Cancellation

* Refund computation and processing
* Release of seats upon cancellation
* Automatic movement of waiting list passengers to confirmed status

---

## 3. Technology Stack

| Component             | Details             |
| --------------------- | ------------------- |
| Programming Language  | Java                |
| Database              | MySQL               |
| Database Connectivity | JDBC                |
| Execution Environment | Console Application |

---

## 4. Database Schema Requirements

The system requires the following tables:

* user
* train
* pnr
* passenger
* upi
* wait

Each table should be designed with appropriate primary and foreign keys to ensure data integrity.

---

## 5. How to Execute

### Step 1: Prerequisites

* Java JDK 8 or later
* MySQL Server
* MySQL JDBC Driver

### Step 2: Create Database

```sql
CREATE DATABASE project;
USE project;
```

### Step 3: Configure Database Credentials

Modify in the `main` method:

```java
String url = "jdbc:mysql://localhost:3306/project";
String user = "root";
String password = "your_mysql_password";
```

### Step 4: Compile the Program

```bash
javac TrainReservationSystem.java
```

### Step 5: Run the Program

```bash
java TrainReservationSystem
```

---

## 6. Functional Flow

1. User signs up or logs in
2. User selects one of the operations:

   * Book Ticket
   * Cancel Ticket
   * Check PNR Status
   * Logout
3. System interacts with MySQL database using JDBC
4. Transactions are used to maintain data consistency
5. Waiting list and seat availability are automatically updated

---

## 7. Constraints and Validations

* Password must contain at least four characters
* Phone number must be exactly ten digits
* Accepted email formats include:

  * `@gmail.com`
  * `@yahoo.com`
  * `.in`
  * `.ac.in`
* One PNR is generated per booking
* Waiting list positions are allocated in sequential order
