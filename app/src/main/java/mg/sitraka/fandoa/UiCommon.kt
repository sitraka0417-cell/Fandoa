@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package mg.sitraka.fandoa

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import java.util.Calendar

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val shape = RoundedCornerShape(20.dp)
    val elev = CardDefaults.cardElevation(defaultElevation = 1.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, elevation = elev, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, elevation = elev, content = content)
    }
}

@Composable
fun Avatar(user: FirebaseUser?, size: Dp = 36.dp) {
    val url = user?.photoUrl?.toString()
    val base = user?.displayName?.takeIf { it.isNotBlank() } ?: user?.email ?: "?"
    val letter = (base.trim().firstOrNull() ?: '?').uppercaseChar().toString()
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun StatusChip(st: Status) {
    val c = st.color()
    Surface(shape = RoundedCornerShape(50), color = c.copy(alpha = 0.14f)) {
        Text(
            st.label, color = c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    suffix: String? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier,
        label = { Text(label) }, singleLine = minLines == 1, minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        suffix = if (suffix != null) ({ Text(suffix) }) else null
    )
}

@Composable
fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun ClickRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FullDialog(
    title: String,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Fermer") } }
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content
                )
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(50.dp)) { Text("Annuler") }
                    Button(onClick = onSave, modifier = Modifier.weight(1f).height(50.dp)) { Text("Enregistrer") }
                }
            }
        }
    }
}

@Composable
fun DateDialog(initial: Long?, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = initial?.let { localToUtc(it) })
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { onPick(utcToLocal(it)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    ) { DatePicker(state = st) }
}

@Composable
fun TimeDialog(initial: Long, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = initial }
    val st = rememberTimePickerState(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(st.hour, st.minute); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        text = { TimePicker(state = st) }
    )
}

@Composable
fun Root(vm: MainVM, act: MainActivity) {
    val user by vm.user.collectAsState()
    if (act.locked && Security.pinEnabled(act)) {
        LockScreen(act) { act.locked = false }
    } else if (user == null) {
        LoginScreen()
    } else {
        Shell(vm, act)
    }
}

@Composable
fun LockScreen(act: FragmentActivity, onOk: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    val bio = Security.bioEnabled(act) && Security.canBio(act)
    LaunchedEffect(Unit) { if (bio) Security.prompt(act, onOk) }

    fun press(d: String) {
        if (pin.length >= 6) return
        pin += d
        err = false
        if (pin.length >= 4 && Security.checkPin(act, pin)) {
            onOk()
        } else if (pin.length == 6) {
            err = true
            pin = ""
        }
    }

    @Composable
    fun Key(onClick: () -> Unit, content: @Composable () -> Unit) {
        Box(
            Modifier.padding(6.dp).size(66.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { content() }
    }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(TealDark, Teal))).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(8.dp))
        Text("Fandoa", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            if (err) "Code incorrect" else "Entrez votre code PIN",
            color = if (err) Color(0xFFFFB4AB) else Color.White.copy(alpha = 0.85f)
        )
        Spacer(Modifier.height(16.dp))
        Row {
            repeat(6) { i ->
                Box(
                    Modifier.padding(6.dp).size(14.dp).clip(CircleShape)
                        .background(if (i < pin.length) Color.White else Color.White.copy(alpha = 0.3f))
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row {
                row.forEach { d ->
                    Key(onClick = { press(d) }) { Text(d, color = Color.White, fontSize = 24.sp) }
                }
            }
        }
        Row {
            if (bio) {
                Key(onClick = { Security.prompt(act, onOk) }) { Icon(Icons.Default.Fingerprint, null, tint = Color.White) }
            } else {
                Spacer(Modifier.padding(6.dp).size(66.dp))
            }
            Key(onClick = { press("0") }) { Text("0", color = Color.White, fontSize = 24.sp) }
            Key(onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }) {
                Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (Security.checkPin(act, pin)) onOk() else { err = true; pin = "" }
            },
            enabled = pin.length >= 4,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = TealDark),
            modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
        ) { Text("Valider", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun LoginScreen() {
    val ctx = LocalContext.current
    var loading by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        try {
            val acc = GoogleSignIn.getSignedInAccountFromIntent(r.data).getResult(ApiException::class.java)
            val cred = GoogleAuthProvider.getCredential(acc.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(cred).addOnCompleteListener {
                loading = false
                if (!it.isSuccessful) Toast.makeText(ctx, "Connexion refusée par Firebase", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            loading = false
            if (e.statusCode != 12501) {
                Toast.makeText(ctx, "Connexion impossible (code ${e.statusCode})", Toast.LENGTH_LONG).show()
            }
        }
    }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(TealDark, Teal)))
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Payments, null, tint = Color.White, modifier = Modifier.size(52.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("Fandoa", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Suivez les salaires et les paiements\nde votre équipe, simplement.",
            color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, fontSize = 16.sp
        )
        Spacer(Modifier.weight(1f))
        if (loading) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Button(
                onClick = { loading = true; launcher.launch(Auth.client(ctx).signInIntent) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F2937)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Image(painterResource(R.drawable.ic_google), null, Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text("Continuer avec Google", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Vos données sont synchronisées de façon sécurisée avec votre compte.",
            color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PinSetupDialog(onDone: (String) -> Unit, onCancel: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Créer un code PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = a, onValueChange = { a = it.filter(Char::isDigit).take(6) },
                    label = { Text("Code (4 à 6 chiffres)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = b, onValueChange = { b = it.filter(Char::isDigit).take(6) },
                    label = { Text("Confirmer le code") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (a.length < 4) err = "Au moins 4 chiffres"
                else if (a != b) err = "Les codes ne correspondent pas"
                else onDone(a)
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Annuler") } }
    )
}

@Composable
fun PinCheckDialog(act: FragmentActivity, onOk: () -> Unit, onCancel: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Désactiver le code PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = a, onValueChange = { a = it.filter(Char::isDigit).take(6) },
                    label = { Text("Code actuel") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (err) Text("Code incorrect", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (Security.checkPin(act, a)) onOk() else err = true }) { Text("Désactiver") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Annuler") } }
    )
}
