package com.example.photomanagementexercise

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle.Event.ON_START
import androidx.lifecycle.Lifecycle.Event.ON_STOP
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photomanagementexercise.access.AccessPermissionType
import com.example.photomanagementexercise.access.AccessPermissionType.*
import com.example.photomanagementexercise.access.CheckAccessByVersionWrapper
import com.example.photomanagementexercise.access.DefaultCheckAccessByVersionWrapper
import com.example.photomanagementexercise.access.DefaultCheckPermissionUtility
import com.example.photomanagementexercise.ui.theme.PhotoManagementExerciseTheme
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
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
class MainActivity : DefaultAppActivity(), LifecycleObserver {

    enum class PhotoSelectionType(val id: Int) {
        PhotoFromGallery(0x0101), PhotoFromCamera(0x0102)
    }

    // region Properties

    private val cameraImageFilePrefix = "camera_image"
    private val imageWidth = 200
    private val imageHeight = 200
    private val pictureDirectory: String by lazy { this.cacheDir.absolutePath }
    private val cameraFileName = "camera.image_%s.png"
    private val galleryFileName = "gallery.image_%s.png"
    private val croppedFileName = "cropped.image_%s.png"
    private val emptyImageUri: Uri = Uri.parse("file://dev/null")
    private val photoFromGalleryActivityCallback: ActivityResultCallback<ActivityResult> =
        ActivityResultCallback<ActivityResult> { result ->
            println("ROEBUCK: selectPhotoFromGallery() result=[$result]")
            if (result.resultCode == RESULT_OK) {
                coroutineScope.launch(Dispatchers.IO) {
                    shouldShowOnProgress.value = true
                    // There are no request codes
                    val data = result.data
                    println("ROEBUCK: selectPhotoFromGallery() data=[$data]")
                    data?.let { intent ->
                        println("ROEBUCK: selectPhotoFromGallery() intent=[$intent]")
                        val uri = intent.data
                        val extras = intent.extras
                        intent.getParcelableExtra<Bitmap>("data")?.let { bitmap ->
                            println("ROEBUCK: selectPhotoFromGallery() bitmap=[$bitmap]")
                        }
                        println("ROEBUCK: selectPhotoFromGallery() uri=[$uri]")
                        uri?.let { inputStream(it) }
                        shouldShowOnProgress.value = false
                    } ?: println("ROEBUCK: selectPhotoFromGallery() data == null")
                }
            }
        }
    private val photoFromGalleryActivityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            coroutineScope.launch(Dispatchers.IO) {
                shouldShowOnProgress.value = true
                println("ROEBUCK: selectPhotoFromGallery() result=[$uri]")
                uri?.let { inputStream(it) }
                shouldShowOnProgress.value = false
            }
        }
    private val takePhotoActivityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.TakePicture(),
        ) { isSuccessful ->
            if (isSuccessful) {
                coroutineScope.launch(Dispatchers.IO) {
                    shouldShowOnProgress.value = true
                    println("ROEBUCK: takePhoto() isSuccessful=[$isSuccessful]")
                    photoOutput?.let {
                        val list = imageFiles.value + it
                        imageFiles.value = list
                    }
                    shouldShowOnProgress.value = false
                }
            }
        }
    private val recordVideoResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.CaptureVideo(),
        ) { isSuccessful ->
            if (isSuccessful) {
                coroutineScope.launch(Dispatchers.IO) {
                    shouldShowOnProgress.value = true
                    println("ROEBUCK: takePhoto() isSuccessful=[$isSuccessful]")
                    photoOutput?.let {
                        val list = videoFiles.value + it
                        videoFiles.value = list
                        playingItemIndex.value = list.size - 1
                    }
                    shouldShowOnProgress.value = false
                }
            }
        }
    private val galleryActivityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            var canContinue = true

            currentPermissions.clear()
            permissions.entries.forEach {
                println("ROEBUCK: onSelectImage() ${it.key} = ${it.value}")
                if (!it.value) {
                    val nextPermission = AccessPermissionType.Companion.valueOf(it.key)
                    currentPermissions.add(nextPermission)
                    canContinue = false
                }
            }
            if (canContinue) {
                galleryActivityResult()
            } else {
                checkPermissionUtility.start(this@MainActivity, currentPermissions)
            }
        }
    private val activityResultCallback: ActivityResultCallback<ActivityResult> =
        ActivityResultCallback<ActivityResult> { result ->
            println("ROEBUCK: result=[$result]")
            if (result.resultCode == RESULT_OK) {
                // There are no request codes
                val data = result.data
                val bitmap = data?.data
                println("ROEBUCK: YAY!!")
                checkPermissionUtility.continueRequestingPermissions()
            }
        }
    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            activityResultCallback
        )
    private val requestPermissionCallback: ActivityResultCallback<Boolean> =
        ActivityResultCallback<Boolean> { result ->
            if (result == true) {
                println("Permission Granted")
                checkPermissionUtility.continueRequestingPermissions()
            } else {
                println("Permission Denied")
            }
        }
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
            requestPermissionCallback
        )
    private val currentPermissions: MutableList<AccessPermissionType> = mutableListOf()
    private val checkAccessByVersionWrapper: CheckAccessByVersionWrapper =
        DefaultCheckAccessByVersionWrapper(activityResultLauncher, requestPermissionLauncher)
    private val checkPermissionUtility = DefaultCheckPermissionUtility(checkAccessByVersionWrapper)

    private lateinit var galleryActivityResult: () -> Unit
    private var photoOutput: Uri? = null

    private lateinit var source: String
    private lateinit var mediaPlayback: MediaPlayback
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var playingItemIndex: MutableState<Int>
    private lateinit var shouldShowOnBoarding: MutableState<Boolean>
    private lateinit var shouldShowOnProgress: MutableState<Boolean>
    private lateinit var imageFiles: MutableState<List<Uri>>
    private lateinit var videoFiles: MutableState<List<Uri>>
    private lateinit var coroutineScope: CoroutineScope

    interface MediaPlayback {
        fun playPause()
        fun forward(durationInMillis: Long)
        fun rewind(durationInMillis: Long)
    }

    // endregion

    @Composable
    override fun MyApp(savedInstanceState: Bundle?) {
        playingItemIndex = rememberSaveable { mutableStateOf(-1) }
        shouldShowOnBoarding = rememberSaveable { mutableStateOf(true) }
        shouldShowOnProgress = rememberSaveable { mutableStateOf(false) }
        coroutineScope = rememberCoroutineScope()
        imageFiles = remember { mutableStateOf(listOf()) }
        videoFiles = remember { mutableStateOf(listOf()) }
        val context = LocalContext.current

        // This is the official way to access current context from Composable functions
        // Do not recreate the player everytime this Composable commits
        exoPlayer = remember(context) {
            ExoPlayer.Builder(context).build().apply {
//                setMediaItem(MediaItem.fromUri(uri), 0)
//                prepare()
//                playWhenReady = isPlaying
            }
        }

        mediaPlayback = getMediaPlayback()


        val lifecycleOwner = this.lifecycle

        if (shouldShowOnBoarding.value) {
            ShowOnBoardingScreen(onContinueClicked = {
//                shouldShowOnBoarding.value = false
            })
            StartVideoIfNecessary()
        } else {
            ShowGreetings()
        }
    }

    @Composable
    private fun StartVideoIfNecessary() {
        val lifecycleOwner = this@MainActivity.lifecycle

        if (videoFiles.value.isNotEmpty()) {
            LaunchedEffect(playingItemIndex) {
                if (playingItemIndex.value < 0) {
                    exoPlayer.pause()
                } else {
                    val index = videoFiles.value.size - 1
                    val video = videoFiles.value.last()
                    exoPlayer.setMediaItem(MediaItem.fromUri(video), 0)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            }
            DisposableEffect(exoPlayer) {
                val lifecycleObserver = LifecycleEventObserver { _, event ->
                    if (playingItemIndex.value < 0) return@LifecycleEventObserver
                    when (event) {
                        ON_START -> exoPlayer.play()
                        ON_STOP -> exoPlayer.pause()
                        else -> {}
                    }
                }

                lifecycleOwner.addObserver(this@MainActivity)
                onDispose {
                    lifecycleOwner.removeObserver(this@MainActivity)
                    exoPlayer.release()
                }
            }
        }
    }

    @Composable
    private fun ShowOnBoardingScreen(onContinueClicked: () -> Unit) {
        val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = BottomSheetState(BottomSheetValue.Collapsed)
        )
        Surface(modifier = Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier
                    .padding(all = 16.dp)
                    .fillMaxWidth()
                    .shadow(375.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (shouldShowOnProgress.value) {
                        CreateLinearProgressIndicator(
                            modifier = Modifier,
                            color = Color.Green,
                            backgroundColor = Color.Red
                        )
                    }
                    if (imageFiles.value.isNotEmpty()) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            text = stringResource(R.string.included_images)
                        )
                        LazyRow(modifier = Modifier.padding(vertical = 4.dp)) {
                            items(items = imageFiles.value) { uri ->
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(uri)
                                        .crossfade(true)
                                        .build(),
                                    error = painterResource(R.drawable.ic_launcher_foreground),
                                    fallback = painterResource(R.drawable.ic_launcher_foreground),
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

                    if (videoFiles.value.isNotEmpty()) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            text = stringResource(R.string.included_videos)
                        )
                        LazyRow(modifier = Modifier.padding(vertical = 4.dp)) {
                            itemsIndexed(
                                items = videoFiles.value,
                                key = { _, video -> video }) { index, uri ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    VideoPlayer(
                                        uri = uri,
                                        isPlaying = index == playingItemIndex.value
                                    ) {
                                        println("ROEBUCK: ShowOnBoardingScreen() currentPosition=[${exoPlayer.currentPosition}], index=[$index]")
//                                        viewModel.onPlayVideoClick(exoPlayer.currentPosition, index)
                                    }
                                }
//                                Row {
//                                    IconButton(onClick = {
//                                        mediaPlayback.rewind(10_000)
//                                    }) {
//                                        Icon(
//                                            imageVector = Icons.Default.ArrowBack,
//                                            contentDescription = null,
//                                            modifier = Modifier.padding(start = 4.dp),
//                                            tint = Color.Black
//                                        )
//                                    }
//
//                                    IconButton(onClick = {
//                                        mediaPlayback.playPause()
//                                    }) {
//                                        Icon(
//                                            imageVector = Icons.Default.PlayArrow,
//                                            contentDescription = null,
//                                            modifier = Modifier.padding(start = 4.dp),
//                                            tint = Color.Black
//                                        )
//                                    }
//
//                                    IconButton(onClick = {
//                                        mediaPlayback.forward(10_000)
//                                    }) {
//                                        Icon(
//                                            imageVector = Icons.Default.ArrowForward,
//                                            contentDescription = null,
//                                            modifier = Modifier.padding(start = 4.dp),
//                                            tint = Color.Black
//                                        )
//                                    }
//                                }
                            }
                        }
                    } else {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            text = stringResource(R.string.no_videos_available)
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
                                            println("ROEBUCK: Choosing from Gallery...")
                                            coroutineScope.launch {
                                                bottomSheetScaffoldState.bottomSheetState.collapse()
                                                onSelectImage { selectPhotoFromGallery() }
                                            }
                                        },
                                        modifier = Modifier,
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = Color.White
                                        ),
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
                                            println("ROEBUCK: Take Photo From Camera...")
                                            coroutineScope.launch {
                                                bottomSheetScaffoldState.bottomSheetState.collapse()
                                                onSelectImage { takeScreenshotFromCamera() }
                                            }
                                        },
                                        modifier = Modifier,
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = Color.White
                                        ),
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
                                Column {
                                    OutlinedButton(
                                        onClick = {
                                            println("ROEBUCK: Record Video...")
                                            coroutineScope.launch {
                                                bottomSheetScaffoldState.bottomSheetState.collapse()
                                                onSelectImage { recordVideo() }
                                            }
                                        },
                                        modifier = Modifier,
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Videocam,
                                            contentDescription = null,
                                            modifier = Modifier.padding(start = 4.dp),
                                            tint = Color.Black
                                        )
                                        Text(
                                            modifier = Modifier.padding(4.dp, 0.dp),
                                            color = Color.Gray,
                                            text = "Record Video"
                                        )
                                    }
                                    Divider()
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column() {
                                    OutlinedButton(
                                        onClick = {
                                            println("ROEBUCK: Cancelling...")
                                            coroutineScope.launch {
                                                bottomSheetScaffoldState.bottomSheetState.collapse()
                                            }
                                        },
                                        modifier = Modifier,
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = Color.White
                                        ),
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
    fun VideosScreen() {
        val listState = rememberLazyListState()
        val isCurrentItemVisible = remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            snapshotFlow {
                listState.visibleAreaContainsItem()
            }.collect { isItemVisible ->
                isCurrentItemVisible.value = isItemVisible
            }
        }
        LaunchedEffect(isCurrentItemVisible.value) {
            if (!isCurrentItemVisible.value && playingItemIndex.value > -1) {
                println("MROEBUCK: VideoScreen() currentPosition=[${exoPlayer.currentPosition}] playingItemIndex=[${playingItemIndex.value}]")
//                viewModel.onPlayVideoClick(exoPlayer.currentPosition, playingItemIndex!!)
            }
        }
    }

    private fun LazyListState.visibleAreaContainsItem(): Boolean {
        return when {
            playingItemIndex.value < 0 -> false
            videoFiles.value.isEmpty() -> false
            else -> {
                layoutInfo.visibleItemsInfo.map { videoFiles.value[it.index] }
                    .contains(videoFiles.value[playingItemIndex.value])
            }
        }
    }

    @Composable
    fun VideoPlayer(uri: Uri, isPlaying: Boolean, onClick: () -> Unit) {
        val context = LocalContext.current

        exoPlayer.apply {
            stop()
            setMediaItem(MediaItem.fromUri(uri), 0)
            prepare()
            playWhenReady = false

        }
        // Gateway to traditional Android Views
        AndroidView({ localContext ->
            StyledPlayerView(localContext).apply {
                player = exoPlayer
//                setOnClickListener { onClick() }
            }
        })
    }

    @Composable
    private fun getMediaPlayback(): MediaPlayback {
        return remember(exoPlayer) {
            object : MediaPlayback {
                override fun playPause() {
                    exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                }

                override fun forward(durationInMillis: Long) {
                    exoPlayer.seekTo(exoPlayer.currentPosition + durationInMillis)
                }

                override fun rewind(durationInMillis: Long) {
                    exoPlayer.seekTo(exoPlayer.currentPosition - durationInMillis)
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

    // region Private Methods

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
        galleryActivityResult = callback
        galleryActivityResultLauncher.launch(
            arrayOf<String>(
                AccessCoarseLocation.permission,
                AccessFineLocation.permission,
                AccessNetworkState.permission,
                AccessNotificationPolicy.permission,
                AccessWifiState.permission,
                AccessCamera.permission,
                AccessInternet.permission,
                AccessReadExternalStorage.permission,
                AccessAudioRecording.permission,
                AccessContacts.permission
            )
        )
    }

    private fun selectPhotoFromGallery() {
        photoOutput = Uri.fromFile(galleryImageFile())
        photoFromGalleryActivityResultLauncher.launch("image/*")
    }

    private fun inputStream(uri: Uri) {
        try {
            photoOutput?.let { output ->
                val out = FileOutputStream(output.path)
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = false
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                println("ROEBUCK: inputStream() inputStream=[$inputStream]")
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                bitmap?.compress(Bitmap.CompressFormat.PNG, 100, out)
                println("ROEBUCK: inputStream() bitmap=[$bitmap]")

                val list = imageFiles.value + output
                imageFiles.value = list
            }
        } catch (t: Throwable) {
            println("ROEBUCK: inputStream() t=[$t]")
        }
    }

    private fun takeScreenshotFromCamera() {
        val file = cameraImageFile()
        photoOutput =
            FileProvider.getUriForFile(
                this,
                applicationContext.packageName + ".provider",
                file
            )

        takePhotoActivityResultLauncher.launch(photoOutput)
    }

    private fun recordVideo() {
        val file = cameraImageFile()
        photoOutput =
            FileProvider.getUriForFile(
                this,
                applicationContext.packageName + ".provider",
                file
            )

        recordVideoResultLauncher.launch(photoOutput)
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

    // endregion

    // region Preview Design

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

    // endregion
}