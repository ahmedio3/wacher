package com.example.data.remote.moviebox.crypto

import java.util.UUID

object MovieBoxDeviceInfo {
    val DEVICE_ID = UUID.randomUUID().toString().replace("-", "")
    const val APP_VERSION = "4.0.04.0527.01"
    const val USER_AGENT = "com.community.oneroom/50020050 (Linux; U; Android 13; en_US; 23078RKD5C; Build/TQ2A.230405.003; Cronet/135.0.7012.3)"
    val CLIENT_INFO = "{\"package_name\":\"com.community.oneroom\",\"version_name\":\"$APP_VERSION\",\"version_code\":50020050,\"os\":\"android\",\"os_version\":\"13\",\"install_ch\":\"ps\",\"device_id\":\"$DEVICE_ID\",\"install_store\":\"ps\",\"gaid\":\"550e8400-e29b-41d4-a716-446655440000\",\"brand\":\"Redmi\",\"model\":\"23078RKD5C\",\"system_language\":\"en\",\"net\":\"NETWORK_WIFI\",\"region\":\"US\",\"timezone\":\"America/New_York\",\"sp_code\":\"40401\",\"X-Play-Mode\":\"2\"}"
}
