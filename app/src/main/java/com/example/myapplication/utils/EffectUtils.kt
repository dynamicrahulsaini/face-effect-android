package com.example.myapplication.utils

import android.content.Context
import android.util.Log
import com.example.myapplication.R
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.atan
import kotlin.math.abs

// TODO: transparency issue in the effect
object EffectUtils {
    private val _logTag: String = EffectUtils.javaClass.name
    var neutral_angle = Double.MIN_VALUE

    fun removeWhitespace(image: Mat, blend: Mat, x: Int, y: Int, threshold: Int = 255) {
        Log.d(_logTag, "${blend.rows()}, ${blend.cols()}, ${blend[0, 0].size} -^")
        for (i in 0..blend.rows()) {
            for (j in 0..blend.cols()) {
                for (k in 0..3) {
                    Log.d(_logTag, "null error: ${blend[i, j][k]}, $i, $j, $k")
                    if (blend[i, j][k] > threshold) {
                        blend[i, j][k] = image[i + x, j + y][k]
                    }
                }
            }
        }
    }

    private fun getAngle(cords: ArrayList<Point>): Double {
        val height = abs((cords[1].y - cords[2].y))
        val base  = abs((cords[1].x - cords[2].x))

        val angle = atan((height/base)) * 180/Math.PI
        if (neutral_angle == Double.MIN_VALUE) {
            neutral_angle = angle
            Log.d(_logTag, "Neutral angle: $neutral_angle")
        }
        return angle
    }

    fun getRectangleCords(cords: ArrayList<Point>, angle: Double): ArrayList<Point> {
        if (angle - neutral_angle == 0.0)
            return arrayListOf(cords[0], cords[3])
        return if (angle - neutral_angle > 0)
            arrayListOf(Point(cords[0].x, cords[2].y), Point(cords[3].x, cords[1].y))
        else
            arrayListOf(Point(cords[1].x, cords[0].y), Point(cords[2].x, cords[3].y))
    }

    private fun getRotatedImage(context: Context, angle: Double): Mat {
        val image = Utils.loadResource(context, R.drawable.spec2, -1)
        val imHeight: Int = image.rows()
        val imWidth: Int = image.cols()

//        Log.d(_logTag, "Channels in effect: ${image[0, 0].size}")
//        Log.d(_logTag, "effect values - ${image[0, 0][0]}, ${image[0, 0][1]}, ${image[0, 0][2]}, ${image[0, 0][3]}")
        val centreX: Int = imWidth/2
        val centreY: Int = imHeight/2

        val rotationMat: Mat = Imgproc.getRotationMatrix2D(
            Point(centreX.toDouble(), centreY.toDouble()),
            angle - neutral_angle,
            1.0
        )
        val cos = Math.abs(rotationMat[0, 0][0])
        val sin = Math.abs((rotationMat[1, 0][0]))
        Log.d(_logTag, "cos: $cos, sin: $sin")

        val newWidth = ((imHeight * sin) + (imWidth * cos)).toInt()
        val newHeight = ((imHeight * cos) + (imWidth * sin)).toInt()

//        Log.d(_logTag, "${ rotationMat[0, 2][0] } : ${ rotationMat[1, 2][0] }")

        rotationMat.put(0, 2, (rotationMat[0, 2][0] + (newWidth/2 - centreX)))
        rotationMat.put(1, 2, (rotationMat[1, 2][0] + (newHeight/2 - centreY)))

//        Log.d(_logTag, "${ rotationMat[0, 2][0] } : ${ rotationMat[1, 2][0] }")

        val dstMat: Mat = Mat.zeros(newHeight, newWidth, CvType.CV_16SC4)
        dstMat.setTo(Scalar(255.0, 255.0, 255.0, 0.0))
//        Log.d(_logTag, "effect values - ${dstMat[0, 0][0]}, ${dstMat[0, 0][1]}, ${dstMat[0, 0][2]}, ${dstMat[0, 0][3]}, ${ image[0, 0].size }, ${ rotationMat[0, 0].size }")
        Imgproc.warpAffine(
            image,
            dstMat,
            rotationMat,
            Size(newWidth.toDouble(), newHeight.toDouble()),
            Imgproc.INTER_LINEAR
        )
        return dstMat
    }

    fun addEffect(context: Context, image: Mat, landmarkCords: ArrayList<Point>): Mat {
        val angle = getAngle(landmarkCords)
        val rectangleCords = getRectangleCords(landmarkCords, angle)
//        Log.d(_logTag, "Angle: $angle," +
//                " RectCords: " +
//                "x: ${ rectangleCords[0].x }, " +
//                "y: ${ rectangleCords[0].y }, " +
//                "x: ${ rectangleCords[1].x }, " +
//                "y: ${ rectangleCords[1].y }"
//        )

        val rotatedEffect = getRotatedImage(context, angle)
        val roi = image.submat(
            rectangleCords[0].y.toInt(),
            rectangleCords[1].y.toInt(),
            rectangleCords[0].x.toInt(),
            rectangleCords[1].x.toInt()
        )

        val w = roi.cols()
        val h = roi.rows()
        Imgproc.resize(rotatedEffect, rotatedEffect, Size(w.toDouble(), h.toDouble()))
        val dst = Mat.zeros(roi.rows(), roi.cols(), roi.type())
        Core.addWeighted(roi, 0.0, rotatedEffect, 1.0, 0.0, dst)
//        removeWhitespace(image, dst, rectangleCords[0].x.toInt(), rectangleCords[0].y.toInt())
        dst.copyTo(roi)
        return image
    }
}
