package org.gala.game

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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

class CallActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var activeCallsRecyclerView: RecyclerView
    private lateinit var pendingCallsRecyclerView: RecyclerView
    private lateinit var noCallsText: TextView
    private lateinit var noPendingText: TextView
    private lateinit var loadingProgress: ProgressBar

    private var worldId: Int = 0
    private var pyModule: PyObject? = null
    private var activeCalls: List<Map<String, Any?>> = emptyList()
    private var pendingCalls: List<Map<String, Any?>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        worldId = intent.getIntExtra("world_id", 0)

        initViews()
        initPython()
        loadCalls()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        activeCallsRecyclerView = findViewById(R.id.activeCallsRecyclerView)
        pendingCallsRecyclerView = findViewById(R.id.pendingCallsRecyclerView)
        noCallsText = findViewById(R.id.noCallsText)
        noPendingText = findViewById(R.id.noPendingText)
        loadingProgress = findViewById(R.id.loadingProgress)

        backButton.setOnClickListener { finish() }

        activeCallsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        pendingCallsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    }

    private fun initPython() {
        val py = Python.getInstance()
        pyModule = py.getModule("backend")
    }

    private fun loadCalls() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                activeCalls = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_active_calls_by_world", worldId)?.asList()?.map { pyObj ->
                        pyObj.asMap().mapKeys { it.key.toString() }
                    } ?: emptyList()
                }

                pendingCalls = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_pending_call_requests", worldId)?.asList()?.map { pyObj ->
                        pyObj.asMap().mapKeys { it.key.toString() }
                    } ?: emptyList()
                }

                updateUI()

            } catch (e: Exception) {
                showError("加载通话数据失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun updateUI() {
        if (activeCalls.isEmpty()) {
            activeCallsRecyclerView.visibility = View.GONE
            noCallsText.visibility = View.VISIBLE
        } else {
            activeCallsRecyclerView.visibility = View.VISIBLE
            noCallsText.visibility = View.GONE
            activeCallsRecyclerView.adapter = ActiveCallAdapter(activeCalls) { call ->
                endCall(call)
            }
        }

        if (pendingCalls.isEmpty()) {
            pendingCallsRecyclerView.visibility = View.GONE
            noPendingText.visibility = View.VISIBLE
        } else {
            pendingCallsRecyclerView.visibility = View.VISIBLE
            noPendingText.visibility = View.GONE
            pendingCallsRecyclerView.adapter = PendingCallAdapter(pendingCalls) { call, action ->
                when (action) {
                    "accept" -> acceptCall(call)
                    "reject" -> rejectCall(call)
                }
            }
        }
    }

    private fun endCall(call: Map<String, Any?>) {
        val callId = call["id"]?.toString()?.toIntOrNull() ?: return

        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pyModule?.callAttr("end_active_call", callId)
                }
                showToast("通话已结束")
                loadCalls()
            } catch (e: Exception) {
                showError("结束通话失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun acceptCall(call: Map<String, Any?>) {
        val requestId = call["id"]?.toString()?.toIntOrNull() ?: return

        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pyModule?.callAttr("accept_call_request", requestId)
                }
                showToast("已接听通话")
                loadCalls()
            } catch (e: Exception) {
                showError("接听失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun rejectCall(call: Map<String, Any?>) {
        val requestId = call["id"]?.toString()?.toIntOrNull() ?: return

        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pyModule?.callAttr("reject_call_request", requestId)
                }
                showToast("已拒绝通话")
                loadCalls()
            } catch (e: Exception) {
                showError("拒绝失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    inner class ActiveCallAdapter(
        private val calls: List<Map<String, Any?>>,
        private val onEndCall: (Map<String, Any?>) -> Unit
    ) : RecyclerView.Adapter<ActiveCallAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val characterName: TextView = view.findViewById(R.id.characterName)
            val callInfo: TextView = view.findViewById(R.id.callInfo)
            val endCallButton: Button = view.findViewById(R.id.endCallButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_active_call, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val call = calls[position]
            holder.characterName.text = call["character_name"]?.toString() ?: "未知角色"
            val date = call["call_start_date"]?.toString() ?: ""
            val time = call["call_start_time"]?.toString() ?: ""
            holder.callInfo.text = "开始时间: $date $time"
            holder.endCallButton.setOnClickListener { onEndCall(call) }
        }

        override fun getItemCount() = calls.size
    }

    inner class PendingCallAdapter(
        private val calls: List<Map<String, Any?>>,
        private val onAction: (Map<String, Any?>, String) -> Unit
    ) : RecyclerView.Adapter<PendingCallAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val characterName: TextView = view.findViewById(R.id.characterName)
            val requestInfo: TextView = view.findViewById(R.id.requestInfo)
            val acceptButton: Button = view.findViewById(R.id.acceptButton)
            val rejectButton: Button = view.findViewById(R.id.rejectButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pending_call, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val call = calls[position]
            holder.characterName.text = call["character_name"]?.toString() ?: "未知角色"
            val date = call["request_date"]?.toString() ?: ""
            val time = call["request_time"]?.toString() ?: ""
            holder.requestInfo.text = "请求时间: $date $time"
            holder.acceptButton.setOnClickListener { onAction(call, "accept") }
            holder.rejectButton.setOnClickListener { onAction(call, "reject") }
        }

        override fun getItemCount() = calls.size
    }
}
