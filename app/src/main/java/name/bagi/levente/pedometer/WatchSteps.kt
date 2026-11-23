/*
 *  Pedometer - Android App
 *  Copyright (C) 2009 Levente Bagi
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package name.bagi.levente.pedometer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.exp

/**
 * Reads the steps an Amazfit watch has recorded.
 *
 * Amazfit watches don't talk to third-party apps directly. The Zepp app
 * (or Zepp Life, for older bands) syncs the watch and writes its steps to
 * Health Connect; this class reads them back from there. Data is only as
 * fresh as Zepp's last sync with the watch.
 *
 * Health Connect's API is Kotlin coroutines; this object wraps it in plain
 * callbacks (delivered on the main thread) so the Java code can use it.
 */
object WatchSteps {
    private const val TAG = "WatchSteps"

    /** Zepp, the app for current Amazfit watches. */
    const val SOURCE_ZEPP = "com.huami.watch.hmwatchmanager"
    /** Zepp Life, the app for older Amazfit bands and Mi Bands. */
    const val SOURCE_ZEPP_LIFE = "com.xiaomi.hm.health"
    /** Setting value meaning "steps from any app in Health Connect". */
    const val SOURCE_ANY = "any"

    private const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

    @JvmField
    val STEPS_PERMISSION: String = HealthPermission.getReadPermission(StepsRecord::class)
    @JvmField
    val HEART_RATE_PERMISSION: String = HealthPermission.getReadPermission(HeartRateRecord::class)
    private val DISTANCE_PERMISSION = HealthPermission.getReadPermission(DistanceRecord::class)
    private val CALORIES_PERMISSION = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    private val RESTING_PERMISSION = HealthPermission.getReadPermission(RestingHeartRateRecord::class)

    /** Writing the phone's steps, so Google Health and other apps can show them. */
    @JvmField
    val WRITE_STEPS_PERMISSION: String = HealthPermission.getWritePermission(StepsRecord::class)
    private val WRITE_DISTANCE_PERMISSION = HealthPermission.getWritePermission(DistanceRecord::class)

    /** What "share with Google Health" asks for. */
    @JvmField
    val SHARE_PERMISSIONS: Set<String> = setOf(WRITE_STEPS_PERMISSION, WRITE_DISTANCE_PERMISSION)

    /** Everything the app asks for. Users may grant only some; each read copes with that. */
    @JvmField
    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    )

    /** Steps and distance for today, plus when the watch data was last updated. */
    class Today(
        @JvmField val steps: Long,
        @JvmField val meters: Double,
        /** Active calories (kcal) the watch recorded today. */
        @JvmField val activeKcal: Double,
        /** End time of the newest step record, or 0 if there is none today. */
        @JvmField val lastSyncMillis: Long,
    )

    class DayTotal(
        @JvmField val date: LocalDate,
        @JvmField val steps: Long,
    )

    interface Callback<T> {
        fun onResult(result: T)
        fun onError(message: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** One of HealthConnectClient.SDK_AVAILABLE / SDK_UNAVAILABLE / SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED. */
    @JvmStatic
    fun sdkStatus(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    @JvmStatic
    fun isAvailable(context: Context): Boolean =
        sdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    @JvmStatic
    fun needsProviderUpdate(context: Context): Boolean =
        sdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    /** Opens the Play Store page to install or update Health Connect (Android 13 and older). */
    @JvmStatic
    fun installIntent(context: Context): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = Uri.parse(
                "market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"
            )
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
        }

    /** Intent for the Health Connect permission screen. Start it with startActivityForResult. */
    @JvmStatic
    fun permissionIntent(context: Context): Intent = permissionIntent(context, PERMISSIONS)

    /** Same, for a chosen set of permissions (e.g. [SHARE_PERMISSIONS]). */
    @JvmStatic
    fun permissionIntent(context: Context, permissions: Set<String>): Intent =
        PermissionController.createRequestPermissionResultContract()
            .createIntent(context, permissions)

    /**
     * Writes steps the phone counted between [startMillis] and [endMillis]
     * (and their distance, if allowed) to Health Connect, where Google Health
     * and other apps can read them. Each interval has a stable ID, so writing
     * it twice replaces rather than duplicates it. Reports false if writing
     * steps isn't allowed.
     */
    @JvmStatic
    fun writeSteps(
        context: Context,
        startMillis: Long,
        endMillis: Long,
        steps: Long,
        meters: Double,
        callback: Callback<Boolean>,
    ) {
        val app = context.applicationContext
        scope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(app)
                val granted = client.permissionController.getGrantedPermissions()
                if (WRITE_STEPS_PERMISSION !in granted) {
                    callback.onResult(false)
                    return@launch
                }
                val zone = ZoneId.systemDefault()
                val start = Instant.ofEpochMilli(startMillis)
                val end = Instant.ofEpochMilli(maxOf(endMillis, startMillis + 1000))
                val startOffset = zone.rules.getOffset(start)
                val endOffset = zone.rules.getOffset(end)
                val device = Device(type = Device.TYPE_PHONE)

                val records = mutableListOf<Record>(
                    StepsRecord(
                        startTime = start, startZoneOffset = startOffset,
                        endTime = end, endZoneOffset = endOffset,
                        count = steps,
                        metadata = Metadata.autoRecorded(
                            clientRecordId = "pedometer-steps-$startMillis",
                            clientRecordVersion = 1L,
                            device = device,
                        ),
                    )
                )
                if (meters > 0 && WRITE_DISTANCE_PERMISSION in granted) {
                    records.add(
                        DistanceRecord(
                            startTime = start, startZoneOffset = startOffset,
                            endTime = end, endZoneOffset = endOffset,
                            distance = Length.meters(meters),
                            metadata = Metadata.autoRecorded(
                                clientRecordId = "pedometer-distance-$startMillis",
                                clientRecordVersion = 1L,
                                device = device,
                            ),
                        )
                    )
                }
                client.insertRecords(records)
                callback.onResult(true)
            } catch (e: Exception) {
                Log.w(TAG, "Writing steps to Health Connect failed", e)
                callback.onError(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** The Health Connect read permissions the user has granted to this app. */
    @JvmStatic
    fun grantedPermissions(context: Context, callback: Callback<Set<String>>) {
        val app = context.applicationContext
        scope.launch {
            try {
                callback.onResult(
                    HealthConnectClient.getOrCreate(app).permissionController.getGrantedPermissions()
                )
            } catch (e: Exception) {
                Log.w(TAG, "Permission check failed", e)
                callback.onError(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Today's totals from [source] (a package name, or [SOURCE_ANY]). */
    @JvmStatic
    fun readToday(context: Context, source: String, callback: Callback<Today>) {
        val app = context.applicationContext
        scope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(app)
                val granted = client.permissionController.getGrantedPermissions()
                val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
                val range = TimeRangeFilter.between(start, Instant.now())
                val origins = originsFor(source)

                // Only ask for what the user allowed; asking for more throws.
                val metrics = mutableSetOf<androidx.health.connect.client.aggregate.AggregateMetric<*>>(
                    StepsRecord.COUNT_TOTAL
                )
                if (DISTANCE_PERMISSION in granted) metrics.add(DistanceRecord.DISTANCE_TOTAL)
                if (CALORIES_PERMISSION in granted) metrics.add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)

                val totals = client.aggregate(
                    AggregateRequest(
                        metrics = metrics,
                        timeRangeFilter = range,
                        dataOriginFilter = origins,
                    )
                )
                val newest = client.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = range,
                        dataOriginFilter = origins,
                        ascendingOrder = false,
                        pageSize = 1,
                    )
                ).records.firstOrNull()

                callback.onResult(
                    Today(
                        steps = totals[StepsRecord.COUNT_TOTAL] ?: 0L,
                        meters = totals[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0,
                        activeKcal = totals[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                            ?.inKilocalories ?: 0.0,
                        lastSyncMillis = newest?.endTime?.toEpochMilli() ?: 0L,
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Reading today's watch steps failed", e)
                callback.onError(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Step totals per day for the last [days] days, oldest first, today included. */
    @JvmStatic
    fun readDaily(context: Context, source: String, days: Int, callback: Callback<List<DayTotal>>) {
        val app = context.applicationContext
        scope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(app)
                val start = LocalDate.now().minusDays((days - 1).toLong()).atStartOfDay()
                val groups = client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start, LocalDateTime.now()),
                        timeRangeSlicer = Period.ofDays(1),
                        dataOriginFilter = originsFor(source),
                    )
                )
                callback.onResult(groups.map {
                    DayTotal(it.startTime.toLocalDate(), it.result[StepsRecord.COUNT_TOTAL] ?: 0L)
                })
            } catch (e: Exception) {
                Log.w(TAG, "Reading watch history failed", e)
                callback.onError(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Result of [readActivityScore]. */
    class ActivityScore(
        /** Points over the last 7 days (the PAI-style weekly score). */
        @JvmField val weekly: Int,
        /** Points earned today. */
        @JvmField val today: Int,
        /** Resting heart rate used, in bpm. */
        @JvmField val restingBpm: Int,
        /** Maximum heart rate used, in bpm. */
        @JvmField val maxBpm: Int,
        /** Number of heart-rate samples found; 0 means the watch shared none. */
        @JvmField val samples: Int,
    )

    /** Pure maths for the activity score, kept apart so it can be unit-tested. */
    object ScoreMath {
        /**
         * Points per minute at a given heart rate. Nothing below 30% of
         * heart-rate reserve; above that it rises steeply, so hard effort
         * counts far more than easy effort (~0.17/min easy walking,
         * ~2.5/min very hard).
         */
        @JvmStatic
        fun pointsPerMinute(bpm: Int, restingBpm: Int, maxBpm: Int): Double {
            val reserve = (maxBpm - restingBpm).coerceAtLeast(1)
            val intensity = (bpm - restingBpm).toDouble() / reserve
            if (intensity < 0.30) return 0.0
            return 0.015 * exp(6.0 * intensity.coerceAtMost(1.0))
        }

        /** Maximum heart rate from age (Tanaka: 208 - 0.7 x age). */
        @JvmStatic
        fun maxHeartRate(age: Int): Int = (208 - 0.7 * age).toInt()
    }

    /**
     * Estimates a PAI-style weekly activity score from the watch's heart rate.
     *
     * Zepp's own PAI is not shared with other apps, so this is an
     * approximation, not Zepp's number. Each heart-rate sample earns points
     * according to its intensity (% of heart-rate reserve) and how long it
     * lasted. The curve is tuned so that about 40 minutes of very hard
     * exercise, or about 10 hours of easy walking, in a week gives ~100.
     *
     * @param age years, for the maximum heart rate (Tanaka: 208 - 0.7 x age)
     * @param fallbackRestingBpm used if Health Connect has no resting heart rate
     */
    @JvmStatic
    fun readActivityScore(
        context: Context,
        source: String,
        age: Int,
        fallbackRestingBpm: Int,
        callback: Callback<ActivityScore>,
    ) {
        val app = context.applicationContext
        scope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(app)
                val origins = originsFor(source)
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val weekStart = LocalDate.now().minusDays(6).atStartOfDay(zone).toInstant()
                val todayStart = LocalDate.now().atStartOfDay(zone).toInstant()

                // Resting heart rate: average of the last 7 days, if recorded and allowed.
                val granted = client.permissionController.getGrantedPermissions()
                val resting = if (RESTING_PERMISSION in granted) {
                    client.readRecords(
                        ReadRecordsRequest(
                            recordType = RestingHeartRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(weekStart, now),
                            dataOriginFilter = origins,
                        )
                    ).records.map { it.beatsPerMinute }
                } else emptyList()
                val restingBpm = if (resting.isNotEmpty()) resting.average().toInt()
                                 else fallbackRestingBpm
                val maxBpm = ScoreMath.maxHeartRate(age)

                // All heart-rate samples for the week, oldest first.
                val samples = ArrayList<Pair<Instant, Long>>()
                var pageToken: String? = null
                do {
                    val page = client.readRecords(
                        ReadRecordsRequest(
                            recordType = HeartRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(weekStart, now),
                            dataOriginFilter = origins,
                            pageToken = pageToken,
                        )
                    )
                    for (record in page.records) {
                        for (sample in record.samples) {
                            samples.add(Pair(sample.time, sample.beatsPerMinute))
                        }
                    }
                    pageToken = page.pageToken
                } while (pageToken != null)
                samples.sortBy { it.first }

                var weekly = 0.0
                var today = 0.0
                for (i in samples.indices) {
                    val (time, bpm) = samples[i]
                    // A sample stands for the time until the next one, at most 10 minutes.
                    val next = if (i + 1 < samples.size) samples[i + 1].first else time.plusSeconds(60)
                    val minutes = ChronoUnit.SECONDS.between(time, next).coerceIn(0L, 600L) / 60.0
                    val points = ScoreMath.pointsPerMinute(bpm.toInt(), restingBpm, maxBpm) * minutes
                    weekly += points
                    if (!time.isBefore(todayStart)) today += points
                }

                callback.onResult(
                    ActivityScore(weekly.toInt(), today.toInt(), restingBpm, maxBpm, samples.size)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Reading heart rate failed", e)
                callback.onError(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun originsFor(source: String): Set<DataOrigin> =
        if (source.isBlank() || source == SOURCE_ANY) emptySet() else setOf(DataOrigin(source))
}
