package org.gala.game

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
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
import java.io.File

class LocationActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var addButton: ImageButton
    private lateinit var currentLocationText: TextView
    private lateinit var locationsRecyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar

    private var worldId: Int = 0
    private var worldName: String = ""
    private var currentLocation: String = ""
    private var pyModule: PyObject? = null
    private var locations: List<Map<String, Any?>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location)

        worldId = intent.getIntExtra("world_id", 0)
        worldName = intent.getStringExtra("world_name") ?: "未知世界"

        initViews()
        initPython()
        loadLocations()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        addButton = findViewById(R.id.addButton)
        currentLocationText = findViewById(R.id.currentLocationText)
        locationsRecyclerView = findViewById(R.id.locationsRecyclerView)
        loadingProgress = findViewById(R.id.loadingProgress)

        findViewById<TextView>(R.id.titleText).text = "$worldName - 位置管理"

        backButton.setOnClickListener { finish() }
        addButton.setOnClickListener { showCreateLocationDialog() }

        locationsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    }

    private fun initPython() {
        val py = Python.getInstance()
        pyModule = py.getModule("backend")
    }

    private fun loadLocations() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val worldData = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_world", worldId)?.asMap()
                }
                currentLocation = worldData?.get("user_location")?.toString() ?: ""
                currentLocationText.text = "当前位置: ${currentLocation.ifEmpty { "未设置" }}"

                locations = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_primary_locations", worldId)?.asList()?.map { pyObj ->
                        pyObj.asMap().mapKeys { it.key.toString() }
                    } ?: emptyList()
                }

                locationsRecyclerView.adapter = LocationAdapter(locations) { location, action ->
                    when (action) {
                        "teleport" -> teleportToLocation(location)
                        "edit" -> editLocation(location)
                    }
                }

            } catch (e: Exception) {
                showError("加载位置失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showCreateLocationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_location, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.nameInput)

        AlertDialog.Builder(this)
            .setTitle("创建位置")
            .setView(dialogView)
            .setPositiveButton("创建") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    createLocation(name)
                } else {
                    showError("请输入位置名称")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createLocation(name: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pyModule?.callAttr("create_location", worldId, name, "")
                }
                showToast("位置创建成功")
                loadLocations()
            } catch (e: Exception) {
                showError("创建失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun teleportToLocation(location: Map<String, Any?>) {
        val locationName = location["name"]?.toString() ?: return

        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pyModule?.callAttr("update_world", worldId, "user_location", locationName)
                }
                currentLocation = locationName
                currentLocationText.text = "当前位置: $locationName"
                showToast("已传送到 $locationName")
            } catch (e: Exception) {
                showError("传送失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun editLocation(location: Map<String, Any?>) {
        val locationId = location["id"]?.toString()?.toIntOrNull() ?: return
        val intent = android.content.Intent(this, LocationEditActivity::class.java)
        intent.putExtra("location_id", locationId)
        intent.putExtra("location_name", location["name"]?.toString() ?: "")
        intent.putExtra("world_id", worldId)
        startActivity(intent)
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

    inner class LocationAdapter(
        private val locations: List<Map<String, Any?>>,
        private val onAction: (Map<String, Any?>, String) -> Unit
    ) : RecyclerView.Adapter<LocationAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val locationImage: ImageView = view.findViewById(R.id.locationImage)
            val locationName: TextView = view.findViewById(R.id.locationName)
            val locationInfo: TextView = view.findViewById(R.id.locationInfo)
            val teleportButton: ImageButton = view.findViewById(R.id.teleportButton)
            val editButton: ImageButton = view.findViewById(R.id.editButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_location, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val location = locations[position]
            val locationName = location["name"]?.toString() ?: "未知位置"

            holder.locationName.text = locationName
            holder.locationInfo.text = "点击编辑查看详情"

            val imagePath = location["image_path"]?.toString()
            if (!imagePath.isNullOrEmpty()) {
                try {
                    val file = File(imagePath)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(imagePath)
                        holder.locationImage.setImageBitmap(bitmap)
                    }
                } catch (e: Exception) {
                }
            }

            val isCurrentLocation = locationName == currentLocation
            holder.teleportButton.alpha = if (isCurrentLocation) 0.3f else 1f
            holder.teleportButton.isEnabled = !isCurrentLocation

            holder.teleportButton.setOnClickListener {
                onAction(location, "teleport")
            }

            holder.editButton.setOnClickListener {
                onAction(location, "edit")
            }

            holder.itemView.setOnClickListener {
                onAction(location, "edit")
            }
        }

        override fun getItemCount() = locations.size
    }
}
