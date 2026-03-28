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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MemoryActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var titleText: TextView
    private lateinit var addMemoryButton: Button
    private lateinit var memoriesRecyclerView: RecyclerView
    private lateinit var noMemoriesText: TextView
    private lateinit var loadingProgress: ProgressBar

    private var worldId: Int = 0
    private var pyModule: PyObject? = null
    private var memories: List<Map<String, Any?>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory)

        worldId = intent.getIntExtra("world_id", 0)

        initViews()
        initPython()
        loadMemories()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        titleText = findViewById(R.id.titleText)
        addMemoryButton = findViewById(R.id.addMemoryButton)
        memoriesRecyclerView = findViewById(R.id.memoriesRecyclerView)
        noMemoriesText = findViewById(R.id.noMemoriesText)
        loadingProgress = findViewById(R.id.loadingProgress)

        titleText.text = "世界记忆"

        backButton.setOnClickListener { finish() }
        addMemoryButton.setOnClickListener { showAddMemoryDialog() }

        memoriesRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    }

    private fun initPython() {
        val py = Python.getInstance()
        pyModule = py.getModule("backend")
    }

    private fun loadMemories() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                memories = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_memories_by_world", worldId, 50)?.asList()?.map { pyObj ->
                        pyObj.asMap().mapKeys { it.key.toString() }
                    } ?: emptyList()
                }

                updateUI()
            } catch (e: Exception) {
                showError("加载记忆失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun updateUI() {
        if (memories.isEmpty()) {
            memoriesRecyclerView.visibility = View.GONE
            noMemoriesText.visibility = View.VISIBLE
        } else {
            memoriesRecyclerView.visibility = View.VISIBLE
            noMemoriesText.visibility = View.GONE
            memoriesRecyclerView.adapter = MemoryAdapter(memories) { memory ->
                deleteMemory(memory)
            }
        }
    }

    private fun showAddMemoryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_memory, null)
        val contentInput = dialogView.findViewById<TextInputEditText>(R.id.memoryContentInput)
        val typeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.memoryTypeSpinner)
        val importanceSlider = dialogView.findViewById<android.widget.SeekBar>(R.id.importanceSlider)

        val types = arrayOf("general", "event", "relationship", "location")
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = adapter

        MaterialAlertDialogBuilder(this)
            .setTitle("添加记忆")
            .setView(dialogView)
            .setPositiveButton("添加") { _, _ ->
                val content = contentInput.text.toString().trim()
                val type = typeSpinner.selectedItem.toString()
                val importance = importanceSlider.progress

                if (content.isNotEmpty()) {
                    createMemory(content, type, importance)
                } else {
                    showError("请输入记忆内容")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createMemory(content: String, type: String, importance: Int) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pyModule?.callAttr("create_memory", worldId, content, type, importance)
                }
                showToast("记忆已添加")
                loadMemories()
            } catch (e: Exception) {
                showError("添加失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun deleteMemory(memory: Map<String, Any?>) {
        val memoryId = memory["id"]?.toString()?.toIntOrNull() ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle("删除记忆")
            .setMessage("确定要删除这条记忆吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            pyModule?.callAttr("delete_memory", memoryId)
                        }
                        showToast("记忆已删除")
                        loadMemories()
                    } catch (e: Exception) {
                        showError("删除失败: ${e.message}")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        addMemoryButton.isEnabled = !show
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    inner class MemoryAdapter(
        private val memories: List<Map<String, Any?>>,
        private val onDelete: (Map<String, Any?>) -> Unit
    ) : RecyclerView.Adapter<MemoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val contentText: TextView = view.findViewById(R.id.memoryContent)
            val typeText: TextView = view.findViewById(R.id.memoryType)
            val importanceText: TextView = view.findViewById(R.id.memoryImportance)
            val deleteButton: ImageButton = view.findViewById(R.id.deleteMemoryButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_memory, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val memory = memories[position]
            holder.contentText.text = memory["content"]?.toString() ?: ""
            holder.typeText.text = memory["memory_type"]?.toString() ?: "general"
            holder.importanceText.text = "重要度: ${memory["importance"]?.toString() ?: "5"}"
            holder.deleteButton.setOnClickListener { onDelete(memory) }
        }

        override fun getItemCount() = memories.size
    }
}
