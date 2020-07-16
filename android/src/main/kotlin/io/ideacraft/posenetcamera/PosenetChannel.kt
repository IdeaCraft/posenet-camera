package io.ideacraft.posenetcamera

import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink


class PosenetChannel(messenger: BinaryMessenger?) {
    private var eventSink: EventSink? = null

    fun send(poseData: HashMap<String, Any>?) {
        if (eventSink == null) {
            return
        }
        eventSink!!.success(poseData)
    }

    init {
        EventChannel(messenger, "posenetCameraViewPlugin/posenetOutputStream")
                .setStreamHandler(
                        object : EventChannel.StreamHandler {
                            override fun onListen(arguments: Any?, sink: EventSink?) {
                                eventSink = sink
                            }

                            override fun onCancel(arguments: Any?) {
                                eventSink = null
                            }
                        })
    }
}