package com.smart.smartnoticeboard

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.smart.smartnoticeboard.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

class MainActivity : FragmentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var storageReference: StorageReference

    private val pdfViewModel by viewModels<PdfViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storageReference = FirebaseStorage.getInstance().reference
        FirebaseStorage.getInstance().maxDownloadRetryTimeMillis = 2500

        sharedPrefs = getPreferences(Context.MODE_PRIVATE)

        pdfViewModel.pdfStreamLiveData.observe(this) { listOfInputStream ->
            if(listOfInputStream.size==1){
               val inputStream = listOfInputStream[0]
                if(inputStream!=null){
                    binding.pdfView1.fromStream(inputStream)
                        .pages(0)
                        .enableSwipe(true) // allows to block changing pages using swipe
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .defaultPage(0)
                        .enableAnnotationRendering(false) // render annotations (such as comments, colors or forms)
                        .scrollHandle(null)
                        .enableAntialiasing(true) // improve rendering a little bit on low-res screens
                        .spacing(0)
                        .autoSpacing(false) // add dynamic spacing to fit each page on its own on the screen
                        .fitEachPage(true) // fit each page to the view, else smaller pages are scaled relative to largest page.
                        .pageFitPolicy(FitPolicy.HEIGHT)
                        .pageSnap(false) // snap pages to screen boundaries
                        .pageFling(false) // make a fling change only a single page like ViewPager
                        .nightMode(false) // toggle night mode
                        .load()
                    binding.progressBar.isVisible = false
                    binding.pdfView1.isVisible = true
                    binding.pdfView2.visibility = View.GONE
                }
            }else{
                val inputStream1 = listOfInputStream[0]
                val inputStream2 = listOfInputStream[1]
                if (inputStream1 != null && inputStream2 !=null) {

                    binding.pdfView1.fromStream(inputStream1)
                        .pages(0)
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .defaultPage(0)
                        .enableAnnotationRendering(false)
                        .scrollHandle(null)
                        .enableAntialiasing(true)
                        .spacing(0)
                        .autoSpacing(false)
                        .fitEachPage(true)
                        .pageFitPolicy(FitPolicy.HEIGHT)
                        .pageSnap(false)
                        .pageFling(false)
                        .nightMode(false)
                        .load()

                    binding.pdfView2.fromStream(inputStream2)
                        .pages(1)
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .defaultPage(1)
                        .enableAnnotationRendering(false)
                        .scrollHandle(null)
                        .enableAntialiasing(true)
                        .spacing(0)
                        .autoSpacing(false)
                        .fitEachPage(true)
                        .pageFitPolicy(FitPolicy.HEIGHT)
                        .pageSnap(false)
                        .pageFling(false)
                        .nightMode(false)
                        .load()
                    binding.progressBar.isVisible = false
                    binding.pdfView2.isVisible = true
                    binding.pdfView1.isVisible = true
                    binding.pdfView2.visibility = View.VISIBLE
                } else {
                    binding.pdfView2.isVisible = true
                    loadPreviousPdf()
                    binding.progressBar.isVisible = false
                }
            }
        }

        fetchPdfUrl()

        binding.updateButton.setOnClickListener {
            fetchPdfUrl()
        }
    }

    private fun fetchPdfUrl() {
        Log.d("PDF_FETCH", "Fetch called for pdf")
        binding.progressBar.isVisible = true
        binding.noticeImage.isVisible = false
        binding.pdfView1.isVisible = false
        binding.pdfView2.isVisible = false

        storageReference.child("mypdfs").listAll()
            .addOnSuccessListener { result ->
                Log.d("PDF_FETCH", "Fetch success")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val allFilesDetails = result.items.map { s ->
                            async {
                                PdfResponse(
                                    s.downloadUrl.await().toString(),
                                    s.metadata.await().updatedTimeMillis,
                                )
                            }
                        }.awaitAll()
                        val sortedList = allFilesDetails.sortedBy {
                            it.uploadTime
                        }
                        pdfViewModel.retrievePDFStream(sortedList.last().pdf_url)
                        val byteArray = inputStreamToByteArray(pdfViewModel.pdfStreamLiveData.value?.get(0))
                        savePdf(byteArray)

                        withContext(Dispatchers.Main) {
                            binding.progressBar.isVisible = false
                            binding.pdfView1.isVisible = true
                            binding.pdfView2.isVisible = true
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.isVisible = false
                            binding.pdfView1.isVisible = true
                            binding.pdfView2.isVisible = true
                            loadPreviousPdf()
                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                // Handle failure here
                Log.e("PDF_FETCH", "Failed to fetch PDF: ${exception.message}", exception)

                binding.progressBar.isVisible = false
                binding.pdfView1.isVisible = true
                binding.pdfView2.isVisible = true
                loadPreviousPdf()

            }
    }

    private fun savePdf(byteArray: ByteArray?) {
        if (byteArray != null) {
            sharedPrefs.edit()
                .putString("lastPDF", Base64.encodeToString(byteArray, Base64.DEFAULT)).apply()
        }
    }

    private fun loadPreviousPdf() {
        val savedPdfString = sharedPrefs.getString("lastPDF", null)
        if (savedPdfString != null) {
            binding.pdfView1.fromAsset("Sample.pdf")
                .pages(0)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .defaultPage(0)
                .enableAnnotationRendering(false)
                .scrollHandle(null)
                .enableAntialiasing(true)
                .spacing(0)
                .autoSpacing(false)
                .fitEachPage(true)
                .pageFitPolicy(FitPolicy.HEIGHT)
                .pageSnap(false)
                .pageFling(false)
                .nightMode(false)
                .load()
            binding.pdfView2.visibility = View.GONE
        }
    }

    private fun inputStreamToByteArray(inputStream: InputStream?): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        inputStream?.use { input ->
            val buffer = ByteArray(1024)
            var length: Int
            while (input.read(buffer).also { length = it } != -1) {
                byteArrayOutputStream.write(buffer, 0, length)
            }
        }
        return byteArrayOutputStream.toByteArray()
    }

}