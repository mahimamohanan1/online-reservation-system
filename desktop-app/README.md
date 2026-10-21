# RailEase Desktop (Java Swing + SQLite)

This standalone desktop module implements the Java GUI reservation assignment. It uses JDBC with a local SQLite database, so no MySQL server is required. The existing React/Spring Boot web application remains in the project root and `backend/`.

## Run

Requires JDK 17 or newer and Maven. Open PowerShell in this folder and run:

```powershell
mvn exec:java
```

If Maven is not on `PATH`, call its full path:

```powershell
& "C:\Users\mahim\Downloads\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd" exec:java
```

Maven downloads the SQLite JDBC driver the first time. The app creates `railease-desktop.db` in the current directory on first launch. Use **Create account** on the sign-in screen; passwords are stored as salted PBKDF2 hashes.

## Import a train list

Train names are read from the SQLite `trains` table. Import an authoritative list with **Import train list (CSV)** on the booking screen. CSV format:

```csv
train_number,train_name
<numeric train number>,<official train name>
```

Do not include the angle-bracket placeholders as data. Use one real train per line. Imports add or update the database records; reservations are never populated from example records.

## Assignment features

- Account creation and login, with invalid credentials denied.
- Passenger, numeric train number, database-populated train name, class, date, source, and destination fields.
- Required-field, train-number, date, and distinct-station validation.
- Database insertion with a unique generated PNR and booking confirmation details.
- PNR lookup that displays the reservation before cancellation is offered.
- Explicit cancellation confirmation followed by physical deletion from SQLite.

The desktop assignment does not include payments or Google OAuth. Those remain available in the separate web application.
