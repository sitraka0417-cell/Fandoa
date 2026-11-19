package mg.sitraka.fandoa

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object Auth {
    fun client(c: Context) = GoogleSignIn.getClient(
        c,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(c.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    )
}

object Repo {
    private val db get() = FirebaseFirestore.getInstance()
    private fun pay(uid: String) = db.collection("users").document(uid).collection("payments")
    private fun ppl(uid: String) = db.collection("users").document(uid).collection("people")

    fun payments(uid: String): Flow<List<Payment>> = callbackFlow {
        val reg = pay(uid).addSnapshotListener { s, _ ->
            if (s != null) trySend(s.documents.map { it.toPayment() })
        }
        awaitClose { reg.remove() }
    }

    fun people(uid: String): Flow<List<Person>> = callbackFlow {
        val reg = ppl(uid).addSnapshotListener { s, _ ->
            if (s != null) trySend(s.documents.map { it.toPerson() })
        }
        awaitClose { reg.remove() }
    }

    fun savePayment(uid: String, p: Payment) {
        val ref = if (p.id.isEmpty()) pay(uid).document() else pay(uid).document(p.id)
        ref.set(p.copy(id = ref.id).toMap())
    }

    fun deletePayment(uid: String, id: String) {
        pay(uid).document(id).delete()
    }

    fun savePerson(uid: String, p: Person) {
        val ref = if (p.id.isEmpty()) ppl(uid).document() else ppl(uid).document(p.id)
        ref.set(
            mapOf(
                "name" to p.name, "role" to p.role, "phone" to p.phone,
                "salary" to p.salary, "createdAt" to p.createdAt
            )
        )
    }

    fun deletePerson(uid: String, id: String) {
        ppl(uid).document(id).delete()
    }
}

private fun Payment.toMap(): Map<String, Any?> = mapOf(
    "name" to name, "personId" to personId, "category" to category,
    "amount" to amount, "reason" to reason, "note" to note,
    "dueDate" to dueDate, "reminderAt" to reminderAt,
    "alarm" to alarm, "notif" to notif, "monthly" to monthly, "spawned" to spawned,
    "createdAt" to createdAt,
    "tranches" to tranches.map { mapOf("amount" to it.amount, "date" to it.date, "note" to it.note) }
)

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.toPayment(): Payment {
    val raw = get("tranches") as? List<Map<String, Any?>> ?: emptyList()
    return Payment(
        id = id,
        name = getString("name") ?: "",
        personId = getString("personId") ?: "",
        category = getString("category") ?: "Salaire",
        amount = getDouble("amount") ?: 0.0,
        reason = getString("reason") ?: "",
        note = getString("note") ?: "",
        dueDate = getLong("dueDate"),
        reminderAt = getLong("reminderAt"),
        alarm = getBoolean("alarm") ?: false,
        notif = getBoolean("notif") ?: false,
        monthly = getBoolean("monthly") ?: false,
        spawned = getBoolean("spawned") ?: false,
        createdAt = getLong("createdAt") ?: 0L,
        tranches = raw.map {
            Tranche(
                (it["amount"] as? Number)?.toDouble() ?: 0.0,
                (it["date"] as? Number)?.toLong() ?: 0L,
                it["note"] as? String ?: ""
            )
        }
    )
}

private fun DocumentSnapshot.toPerson() = Person(
    id = id,
    name = getString("name") ?: "",
    role = getString("role") ?: "",
    phone = getString("phone") ?: "",
    salary = getDouble("salary") ?: 0.0,
    createdAt = getLong("createdAt") ?: 0L
)
