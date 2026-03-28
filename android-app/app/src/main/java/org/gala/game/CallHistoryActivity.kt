package org.gala.game

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallHistoryActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var titleText: TextView
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var noHistoryText: TextView
    private lateinit var loadingProgress: ProgressBar

    private var worldId: Int = 0
    private var pyModule: PyObject? = null
    private var callHistory: List<Map<String, Any?>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_history)

        worldId = intent.getIntExtra("world_id", 0)

        initViews()
        initPython()
        loadHistory()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        titleText = findViewById(R.id.titleText)
        historyRecyclerView = findViewById(R.id.historyRecyclerView)
        noHistoryText = findViewById(R.id.noHistoryText)
        loadingProgress = findViewById(R.id.loadingProgress)

        titleText.text = "通话历史"

        backButton.setOnClickListener { finish() }

        historyRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    }

    private fun initPython() {
        val py = Python.getInstance()
        pyModule = py.getModule("backend")
    }

    private fun loadHistory() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                callHistory = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_call_history", worldId, 50)?.asList()?.map { pyObj ->
                        pyObj.asMap().mapKeys { it.key.toString() }
                    } ?: emptyList()
                }

                updateUI()
            } catch (e: Exception) {
                showError("加载历史失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun updateUI() {
        if (callHistory.isEmpty()) {
            historyRecyclerView.visibility = View.GONE
            noHistoryText.visibility = View.VISIBLE
        } else {
            historyRecyclerView.visibility = View.VISIBLE
            noHistoryText.visibility = View.GONE
            historyRecyclerView.adapter = HistoryAdapter(callHistory)
        }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    inner class HistoryAdapter(
        private val history: List<Map<String, Any?>>
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val characterName: TextView = view.findViewById(R.id.characterName)
            val callTime: TextView = view.findViewById(R.id.callTime)
            val duration: TextView = view.findViewById(R.id.callDuration)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_call_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val call = history[position]
            holder.characterName.text = call["character_name"]?.toString() ?: "未知角色"
            
            val startDate = call["call_start_date"]?.toString() ?: ""
            val startTime = call["call_start_time"]?.toString() ?: ""
            val endDate = call["call_end_date"]?.toString() ?: ""
            val endTime = call["call_end_time"]?.toString() ?: ""
            
            holder.callTime.text = "开始: $startDate $startTime\n结束: $endDate $endTime"
            
            val durationSeconds = call["duration_seconds"]?.toString()?.toIntOrNull() ?: 0
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            holder.duration.text = "时长: ${minutes}分${seconds}秒"
        }

        override fun getItemCount() = history.size
    }
}
