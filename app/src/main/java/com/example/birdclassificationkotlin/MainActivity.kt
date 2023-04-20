package com.example.birdclassificationkotlin//Defines package name

//Importing required classes
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.birdclassificationkotlin.databinding.ActivityMainBinding
import com.example.birdclassificationkotlin.ml.BirdsModel
import org.tensorflow.lite.support.image.TensorImage
import java.io.IOException

class MainActivity : AppCompatActivity() {//Defines the new class named MainActivity that extends the AppCompatActivity class.
    //Declaring variables
    private lateinit var binding: ActivityMainBinding
    private lateinit var imageView: ImageView
    private lateinit var button: Button
    private lateinit var tvOutput: TextView
    private val GALLERY_REQUEST_CODE = 123


    override fun onCreate(savedInstanceState: Bundle?) {//Override the on create method.
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)//Inflating the layout using the binding object
        val view = binding.root
        setContentView(view)

        imageView = binding.imageView//initializing the views
        button = binding.btnCaptureImage
        tvOutput = binding.tvOutput
        val buttonLoad = binding.btnLoadImage

        button.setOnClickListener {//Setting a listener for capturing the image
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)//Checking if permission is granted
                == PackageManager.PERMISSION_GRANTED
            ) {
                takePicturePreview.launch(null)//Launch result if permission is granted
            } else {
                requestPermission.launch(android.Manifest.permission.CAMERA)//Request permission
            }
        }
        buttonLoad.setOnClickListener {//Function triggered when buttonload is clicked
            if (ContextCompat.checkSelfPermission(//Checking if the READ_EXTERNAL_STORAGE permission hsas been granted
                    this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                )
                == PackageManager.PERMISSION_GRANTED
            ) {
                val intent =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)//If permission granted pick an image from the gallery.
                intent.type = "image/*"
                val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")//MIME types of the images that are shown in the gallery
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION//Granting read permission to the intent
                onresult.launch(intent)//Launching intent using onresult activity
            } else {
                requestPermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)//Request permission using requestPermissionLauncher.
            }
        }

        //To redirect the user to Google
        tvOutput.setOnClickListener {//Function triggered when the tvOutput textview is clicked
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${tvOutput.text}"))
            startActivity(intent)
        }

        //To download the image when long press on image view
        imageView.setOnLongClickListener {//Function triggered when long press on image view
            requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)//Image downloaded to external source
            return@setOnLongClickListener true
        }
    }

    //request camera permission
    private val requestPermission =//Variable holding the request permission activity result
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->//Registering an activity result contract that requests permission
            if (granted) {//If granted
                takePicturePreview.launch(null)// launch preview
            } else {
                Toast.makeText(this, "Permission Denied !! Try again", Toast.LENGTH_SHORT).show()//else, show message permission denied
            }
        }

    //Launch camera and take a picture
    private val takePicturePreview =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->//Registering an image result contract that takes the image
            if (bitmap != null) {//Picture is not null, there is a picture
                imageView.setImageBitmap(bitmap)
                outputGenerator(bitmap)//Set bitmap to generate an output
            }
        }

    //to get image from gallery
    private val onresult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.i("TAG", "This is the result: ${result.data} ${result.resultCode}")//Logging result data and code
            onResultReceived(GALLERY_REQUEST_CODE, result)//Call the OnResultRecieved method with the gallery request code and result
        }

    private fun onResultReceived(requestCode: Int, result: ActivityResult) {//Method created to handle the result that is received
        when (requestCode) {
            GALLERY_REQUEST_CODE -> {//If the request code is for gallery
                if (result?.resultCode == Activity.RESULT_OK) {//If the request code is ok, get the image data and set the bitmap in the image view and generate an output.
                    result.data?.data?.let { uri ->
                        Log.i("TAG", "onResultReceived: $uri")
                        val bitmap =
                            BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                        imageView.setImageBitmap(bitmap)
                        outputGenerator(bitmap)
                    }
                } else {//Else if code not ok show below message.
                    Log.e("TAG", "onActivityResult: error in selecting image")
                }
                }
            }
        }

    private fun outputGenerator(bitmap: Bitmap){//Function takes bitmap as input
        //declaring tensorflow lite model variable

        val birdsModel = BirdsModel.newInstance(this)//Run through tensorflow lite model

        //Converting bitmap into tensorflow image.
        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val tfimage = TensorImage.fromBitmap(newBitmap)

        //Process the image using the trained model and sort it in descending order
        val outputs = birdsModel.process(tfimage)
            .probabilityAsCategoryList.apply {
                sortByDescending { it.score }
            }

        //getting result having high probability
        val highProbabilityOutput = outputs[0]

        //Setting Output text
        tvOutput.text = highProbabilityOutput.label//Most probable result is displayed
        Log.i("TAG", "outputGenerator: $highProbabilityOutput")

    }

    //To download image to device
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){//Request permission to download image
        isGranted: Boolean ->
        if (isGranted){
            AlertDialog.Builder(this).setTitle("Download Image?")//Permission granted asks user if they want to download the image
                .setMessage("Do you want to download this image to your device?")
                .setPositiveButton("Yes"){_, _ ->
                    val drawable:BitmapDrawable = imageView.drawable as BitmapDrawable//If user wants to downlod the image the image is retrieved as a bitmap
                    val bitmap = drawable.bitmap
                    downloadImage(bitmap)//Passed to download image function.
                }
                .setNegativeButton("No") {dialog, _ ->
                    dialog.dismiss()//Dismisses if user does not want to download image
                }
                .show()
        }else{
            Toast.makeText(this, "Please allow permission to download image", Toast.LENGTH_LONG).show()//Displays message if permission is not granted
        }
    }

    //fun that takes a bitmap and stores it on the users device by creating a new file to store it in
    private fun downloadImage(mBitmap: Bitmap):Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"Birds_Images"+ System.currentTimeMillis()/1000)
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
        }
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        if (uri != null){
            contentResolver.insert(uri, contentValues)?.also {
                contentResolver.openOutputStream(it).use { outputStream ->
                    if (!mBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                        throw IOException("Couldn't save the bitmap")//Couldn't saved message is displayed iuf the image could not be saved
                    }
                    else{
                        Toast.makeText(applicationContext, "Image Saved", Toast.LENGTH_LONG).show()//Message displayed showing that the image was saved

                    }
                }
                return it
            }
        }
        return null
    }
}


