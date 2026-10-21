# RailEase

RailEase is a React/Vite client backed by a Java 17 / Spring Boot REST API and MySQL. Sign-in supports Firebase Authentication with Google, plus a RailEase username/password account. Local account passwords are stored as BCrypt hashes. Reservations use database-backed train records, server-generated PNRs, and CSRF-protected session requests. There are no demo accounts or fabricated train records.

## Java desktop assignment version

The standalone Java Swing + JDBC + SQLite version is in `desktop-app/`. It implements local account registration/login, train lookup from an imported CSV, booking with PNR confirmation, and fetch-before-confirm cancellation. See [desktop-app/README.md](desktop-app/README.md) for launch and train-list instructions. This version is separate from the web application described below.

## Firebase Google sign-in setup

1. In the [Firebase Console](https://console.firebase.google.com/), create or choose a project and add a **Web app**. In **Authentication → Sign-in method**, enable **Google**. In **Authentication → Settings → Authorized domains**, ensure `localhost` is listed.
2. Copy the web app config values from Firebase into a project-root `.env` file. Start with `.env.example` and fill in the `VITE_FIREBASE_*` values. These browser config values identify the Firebase project; do not put a service-account private key in this file.
3. In Firebase **Project settings → Service accounts**, generate a private key JSON file. Keep it outside the project and set these variables in the PowerShell window that runs the backend:

   ```powershell
   $env:FIREBASE_PROJECT_ID = "your-firebase-project-id"
   $env:GOOGLE_APPLICATION_CREDENTIALS = "C:\path\to\firebase-service-account.json"
   $env:APP_ALLOWED_ORIGIN = "http://localhost:5173"
   ```

   The backend verifies Firebase ID tokens with the Firebase Admin SDK before creating a RailEase session. Never commit or share the service-account JSON file.

## Run locally

1. Create the MySQL database and tables using `backend/schema.sql`.
2. Set your MySQL connection values in the backend terminal:

   ```powershell
   $env:DB_USERNAME = "root"
   $env:DB_PASSWORD = "your-mysql-password"
   ```

3. Start the API from `backend` with `mvn spring-boot:run`. It starts at `http://localhost:8080`.
4. Load your authoritative train list into the MySQL `trains` table. Train lookup and booking use these records; there are no sample trains.
5. From the project root, run `npm install` and `npm run dev`, then open `http://localhost:5173`. Sign in with Google or choose **Create an account** to register a RailEase username and password. Manual account passwords must be at least 12 characters.

The client and database credentials belong only in the backend environment. For production, use HTTPS and set `SESSION_COOKIE_SECURE=true`, `APP_ALLOWED_ORIGIN` to your public origin, and `VITE_API_BASE_URL` to your API base if it is not served from the same origin.

## API

- `GET /api/auth/csrf`, `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`
- `GET /api/trains/{trainNumber}`
- `GET /api/reservations/{pnr}`, `DELETE /api/reservations/{pnr}`

Login is required for train and reservation endpoints. Cancellation physically deletes a reservation only after the user confirms in the app.
