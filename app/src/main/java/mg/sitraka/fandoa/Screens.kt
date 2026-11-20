@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package mg.sitraka.fandoa

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch

private const val HOUR = 3_600_000L
private const val DAY = 86_400_000L

@Composable
fun Shell(vm: MainVM, act: MainActivity) {
    val user by vm.user.collectAsState()
    val payments by vm.payments.collectAsState()
    val people by vm.people.collectAsState()
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val tab = pager.currentPage
    var formPayment by remember { mutableStateOf<Payment?>(null) }
    var formPerson by remember { mutableStateOf<Person?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var account by remember { mutableStateOf(false) }
    val titles = listOf("Paiements", "Personnel", "Réglages")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(titles[tab], fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { account = true }) { Avatar(user, 34.dp) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { scope.launch { pager.animateScrollToPage(0) } },
                    icon = { Icon(Icons.Default.Payments, null) }, label = { Text("Paiements") })
                NavigationBarItem(selected = tab == 1, onClick = { scope.launch { pager.animateScrollToPage(1) } },
                    icon = { Icon(Icons.Default.People, null) }, label = { Text("Personnel") })
                NavigationBarItem(selected = tab == 2, onClick = { scope.launch { pager.animateScrollToPage(2) } },
                    icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Réglages") })
            }
        },
        floatingActionButton = {
            if (tab < 2) {
                FloatingActionButton(
                    onClick = {
                        if (tab == 0) formPayment = Payment(createdAt = System.currentTimeMillis())
                        else formPerson = Person()
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { Icon(Icons.Default.Add, "Ajouter") }
            }
        }
    ) { pad ->
        HorizontalPager(
            state = pager, beyondViewportPageCount = 2,
            modifier = Modifier.padding(pad).fillMaxSize()
        ) { page ->
            when (page) {
                0 -> HomeTab(payments) { detailId = it.id }
                1 -> PeopleTab(people, payments,
                    onEdit = { formPerson = it },
                    onPay = {
                        formPayment = Payment(
                            name = it.name, personId = it.id, amount = it.salary,
                            category = "Salaire", createdAt = System.currentTimeMillis()
                        )
                    })
                else -> SettingsTab(act, vm, user, payments)
            }
        }
    }

    val detail = payments.find { it.id == detailId }
    if (detail != null) {
        DetailSheet(
            p = detail,
            onClose = { detailId = null },
            onEdit = { formPayment = detail; detailId = null },
            onDelete = { vm.deletePayment(detail.id); detailId = null },
            onSave = { vm.savePayment(it) }
        )
    }
    formPayment?.let { fp ->
        PaymentForm(fp, people, onSave = { vm.savePayment(it); formPayment = null }, onCancel = { formPayment = null })
    }
    formPerson?.let { fp ->
        PersonForm(fp,
            onSave = { vm.savePerson(it); formPerson = null },
            onDelete = { vm.deletePerson(fp.id); formPerson = null },
            onCancel = { formPerson = null })
    }
    if (account) {
        AlertDialog(
            onDismissRequest = { account = false },
            icon = { Avatar(user, 64.dp) },
            title = { Text(user?.displayName ?: "Compte Google") },
            text = { Text(user?.email ?: "") },
            confirmButton = {
                TextButton(onClick = { account = false; vm.signOut(act) }) { Text("Se déconnecter") }
            },
            dismissButton = { TextButton(onClick = { account = false }) { Text("Fermer") } }
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
fun HomeTab(payments: List<Payment>, onOpen: (Payment) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf<Status?>(null) }
    var month by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val months = remember(payments) { payments.map { monthKey(it) }.distinct().sortedDescending() }
    val base = payments.filter {
        (month == null || monthKey(it) == month) &&
            (query.isBlank() || it.name.contains(query, true) || it.reason.contains(query, true))
    }
    val list = base.filter { filter == null || it.status() == filter }.sortedWith(
        compareBy<Payment>({
            when (it.status()) { Status.LATE -> 0; Status.PAID -> 2; else -> 1 }
        }, { it.dueDate ?: Long.MAX_VALUE })
    )
    val lateCount = base.count { it.status() == Status.LATE }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(TealDark, Teal))).padding(20.dp)
            ) {
                Column {
                    Text("Reste à payer", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Text(base.sumOf { it.remaining }.ar(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("Total", base.sumOf { it.amount }.ar())
                        Stat("Payé", base.sumOf { it.paid }.ar())
                        Stat("En retard", lateCount.toString())
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Rechercher un nom ou un motif") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Box {
                        AssistChip(
                            onClick = { menu = true },
                            label = { Text(month?.let { monthLabel(it) } ?: "Tous les mois") },
                            leadingIcon = { Icon(Icons.Default.Event, null, Modifier.size(18.dp)) }
                        )
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Tous les mois") }, onClick = { month = null; menu = false })
                            months.forEach { m ->
                                DropdownMenuItem(text = { Text(monthLabel(m)) }, onClick = { month = m; menu = false })
                            }
                        }
                    }
                }
                item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Tous") }) }
                items(Status.values().toList()) { s ->
                    FilterChip(selected = filter == s, onClick = { filter = if (filter == s) null else s }, label = { Text(s.label) })
                }
            }
        }
        if (list.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Receipt, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text("Aucun paiement", fontWeight = FontWeight.SemiBold)
                    Text("Appuyez sur + pour en ajouter un.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(list, key = { it.id }) { p -> PaymentCard(p) { onOpen(p) } }
    }
}

@Composable
fun PaymentCard(p: Payment, onClick: () -> Unit) {
    val st = p.status()
    val col = st.color()
    Panel(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(col.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text((p.name.firstOrNull() ?: '?').uppercaseChar().toString(), color = col, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(p.category, p.reason).filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    p.dueDate?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Event, null, Modifier.size(13.dp),
                                tint = if (st == Status.LATE) col else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                it.dateFr(), fontSize = 12.sp,
                                color = if (st == Status.LATE) col else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if ((p.alarm || p.notif) && p.reminderAt != null) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.Alarm, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(p.amount.ar(), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    StatusChip(st)
                }
            }
            if (p.paid > 0 && p.amount > 0) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (p.paid / p.amount).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = col, trackColor = col.copy(alpha = 0.15f)
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Payé ${p.paid.ar()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Reste ${p.remaining.ar()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun DetailSheet(
    p: Payment,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Payment) -> Unit
) {
    val ctx = LocalContext.current
    var addTranche by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val st = p.status()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(p.category, p.reason).filter { it.isNotBlank() }.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(st)
            }
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Montant"); Text(p.amount.ar(), fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Déjà payé"); Text(p.paid.ar(), color = Status.PAID.color(), fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Reste à payer"); Text(p.remaining.ar(), color = st.color(), fontWeight = FontWeight.Bold)
                    }
                    if (p.amount > 0) {
                        LinearProgressIndicator(
                            progress = { (p.paid / p.amount).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = st.color(), trackColor = st.color().copy(alpha = 0.15f)
                        )
                    }
                }
            }
            p.dueDate?.let { Text("Échéance : ${it.dateFr()}") }
            if ((p.alarm || p.notif) && p.reminderAt != null) {
                Text(
                    (if (p.alarm) "Alarme" else "Notification") + " : " + p.reminderAt.dateTimeFr(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (p.monthly) Text("Se répète chaque mois", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p.note.isNotBlank()) Text("Note : ${p.note}", color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (p.tranches.isNotEmpty()) {
                Text("Historique des versements", fontWeight = FontWeight.SemiBold)
                p.tranches.forEachIndexed { i, t ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.amount.ar(), fontWeight = FontWeight.Medium)
                            Text(
                                t.date.dateFr() + if (t.note.isNotBlank()) " · ${t.note}" else "",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onSave(p.copy(tranches = p.tranches.filterIndexed { j, _ -> j != i })) }) {
                            Icon(Icons.Default.Delete, "Supprimer le versement")
                        }
                    }
                }
            }

            if (!p.isPaid) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onSave(p.copy(tranches = p.tranches + Tranche(p.remaining, System.currentTimeMillis()))) },
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Marquer payé") }
                    OutlinedButton(onClick = { addTranche = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Tranche")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { Export.receipt(ctx, p) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Reçu")
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Modifier")
                }
                OutlinedButton(
                    onClick = { confirmDelete = true }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Icon(Icons.Default.Delete, null) }
            }
        }
    }

    if (addTranche) {
        var amt by remember { mutableStateOf(trimNum(p.remaining)) }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addTranche = false },
            title = { Text("Ajouter une tranche") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(amt, { amt = it }, "Montant versé", keyboard = KeyboardType.Decimal, suffix = "Ar")
                    Field(note, { note = it }, "Note (facultatif)")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = amt.replace(" ", "").replace(',', '.').toDoubleOrNull()
                    if (v != null && v > 0) {
                        onSave(p.copy(tranches = p.tranches + Tranche(v, System.currentTimeMillis(), note.trim())))
                        addTranche = false
                    }
                }) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = { addTranche = false }) { Text("Annuler") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce paiement ?") },
            text = { Text("Cette action est définitive et sera synchronisée sur tous vos appareils.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
        )
    }
}

private fun num(s: String): Double? = s.replace(" ", "").replace(',', '.').toDoubleOrNull()

@Composable
fun PaymentForm(init: Payment, people: List<Person>, onSave: (Payment) -> Unit, onCancel: () -> Unit) {
    val isNew = init.id.isEmpty()
    var name by remember { mutableStateOf(init.name) }
    var personId by remember { mutableStateOf(init.personId) }
    var category by remember { mutableStateOf(init.category) }
    var amountTxt by remember { mutableStateOf(if (init.amount > 0) trimNum(init.amount) else "") }
    var reason by remember { mutableStateOf(init.reason) }
    var note by remember { mutableStateOf(init.note) }
    var due by remember { mutableStateOf(init.dueDate) }
    var mode by remember { mutableIntStateOf(if (init.isPaid) 1 else if (init.paid > 0) 2 else 0) }
    var paidTxt by remember { mutableStateOf(if (init.paid > 0) trimNum(init.paid) else "") }
    var monthly by remember { mutableStateOf(init.monthly) }
    var alarm by remember { mutableStateOf(init.alarm) }
    var notif by remember { mutableStateOf(init.notif) }
    var remAt by remember { mutableStateOf(init.reminderAt) }
    var error by remember { mutableStateOf<String?>(null) }
    var dateTarget by remember { mutableIntStateOf(0) }
    var timeDialog by remember { mutableStateOf(false) }

    fun defaultRem(): Long = (due ?: (startOfDay(System.currentTimeMillis()) + DAY)) + 8 * HOUR
    fun ensureRem() { if (remAt == null) remAt = defaultRem() }
    fun setRemDate(localMidnight: Long) {
        val cur = Calendar.getInstance().apply { timeInMillis = remAt ?: defaultRem() }
        val c = Calendar.getInstance().apply { timeInMillis = localMidnight }
        c.set(Calendar.HOUR_OF_DAY, cur.get(Calendar.HOUR_OF_DAY))
        c.set(Calendar.MINUTE, cur.get(Calendar.MINUTE))
        remAt = c.timeInMillis
    }
    fun setRemTime(h: Int, m: Int) {
        val c = Calendar.getInstance().apply { timeInMillis = remAt ?: defaultRem() }
        c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m); c.set(Calendar.SECOND, 0)
        remAt = c.timeInMillis
    }

    fun submit() {
        val amount = num(amountTxt)
        if (name.isBlank()) { error = "Saisissez un nom"; return }
        if (amount == null || amount <= 0) { error = "Saisissez un montant valide"; return }
        val target = when (mode) {
            0 -> 0.0
            1 -> amount
            else -> (num(paidTxt) ?: 0.0).coerceIn(0.0, amount)
        }
        val useRem = alarm || notif
        onSave(
            init.copy(
                name = name.trim(), personId = personId, category = category, amount = amount,
                reason = reason.trim(), note = note.trim(), dueDate = due,
                reminderAt = if (useRem) remAt else null, alarm = alarm, notif = notif,
                monthly = monthly, tranches = adjustPaid(init.tranches, target),
                createdAt = if (init.createdAt == 0L) System.currentTimeMillis() else init.createdAt
            )
        )
    }

    FullDialog(if (isNew) "Nouveau paiement" else "Modifier le paiement", onCancel, ::submit) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Bénéficiaire", fontWeight = FontWeight.SemiBold)
                Field(name, { name = it; personId = "" }, "Nom")
                if (isNew && people.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(people) { pr ->
                            AssistChip(
                                onClick = {
                                    name = pr.name; personId = pr.id
                                    if (amountTxt.isBlank() && pr.salary > 0) amountTxt = trimNum(pr.salary)
                                },
                                label = { Text(pr.name) },
                                leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Paiement", fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Salaire", "Avance", "Prime", "Autre")) { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
                Field(amountTxt, { amountTxt = it }, "Montant à payer", keyboard = KeyboardType.Decimal, suffix = "Ar")
                Field(reason, { reason = it }, "Motif du paiement")
                Field(note, { note = it }, "Note", minLines = 2)
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Date d'échéance (facultatif)", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { dateTarget = 1 }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Event, null)
                        Spacer(Modifier.width(8.dp))
                        Text(due?.dateFr() ?: "Choisir une date")
                    }
                    if (due != null) {
                        IconButton(onClick = { due = null }) { Icon(Icons.Default.Close, "Effacer la date") }
                    }
                }
                SwitchRow(Icons.Default.Repeat, "Répéter chaque mois", "Crée le paiement suivant une fois celui-ci soldé", monthly) { monthly = it }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Mode de paiement", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Non payé") })
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Payé") })
                    FilterChip(selected = mode == 2, onClick = { mode = 2 }, label = { Text("Par tranches") })
                }
                if (mode == 2) {
                    Field(paidTxt, { paidTxt = it }, "Déjà payé", keyboard = KeyboardType.Decimal, suffix = "Ar")
                    val total = num(amountTxt) ?: 0.0
                    val paid = (num(paidTxt) ?: 0.0).coerceIn(0.0, maxOf(total, 0.0))
                    Text("Reste à payer : ${(total - paid).coerceAtLeast(0.0).ar()}", color = Status.PARTIAL.color(), fontWeight = FontWeight.Medium)
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Rappels", fontWeight = FontWeight.SemiBold)
                SwitchRow(Icons.Default.Alarm, "Alarme sonore", "Sonne à la date et l'heure choisies", alarm) { alarm = it; if (it) ensureRem() }
                SwitchRow(Icons.Default.Notifications, "Notification", "Dans la barre d'état", notif) { notif = it; if (it) ensureRem() }
                if (alarm || notif) {
                    val at = remAt ?: defaultRem()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { ensureRem(); dateTarget = 2 }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Event, null); Spacer(Modifier.width(6.dp)); Text(at.dateFr())
                        }
                        OutlinedButton(onClick = { ensureRem(); timeDialog = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(6.dp)); Text(at.hourFr())
                        }
                    }
                }
            }
        }
    }

    if (dateTarget != 0) {
        DateDialog(
            initial = if (dateTarget == 1) due else remAt,
            onDismiss = { dateTarget = 0 },
            onPick = { v -> if (dateTarget == 1) due = v else setRemDate(v) }
        )
    }
    if (timeDialog) {
        TimeDialog(remAt ?: defaultRem(), { timeDialog = false }) { h, m -> setRemTime(h, m) }
    }
}

@Composable
fun PeopleTab(people: List<Person>, payments: List<Payment>, onEdit: (Person) -> Unit, onPay: (Person) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (people.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.People, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text("Aucune personne", fontWeight = FontWeight.SemiBold)
                    Text("Ajoutez votre personnel avec +.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(people, key = { it.id }) { pr ->
            val due = payments.filter { it.personId == pr.id || (it.personId.isEmpty() && it.name.equals(pr.name, true)) }
                .sumOf { it.remaining }
            Panel(Modifier.fillMaxWidth(), onClick = { onEdit(pr) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (pr.name.firstOrNull() ?: '?').uppercaseChar().toString(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pr.name, fontWeight = FontWeight.SemiBold)
                        val sub = listOf(pr.role, pr.phone).filter { it.isNotBlank() }.joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (pr.salary > 0) Text("Salaire : ${pr.salary.ar()}", fontSize = 12.sp)
                        if (due > 0) Text("Reste dû : ${due.ar()}", fontSize = 12.sp, color = Status.LATE.color())
                    }
                    IconButton(onClick = { onPay(pr) }) {
                        Icon(Icons.Default.Payments, "Nouveau paiement", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun PersonForm(init: Person, onSave: (Person) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    val isNew = init.id.isEmpty()
    var name by remember { mutableStateOf(init.name) }
    var role by remember { mutableStateOf(init.role) }
    var phone by remember { mutableStateOf(init.phone) }
    var salary by remember { mutableStateOf(if (init.salary > 0) trimNum(init.salary) else "") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    FullDialog(if (isNew) "Nouvelle personne" else "Modifier la personne", onCancel, {
        if (name.isBlank()) error = "Saisissez un nom"
        else onSave(init.copy(name = name.trim(), role = role.trim(), phone = phone.trim(), salary = num(salary) ?: 0.0))
    }) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(name, { name = it }, "Nom")
                Field(role, { role = it }, "Poste / rôle")
                Field(phone, { phone = it }, "Téléphone", keyboard = KeyboardType.Phone)
                Field(salary, { salary = it }, "Salaire mensuel", keyboard = KeyboardType.Decimal, suffix = "Ar")
            }
        }
        if (!isNew) {
            OutlinedButton(
                onClick = { confirm = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Supprimer cette personne") }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Supprimer ${init.name} ?") },
            text = { Text("Ses paiements déjà enregistrés sont conservés.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } }
        )
    }
}

@Composable
fun SettingsTab(act: MainActivity, vm: MainVM, user: com.google.firebase.auth.FirebaseUser?, payments: List<Payment>) {
    var pinOn by remember { mutableStateOf(Security.pinEnabled(act)) }
    var bioOn by remember { mutableStateOf(Security.bioEnabled(act)) }
    var hideOn by remember { mutableStateOf(Security.hideRecents(act)) }
    var grace by remember { mutableIntStateOf(Security.graceSec(act)) }
    var pinDialog by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(user, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(user?.displayName ?: "Compte Google", fontWeight = FontWeight.SemiBold)
                    Text(user?.email ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Synchronisé", fontSize = 12.sp, color = Status.PAID.color())
                }
                IconButton(onClick = { vm.signOut(act) }) { Icon(Icons.AutoMirrored.Filled.Logout, "Se déconnecter") }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Sécurité", fontWeight = FontWeight.SemiBold)
                SwitchRow(Icons.Default.Lock, "Code PIN", "Protège l'accès à l'application", pinOn) {
                    pinDialog = if (it) 1 else 2
                }
                SwitchRow(
                    Icons.Default.Fingerprint, "Empreinte digitale",
                    if (Security.canBio(act)) "Déverrouillage rapide" else "Non disponible sur cet appareil",
                    bioOn, enabled = pinOn && Security.canBio(act)
                ) { on ->
                    if (on) Security.prompt(act) { Security.setBio(act, true); bioOn = true }
                    else { Security.setBio(act, false); bioOn = false }
                }
                if (pinOn) {
                    Text("Verrouiller après", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Tout de suite" to 0, "30 s" to 30, "2 min" to 120).forEach { (l, v) ->
                            FilterChip(selected = grace == v, onClick = { grace = v; Security.setGraceSec(act, v) }, label = { Text(l) })
                        }
                    }
                }
                SwitchRow(Icons.Default.VisibilityOff, "Masquer l'aperçu", "Bloque les captures d'écran et l'aperçu des applis récentes", hideOn) {
                    hideOn = it; Security.setHide(act, it); act.applySecure()
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Rappels", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                ClickRow(Icons.Default.Notifications, "Paramètres des notifications") {
                    act.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, act.packageName)
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ClickRow(Icons.Default.Alarm, "Autoriser les alarmes exactes", "Pour sonner à l'heure précise") {
                        act.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + act.packageName))
                        )
                    }
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Données", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                ClickRow(Icons.Default.PictureAsPdf, "Exporter en PDF", "Rapport de tous les paiements") { Export.pdf(act, payments) }
                ClickRow(Icons.Default.TableChart, "Exporter en CSV", "Ouvrable dans Excel") { Export.csv(act, payments) }
            }
        }

        Text(
            "Fandoa 1.1", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }

    if (pinDialog == 1) {
        PinSetupDialog(
            onDone = { Security.setPin(act, it); pinOn = true; pinDialog = 0 },
            onCancel = { pinDialog = 0 }
        )
    }
    if (pinDialog == 2) {
        PinCheckDialog(
            act,
            onOk = { Security.clearPin(act); pinOn = false; bioOn = false; pinDialog = 0 },
            onCancel = { pinDialog = 0 }
        )
    }
}
