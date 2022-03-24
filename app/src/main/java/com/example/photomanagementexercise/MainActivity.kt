package com.example.photomanagementexercise

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photomanagementexercise.access.AccessPermissionType.*
import com.example.photomanagementexercise.access.CheckPermissionUtility
import com.example.photomanagementexercise.access.DefaultCheckPermissionUtility
import com.example.photomanagementexercise.ui.theme.PhotoManagementExerciseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.util.*


@ExperimentalMaterialApi
@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : DefaultAppActivity() {

    enum class PhotoSelectionType(val id: Int) {
        PhotoFromGallery(0x0101), PhotoFromCamera(0x0102)
    }

    private val cameraImageFilePrefix = "camera_image"
    private val imageWidth = 200
    private val imageHeight = 200
    private val pictureDirectory: String by lazy { this.cacheDir.absolutePath }
    private val cameraFileName = "camera.image_%s.png"
    private val galleryFileName = "gallery.image_%s.png"
    private val croppedFileName = "cropped.image_%s.png"
    private val emptyImageUri: Uri = Uri.parse("file://dev/null")

    private var photoOutput: Uri? = null

    private lateinit var shouldShowOnBoarding: MutableState<Boolean>
    private lateinit var shouldShowOnProgress: MutableState<Boolean>
    private lateinit var imageFiles: MutableState<List<Uri>>
    private lateinit var checkPermissionUtility: CheckPermissionUtility
    private lateinit var coroutineScope: CoroutineScope

    @Composable
    override fun MyApp(savedInstanceState: Bundle?) {
        shouldShowOnBoarding = rememberSaveable { mutableStateOf(true) }
        shouldShowOnProgress = rememberSaveable { mutableStateOf(false) }
        coroutineScope = rememberCoroutineScope()
        imageFiles = remember { mutableStateOf(listOf()) }

        if (shouldShowOnBoarding.value) {
            ShowOnBoardingScreen(onContinueClicked = {
//                shouldShowOnBoarding.value = false
            })
        } else {
            ShowGreetings()
        }
    }

    @Composable
    private fun ShowOnBoardingScreen(onContinueClicked: () -> Unit) {
        val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = BottomSheetState(BottomSheetValue.Collapsed)
        )
        Surface(modifier = Modifier.fillMaxSize()) {
            Card(
                modifier =  Modifier
                    .padding(all = 16.dp)
                    .fillMaxWidth()
                    .shadow(375.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if(shouldShowOnProgress.value) {
                        CreateLinearProgressIndicator(
                            modifier = Modifier,
                            color = Color.Green,
                            backgroundColor = Color.Red
                        )
                    }
                    if(imageFiles.value.isNotEmpty()) {
                        LazyRow(modifier = Modifier.padding(vertical = 4.dp)) {
                            items(items = imageFiles.value) { uri ->
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(uri)
                                        .crossfade(true)
                                        .build(),
                                    placeholder = painterResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = stringResource(R.string.image_included),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(128.dp)
                                        .clip(CircleShape)
                                        .border(5.dp, Color.Gray, CircleShape),
                                )
                            }
                        }
                    } else {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            text = stringResource(R.string.no_images_available)
                        )
                    }
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        text = stringResource(R.string.instructions)
                    )
                    Button(
                        modifier = Modifier.padding(vertical = 24.dp),
                        onClick = {
                            coroutineScope.launch {
                                if (bottomSheetScaffoldState.bottomSheetState.isCollapsed) {
                                    bottomSheetScaffoldState.bottomSheetState.expand()
                                } else {
                                    bottomSheetScaffoldState.bottomSheetState.collapse()
                                }
                            }
                        }) {
                        Text(stringResource(R.string.continue_text))
                    }
                    BottomSheetScaffold(
                        modifier = Modifier,
                        scaffoldState = bottomSheetScaffoldState,
                        sheetPeekHeight = 0.dp,
                        sheetContent = {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    OutlinedButton(
                                        onClick = {
                                            println("MROEBUCK: Choosing from Gallery...")
                                            coroutineScope.launch {
                                                bottomSheetScaffoldState.bottomSheetState.collapse()
                                                onSelectImage { selectPhotoFromGallery() }
                                            }
                                        },
                                        modifier = Modifier,
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoAlbum,
                                            contentDescription = null,
                                            modifier = Modifier.padding(start = 4.dp),
                                            tint = Color.Black
                                        )
                                        Text(
                                            modifier = Modifier.padding(4.dp, 0.dp),
                                            color = Color.Gray,
                                            text = "Choose From Gallery"
                                        )
                                    }
                                    Divider()
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    OutlinedButton(
                                        onClick = {
                                            println("MROEBUCK: Take Photo From Camera...")
                                            coroutineScope.launch {
                                                bottomSheetScaffoldState.bottomSheetState.collapse()
                                                onSelectImage { takeScreenshotFromCamera() }
                                            }
                                        },
                                        modifier = Modifier,
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Camera,
                                            contentDescription = null,
                                            modifier = Modifier.padding(start = 4.dp),
                                            tint = Color.Black
                                        )
                                        Text(
                                            modifier = Modifier.padding(4.dp, 0.dp),
                                            color = Color.Gray,
                                            text = "Take Photo From Camera"
                                        )
                                    }
                                    Divider()
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column() {
                                    OutlinedButton(
                                        onClick = {
                                            println("MROEBUCK: Cancelling...")
                                            coroutineScope.launch {
                                                bottomSheetScaffoldState.bottomSheetState.collapse()
                                            }
                                        },
                                        modifier = Modifier,
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cancel,
                                            contentDescription = null,
                                            modifier = Modifier.padding(start = 4.dp),
                                            tint = Color.Black
                                        )
                                        Text(
                                            modifier = Modifier.padding(4.dp, 0.dp),
                                            color = Color.Gray,
                                            text = "Cancel"
                                        )
                                    }
                                    Divider()
                                }
                            }
                        }
                    ) {

                    }
                }
            }
        }
    }

    @Composable
    private fun CreateLinearProgressIndicator(
        modifier: Modifier = Modifier,
        color: Color = MaterialTheme.colors.primary,
        backgroundColor: Color = color.copy(alpha = ProgressIndicatorDefaults.IndicatorBackgroundOpacity)
    ) {
        LinearProgressIndicator(
            modifier = modifier,
            color = color,
            backgroundColor = backgroundColor
        )
    }

    @Composable
    private fun ShowGreetings(names: List<String> = List(1000) { "$it" }) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                items(items = names) { name ->
                    Greeting(name = name)
                }
            }
        }
    }

    @Composable
    private fun Greeting(name: String) {
        Text(text = "Hello $name!")
    }

    private fun croppedImageFile() = createFileName(croppedFileName)
    private fun cameraImageFile() = createFileName(cameraFileName)
    private fun galleryImageFile() = createFileName(galleryFileName)
    private fun createFileName(fileName: String): File {
        val now = LocalDateTime.now()
        return File(
            this.cacheDir, String.format(
                Locale.getDefault(),
                fileName, now.toString()
            )
        )
    }

    private fun cameraImageFile(preR: Boolean = false): File {
        return if (preR || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) File(
            pictureDirectory,
            cameraFileName
        )
        else File.createTempFile(cameraImageFilePrefix, cameraFileName)
    }

    private fun onSelectImage(callback: () -> Unit) {
        checkPermissionUtility = DefaultCheckPermissionUtility()
        lifecycleScope.launch {
            checkPermissionUtility.state.collect { value ->
                val result = value.fold({ it }, { it })
                if (result is Int) {
                    callback()
                }
                println("value=[$value]")
            }
        }

        checkPermissionUtility.start(
            this@MainActivity, mutableListOf(
                AccessCoarseLocation,
                AccessFineLocation,
                AccessNetworkState,
                AccessNotificationPolicy,
                AccessWifiState,
                AccessCamera,
                AccessInternet,
                AccessReadExternalStorage,
                AccessAudioRecording,
                AccessContacts
            )
        )
    }

    private fun selectPhotoFromGallery() {
        val getIntent = Intent(Intent.ACTION_PICK)
        getIntent.type = "image/*"

        photoOutput = Uri.fromFile(galleryImageFile())

        activityLauncher.launch(getIntent, object :
            AppActivityResult.OnActivityResult<ActivityResult> {
            override fun onActivityResult(result: ActivityResult) {
                if (result.resultCode == RESULT_OK) {
                    coroutineScope.launch(Dispatchers.IO) {
                        shouldShowOnProgress.value = true
                        // There are no request codes
                        val data = result.data
                        println("MROEBUCK: data=[$data]")
                        data?.let { intent ->
                            println("MROEBUCK: intent=[$intent]")
                            val uri = intent.data
                            val extras = intent.extras
                            intent.getParcelableExtra<Bitmap>("data")?.let { bitmap ->
                                println("MROEBUCK: bitmap=[$bitmap]")
                            }
                            println("MROEBUCK: uri=[$uri]")
                            uri?.let { inputStream(it) }
                            shouldShowOnProgress.value = false
                        } ?: Log.e(this::class.java.simpleName, "data == null")
                    }
                }
            }
        })
    }

    private fun inputStream(uri: Uri) {
        try {
            photoOutput?.let { output ->
                val out = FileOutputStream(output.path)
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = false
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                println("MROEBUCK: inputStream() inputStream=[$inputStream]")
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                bitmap?.compress(Bitmap.CompressFormat.PNG, 100, out)
                println("MROEBUCK: inputStream() bitmap=[$bitmap]")

                val list = imageFiles.value + output
                imageFiles.value = list
            }
        } catch(t: Throwable) {
            println("MROEBUCK: inputStream() t=[$t]")
        }
    }

    private fun takeScreenshotFromCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        photoOutput = Uri.fromFile(cameraImageFile())
        activityLauncher.launch(intent, object :
            AppActivityResult.OnActivityResult<ActivityResult> {
            override fun onActivityResult(result: ActivityResult) {
                if (result.resultCode == RESULT_OK) {
                    // There are no request codes
                    val data = result.data
                    val bitmap = data?.data
                    println("MROEBUCK: YAY!!")
                }
            }
        })
    }
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        if (resultCode == RESULT_OK) {
//            when (requestCode) {
//                PhotoSelectionType.PhotoFromGallery.id -> {
//                    data?.let {
//                        it.getParcelableExtra<Bitmap>("data")?.let { bitmap ->
//                            try {
//                                val out = FileOutputStream(photoOutput?.path)
//                                // bmp is your Bitmap instance
//                                // PNG is a lossless format, the compression factor (100) is ignored
//                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
//                            } catch (e: IOException) {
//                                Log.e(this::class.java.simpleName, "Bitmap save failed=[$e]")
//                            }
//                        } ?:  Log.e(this::class.java.simpleName, "data.bitmap == null")
//                    } ?: Log.e(this::class.java.simpleName, "data == null")
//                }
//                PhotoSelectionType.PhotoFromCamera.id -> {
//                    val bitmap = data?.data
//                }
////                CROP_PHOTO_REQUEST_CODE -> runOnPresenter {
////                    if (resultCode == RESULT_CANCELED) {
////                        it.onError(Exception("Crop photo activity result code is canceled"))
////                    } else {
////                        data?.getStringExtra(CroppingImageScreen.EXTRA_OUTPUT)?.let { pathname ->
////                            it.onNext(Uri.fromFile(File(pathname)))
////                            it.onCompleted()
////                        }
////                    }
////                    photo = null
////                }
//            }
//        }
//    }

    @Preview(
        fontScale = 1.5f,
        name = "On Boarding Light Mode",
        uiMode = Configuration.UI_MODE_NIGHT_NO,
        showSystemUi = true,
        showBackground = true,
        widthDp = 320,
        heightDp = 320
    )
    @Composable
    fun OnBoardingLightPreview() {
        PhotoManagementExerciseTheme {
            ShowOnBoardingScreen(onContinueClicked = {})
        }
    }

    @Preview(
        fontScale = 1.5f,
        name = "On Boarding Dark Mode",
        uiMode = Configuration.UI_MODE_NIGHT_YES,
        showSystemUi = true,
        showBackground = true,
        widthDp = 320,
        heightDp = 320
    )
    @Composable
    fun OnBoardingDarkModePreview() {
        PhotoManagementExerciseTheme {
            ShowOnBoardingScreen(onContinueClicked = {})
        }
    }

    @Preview(
        fontScale = 1.5f,
        name = "Light Mode",
        uiMode = Configuration.UI_MODE_NIGHT_NO,
        showSystemUi = true,
        showBackground = true,
        widthDp = 320,
        heightDp = 320
    )
    @Composable
    fun ShowGreetingsInLightModePreview() {
        PhotoManagementExerciseTheme {
            Greeting("Android")
        }
    }

    @Preview(
        fontScale = 1.5f,
        name = "Dark Mode",
        uiMode = Configuration.UI_MODE_NIGHT_YES,
        showSystemUi = true,
        showBackground = true,
        widthDp = 320,
        heightDp = 320
    )
    @Composable
    fun ShowGreetingsInDarkModePreview() {
        PhotoManagementExerciseTheme {
            Greeting("Android")
        }
    }
}