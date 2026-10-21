package com.railease;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class FirebaseTokenVerifier {
    @Value("${app.firebase.project-id:}") private String projectId;
    private volatile FirebaseAuth auth;

    FirebaseToken verify(String idToken) throws FirebaseAuthException {
        return firebaseAuth().verifyIdToken(idToken);
    }

    private FirebaseAuth firebaseAuth() {
        FirebaseAuth current = auth;
        if (current != null) return current;
        synchronized (this) {
            if (auth != null) return auth;
            if (projectId == null || projectId.isBlank()) {
                throw notConfigured();
            }
            try {
                FirebaseApp app;
                try {
                    app = FirebaseApp.getInstance("railease-firebase-auth");
                } catch (IllegalStateException missingApp) {
                    FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .setProjectId(projectId.trim())
                        .build();
                    app = FirebaseApp.initializeApp(options, "railease-firebase-auth");
                }
                auth = FirebaseAuth.getInstance(app);
                return auth;
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Firebase Admin is not configured. Set FIREBASE_PROJECT_ID and GOOGLE_APPLICATION_CREDENTIALS for the backend.", e);
            }
        }
    }

    private ResponseStatusException notConfigured() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Firebase Admin is not configured. Set FIREBASE_PROJECT_ID and GOOGLE_APPLICATION_CREDENTIALS for the backend.");
    }
}
