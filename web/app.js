const storageKey = 'noteify-pwa-notes';
const notes = loadNotes();
const list = document.querySelector('#notes-list');
const emptyState = document.querySelector('#empty-state');
const countLabel = document.querySelector('#count-label');
const dialog = document.querySelector('#note-dialog');
const form = document.querySelector('#note-form');

function loadNotes() { try { return JSON.parse(localStorage.getItem(storageKey) || '[]'); } catch { return []; } }
function saveNotes() { localStorage.setItem(storageKey, JSON.stringify(notes)); }
function formatDeadline(value) { return new Intl.DateTimeFormat(undefined, { dateStyle:'medium', timeStyle:'short' }).format(new Date(value)); }
function reminders(note) { return [note.dayReminder && '1 day before', note.hourReminder && '1 hour before'].filter(Boolean).join(' and '); }
function render() {
  const sorted = [...notes].sort((a, b) => new Date(a.deadline) - new Date(b.deadline));
  list.innerHTML = sorted.map((note, index) => `<article class="note-card ${note.completed ? 'completed' : ''}" style="animation-delay:${index * 50}ms"><div><h3 class="note-title">${escapeHtml(note.title)}</h3><p class="note-date">${formatDeadline(note.deadline)}</p>${note.notes ? `<p class="note-details">${escapeHtml(note.notes)}</p>` : ''}${reminders(note) ? `<p class="note-reminders">Reminders: ${reminders(note)}</p>` : ''}</div><div class="note-actions"><button class="action-button" data-action="complete" data-id="${note.id}" title="Mark complete" aria-label="Mark complete">${note.completed ? 'Undo' : 'Done'}</button><button class="action-button" data-action="delete" data-id="${note.id}" title="Delete" aria-label="Delete">Delete</button></div></article>`).join('');
  emptyState.hidden = sorted.length > 0;
  countLabel.textContent = sorted.length ? `${sorted.filter(note => !note.completed).length} active ${sorted.length === 1 ? 'activity' : 'activities'}` : 'Nothing scheduled yet';
}
function escapeHtml(value) { return value.replace(/[&<>'"]/g, character => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;' })[character]); }
function openDialog() {
  const date = new Date(Date.now() + 2 * 60 * 60 * 1000);
  document.querySelector('#date').value = date.toISOString().slice(0, 10);
  document.querySelector('#time').value = date.toTimeString().slice(0, 5);
  dialog.showModal();
}
document.querySelector('#add-button').addEventListener('click', openDialog);
document.querySelector('#empty-add-button').addEventListener('click', openDialog);
form.addEventListener('submit', event => {
  event.preventDefault();
  const data = new FormData(form);
  const note = { id: crypto.randomUUID(), title: data.get('title').trim(), notes: data.get('notes').trim(), deadline: `${data.get('date')}T${data.get('time')}`, dayReminder: document.querySelector('#day-reminder').checked, hourReminder: document.querySelector('#hour-reminder').checked, completed: false };
  notes.push(note); saveNotes(); scheduleReminder(note); render(); form.reset(); dialog.close();
});
list.addEventListener('click', event => { const button = event.target.closest('button'); if (!button) return; const index = notes.findIndex(note => note.id === button.dataset.id); if (index < 0) return; if (button.dataset.action === 'delete') notes.splice(index, 1); else notes[index].completed = !notes[index].completed; saveNotes(); render(); });
document.querySelector('#notifications-button').addEventListener('click', async () => { if (!('Notification' in window)) return alert('Notifications are not supported by this browser.'); const permission = await Notification.requestPermission(); document.querySelector('#notifications-button').textContent = permission === 'granted' ? 'Notifications on' : 'Bell'; });
function scheduleReminder(note) { if (!('Notification' in window) || Notification.permission !== 'granted') return; const deadline = new Date(note.deadline).getTime(); [[note.dayReminder, 86400000, '1 day'], [note.hourReminder, 3600000, '1 hour']].forEach(([enabled, offset, label]) => { const delay = deadline - offset - Date.now(); if (enabled && delay > 0 && delay < 2147483647) setTimeout(() => new Notification(`${note.title} is due in ${label}`, { body:'Open Noteify to check the deadline.' }), delay); }); }
if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js');
notes.forEach(scheduleReminder); render();
