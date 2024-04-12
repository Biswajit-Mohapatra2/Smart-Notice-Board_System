package com.smart.smartnoticeboard

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.URL

class PdfViewModel: ViewModel() {

    val pdfStreamLiveData = MutableLiveData<List<InputStream?>>()

    fun retrievePDFStream(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = URL(url).openStream()
                val inputStream1 = URL(url).openStream()
                val inputStream2 = URL(url).openStream()

                val pageCount = PDDocument.load(inputStream).numberOfPages
                withContext(Dispatchers.Main) {
                    if(pageCount>1){
                        pdfStreamLiveData.value = listOf(inputStream1, inputStream2)
                    }else{
                        pdfStreamLiveData.value = listOf(inputStream1)
                    }
                }
            } catch (e: IOException) {
                // Handle the exception
                withContext(Dispatchers.Main) {
                    pdfStreamLiveData.value = emptyList()
                }
            }
        }
    }

}