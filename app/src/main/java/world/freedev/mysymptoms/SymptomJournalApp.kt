package world.freedev.mysymptoms

import android.app.TimePickerDialog
import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import java.time.temporal.WeekFields
import kotlinx.coroutines.launch

private enum class JournalScreen {
    HOME,
    ADD_EVENT,
    EDIT_EVENT,
    CALENDAR
}

private val symptomEventSaver = Saver<SymptomEvent?, List<Any>>(
    save = { event ->
        event?.let {
            listOf(it.id, it.profileId, it.symptomId, it.occurredAt, it.note)
        } ?: emptyList()
    },
    restore = { values ->
        val id = values.getOrNull(0) as? Long
        val profileId = values.getOrNull(1) as? Long
        val symptomId = values.getOrNull(2) as? String
        val occurredAt = values.getOrNull(3) as? Long
        val note = values.getOrNull(4) as? String
        if (id != null && profileId != null && symptomId != null &&
            occurredAt != null && note != null
        ) {
            SymptomEvent(id, profileId, symptomId, occurredAt, note)
        } else {
            null
        }
    }
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SymptomJournalApp() {
    val context = LocalContext.current
    val store = remember { SymptomStore(context.applicationContext) }
    var profiles by remember { mutableStateOf(store.ensureInitialProfile()) }
    var activeProfileId by remember {
        mutableLongStateOf(store.activeProfileId(profiles))
    }
    var screen by rememberSaveable { mutableStateOf(JournalScreen.HOME) }
    var selectedSymptomId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingEvent by rememberSaveable(stateSaver = symptomEventSaver) {
        mutableStateOf<SymptomEvent?>(null)
    }
    var calendarFocusDate by remember { mutableStateOf(LocalDate.now()) }
    var showAddProfile by remember { mutableStateOf(false) }
    var refreshCalendar by remember { mutableIntStateOf(0) }
    var layout by remember { mutableStateOf(store.savedLayout()) }

    val activeProfile = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val visibleProfileName = when {
        profiles.size == 1 && activeProfile.name.isNullOrBlank() -> null
        else -> activeProfile.name?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.unnamed_profile)
    }

    BackHandler(enabled = screen != JournalScreen.HOME) {
        when (screen) {
            JournalScreen.EDIT_EVENT -> {
                editingEvent = null
                screen = JournalScreen.CALENDAR
            }
            JournalScreen.ADD_EVENT, JournalScreen.CALENDAR -> screen = JournalScreen.HOME
            JournalScreen.HOME -> Unit
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.menu),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                    DrawerRow("＋", stringResource(R.string.log_symptom)) {
                        screen = JournalScreen.HOME
                        scope.launch { drawerState.close() }
                    }
                    DrawerRow("▦", stringResource(R.string.calendar)) {
                        screen = JournalScreen.CALENDAR
                        scope.launch { drawerState.close() }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    if (!(profiles.size == 1 && activeProfile.name.isNullOrBlank())) {
                        profiles.forEach { profile ->
                            val name = profile.name?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.unnamed_profile)
                            DrawerRow(
                                icon = if (profile.id == activeProfileId) "✓" else "○",
                                title = name
                            ) {
                                activeProfileId = profile.id
                                store.setActiveProfile(profile.id)
                                screen = JournalScreen.HOME
                                scope.launch { drawerState.close() }
                            }
                        }
                    }
                    DrawerRow("＋", stringResource(R.string.add_user)) {
                        showAddProfile = true
                        scope.launch { drawerState.close() }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (visibleProfileName != null) {
                                Text(
                                    text = visibleProfileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", fontSize = 24.sp)
                        }
                    },
                    actions = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                )
            }
        ) { innerPadding ->
            when (screen) {
                JournalScreen.HOME -> HomeScreen(
                    layout = layout,
                    onLayoutChange = { newLayout ->
                        layout = newLayout
                        store.setLayout(newLayout)
                    },
                    onSymptomSelected = {
                        selectedSymptomId = it.id
                        screen = JournalScreen.ADD_EVENT
                    },
                    modifier = Modifier.padding(innerPadding)
                )

                JournalScreen.ADD_EVENT -> {
                    val symptom = symptoms.firstOrNull { it.id == selectedSymptomId }
                        ?: symptoms.first()
                    AddEventScreen(
                        symptom = symptom,
                        onCancel = { screen = JournalScreen.HOME },
                        onSave = { occurredAt, note ->
                            store.saveEvent(activeProfileId, symptom.id, occurredAt, note)
                            refreshCalendar += 1
                            Toast.makeText(
                                context,
                                R.string.entry_saved,
                                Toast.LENGTH_SHORT
                            ).show()
                            screen = JournalScreen.HOME
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                JournalScreen.EDIT_EVENT -> editingEvent?.let { event ->
                    EditEventScreen(
                        event = event,
                        onCancel = {
                            editingEvent = null
                            screen = JournalScreen.CALENDAR
                        },
                        onSave = { occurredAt, note ->
                            store.updateEvent(event.id, event.profileId, occurredAt, note)
                            calendarFocusDate = Instant.ofEpochMilli(occurredAt)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            refreshCalendar += 1
                            editingEvent = null
                            screen = JournalScreen.CALENDAR
                        },
                        onDelete = {
                            store.deleteEvent(event.id, event.profileId)
                            calendarFocusDate = Instant.ofEpochMilli(event.occurredAt)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            refreshCalendar += 1
                            editingEvent = null
                            screen = JournalScreen.CALENDAR
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                JournalScreen.CALENDAR -> CalendarScreen(
                    store = store,
                    profileId = activeProfileId,
                    refreshKey = refreshCalendar,
                    focusDate = calendarFocusDate,
                    onEventClick = { event ->
                        editingEvent = event
                        calendarFocusDate = Instant.ofEpochMilli(event.occurredAt)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        screen = JournalScreen.EDIT_EVENT
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    if (showAddProfile) {
        val shouldWarnAboutExistingEvents =
            profiles.size == 1 &&
                activeProfile.name.isNullOrBlank() &&
                store.eventCount(activeProfile.id) > 0
        AddProfileDialog(
            showExistingDataWarning = shouldWarnAboutExistingEvents,
            onDismiss = { showAddProfile = false },
            onAdd = { name ->
                val newId = store.addOrNameProfile(name, profiles)
                profiles = store.getProfiles()
                activeProfileId = newId
                store.setActiveProfile(newId)
                showAddProfile = false
                screen = JournalScreen.HOME
            }
        )
    }
}

@Composable
private fun DrawerRow(
    icon: String,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 20.sp, modifier = Modifier.width(36.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HomeScreen(
    layout: String,
    onLayoutChange: (String) -> Unit,
    onSymptomSelected: (Symptom) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = layout == SymptomStore.LAYOUT_LIST,
                onClick = { onLayoutChange(SymptomStore.LAYOUT_LIST) },
                label = { Text(stringResource(R.string.list)) },
                leadingIcon = { Text("☷") }
            )
            FilterChip(
                selected = layout == SymptomStore.LAYOUT_TABLE,
                onClick = { onLayoutChange(SymptomStore.LAYOUT_TABLE) },
                label = { Text(stringResource(R.string.table)) },
                leadingIcon = { Text("▦") }
            )
        }

        if (layout == SymptomStore.LAYOUT_LIST) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(symptoms.size) { index ->
                    val symptom = symptoms[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSymptomSelected(symptom) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(symptom.icon, fontSize = 25.sp)
                            Text(
                                stringResource(symptom.titleResId),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 14.dp)
                            )
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(symptoms.size) { index ->
                    val symptom = symptoms[index]
                    val symptomTitle = stringResource(symptom.titleResId)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .semantics {
                                contentDescription = symptomTitle
                            }
                            .clickable { onSymptomSelected(symptom) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(symptom.icon, fontSize = 42.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddEventScreen(
    symptom: Symptom,
    onCancel: () -> Unit,
    onSave: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var occurredAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var note by rememberSaveable { mutableStateOf("") }
    var selectedMinutesAgo by rememberSaveable { mutableIntStateOf(0) }
    var exactTimeSelected by rememberSaveable { mutableStateOf(false) }
    var exactTimeLabel by rememberSaveable { mutableStateOf("") }
    val presets = listOf(0, 5, 15, 30, 60)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(symptom.icon, fontSize = 42.sp)
            Text(
                text = stringResource(symptom.titleResId),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 14.dp)
            )
        }
        Text(
            text = stringResource(R.string.time_started_question),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 30.dp, bottom = 12.dp)
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { minutesAgo ->
                val label = when (minutesAgo) {
                    0 -> stringResource(R.string.time_now)
                    5, 15 -> stringResource(R.string.time_minutes_ago, minutesAgo)
                    30 -> stringResource(R.string.time_half_hour_ago)
                    else -> stringResource(R.string.time_hour_ago)
                }
                FilterChip(
                    selected = !exactTimeSelected && selectedMinutesAgo == minutesAgo,
                    onClick = {
                        occurredAt = if (minutesAgo == 0) {
                            System.currentTimeMillis()
                        } else {
                            timestampMinutesAgo(System.currentTimeMillis(), minutesAgo)
                        }
                        selectedMinutesAgo = minutesAgo
                        exactTimeSelected = false
                    },
                    label = { Text(label) }
                )
            }
        }
        val calendar = Calendar.getInstance().apply { timeInMillis = occurredAt }
        val openTimePicker: () -> Unit = {
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val selectedTime = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    occurredAt = selectedTime.timeInMillis
                    exactTimeLabel = String.format(
                        Locale.getDefault(),
                        "%02d:%02d",
                        hourOfDay,
                        minute
                    )
                    exactTimeSelected = true
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }
        TextButton(
            onClick = openTimePicker,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                "◷  " + if (exactTimeSelected) {
                    stringResource(R.string.exact_time_label, exactTimeLabel)
                } else {
                    stringResource(R.string.exact_time)
                }
            )
        }
        Text(
            text = stringResource(R.string.started_at, formatDateTime(occurredAt)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.note)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            minLines = 3
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { onSave(occurredAt, note) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.save_entry))
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun EditEventScreen(
    event: SymptomEvent,
    onCancel: () -> Unit,
    onSave: (Long, String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var occurredAt by rememberSaveable(event.id) {
        mutableLongStateOf(event.occurredAt)
    }
    var note by rememberSaveable(event.id) { mutableStateOf(event.note) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    val symptom = symptoms.firstOrNull { it.id == event.symptomId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("<-", style = MaterialTheme.typography.titleLarge)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { showDeleteConfirmation = true }) {
                    Text("🗑", fontSize = 22.sp, color = Color.Red)
                }
                Button(
                    onClick = { onSave(occurredAt, note) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32)
                    )
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 20.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(symptom?.icon ?: "•", fontSize = 36.sp)
            Text(
                text = symptom?.let { stringResource(it.titleResId) }
                    ?: stringResource(R.string.symptom_fallback),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        val dateTime = Calendar.getInstance().apply { timeInMillis = occurredAt }
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        occurredAt = Calendar.getInstance().apply {
                            timeInMillis = occurredAt
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        }.timeInMillis
                    },
                    dateTime.get(Calendar.YEAR),
                    dateTime.get(Calendar.MONTH),
                    dateTime.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(
                    R.string.edit_date,
                    Instant.ofEpochMilli(occurredAt)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
                )
            )
        }
        OutlinedButton(
            onClick = {
                val currentTime = Calendar.getInstance().apply { timeInMillis = occurredAt }
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        occurredAt = Calendar.getInstance().apply {
                            timeInMillis = occurredAt
                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    },
                    currentTime.get(Calendar.HOUR_OF_DAY),
                    currentTime.get(Calendar.MINUTE),
                    true
                ).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Text(
                stringResource(
                    R.string.edit_time,
                    formatClockTime(occurredAt)
                )
            )
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.note)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            minLines = 4
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            text = { Text(stringResource(R.string.confirm_delete_entry)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.yes), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }
}

@Composable
private fun CalendarScreen(
    store: SymptomStore,
    profileId: Long,
    refreshKey: Int,
    focusDate: LocalDate,
    onEventClick: (SymptomEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var month by remember { mutableStateOf(YearMonth.from(focusDate)) }
    var selectedDate by remember { mutableStateOf(focusDate) }
    val zone = ZoneId.systemDefault()
    val events = remember(profileId, month, refreshKey) {
        val first = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        store.eventsBetween(profileId, first, end)
    }
    val eventsByDate = events.groupBy {
        Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
    }
    val locale = Locale.getDefault()
    val monthTitle = month.format(
        DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    ).replaceFirstChar { it.titlecase(locale) }
    val weekStart = WeekFields.of(locale).firstDayOfWeek
    val weekDays = (0..6).map { weekStart.plus(it.toLong()) }
    val offset = (month.atDay(1).dayOfWeek.value - weekStart.value + 7) % 7
    val dayCells: List<Int?> = buildList {
        repeat(offset) { add(null) }
        (1..month.lengthOfMonth()).forEach(::add)
        while (size % 7 != 0) add(null)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.calendar),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                month = month.minusMonths(1)
                selectedDate = month.atDay(1)
            }) { Text("‹", fontSize = 28.sp) }
            Text(monthTitle, style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = {
                month = month.plusMonths(1)
                selectedDate = month.atDay(1)
            }) { Text("›", fontSize = 28.sp) }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.SHORT, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        dayCells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            val date = month.atDay(day)
                            val hasEvents = !eventsByDate[date].isNullOrEmpty()
                            Surface(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable { selectedDate = date },
                                shape = CircleShape,
                                color = if (date == selectedDate) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        day.toString(),
                                        color = if (date == selectedDate) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (hasEvents) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 3.dp)
                                                .size(4.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        val selectedTitle = selectedDate.format(
            DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)
        ).replaceFirstChar { it.titlecase(locale) }
        Text(
            text = selectedTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        val dayEvents = eventsByDate[selectedDate].orEmpty()
        if (dayEvents.isEmpty()) {
            Text(
                text = stringResource(R.string.no_entries_for_day),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        } else {
            dayEvents.forEach { event ->
                val symptom = symptoms.firstOrNull { it.id == event.symptomId }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onEventClick(event) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(symptom?.icon ?: "•", fontSize = 24.sp)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                if (symptom != null) {
                                    stringResource(symptom.titleResId)
                                } else {
                                    stringResource(R.string.symptom_fallback)
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                formatClockTime(event.occurredAt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (event.note.isNotBlank()) {
                                Text(
                                    event.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddProfileDialog(
    showExistingDataWarning: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_user_title)) },
        text = {
            Column {
                if (showExistingDataWarning) {
                    Text(
                        stringResource(R.string.existing_data_warning)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun formatDateTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.getDefault()))

private fun formatClockTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
