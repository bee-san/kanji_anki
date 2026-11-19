# Kani R8 keep rules
# The activity, widget receiver and WorkManager worker are referenced from the
# manifest or instantiated reflectively; AGP and WorkManager ship the rules for
# those. Keep the reminder worker explicitly since WorkManager creates it by name.
-keep class dev.bee.kanjianki.kani.reminder.DailyReminder$ReminderWorker { <init>(...); }
