package ru.cantata.chat

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.chat2desk.chat2desk_sdk.AttachedFile
import com.chat2desk.chat2desk_sdk.Chat2Desk
import com.chat2desk.chat2desk_sdk.IChat2Desk
import com.chat2desk.chat2desk_sdk.Settings
import com.chat2desk.chat2desk_sdk.create
import com.chat2desk.chat2desk_sdk.datasource.services.ConnectionState
import com.chat2desk.chat2desk_sdk.domain.entities.Attachment
import com.chat2desk.chat2desk_sdk.domain.entities.Message
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ChatModule(reactContext: ReactApplicationContext) :
        ReactContextBaseJavaModule(reactContext) {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val rContext: ReactApplicationContext = reactContext
    private var instanceChat2Desk: IChat2Desk? = null;
    private var isConnected = ConnectionState.CLOSED

    @Volatile
    private var isConnectedJob: Job? = null

    @Volatile
    private var isConnectedMessages: Job? = null

    companion object {
        const val NAME = "Chat2Desk"
    }

    override fun getName(): String {
        return NAME
    }

    private fun convertMessageToWritableMap(message: Message): WritableMap {
        val map: WritableMap = WritableNativeMap()
        map.putString("id", message.id)
        map.putDouble("realId", message.realId.toDouble())
        map.putString("read", message.read.name)
        map.putString("status", message.status.name)
        map.putString("text", message.text)
        map.putString("type", message.type.name)
        map.putString("date", message.date.toString()) // assuming the appropriate date format or object is used

        val attachmentArray: WritableArray = WritableNativeArray()
        message.attachments?.forEach { attachment ->
            val attachmentMap: WritableMap = convertAttachmentToWritableMap(attachment)
            attachmentArray.pushMap(attachmentMap)
        }
        map.putArray("attachments", attachmentArray)

        return map
    }

    // Replace this implementation with your Attachment class properties
    private fun convertAttachmentToWritableMap(attachment: Attachment): WritableMap {
        val map: WritableMap = WritableNativeMap()
        map.putDouble("id", attachment.id.toDouble())
        map.putInt("fileSize", attachment.fileSize)
        map.putString("contentType", attachment.contentType)
        map.putString("link", attachment.link)
        map.putString("originalFileName", attachment.originalFileName)
        map.putString("status", attachment.status.name)

        return map
    }

    private fun convertMessagesToWritableArray(messages: List<Message>): WritableArray {
        val array: WritableArray = WritableNativeArray()

        for (message in messages) {
            val map = convertMessageToWritableMap(message)
            array.pushMap(map)
        }

        return array
    }

    private fun sendEvent(eventName: String, params: WritableMap?) {
        rContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
    }

    private var listenerCount = 0

    @ReactMethod
    fun addListener(eventName: String) {
        if (listenerCount == 0) {
            // Set up any upstream listeners or background tasks as necessary
        }

        listenerCount += 1
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        listenerCount -= count
        if (listenerCount == 0) {
        }
    }


    private fun getMessages() {
        isConnectedMessages?.cancel()
        isConnectedMessages = coroutineScope.launch {
            instanceChat2Desk?.messages?.collect { messages ->
                val params = Arguments.createMap().apply {
                    putArray("messages", convertMessagesToWritableArray(messages))
                }
                sendEvent("onMessage", params)
            }
        }
    }




    @ReactMethod
    fun initChat(params: ReadableMap) {
        val token = params.getString("token")
        val baseHost = params.getString("baseHost")
        val wsHost = params.getString("wsHost")
        val storageHost = params.getString("storageHost")


        if (baseHost != null && token != null && wsHost != null && storageHost != null) {
            val settings = Settings(
                    authToken = token,
                    baseHost = baseHost,
                    wsHost = wsHost,
                    storageHost = storageHost
            )
            settings.withLog = true

            instanceChat2Desk = Chat2Desk.create(settings, rContext)
        }
    }


    fun checkIsConnected() {
        try {
            isConnectedJob?.cancel()
            isConnectedJob = coroutineScope.launch {
                try {
                    instanceChat2Desk?.connectionStatus?.collect {
                        val params = Arguments.createMap().apply {
                            putString("connection", it.toString())
                        }
                        sendEvent("connection_status", params)
                    }
                } catch (e: Throwable) {
                    println(e)
                }

            }
        } catch (e: Throwable) {
            println(e)
        }
    }


    @ReactMethod
    fun openChat(promise: Promise) {
        try {
            runBlocking {
                val deferred: Deferred<String?> = async {
                    instanceChat2Desk?.start()
                }
                val res = deferred.await()
                if (isConnected != ConnectionState.CONNECTED) {
                    checkIsConnected()
                    getMessages()
                }
                promise.resolve(res)
            }
        } catch (e: Throwable) {
            promise.reject("Error", e)
        }
    }

    @ReactMethod
    fun sendUserInfo(userInfoParams: ReadableMap, promise: Promise) {
        coroutineScope.launch {
            try {
                val name = userInfoParams.getString("name")
                val phone = userInfoParams.getString("phone")

                if (name != null && phone != null) {
                    instanceChat2Desk?.sendClientParams(name, phone)
                    promise.resolve("success send params")
                }
            } catch (e: Throwable) {
                promise.reject("Error", e)
            }
        }
    }

    @ReactMethod
    fun sendMessage(text: String, promise: Promise) {
        coroutineScope.launch {
            try {
                instanceChat2Desk?.sendMessage(text)
                promise.resolve("Success")
            } catch (e: Throwable) {
                promise.reject("Error", e)
            }

        }
    }

    @ReactMethod
    fun sendFile(file: ReadableMap, promise: Promise) {
        coroutineScope.launch {
            try {
                val uri = file.getString("uri")?.toUri()
                val originalName = file.getString("originalName")
                val mimeType = file.getString("mimeType")
                val fileSize = file.getInt("fileSize")
                if (uri != null && originalName != null && mimeType != null) {
                    val attachedFile = AttachedFile.fromUri(
                        rContext,
                        uri,
                        originalName,
                        mimeType,
                        fileSize
                    )
                    instanceChat2Desk?.sendMessage("", attachedFile)
                }

            } catch (e: Throwable) {

            }
        }
    }

    @ReactMethod
    fun fetchNewMessages(promise: Promise) {
        coroutineScope.launch {
            try {
                instanceChat2Desk?.fetchNewMessages()
                promise.resolve("success")
            } catch (e: Throwable) {
                promise.reject("Error", e)
            }
        }
    }




    @ReactMethod
    fun closeChat(promise: Promise) {
        coroutineScope.launch {
            try {
                val response = instanceChat2Desk?.stop()
                promise.resolve(response)
            } catch (e: Throwable) {
                promise.reject("ERROR", e)
            }
        }
    }

    @ReactMethod
    fun destroyChat(promise: Promise) {
        coroutineScope.launch {
            try {
                val response = instanceChat2Desk?.stop()
                promise.resolve(response)
            } catch (e: Throwable) {
                promise.reject("ERROR", e)
            }
        }
    }


}
