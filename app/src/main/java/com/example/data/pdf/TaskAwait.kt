package com.example.data.pdf

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) {
            continuation.resume(result)
        }
    }
    addOnFailureListener { exception ->
        if (continuation.isActive) {
            continuation.resumeWithException(exception)
        }
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
