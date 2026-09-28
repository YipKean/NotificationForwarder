# Design

The Android interface uses Jetpack Compose Material 3 with the dark palette in
`app/src/main/java/com/notificationforwarder/app/ui/theme/AppTheme.kt`. Keep the
existing Home, Webhook, Filter and Queue tabs, Material typography, full-width
form controls and vertically scrollable content. Use Material components for
labels, focus, touch targets and accessibility; allow explanatory text to wrap.

Retry settings describe retention and scheduling separately. **Backoff growth
limit** caps exponential delay growth (1–20 attempts; delay itself caps at 32
minutes). It never means deletion after that many failures. Supporting text must
say temporary failures retry until expiry. **Retry retention** remains 1–24 hours
or OFF; OFF disables time-based expiry. Permanent failures and intentional policy
clears still delete content. Preserve existing styles for these copy changes.

Transient feedback uses the Material snackbar above the bottom navigation. Every
message has an accessible close button, supports horizontal swipe dismissal in
either direction and uses the short timeout (respecting Android accessibility
timeouts). New feedback replaces the visible message; rapid repeated actions
must not create a backlog. Dismissal affects only feedback, never the underlying
save, deletion or network operation. Keep full message text and the current theme.
