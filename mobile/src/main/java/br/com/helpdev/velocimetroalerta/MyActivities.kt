package br.com.helpdev.velocimetroalerta

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.helpdev.velocimetroalerta.activities.ActivityRepository
import br.com.helpdev.velocimetroalerta.activities.ActivitySummary
import br.com.helpdev.velocimetroalerta.activities.RoutePreviewView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MyActivities : AppCompatActivity() {
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private lateinit var activityList: RecyclerView
    private lateinit var emptyState: View
    private lateinit var loading: View
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.enable(window)
        setContentView(R.layout.activity_my_activities)
        EdgeToEdge.applySystemBarInsets(window, findViewById(R.id.my_activities_root))
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.minhas_atividades)
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener { finish() }

        activityList = findViewById(R.id.activity_list)
        emptyState = findViewById(R.id.activity_empty_state)
        loading = findViewById(R.id.activity_feed_loading)
        activityList.layoutManager = LinearLayoutManager(this)
        activityList.adapter = ActivityAdapter { summary ->
            startActivity(ActivityDetailActivity.newIntent(this, summary.id))
        }
    }

    override fun onStart() {
        super.onStart()
        loadActivities()
    }

    private fun loadActivities() {
        val generation = ++loadGeneration
        loading.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        activityList.visibility = View.GONE
        ioExecutor.execute {
            val activities = ActivityRepository(applicationContext.filesDir).list()
            runOnUiThread {
                if (isFinishing || generation != loadGeneration) return@runOnUiThread
                loading.visibility = View.GONE
                if (activities.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                } else {
                    (activityList.adapter as ActivityAdapter).submit(activities)
                    activityList.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onDestroy() {
        ioExecutor.shutdown()
        super.onDestroy()
    }

    private class ActivityAdapter(
        private val onClick: (ActivitySummary) -> Unit
    ) : RecyclerView.Adapter<ActivityAdapter.Holder>() {
        private sealed interface Entry {
            data class Month(val label: String) : Entry
            data class Activity(val summary: ActivitySummary) : Entry
        }

        private val entries = mutableListOf<Entry>()

        fun submit(summaries: List<ActivitySummary>) {
            entries.clear()
            val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            var previousMonth: String? = null
            summaries.forEach { summary ->
                val month = monthFormatter.format(Date(summary.startedAtEpochMillis))
                    .replaceFirstChar { it.titlecase(Locale.getDefault()) }
                if (month != previousMonth) {
                    entries += Entry.Month(month)
                    previousMonth = month
                }
                entries += Entry.Activity(summary)
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_activity_summary, parent, false), onClick
        )

        override fun getItemCount() = entries.size

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(entries[position])

        class Holder(itemView: View, private val onClick: (ActivitySummary) -> Unit) : RecyclerView.ViewHolder(itemView) {
            private val monthHeader: TextView = itemView.findViewById(R.id.month_header)
            private val card: View = itemView.findViewById(R.id.activity_card)
            private val route: RoutePreviewView = itemView.findViewById(R.id.activity_route_preview)
            private val name: TextView = itemView.findViewById(R.id.activity_name)
            private val date: TextView = itemView.findViewById(R.id.activity_date)
            private val metrics: TextView = itemView.findViewById(R.id.activity_metrics)

            fun bind(entry: Entry) {
                when (entry) {
                    is Entry.Month -> {
                        monthHeader.visibility = View.VISIBLE
                        monthHeader.text = entry.label
                        card.visibility = View.GONE
                        card.setOnClickListener(null)
                    }
                    is Entry.Activity -> {
                        val summary = entry.summary
                        monthHeader.visibility = View.GONE
                        card.visibility = View.VISIBLE
                        route.submit(summary.routePreview)
                        name.text = summary.name
                        date.text = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(summary.startedAtEpochMillis))
                        val number = NumberFormat.getNumberInstance().apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }
                        metrics.text = itemView.context.getString(
                            R.string.activity_summary_metrics,
                            number.format(summary.distanceKm),
                            formatDuration(summary.movingDurationMillis),
                            number.format(summary.averageSpeedKmh)
                        )
                        card.setOnClickListener { onClick(summary) }
                    }
                }
            }

            private fun formatDuration(milliseconds: Long): String {
                val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) - TimeUnit.HOURS.toMinutes(hours)
                return if (hours > 0) "${hours}h${minutes.toString().padStart(2, '0')}" else "${minutes}min"
            }
        }
    }
}
