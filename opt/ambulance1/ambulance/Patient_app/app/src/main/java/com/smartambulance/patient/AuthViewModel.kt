package com.smartambulance.patient

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartambulance.patient.network.RetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth

sealed class AuthStatus {
    object Idle : AuthStatus()
    object Loading : AuthStatus()
    object CodeSent : AuthStatus()
    data class Authenticated(val role: String, val isProfileComplete: Boolean) : AuthStatus()
    data class Error(val message: String) : AuthStatus()
}

class AuthViewModel : ViewModel() {
    var authStatus by mutableStateOf<AuthStatus>(AuthStatus.Idle)
    var timeLeft by mutableStateOf(0)
    var resendCount by mutableStateOf(0)
    val maxResends = 3

    private val maxVerifyAttempts = 5
    private var verifyAttempts by mutableStateOf(0)
    private var timerJob: Job? = null
    private var otpRequestInFlight = false
    private var pendingEmail: String? = null

    init {
        checkAuthStatus()
    }

    fun checkAuthStatus() {
        if (UserSession.isPatientLoggedIn) {
            authStatus = AuthStatus.Authenticated("patient", true)
        }
    }

    fun sendEmailOtp(email: String) {
        val cleanEmail = email.trim().lowercase()
        if (!isValidEmail(cleanEmail)) {
            authStatus = AuthStatus.Error("Please enter a valid email address.")
            return
        }
        if (otpRequestInFlight) {
            authStatus = AuthStatus.Error("OTP request already in progress. Please wait.")
            return
        }
        if (resendCount >= maxResends) {
            authStatus = AuthStatus.Error("Max resend attempts reached. Try again later.")
            return
        }
        if (pendingEmail == cleanEmail && timeLeft > 0) {
            authStatus = AuthStatus.Error("Please wait $timeLeft seconds before resending OTP.")
            return
        }

        authStatus = AuthStatus.Loading
        otpRequestInFlight = true

        val actionCodeSettings = ActionCodeSettings.newBuilder()
            .setUrl("https://resqgo-system.firebaseapp.com/finishSignUp") // Matches Manifest
            .setHandleCodeInApp(true)
            .setAndroidPackageName("com.smartambulance.patient", true, "1")
            .build()

        FirebaseAuth.getInstance().sendSignInLinkToEmail(cleanEmail, actionCodeSettings)
            .addOnCompleteListener { task ->
                otpRequestInFlight = false
                if (task.isSuccessful) {
                    pendingEmail = cleanEmail
                    UserSession.email = cleanEmail
                    UserSession.role = "patient"
                    verifyAttempts = 0
                    if (timeLeft == 0) resendCount++
                    timeLeft = 30
                    authStatus = AuthStatus.CodeSent
                    startTimer()
                } else {
                    authStatus = AuthStatus.Error(task.exception?.message ?: "Could not send link.")
                }
            }
    }

    fun verifyEmailLink(link: String) {
        val email = pendingEmail ?: UserSession.email
        if (authStatus is AuthStatus.Loading) return
        if (!isValidEmail(email)) {
            authStatus = AuthStatus.Error("Invalid session. Please request link again.")
            return
        }

        authStatus = AuthStatus.Loading
        FirebaseAuth.getInstance().signInWithEmailLink(email, link)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    UserSession.role = "patient"
                    UserSession.email = email
                    pendingEmail = null
                    timerJob?.cancel()
                    timeLeft = 0
                    authStatus = AuthStatus.Authenticated("patient", UserSession.isProfileComplete)
                } else {
                    authStatus = AuthStatus.Error(task.exception?.message ?: "Sign in failed. Link may be expired.")
                }
            }
    }

    fun signOut() {
        UserSession.role = ""
        UserSession.isProfileComplete = false
        pendingEmail = null
        resendCount = 0
        verifyAttempts = 0
        timeLeft = 0
        timerJob?.cancel()
        authStatus = AuthStatus.Idle
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}
