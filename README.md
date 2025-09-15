# Store Management System


## Overview
A clean **client server**, **object‑oriented** **store network management** project written in Java.  
This repository includes a multi‑layered architecture with **Models**, **Services**, **Server/Client**, **Utilities**, and **Custom Exceptions**. 

---

## System Purpose (Goals & Scope)

A full **Store Network Management** featuring:

-  **Clear OOP design**
- **Per-branch inventory** that updates in **real time** for all employees in the branch.
- **Central customer registry** shared across **all branches**.
- **Employee data management** across branches (**admins/managers only**).
- **Cross-branch chat system** for real-time communication between employees.
- **Double-login prevention** (the same user cannot be signed in from multiple devices).
- **Server with per‑client threading** (`ClientHandler`) for concurrent clients.
- **Centralized error handling with typed custom exceptions**
- **Client–Server architecture** 
- **Login  with authentication**  
- **Reports module (by branch / product / category)**


---

##  Architecture
- **Models (`Models.*`)** – Domain entities (customers, employees, products, etc.)
- **Services (`Services.*`)** – Business logic and validation
- **Server (`Server.*`)** – TCP server; one `Thread` per client connection
- **Utilities (`Server.Utils.*`)** – I/O & JSON helpers
- **Exceptions (`Exceptions.*`)** – Typed exception hierarchy

---


##  Class Reference

| Package        | Kind             | Class                 | Purpose                                             |
|----------------|------------------|-----------------------|-----------------------------------------------------|
| `Client`       | `class`          | **ClientApp**         | Starts the client; logs into server.                |
| `Exceptions`   | `class`          | **CustomExceptions**  | Defines typed domain/validation errors.             |
| `Models`       | `class`          | **Branch**            | Represents a store branch (id, name).               |
| `Models`       | `abstract class` | **Customer**          | Base customer; validation and pricing hooks.        |
| `Models`       | `class`          | **Employee**          | Employee data: branch, role, credentials.           |
| `Models`       | `class`          | **Product**           | Product data: category, price, stock, branch.       |
| `Models`       | `class`          | **NewCustomer**       | Pricing strategy for new customers.                 |
| `Models`       | `class`          | **ReturningCustomer** | Pricing strategy for returning customers.           |
| `Models`       | `class`          | **VIPCustomer**       | Pricing strategy for VIP customers.                 |
| `Models`       | `enum`           | **Role**              | Enumerates employee roles.                          |
| `Server`       | `class`          | **ServerApp**         | Bootstraps services; listens for clients.           |
| `Server`       | `class`          | **ClientHandler**     | Handles a single client on its own thread.          |
| `Server.Utils` | `class`          | **FileUtils**         | File I/O and JSON serialization helpers.            |
| `Services`     | `class`          | **AuthService**       | Login/logout; session management; block duplicates. |
| `Services`     | `class`          | **BranchService**     | Branch validation                                   |
| `Services`     | `class`          | **CustomerService**   | Customer and searches validation.                   |
| `Services`     | `class`          | **EmployeeService**   | Employee role/branch assignment.                    |
| `Services`     | `class`          | **ProductService**    | Product inventory operations.                       |
| `Services`     | `class`          | **SaleService**       | Validate stock; apply pricing; record sales.        |
| `Services`     | `class`          | **ChatService**       | Queue and route cross-branch chats.                 |
| `Services`     | `class`          | **LogsService**       | Write logs.                                         |


---

##  Tech Stack

- **Language:** Java  
- **Architecture:** Client–Server  
- **Data Format:** JSON
---

## Developed by
Stav Sivilya, 318430667
Ariel Kurichar, 209506047
Roman Dubrovin, 324306661
Aviv Hanoon, 213389315
Stanislav Berlovich, 323539015

## Course
Internet Programming in Java

## Lecturer
Mr. Roey Zimon

---
