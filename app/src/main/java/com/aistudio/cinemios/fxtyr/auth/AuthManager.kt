package com.aistudio.cinemios.fxtyr.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

object AuthManager {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()

    suspend fun signInWithGoogle(context: Context): String {
        try {
            val credentialManager = CredentialManager.create(context)
            
            // Generate a nonce
            val rawNonce = UUID.randomUUID().toString()
            val bytes = rawNonce.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            // To use Web Client ID, we need to extract it from google-services.json string implicitly available.
            // But we actually need the OAuth 2.0 Web Client ID from Firebase Console.
            // Since we don't have it explicitly right now, we can omit serverClientId for basic functionality or use a placeholder.
            // Firebase Auth needs the Web Client ID, not the Android Client ID.
            // For now, we will leave it empty and tell the user to replace it.
            val webClientId = "289243493446-4luunbk2iou67b0klublipfv4skv7cie.apps.googleusercontent.com" // The user needs to provide this from Firebase Console

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            
            val credential = result.credential
            if (GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL == credential.type) {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(firebaseCredential).await()
                    return "success"
                } catch (e: Exception) {
                    e.printStackTrace()
                    return e.message ?: "Firebase Auth error"
                }
            } else {
                return "Unknown credential type"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return e.message ?: e.toString()
        }
    }
}
