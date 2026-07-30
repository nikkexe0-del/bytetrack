package com.zestyy.bytetrack.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromNetworkType(value: NetworkType): String = value.name

    @TypeConverter
    fun toNetworkType(value: String): NetworkType = NetworkType.valueOf(value)
}
