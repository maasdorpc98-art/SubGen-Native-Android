package android.os
object Build
object Environment { const val DIRECTORY_DOWNLOADS="Download" }

object SystemClock { fun elapsedRealtime():Long=java.lang.System.nanoTime()/1000000L }
