package github.hotstu.camerax.qrcodec

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.Executor

const val FLAGS_FULLSCREEN =
    View.SYSTEM_UI_FLAG_LOW_PROFILE or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

const val IMMERSIVE_FLAG_TIMEOUT = 500L

class MainActivity : AppCompatActivity() {

    var analyzerHandler: Handler? = null
    var analysis: ImageAnalysis? = null
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rxPermissions = RxPermissions(this)
        rxPermissions.request(android.Manifest.permission.CAMERA)
            .subscribe {
                previewView.post {

                    cameraProviderFuture = ProcessCameraProvider.getInstance(this)

                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(LensFacing.BACK).build()

                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .setTargetRotation(previewView.display.rotation)
                        .build()
                    preview.previewSurfaceProvider = previewView.previewSurfaceProvider

                    analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.BackpressureStrategy.KEEP_ONLY_LATEST)
                        .build()

                    val analyzerThread = HandlerThread("BarcodeAnalyzer").apply { start() }
                    analyzerHandler = Handler(analyzerThread.looper)

                    val han = analyzerHandler!!
                    analysis!!.setAnalyzer(
                        Executor { han.post(it) },
                        ZxingQrCodeAnalyzer {
                            Log.d("QR", "Result: $it")
                            Toast.makeText(this@MainActivity, it.text, Toast.LENGTH_SHORT).show()
                        })

                    cameraProviderFuture.addListener(Runnable {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProvider.bindToLifecycle(
                            this@MainActivity,
                            cameraSelector,
                            preview,
                            analysis
                        )
                    }, ContextCompat.getMainExecutor(this))
//                    CameraX.bindToLifecycle(
//                        this@MainActivity,
//                        preview,
//                        analysis
//                    )

                }
            }
    }

    override fun onResume() {
        super.onResume()
        // Before setting full screen flags, we must wait a bit to let UI settle; otherwise, we may
        // be trying to set app to immersive mode before it's ready and the flags do not stick
        previewView.postDelayed({
            previewView.systemUiVisibility = FLAGS_FULLSCREEN
        }, IMMERSIVE_FLAG_TIMEOUT)
    }

    override fun onDestroy() {
        analyzerHandler?.removeCallbacksAndMessages(null)
        analyzerHandler?.looper?.quitSafely()
        analysis?.clearAnalyzer()
        super.onDestroy()
    }
}
