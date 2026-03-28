package org.gala.game

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LocationEditActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var saveButton: ImageButton
    private lateinit var deleteButton: ImageButton
    private lateinit var titleText: TextView
    private lateinit var nameInput: EditText
    private lateinit var locationImage: ImageView
    private lateinit var selectImageButton: android.widget.Button
    private lateinit var subLocationsRecyclerView: RecyclerView
    private lateinit var addSubLocationButton: android.widget.Button
    private lateinit var loadingProgress: ProgressBar

    private var locationId: Int = 0
    private var locationName: String = ""
    private var worldId: Int = 0
    private var imagePath: String = ""
    private var pyModule: PyObject? = null
    private var subLocations: List<Map<String, Any?>> = emptyList()

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImageSelection(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_edit)

        locationId = intent.getIntExtra("location_id", 0)
        locationName = intent.getStringExtra("location_name") ?: ""
        worldId = intent.getIntExtra("world_id", 0)

        initViews()
        initPython()
        loadLocationData()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        saveButton = findViewById(R.id.saveButton)
        deleteButton = findViewById(R.id.deleteButton)
        titleText = findViewById(R.id.titleText)
        nameInput = findViewById(R.id.nameInput)
        locationImage = findViewById(R.id.locationImage)
        selectImageButton = findViewById(R.id.selectImageButton)
        subLocationsRecyclerView = findViewById(R.id.subLocationsRecyclerView)
        addSubLocationButton = findViewById(R.id.addSubLocationButton)
        loadingProgress = findViewById(R.id.loadingProgress)

        titleText.text = locationName.ifEmpty { "编辑位置" }
        nameInput.setText(locationName)

        backButton.setOnClickListener { finish() }
        saveButton.setOnClickListener { saveLocation() }
        deleteButton.setOnClickListener { confirmDelete() }
        selectImageButton.setOnClickListener { showImageOptionsDialog() }
        addSubLocationButton.setOnClickListener { addSubLocation() }

        subLocationsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    }

    private fun showImageOptionsDialog() {
        val options = arrayOf("从相册选择", "清除图片")
        AlertDialog.Builder(this)
            .setTitle("选择位置图片")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> imagePicker.launch("image/*")
                    1 -> {
                        imagePath = ""
                        locationImage.setImageResource(android.R.color.darker_gray)
                        showToast("图片已清除")
                    }
                }
            }
            .show()
    }

    private fun handleImageSelection(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val imageDir = File(getExternalFilesDir(null), "worlds/world_$worldId/locations")
                if (!imageDir.exists()) imageDir.mkdirs()
                
                val fileName = "location_$locationId.jpg"
                val destFile = File(imageDir, fileName)
                
                FileOutputStream(destFile).use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
                
                imagePath = destFile.absolutePath
                val bitmap = BitmapFactory.decodeFile(destFile.absolutePath)
                locationImage.setImageBitmap(bitmap)
                showToast("图片设置成功")
            }
        } catch (e: Exception) {
            showError("图片保存失败: ${e.message}")
        }
    }

    private fun initPython() {
        val py = Python.getInstance()
        pyModule = py.getModule("backend")
    }

    private fun loadLocationData() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val locationData = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_location", locationId)?.asMap()
                }
                
                if (locationData != null) {
                    val existingImagePath = locationData["image_path"]?.toString()
                    if (!existingImagePath.isNullOrEmpty()) {
                        imagePath = existingImagePath
                        val file = File(existingImagePath)
                        if (file.exists()) {
                            val bitmap = BitmapFactory.decodeFile(existingImagePath)
                            locationImage.setImageBitmap(bitmap)
                        }
                    }
                }

                subLocations = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_sub_locations", locationId)?.asList()?.map { pyObj ->
                        pyObj.asMap().mapKeys { it.key.toString() }
                    } ?: emptyList()
                }

                subLocationsRecyclerView.adapter = SubLocationAdapter(subLocations) { subLocation ->
                    deleteSubLocation(subLocation)
                }

            } catch (e: Exception) {
                showError("加载数据失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun saveLocation() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            showError("请输入位置名称")
            return
        }

        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pyModule?.callAttr("update_location", locationId, "name", name, "image_path", imagePath)
                }
                showToast("保存成功")
                finish()
            } catch (e: Exception) {
                showError("保存失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("删除位置")
            .setMessage("确定要删除此位置吗？")
            .setPositiveButton("删除") { _, _ -> deleteLocation() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteLocation() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pyModule?.callAttr("delete_location", locationId)
                }
                showToast("删除成功")
                finish()
            } catch (e: Exception) {
                showError("删除失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun addSubLocation() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_location, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.nameInput)

        AlertDialog.Builder(this)
            .setTitle("添加子位置")
            .setView(dialogView)
            .setPositiveButton("添加") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    createSubLocation(name)
                } else {
                    showError("请输入子位置名称")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createSubLocation(name: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pyModule?.callAttr("create_location", worldId, name, "", locationId)
                }
                showToast("子位置创建成功")
                loadLocationData()
            } catch (e: Exception) {
                showError("创建失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun deleteSubLocation(subLocation: Map<String, Any?>) {
        val subLocationId = subLocation["id"]?.toString()?.toIntOrNull() ?: return

        AlertDialog.Builder(this)
            .setTitle("删除子位置")
            .setMessage("确定要删除此子位置吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            pyModule?.callAttr("delete_location", subLocationId)
                        }
                        showToast("删除成功")
                        loadLocationData()
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
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    inner class SubLocationAdapter(
        private val subLocations: List<Map<String, Any?>>,
        private val onDelete: (Map<String, Any?>) -> Unit
    ) : RecyclerView.Adapter<SubLocationAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.subLocationName)
            val deleteButton: ImageButton = view.findViewById(R.id.deleteSubLocationButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sub_location, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val subLocation = subLocations[position]
            holder.nameText.text = subLocation["name"]?.toString() ?: "未知"
            holder.deleteButton.setOnClickListener { onDelete(subLocation) }
        }

        override fun getItemCount() = subLocations.size
    }
}
