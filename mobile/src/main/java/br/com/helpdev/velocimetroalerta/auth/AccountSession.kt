package br.com.helpdev.velocimetroalerta.auth

data class AccountSession(
    val uid: String,
    val displayName: String?,
    val email: String?
)
