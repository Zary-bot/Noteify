const storageKey = 'noteify-pwa-notes';
const notes = loadNotes();
const list = document.querySelector('#notes-list');
const emptyState = document.querySelector('#empty-state');
const countLabel = document.querySelector('#count-label');
const dialog = document.querySelector('#note-dialog');
const form = document.querySelector('#note-form');
const installButton = document.querySelector('#install-button');
let editingId = null;
let installPrompt = null;

function loadNotes() { try { return JSON.parse(localStorage.getItem(storageKey) || '[]'); } catch { return []; } }
function saveNotes() { localStorage.setItem(storageKey, JSON.stringify(notes)); }
function formatDeadline(value) { return new Intl.DateTimeFormat(undefined, { dateStyle:'medium', timeStyle:'short' }).format(new Date(value)); }
function reminders(note) { return [note.dayReminder && '1 day before', note.hourReminder && '1 hour before', note.customReminder?.enabled && `${note.customReminder.value} ${note.customReminder.unit} before`].filter(Boolean).join(' and '); }
function render() {
  const sorted = [...notes].sort((a, b) => new Date(a.deadline) - new Date(b.deadline));
  list.innerHTML = sorted.map((note, index) => `<article class="note-card ${note.completed ? 'completed' : ''}" style="animation-delay:${index * 50}ms"><div><h3 class="note-title">${escapeHtml(note.title)}</h3><p class="note-date">${formatDeadline(note.deadline)}</p>${note.notes ? `<p class="note-details">${escapeHtml(note.notes)}</p>` : ''}${reminders(note) ? `<p class="note-reminders">Reminders: ${reminders(note)}</p>` : ''}</div><div class="note-actions"><button class="action-button done-button" data-action="complete" data-id="${note.id}" title="Mark complete" aria-label="Mark complete">${note.completed ? 'Undo' : 'Done'}</button><button class="action-button edit-button" data-action="edit" data-id="${note.id}" title="Edit activity" aria-label="Edit activity">Edit</button><button class="action-button delete-button" data-action="delete" data-id="${note.id}" title="Delete activity" aria-label="Delete activity">Delete</button></div></article>`).join('');
  emptyState.hidden = sorted.length > 0;
  countLabel.textContent = sorted.length ? `${sorted.filter(note => !note.completed).length} active ${sorted.length === 1 ? 'activity' : 'activities'}` : 'Nothing scheduled yet';
}
function escapeHtml(value) { return value.replace(/[&<>'"]/g, character => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;' })[character]); }
function openDialog(note = null) {
  editingId = note?.id || null;
  document.querySelector('#dialog-eyebrow').textContent = note ? 'Update reminder' : 'New reminder';
  document.querySelector('#dialog-title').textContent = note ? 'Edit activity' : 'Add activity';
  document.querySelector('#save-button').textContent = note ? 'Save changes' : 'Save activity';
  const date = new Date(Date.now() + 2 * 60 * 60 * 1000);
  document.querySelector('#title').value = note?.title || '';
  document.querySelector('#notes').value = note?.notes || '';
  const selectedDate = note ? new Date(note.deadline) : date;
  document.querySelector('#date').value = selectedDate.toISOString().slice(0, 10);
  document.querySelector('#time').value = selectedDate.toTimeString().slice(0, 5);
  document.querySelector('#day-reminder').checked = note ? note.dayReminder : true;
  document.querySelector('#hour-reminder').checked = note ? note.hourReminder : true;
  const customFields = document.querySelector('#custom-reminder-fields');
  customFields.classList.toggle('is-open', Boolean(note?.customReminder));
  customFields.setAttribute('aria-hidden', String(!note?.customReminder));
  document.querySelector('#custom-value').value = note?.customReminder?.value || 2;
  document.querySelector('#custom-unit').value = note?.customReminder?.unit || 'hours';
  document.querySelector('#custom-reminder-enabled').checked = note?.customReminder?.enabled ?? true;
  dialog.showModal();
}
document.querySelector('#add-button').addEventListener('click', openDialog);
document.querySelector('#empty-add-button').addEventListener('click', openDialog);
document.querySelector('#custom-reminder-button').addEventListener('click', () => {
  const customFields = document.querySelector('#custom-reminder-fields');
  customFields.classList.add('is-open');
  customFields.setAttribute('aria-hidden', 'false');
  document.querySelector('#custom-value').focus();
});
form.addEventListener('submit', event => {
  event.preventDefault();
  const data = new FormData(form);
  const customFieldsVisible = document.querySelector('#custom-reminder-fields').classList.contains('is-open');
  const customEnabled = document.querySelector('#custom-reminder-enabled').checked;
  const customValue = Number(document.querySelector('#custom-value').value);
  const note = { id: editingId || crypto.randomUUID(), title: data.get('title').trim(), notes: data.get('notes').trim(), deadline: `${data.get('date')}T${data.get('time')}`, dayReminder: document.querySelector('#day-reminder').checked, hourReminder: document.querySelector('#hour-reminder').checked, customReminder: customFieldsVisible && customValue > 0 ? { value: customValue, unit: document.querySelector('#custom-unit').value, enabled: customEnabled } : null, completed: editingId ? notes.find(item => item.id === editingId).completed : false };
  const existingIndex = notes.findIndex(item => item.id === editingId);
  if (existingIndex >= 0) notes[existingIndex] = note; else notes.push(note);
  saveNotes(); scheduleReminder(note); render(); form.reset(); document.querySelector('#custom-reminder-fields').classList.remove('is-open'); document.querySelector('#custom-reminder-fields').setAttribute('aria-hidden', 'true'); editingId = null; dialog.close();
});
list.addEventListener('click', event => { const button = event.target.closest('button'); if (!button) return; const index = notes.findIndex(note => note.id === button.dataset.id); if (index < 0) return; if (button.dataset.action === 'delete') notes.splice(index, 1); else if (button.dataset.action === 'edit') openDialog(notes[index]); else notes[index].completed = !notes[index].completed; saveNotes(); render(); });
document.querySelector('#notifications-button').addEventListener('click', async () => { if (!('Notification' in window)) return alert('Notifications are not supported by this browser.'); const permission = await Notification.requestPermission(); document.querySelector('#notifications-button').textContent = permission === 'granted' ? 'Notifications on' : 'Bell'; });
window.addEventListener('beforeinstallprompt', event => { event.preventDefault(); installPrompt = event; });
installButton.addEventListener('click', async () => {
  if (!installPrompt) {
    alert('Use your browser menu and choose "Install Noteify" or "Add to Home screen".');
    return;
  }
  installPrompt.prompt();
  const result = await installPrompt.userChoice;
  if (result.outcome === 'accepted') installButton.hidden = true;
  installPrompt = null;
});
window.addEventListener('appinstalled', () => { installButton.hidden = true; });
function scheduleReminder(note) { if (!('Notification' in window) || Notification.permission !== 'granted') return; const deadline = new Date(note.deadline).getTime(); const customOffsets = { minutes: 60000, hours: 3600000, days: 86400000 }; const remindersToSchedule = [[note.dayReminder, 86400000, '1 day'], [note.hourReminder, 3600000, '1 hour']]; if (note.customReminder?.enabled) remindersToSchedule.push([true, note.customReminder.value * customOffsets[note.customReminder.unit], `${note.customReminder.value} ${note.customReminder.unit}`]); remindersToSchedule.forEach(([enabled, offset, label]) => { const delay = deadline - offset - Date.now(); if (enabled && delay > 0 && delay < 2147483647) setTimeout(() => new Notification(`${note.title} is due in ${label}`, { body:'Open Noteify to check the deadline.' }), delay); }); }
if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js');
notes.forEach(scheduleReminder); render();
