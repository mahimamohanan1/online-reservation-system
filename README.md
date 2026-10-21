# RailEase

RailEase is a React/Vite client backed by a Java 17 / Spring Boot REST API and MySQL. Sign-in supports Google OAuth 2.0 or a RailEase username/password account. Local account passwords are stored as BCrypt hashes. Reservations use database-backed train records, server-generated PNRs, and CSRF-protected session requests. There are no demo accounts or fabricated train records.

## Java desktop assignment version

The standalone Java Swing + JDBC + SQLite version is in `desktop-app/`. It implements local account registration/login, train lookup from an imported CSV, booking with PNR confirmation, and fetch-before-confirm cancellation. See [desktop-app/README.md](desktop-app/README.md) for launch and train-list instructions. This version is separate from the web application described below.

## Google OAuth setup

1. In Google Cloud Console, create an OAuth client ID of type **Web application** and configure the OAuth consent screen for your app.
2. Add this authorized redirect URI for local development: `http://localhost:5173/login/oauth2/code/google`. For production, add your public app origin followed by `/login/oauth2/code/google`.
3. In the PowerShell window that will run the backend, set your credentials and application origin (keep the client secret private):

   ```powershell
   $env:GOOGLE_CLIENT_ID = "your-client-id.apps.googleusercontent.com"
   $env:GOOGLE_CLIENT_SECRET = "your-client-secret"
   $env:APP_ALLOWED_ORIGIN = "http://localhost:5173"
   ```

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

## Razorpay payments

The booking flow uses Razorpay Standard Checkout. It creates the payment order on the backend, takes the amount from the database, verifies Razorpay's signature and captured payment on the backend, and only then stores and confirms the reservation. No ticket is confirmed if checkout is dismissed or payment verification fails.

1. In the Razorpay Dashboard, generate **Test Mode** API keys and set them in the backend terminal (never in the React `.env`):

   ```powershell
   $env:RAZORPAY_KEY_ID = "rzp_test_your-key-id"
   $env:RAZORPAY_KEY_SECRET = "your-test-key-secret"
   ```

2. Add the actual fares used by your service to `route_fares`. `amount_paise` is the INR amount in paise (for example, ₹325 is `32500`). Add each train, class, and direction with its real fare; the server will reject checkout if a matching fare row is missing.

   ```sql
   INSERT INTO route_fares (train_number, class_type, source_station, destination_station, amount_paise)
   VALUES (<train_number>, '<class_type>', '<source_station>', '<destination_station>', <fare_in_paise>);
   ```

   Replace the placeholders with your verified schedule, station names, class, and actual price. Reverse direction is a separate fare row. Do not use an estimated fare for live payments.

3. Restart Spring Boot after setting the keys. Use Razorpay test checkout credentials while the keys start with `rzp_test_`. Switch to live keys only after testing and completing the merchant's go-live setup.

The client secret and database credentials belong only in the backend environment. For production, use HTTPS and set `SESSION_COOKIE_SECURE=true`, `APP_ALLOWED_ORIGIN` to your public origin, and `VITE_API_BASE_URL` to your API base if it is not served from the same origin.

## API

- `GET /api/auth/csrf`, `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`
- `GET /api/trains/{trainNumber}`
- `POST /api/payments/order`, `POST /api/payments/verify`
- `GET /api/reservations/{pnr}`, `DELETE /api/reservations/{pnr}`

Login is required for train and reservation endpoints. Cancellation physically deletes a reservation only after the user confirms in the app.
