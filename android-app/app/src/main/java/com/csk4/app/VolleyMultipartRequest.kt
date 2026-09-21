package com.android.volley

import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException

abstract class VolleyMultipartRequest(
    method: Int,
    url: String,
    private val listener: Response.Listener<NetworkResponse>,
    private val errorListener: Response.ErrorListener
) : Request<NetworkResponse>(method, url, errorListener) {

    private val boundary = "apiclient-" + System.currentTimeMillis()
    private val mimeType = "multipart/form-data;boundary=$boundary"

    override fun getBodyContentType(): String = mimeType

    override fun getBody(): ByteArray {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        try {
            getParams().forEach { (key, value) ->
                dos.writeBytes("--$boundary\r\n")
                dos.writeBytes("Content-Disposition: form-data; name=\"$key\"\r\n\r\n")
                dos.writeBytes("$value\r\n")
            }
            getByteData().forEach { (key, data) ->
                dos.writeBytes("--$boundary\r\n")
                dos.writeBytes("Content-Disposition: form-data; name=\"$key\"; filename=\"${data.fileName}\"\r\n")
                dos.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                dos.write(data.content)
                dos.writeBytes("\r\n")
            }
            dos.writeBytes("--$boundary--\r\n")
        } catch (e: IOException) { e.printStackTrace() }
        return bos.toByteArray()
    }

    override fun parseNetworkResponse(response: NetworkResponse): Response<NetworkResponse> {
        return try {
            Response.success(response, HttpHeaderParser.parseCacheHeaders(response))
        } catch (e: Exception) {
            Response.error(com.android.volley.ParseError(e))
        }
    }

    override fun deliverResponse(response: NetworkResponse) {
        listener.onResponse(response)
    }

    override fun deliverError(error: com.android.volley.VolleyError) {
        errorListener.onErrorResponse(error)
    }

    abstract fun getByteData(): MutableMap<String, DataPart>
    abstract fun getParams(): MutableMap<String, String>

    data class DataPart(val fileName: String, val content: ByteArray)
}