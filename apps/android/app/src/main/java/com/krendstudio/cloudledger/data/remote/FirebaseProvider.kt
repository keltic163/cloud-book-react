package com.krendstudio.cloudledger.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.ktx.functions

object FirebaseProvider {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val firestore: FirebaseFirestore by lazy {
        val instance = Firebase.firestore
        instance.firestoreSettings = firestoreSettings {
            isPersistenceEnabled = true
        }
        instance
    }

    val functions: FirebaseFunctions by lazy { Firebase.functions }
}
