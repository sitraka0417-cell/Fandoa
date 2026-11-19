package mg.sitraka.fandoa

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainVM(private val app: Application) : AndroidViewModel(app) {
    val user = MutableStateFlow<FirebaseUser?>(FirebaseAuth.getInstance().currentUser)
    val payments = MutableStateFlow<List<Payment>>(emptyList())
    val people = MutableStateFlow<List<Person>>(emptyList())

    init {
        FirebaseAuth.getInstance().addAuthStateListener { user.value = it.currentUser }
        viewModelScope.launch {
            user.collectLatest { u ->
                if (u == null) {
                    payments.value = emptyList()
                    people.value = emptyList()
                    Reminders.syncAll(app, emptyList())
                } else {
                    coroutineScope {
                        launch {
                            Repo.payments(u.uid).collect {
                                payments.value = it
                                Reminders.syncAll(app, it)
                            }
                        }
                        launch { Repo.people(u.uid).collect { people.value = it } }
                    }
                }
            }
        }
    }

    fun savePayment(p: Payment) {
        val uid = user.value?.uid ?: return
        var q = p
        if (q.monthly && !q.spawned && q.isPaid) {
            val next = q.copy(
                id = "", tranches = emptyList(), spawned = false,
                dueDate = q.dueDate?.let { addMonth(it) },
                reminderAt = q.reminderAt?.let { addMonth(it) },
                createdAt = System.currentTimeMillis()
            )
            Repo.savePayment(uid, next)
            q = q.copy(spawned = true)
        }
        Repo.savePayment(uid, q)
    }

    fun deletePayment(id: String) {
        val uid = user.value?.uid ?: return
        Repo.deletePayment(uid, id)
    }

    fun savePerson(p: Person) {
        val uid = user.value?.uid ?: return
        Repo.savePerson(uid, p.copy(createdAt = if (p.createdAt == 0L) System.currentTimeMillis() else p.createdAt))
    }

    fun deletePerson(id: String) {
        val uid = user.value?.uid ?: return
        Repo.deletePerson(uid, id)
    }

    fun signOut(ctx: Context) {
        FirebaseAuth.getInstance().signOut()
        Auth.client(ctx).signOut()
    }
}

class MainActivity : FragmentActivity() {
    private val vm: MainVM by viewModels()
    private var leftAt = 0L
    var locked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locked = Security.pinEnabled(this)
        applySecure()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { FandoaTheme { Root(vm, this@MainActivity) } }
    }

    fun applySecure() {
        if (Security.hideRecents(this)) window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE
        ) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStop() {
        super.onStop()
        leftAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (leftAt > 0 && Security.pinEnabled(this) &&
            SystemClock.elapsedRealtime() - leftAt >= Security.graceSec(this) * 1000L
        ) locked = true
    }
}
