package org.gala.game

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class CharacterEditActivity : AppCompatActivity() {

    private lateinit var nameInput: TextInputEditText
    private lateinit var descInput: TextInputEditText
    private lateinit var backgroundInput: TextInputEditText
    private lateinit var locationSpinner: Spinner
    private lateinit var activitySlider: SeekBar
    private lateinit var activityValueText: TextView
    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button
    private lateinit var loadingProgress: View
    private lateinit var titleText: TextView
    private lateinit var backButton: ImageButton
    private lateinit var avatarContainer: FrameLayout
    private lateinit var avatarText: TextView
    private lateinit var avatarImage: ImageView
    private lateinit var portraitButton: Button

    private var worldId: Int = 0
    private var characterId: Int = 0
    private var characterName: String = ""
    private var pyModule: PyObject? = null
    private var character: Character? = null
    private var locations: List<Map<String, Any>> = emptyList()
    private var selectedAvatarPath: String? = null
    private var selectedPortraitPath: String? = null

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImageSelection(it, "avatar") }
    }

    private val portraitPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImageSelection(it, "portrait") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_edit)

        worldId = intent.getIntExtra("world_id", 0)
        characterId = intent.getIntExtra("character_id", 0)
        characterName = intent.getStringExtra("character_name") ?: "未知角色"

        initViews()
        initPython()
        loadCharacterData()
    }

    private fun initViews() {
        nameInput = findViewById(R.id.nameInput)
        descInput = findViewById(R.id.descInput)
        backgroundInput = findViewById(R.id.backgroundInput)
        locationSpinner = findViewById(R.id.locationSpinner)
        activitySlider = findViewById(R.id.activitySlider)
        activityValueText = findViewById(R.id.activityValueText)
        saveButton = findViewById(R.id.saveButton)
        deleteButton = findViewById(R.id.deleteButton)
        loadingProgress = findViewById(R.id.loadingProgress)
        titleText = findViewById(R.id.titleText)
        backButton = findViewById(R.id.backButton)
        avatarContainer = findViewById(R.id.avatarContainer)
        avatarText = findViewById(R.id.avatarText)
        avatarImage = findViewById(R.id.avatarImage)
        portraitButton = findViewById(R.id.portraitButton)

        titleText.text = "编辑角色: $characterName"
        avatarText.text = characterName.firstOrNull()?.toString() ?: "?"

        backButton.setOnClickListener { finish() }
        saveButton.setOnClickListener { saveCharacter() }
        deleteButton.setOnClickListener { confirmDelete() }

        activitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                activityValueText.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        avatarContainer.setOnClickListener {
            showImageOptionsDialog("avatar")
        }

        portraitButton.setOnClickListener {
            showImageOptionsDialog("portrait")
        }
    }

    private fun showImageOptionsDialog(type: String) {
        val options = arrayOf("从相册选择", "清除图片")
        MaterialAlertDialogBuilder(this)
            .setTitle(if (type == "avatar") "选择头像" else "选择立绘")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (type == "avatar") avatarPicker.launch("image/*")
                        else portraitPicker.launch("image/*")
                    }
                    1 -> {
                        if (type == "avatar") {
                            selectedAvatarPath = null
                            avatarImage.visibility = View.GONE
                            avatarText.visibility = View.VISIBLE
                        } else {
                            selectedPortraitPath = null
                            showToast("立绘已清除")
                        }
                    }
                }
            }
            .show()
    }

    private fun handleImageSelection(uri: Uri, type: String) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val imageDir = File(getExternalFilesDir(null), "worlds/world_$worldId/avatars")
                if (!imageDir.exists()) imageDir.mkdirs()
                
                val fileName = if (type == "avatar") "avatar_$characterId.jpg" else "portrait_$characterId.jpg"
                val destFile = File(imageDir, fileName)
                
                FileOutputStream(destFile).use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
                
                if (type == "avatar") {
                    selectedAvatarPath = destFile.absolutePath
                    val bitmap = BitmapFactory.decodeFile(destFile.absolutePath)
                    avatarImage.setImageBitmap(bitmap)
                    avatarImage.visibility = View.VISIBLE
                    avatarText.visibility = View.GONE
                } else {
                    selectedPortraitPath = destFile.absolutePath
                    showToast("立绘设置成功")
                }
            }
        } catch (e: Exception) {
            showError("图片保存失败: ${e.message}")
        }
    }

    private fun initPython() {
        val py = Python.getInstance()
        pyModule = py.getModule("backend")
    }

    private fun loadCharacterData() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val charData = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_character", characterId)
                }

                val locData = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_locations", worldId)?.asList()?.map { pyObj ->
                        mapOf(
                            "id" to pyObj["id"].toString().toInt(),
                            "name" to pyObj["name"].toString()
                        )
                    } ?: emptyList()
                }
                locations = locData

                if (charData != null) {
                    character = Character(
                        id = charData["id"].toString().toInt(),
                        worldId = charData["world_id"].toString().toInt(),
                        name = charData["name"].toString(),
                        background = charData["background"]?.toString() ?: "",
                        description = charData["description"]?.toString() ?: "",
                        avatarPath = charData["avatar_path"]?.toString(),
                        location = charData["location"]?.toString() ?: "",
                        gender = charData["gender"]?.toString() ?: "female",
                        activityScore = charData["activity_score"]?.toString()?.toInt() ?: 50
                    )

                    populateFields()
                }
            } catch (e: Exception) {
                showError("加载角色数据失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun populateFields() {
        character?.let { c ->
            nameInput.setText(c.name)
            descInput.setText(c.description)
            backgroundInput.setText(c.background)
            activitySlider.progress = c.activityScore
            activityValueText.text = c.activityScore.toString()

            val locationNames = mutableListOf("无位置")
            locationNames.addAll(locations.map { it["name"].toString() })
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, locationNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            locationSpinner.adapter = adapter

            val locationIndex = locationNames.indexOf(c.location)
            if (locationIndex > 0) {
                locationSpinner.setSelection(locationIndex)
            }

            if (!c.avatarPath.isNullOrEmpty()) {
                selectedAvatarPath = c.avatarPath
                val file = File(c.avatarPath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(c.avatarPath)
                    avatarImage.setImageBitmap(bitmap)
                    avatarImage.visibility = View.VISIBLE
                    avatarText.visibility = View.GONE
                }
            }
        }
    }

    private fun saveCharacter() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            showError("请输入角色名称")
            return
        }

        showLoading(true)
        lifecycleScope.launch {
            try {
                val locationName = locationSpinner.selectedItemPosition.let { pos ->
                    if (pos > 0) locations[pos - 1]["name"].toString() else ""
                }
                
                withContext(Dispatchers.IO) {
                    pyModule?.callAttr(
                        "update_character",
                        characterId,
                        name = name,
                        background = backgroundInput.text.toString(),
                        description = descInput.text.toString(),
                        location = locationName,
                        avatar_path = selectedAvatarPath,
                        activity_score = activitySlider.progress
                    )
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
        MaterialAlertDialogBuilder(this)
            .setTitle("删除角色")
            .setMessage("确定要删除角色 \"$characterName\" 吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                deleteCharacter()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteCharacter() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pyModule?.callAttr("delete_character", characterId)
                }
                showToast("角色已删除")
                finish()
            } catch (e: Exception) {
                showError("删除失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        saveButton.isEnabled = !show
        deleteButton.isEnabled = !show
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("错误")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
}
