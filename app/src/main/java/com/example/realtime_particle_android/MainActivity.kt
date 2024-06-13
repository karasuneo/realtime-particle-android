package com.example.realtime_particle_android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.realtime_particle_android.ui.theme.RealtimeparticleandroidTheme
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.io.FileWriter
import java.io.IOException

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accSensor: Sensor? = null
    private var gyroSensor: Sensor? = null

    lateinit var accDataArray: Array<MutableState<String>>
    lateinit var gyroDataArray: Array<MutableState<String>>

    private val sensorDataList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            accDataArray = Array(3) {
                 remember {
                    mutableStateOf("準備中")
                }
            }
            gyroDataArray = Array(3) {
                remember {
                    mutableStateOf("準備中")
                }
            }
            RealtimeparticleandroidTheme {
                SensorView()
            }
        }
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        requestPermissions()
    }

    private fun requestPermissions() {
        val requestPermissionLauncher =
            registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    Log.d("Permission", "Storage permission granted")
                } else {
                    Log.d("Permission", "Storage permission denied")
                }
            }
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d("Permission", "Storage permission already granted")
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        saveDataToCSV()
        sendCSVToServer()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            when (event.sensor.type) {
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    accDataArray[0].value = "x軸${event.values[0]}"
                    accDataArray[1].value = "y軸${event.values[1]}"
                    accDataArray[2].value = "z軸${event.values[2]}"
                    sensorDataList.add("${event.values[0]},${event.values[1]},${event.values[2]}")
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroDataArray[0].value = "x軸${event.values[0]}"
                    gyroDataArray[1].value = "y軸${event.values[1]}"
                    gyroDataArray[2].value = "z軸${event.values[2]}"
                    val lastIndex = sensorDataList.size - 1
                    if (lastIndex >= 0) {
                        val lastEntry = sensorDataList[lastIndex]
                        sensorDataList[lastIndex] = "$lastEntry,${event.values[0]},${event.values[1]},${event.values[2]}"
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun saveDataToCSV() {
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "sensor_data.csv")

        try {
            FileWriter(file).use { writer ->
                writer.append("acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z\n")
                sensorDataList.forEach { data ->
                    writer.append("$data\n")
                }
            }
            Log.d("CSV", "Data successfully saved to CSV")
        } catch (e: IOException) {
            Log.e("CSV", "Error writing CSV file", e)
        }
    }

    private fun sendCSVToServer() {
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "sensor_data.csv")
        if (!file.exists()) {
            Log.e("CSV", "File not found")
            return
        }

        val client = OkHttpClient()
        val mediaType = "text/csv".toMediaTypeOrNull()
        val fileBody = RequestBody.create(mediaType, file)
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, fileBody)
            .build()

        val request = Request.Builder()
            .url("https://your_api_endpoint.com/upload")  // サーバのURLに置き換えてください
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("HTTP", "Failed to send CSV", e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    Log.d("HTTP", "CSV successfully sent")
                } else {
                    Log.e("HTTP", "Failed to send CSV, response code: ${response.code}")
                }
            }
        })
    }

    @Composable
    fun SensorView() {
        val sensorModifier = Modifier.fillMaxWidth(0.6f)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "加速度センサ", textAlign = TextAlign.Center)
            Text(text = accDataArray[0].value, textAlign = TextAlign.Left, modifier = sensorModifier)
            Text(text = accDataArray[1].value, textAlign = TextAlign.Left, modifier = sensorModifier)
            Text(text = accDataArray[2].value, textAlign = TextAlign.Left, modifier = sensorModifier)
            Text(text = "ジャイロセンサ", textAlign = TextAlign.Center)
            Text(text = gyroDataArray[0].value, textAlign = TextAlign.Left, modifier = sensorModifier)
            Text(text = gyroDataArray[1].value, textAlign = TextAlign.Left, modifier = sensorModifier)
            Text(text = gyroDataArray[2].value, textAlign = TextAlign.Left, modifier = sensorModifier)
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RealtimeparticleandroidTheme {
        Greeting("Android")
    }
}
