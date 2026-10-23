package com.matan.vault.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.matan.vault.MainActivity
import com.matan.vault.R
import com.matan.vault.data.DocumentEntity
import com.matan.vault.data.VaultDatabase
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Runs once a day around 09:00 and sends at most one notification summarising what is about to expire. */
class ExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = VaultDatabase.get(applicationContext).documents()
        val today = LocalDate.now()
        val due = dao.withExpiry().filter { isDue(it, today) }.sortedBy { it.expiryDate }
        if (due.isEmpty()) return Result.success()
        notify(applicationContext, due, today)
        dao.markNotified(due.map { it.id }, today.toEpochDay())
        return Result.success()
    }

    companion object {
        const val CHANNEL = "expiry"
        private const val WORK = "expiry-daily"

        fun isDue(doc: DocumentEntity, today: LocalDate): Boolean {
            val left = doc.daysLeft(today) ?: return false
            if (left > doc.leadDays) return false
            val last = doc.lastNotifiedDay ?: return true
            val repeat = doc.cat().repeatDays
            if (repeat > 0) return today.toEpochDay() - last >= repeat
            // one-time categories: once at the lead time, once more on the day it expires
            return left <= 0 && last < (doc.expiryDate ?: 0)
        }

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(9, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes()
            val req = PeriodicWorkRequestBuilder<ExpiryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "תוקף מסמכים", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        fun describe(doc: DocumentEntity, today: LocalDate): String {
            val left = doc.daysLeft(today) ?: 0
            return when {
                left < 0 -> "${doc.title} פג לפני ${-left} ימים"
                left == 0L -> "${doc.title} פג היום"
                else -> "${doc.title} פג בעוד $left ימים"
            }
        }

        private fun notify(context: Context, due: List<DocumentEntity>, today: LocalDate) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33
            ) return
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (due.size == 1) putExtra(MainActivity.EXTRA_DOC_ID, due[0].id)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val title = if (due.size == 1) describe(due[0], today) else "${due.size} מסמכים פגים בקרוב"
            val style = NotificationCompat.InboxStyle()
            due.take(6).forEach { style.addLine(describe(it, today)) }
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_fg)
                .setContentTitle(title)
                .setContentText(if (due.size == 1) "פתח את המסמך כדי לחדש או לדחות" else due.joinToString(" · ") { it.title })
                .setStyle(style)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(1001, n)
            } catch (_: SecurityException) {
            }
        }
    }
}
