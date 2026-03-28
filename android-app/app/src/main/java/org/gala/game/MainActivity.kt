package org.gala.game

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var worldsRecyclerView: RecyclerView
    private lateinit var addWorldButton: FloatingActionButton
    private lateinit var loadingProgress: View
    private lateinit var titleText: android.widget.TextView

    private var pyModule: PyObject? = null
    private var dbManager: PyObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initPython()
        loadWorlds()
    }

    private fun initViews() {
        worldsRecyclerView = findViewById(R.id.worldsRecyclerView)
        addWorldButton = findViewById(R.id.addWorldButton)
        loadingProgress = findViewById(R.id.loadingProgress)
        titleText = findViewById(R.id.titleText)

        worldsRecyclerView.layoutManager = LinearLayoutManager(this)

        addWorldButton.setOnClickListener {
            showCreateWorldDialog()
        }
    }

    private fun initPython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        val py = Python.getInstance()
        pyModule = py.getModule("backend")
        dbManager = pyModule?.callAttr("get_db_manager")
    }

    private fun loadWorlds() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val worlds = withContext(Dispatchers.IO) {
                    dbManager?.callAttr("get_all_worlds")?.asList()?.map { pyObj ->
                        World(
                            id = pyObj["id"].toString().toInt(),
                            name = pyObj["name"].toString(),
                            description = pyObj["description"].toString(),
                            createdAt = pyObj["created_at"].toString()
                        )
                    } ?: emptyList()
                }

                worldsRecyclerView.adapter = WorldAdapter(worlds) { world ->
                    enterWorld(world)
                }

                if (worlds.isEmpty()) {
                    titleText.text = "还没有创建世界"
                } else {
                    titleText.text = "选择一个世界"
                }
            } catch (e: Exception) {
                showError("加载世界失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showCreateWorldDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_world, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.worldNameInput)
        val descInput = dialogView.findViewById<TextInputEditText>(R.id.worldDescInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("创建新世界")
            .setView(dialogView)
            .setPositiveButton("创建") { dialog, _ ->
                val name = nameInput.text.toString().trim()
                val desc = descInput.text.toString().trim()

                if (name.isNotEmpty()) {
                    createWorld(name, desc)
                } else {
                    showError("请输入世界名称")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createWorld(name: String, description: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    dbManager?.callAttr("create_world", name, description)
                }
                loadWorlds()
            } catch (e: Exception) {
                showError("创建世界失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun enterWorld(world: World) {
        val intent = android.content.Intent(this, GameActivity::class.java)
        intent.putExtra("world_id", world.id)
        intent.putExtra("world_name", world.name)
        startActivity(intent)
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        addWorldButton.isEnabled = !show
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("错误")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
}

data class World(
    val id: Int,
    val name: String,
    val description: String,
    val createdAt: String
)

class WorldAdapter(
    private val worlds: List<World>,
    private val onWorldClick: (World) -> Unit
) : RecyclerView.Adapter<WorldAdapter.WorldViewHolder>() {

    class WorldViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: android.widget.TextView = view.findViewById(R.id.worldNameText)
        val descText: android.widget.TextView = view.findViewById(R.id.worldDescText)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): WorldViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_world, parent, false)
        return WorldViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorldViewHolder, position: Int) {
        val world = worlds[position]
        holder.nameText.text = world.name
        holder.descText.text = world.description.ifEmpty { "暂无描述" }
        holder.itemView.setOnClickListener { onWorldClick(world) }
    }

    override fun getItemCount() = worlds.size
}
