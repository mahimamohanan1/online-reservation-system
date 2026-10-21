import { getApp, getApps, initializeApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider } from 'firebase/auth';
import { firebaseConfig, firebaseConfigured } from './firebase-config.js';

export function getFirebaseAuth() {
  if (!firebaseConfigured) {
    throw new Error('Add the Firebase web app values to the project root .env file first.');
  }
  const app = getApps().length ? getApp() : initializeApp(firebaseConfig);
  return getAuth(app);
}

export function createGoogleProvider() {
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });
  return provider;
}
