package com.noteify.app

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.UUID

private val Ink = Color(0xFF262522)
private val Cream = Color(0xFFF7F4EF)
private val Coral = Color(0xFFD97757)
private val Sage = Color(0xFF6E8B74)

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        }
        setContent { NoteifyApp() }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "deadlines", "Deadline reminders", NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

data class Note(
    val id: String,
    val title: String,
    val notes: String,
    val deadline: Long,
    val dayReminder: Boolean,
    val hourReminder: Boolean,
    val completed: Boolean = false
)

private class NoteStore(context: Context) {
    private val prefs = context.getSharedPreferences("notes", Context.MODE_PRIVATE)
    fun load(): List<Note> = prefs.getStringSet("items", emptySet()).orEmpty().mapNotNull { row ->
        val fields = row.split("\\u001F")
        if (fields.size < 7) null else Note(fields[0], fields[1], fields[2], fields[3].toLongOrNull() ?: return@mapNotNull null, fields[4] == "1", fields[5] == "1", fields[6] == "1")
    }
    fun save(notes: List<Note>) {
        prefs.edit().putStringSet("items", notes.map { note -> listOf(note.id, note.title, note.notes, note.deadline.toString(), if (note.dayReminder) "1" else "0", if (note.hourReminder) "1" else "0", if (note.completed) "1" else "0").joinToString("\\u001F") }.toSet()).apply()
    }
}

@Composable
private fun NoteifyApp() {
    val context = LocalContext.current
    val store = remember { NoteStore(context) }
    var notes by remember { mutableStateOf(store.load().sortedBy { it.deadline }) }
    var showAdd by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }

    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(primary = Coral, onBackground = Ink, background = Cream)) {
        Scaffold(
            containerColor = Cream,
            topBar = {
                TopAppBar(
                    title = { Text("Noteify", fontWeight = FontWeight.Bold) },
                    actions = { Icon(Icons.Rounded.NotificationsNone, contentDescription = "Reminders", tint = Coral, modifier = Modifier.padding(end = 18.dp)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { editingNote = null; showAdd = true }, containerColor = Coral, contentColor = Color.White) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add activity")
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                Text("Your next things", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Keep the important dates close.", color = Color(0xFF77736D), modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
                if (notes.isEmpty()) EmptyState(onAdd = { editingNote = null; showAdd = true }) else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(notes, key = { it.id }) { note ->
                            NoteCard(note,
                                onComplete = { updated -> notes = notes.map { if (it.id == updated.id) updated else it }; store.save(notes) },
                                onDelete = { notes = notes.filterNot { it.id == note.id }; store.save(notes) },
                                onEdit = { editingNote = note; showAdd = true })
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddNoteDialog(
        existingNote = editingNote,
        onDismiss = { showAdd = false; editingNote = null },
        onSave = { note ->
            notes = if (editingNote == null) notes + note else notes.map { if (it.id == note.id) note else it }.sortedBy { it.deadline }
            store.save(notes)
            scheduleReminders(context, note)
            showAdd = false; editingNote = null
        }
    )
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.NotificationsNone, null, tint = Coral, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("Nothing scheduled yet", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("Add an activity and let Noteify keep watch.", color = Color(0xFF77736D), modifier = Modifier.padding(top = 6.dp))
            Button(onClick = onAdd, modifier = Modifier.padding(top = 20.dp)) { Text("Add activity") }
        }
    }
}

@Composable
private fun NoteCard(note: Note, onComplete: (Note) -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (note.completed) Color.Gray else Ink)
                    Text(if (note.completed) "Completed" else formatter.format(Date(note.deadline)), color = if (note.completed) Sage else Coral, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
                IconButton(onClick = { onComplete(note.copy(completed = !note.completed)) }) { Icon(Icons.Rounded.Check, "Complete", tint = if (note.completed) Sage else Color.LightGray) }
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Edit", tint = Color(0xFF77736D)) }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Delete", tint = Color(0xFF99938B)) }
            }
            if (note.notes.isNotBlank()) Text(note.notes, color = Color(0xFF77736D), modifier = Modifier.padding(top = 10.dp))
            if (note.dayReminder || note.hourReminder) Text("Reminders: ${listOfNotNull(if (note.dayReminder) "1 day before" else null, if (note.hourReminder) "1 hour before" else null).joinToString(" and ")}", color = Sage, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun AddNoteDialog(existingNote: Note?, onDismiss: () -> Unit, onSave: (Note) -> Unit) {
    val context = LocalContext.current
    var title by remember(existingNote) { mutableStateOf(existingNote?.title ?: "") }
    var notes by remember(existingNote) { mutableStateOf(existingNote?.notes ?: "") }
    var deadline by remember(existingNote) { mutableStateOf(Calendar.getInstance().apply { timeInMillis = existingNote?.deadline ?: (System.currentTimeMillis() + 2 * 60 * 60 * 1000) }) }
    var dayReminder by remember(existingNote) { mutableStateOf(existingNote?.dayReminder ?: true) }
    var hourReminder by remember(existingNote) { mutableStateOf(existingNote?.hourReminder ?: true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existingNote == null) "New activity" else "Edit activity") }, text = {
        Column {
            androidx.compose.material3.OutlinedTextField(title, { title = it }, label = { Text("Activity") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            OutlinedButton(onClick = { pickDate(context, deadline) { deadline = it } }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(deadline.time)) }
            OutlinedButton(onClick = { pickTime(context, deadline) { deadline = it } }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(deadline.time)) }
            ReminderToggle("Remind me 1 day before", dayReminder) { dayReminder = it }
            ReminderToggle("Remind me 1 hour before", hourReminder) { hourReminder = it }
        }
    }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(Note(existingNote?.id ?: UUID.randomUUID().toString(), title.trim(), notes.trim(), deadline.timeInMillis, dayReminder, hourReminder, existingNote?.completed ?: false)) }) { Text(if (existingNote == null) "Save" else "Save changes") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ReminderToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Checkbox(checked, onCheckedChange); Text(label) }
}

private fun pickDate(context: Context, initial: Calendar, onPicked: (Calendar) -> Unit) {
    DatePickerDialog(context, { _, year, month, day -> onPicked((initial.clone() as Calendar).apply { set(year, month, day) }) }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
}

private fun pickTime(context: Context, initial: Calendar, onPicked: (Calendar) -> Unit) {
    TimePickerDialog(context, { _, hour, minute -> onPicked((initial.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }) }, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE), false).show()
}

private fun scheduleReminders(context: Context, note: Note) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val reminders = listOfNotNull(if (note.dayReminder) note.deadline - 86_400_000L to "1 day" else null, if (note.hourReminder) note.deadline - 3_600_000L to "1 hour" else null)
    reminders.forEachIndexed { index, reminder ->
        if (reminder.first <= System.currentTimeMillis()) return@forEachIndexed
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("title", note.title).putExtra("lead", reminder.second)
        val pending = PendingIntent.getBroadcast(context, note.id.hashCode() + index, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.first, pending)
    }
}
