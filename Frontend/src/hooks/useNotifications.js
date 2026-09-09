// The notification slice lives in Context now, so a message pushed over the
// real-time channel (§9.3) reaches the bell and a toast alike. This module keeps the
// hook where the other hooks are, and keeps every existing import path working.
export { useNotifications } from '../context/NotificationContext'
