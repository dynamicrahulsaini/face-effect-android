package com.example.myapplication.utils

import android.content.Context
import android.util.Log
import com.example.myapplication.R
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.atan
import kotlin.math.abs

// TODO: address transparency issue in the effect
object EffectUtils {
    private val _logTag: String = EffectUtils.javaClass.name
    var neutral_angle = Double.MIN_VALUE

//    private fun removeWhitespace(image: Mat, blend: Mat, x: Int, y: Int, threshold: Int = 255) {
//        Log.d(_logTag, "${blend.rows()}, ${blend.cols()}, ${blend[0, 0].size} -^")
//        Log.d(_logTag, "${ image.cols() }, ${ image.rows() }, $x, $y")
////        for (i in 0 until blend.rows()) {
////            for (j in 0 until blend.cols()) {
////                for (k in 0..3) {
////                    Log.d(_logTag, "null error: ${blend[i, j][k]}, $i, $j, $k")
////                    if (blend[i, j][k] >= threshold)
////                        blend[i, j][k] = image[i + y, j + x][k]
////                }
////            }
////        }
////        for (i in 0 until dst.rows()){
////            for (j in 0 until dst.cols()) {
////                if (dst[i, j][0] == 1.0) {
////
////                }
////            }
////        }
//
////        val dst = Mat(blend.rows(), blend.cols(), blend.type()).setTo(Scalar(0.0, 0.0, 0.0, 0.0))
////        Core.compare(blend, Scalar(255.0, 255.0, 255.0, 0.0), dst, Core.CMP_EQ)
////        logMat(dst)
//    }

    fun logMat(mat: Mat) {
        var s = "dstmat: ${mat[0, 0].size}\n"
        for (i in 0 until mat.cols()) {
            for (j in 0 until mat.rows()) {
                s += "${ mat[i, j][0] } }\t"
//                s += "${ mat[i, j][0] }, ${ mat[i, j][1] }, ${ mat[i, j][2] }, ${ mat[i, j][3] }\t"
            }
            s+="\n"
        }
        Log.d("log-$_logTag", s)
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

    private fun getRectangleCords(cords: ArrayList<Point>, angle: Double): ArrayList<Point> {
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

        val centreX: Int = imWidth/2
        val centreY: Int = imHeight/2

        val rotationMat: Mat = Imgproc.getRotationMatrix2D(
            Point(centreX.toDouble(), centreY.toDouble()),
            angle - neutral_angle,
            1.0
        )
        val cos = Math.abs(rotationMat[0, 0][0])
        val sin = Math.abs((rotationMat[1, 0][0]))

        val newWidth = ((imHeight * sin) + (imWidth * cos)).toInt()
        val newHeight = ((imHeight * cos) + (imWidth * sin)).toInt()

        rotationMat.put(0, 2, (rotationMat[0, 2][0] + (newWidth/2 - centreX)))
        rotationMat.put(1, 2, (rotationMat[1, 2][0] + (newHeight/2 - centreY)))

        val dstMat: Mat = Mat(newHeight, newWidth, CvType.CV_16SC4)
//        Log.d(_logTag, "rotatedMat values - ${dstMat[0, 0][0]}, ${dstMat[0, 0][1]}, ${dstMat[0, 0][2]}, ${dstMat[0, 0][3]}")
        Imgproc.warpAffine(
            image,
            dstMat,
            rotationMat,
            Size(newWidth.toDouble(), newHeight.toDouble()),
            Imgproc.INTER_LINEAR,
            Core.BORDER_CONSTANT,
            Scalar(255.0, 255.0, 255.0, 0.0)
        )
//        Log.d(_logTag, "rotatedMat values - ${dstMat[0, 0][0]}, ${dstMat[0, 0][1]}, ${dstMat[0, 0][2]}, ${dstMat[0, 0][3]}")
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
        Core.bitwise_and(roi, rotatedEffect, rotatedEffect)
        rotatedEffect.copyTo(roi)
        Log.d(_logTag, "effect values - ${rotatedEffect[0, 0][0]}, ${rotatedEffect[0, 0][1]}, ${rotatedEffect[0, 0][2]}, ${rotatedEffect[0, 0][3]}")
        return image
    }

}
