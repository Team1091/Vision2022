package team1091

import java.awt.Color
import java.awt.image.BufferedImage
import java.text.DecimalFormat
import kotlin.math.pow

class TargetingOutput(
    val imageWidth: Int,
    val imageHeight: Int,
    val xCenterColor: Int,
    val yCenterColor: Int,
    val xCenterAvg: Int,
    val yCenterAvg: Int,
    var targetDistance: Double,
    val processedImage: BufferedImage,
    val seen: Boolean,
    val rightExtension: Int,
    val leftExtension: Int
) {


    /**
     * Get the center as a fraction of total image width
     *
     * @return float from -0.5 to 0.5
     */
    val targetXCenter: Double
        get() = xCenterAvg.toDouble() / imageWidth.toDouble() - 0.5

    val targetYCenter: Double
        get() = yCenterAvg.toDouble() / imageHeight.toDouble() - 0.5


    val targetDistanceInches: Double
        get() = targetDistance / 25.4

    /**
     * This draws debug info onto the image before it's displayed.
     *
     * @param outputImage
     * @return
     */
    fun drawOntoImage(drawColor: Color, outputImage: BufferedImage): BufferedImage {

        val g = outputImage.createGraphics()

        g.color = drawColor
        g.drawLine(xCenterColor, yCenterColor - 10, xCenterColor, yCenterColor + 10)

        g.color = Color.ORANGE
        g.drawLine(xCenterAvg, yCenterColor - 5, xCenterAvg, yCenterColor + 5)

        if (seen) {
            g.color = Color.YELLOW
            g.drawLine(xCenterColor - leftExtension, yCenterColor, xCenterColor + rightExtension, yCenterColor)

            // width labels, px and % screen width
            g.color = Color.BLUE
            g.drawString(df.format(targetDistanceInches) + " Inches", 10, 10)
            val distance = 547.0 * (leftExtension + rightExtension).toDouble().pow(-1.08)
            g.drawString(distance.toString(), 10, 30)
        }

        return outputImage
    }

    companion object {
        private val df = DecimalFormat("#.0")
    }
}