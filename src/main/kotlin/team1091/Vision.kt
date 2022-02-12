package team1091

import com.github.sarxos.webcam.Webcam
import com.github.sarxos.webcam.WebcamPanel
import com.github.sarxos.webcam.ds.ipcam.IpCamDeviceRegistry
import com.github.sarxos.webcam.ds.ipcam.IpCamDriver
import com.github.sarxos.webcam.ds.ipcam.IpCamMode
import edu.wpi.first.networktables.NetworkTableEntry
import edu.wpi.first.networktables.NetworkTableInstance
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.lang.Double.min
import java.lang.Thread.sleep
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.WindowConstants
import kotlin.math.abs
import kotlin.math.pow

private val testImage: String? = null // "test.png"
private const val robotAddr = "http://roborio-1091-frc.local" // "http://localhost"//
var isRemote = false
var teamColor = "blue"

var seenEntry: NetworkTableEntry? = null
var centerXEntry: NetworkTableEntry? = null
var centerYEntry: NetworkTableEntry? = null
var distanceEntry: NetworkTableEntry? = null

fun main(args: Array<String>) = runBlocking {
    var webcam: Webcam? = null
    isRemote = args.size != 1
    while (webcam?.isOpen != true) {
        webcam = connect()
    }

    println("connected")

    val panel = WebcamPanel(webcam)
    panel.painter = getPainter()

    val window = JFrame("Webcam Panel")
    window.add(panel)
    window.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
    window.pack()
    window.isVisible = true

    window.addKeyListener(ToggleColor())

    val inst = NetworkTableInstance.getDefault()
    val table = inst.getTable("datatable")
    seenEntry = table.getEntry("seen")
    centerXEntry = table.getEntry("centerX")
    centerYEntry = table.getEntry("centerY")
    distanceEntry = table.getEntry("distance")
}

private fun getPainter() = object : WebcamPanel.Painter {
    override fun paintPanel(panel: WebcamPanel, g2: Graphics2D) {}

    override fun paintImage(panel: WebcamPanel, image: BufferedImage, g2: Graphics2D) {
        val imageToUse = if (testImage != null) ImageIO.read(File(testImage)) else image

        // Lets You Pick what team you are on "red" or "blue"
        val targetingOutput = getTargetingOutput(imageToUse)

        // pull out results we care about, let web server serve them as quick as possible
        val imageInfo = ImageInfo(
            seen = targetingOutput.seen,
            centerX = targetingOutput.targetXCenter,
            centerY = targetingOutput.targetYCenter,
            distance = min(1000.0, targetingOutput.targetDistanceInches)
        )

        sendToNetworkTables(imageInfo)

        writeToPanel(panel, g2, targetingOutput)
    }

    private fun getTargetingOutput(imageToUse: BufferedImage) =
        if (teamColor == "blue") {
            process(Color.GREEN, imageToUse) { r, g, b ->
                b > 75 && b > r + 45 && b > g + 45
            }
        } else { // red
            process(Color.GREEN, imageToUse) { r, g, b ->
                r > 95 && r > b + 65 && r > g + 65
            }
        }

    /**
     * Writes an image onto the panel, and deals with stretching it while keeping aspect ratio
     * @param panel
     * @param g2
     * @param targetingOutput
     */
    /**
     * Writes an image onto the panel, and deals with stretching it while keeping aspect ratio
     * @param panel
     * @param g2
     * @param targetingOutput
     */
    private fun writeToPanel(panel: WebcamPanel, g2: Graphics2D, targetingOutput: TargetingOutput) {

        // Draw our results onto the image, so that the driver can see if the autonomous code is tracking
        val outImage = targetingOutput.drawOntoImage(Color.RED, targetingOutput.processedImage)

        val imageX = outImage.width
        val imageY = outImage.height

        val imageAspectRatio = imageX.toFloat() / imageY.toFloat()

        val panelX = panel.width
        val panelY = panel.height

        val screenAspectRatio = panelX.toFloat() / panelY.toFloat()

        if (imageAspectRatio < screenAspectRatio) {
            // wide screen - y to the max
            val scaledImageX = (panelY * imageAspectRatio).toInt()
            g2.drawImage(outImage, (panelX - scaledImageX) / 2, 0, scaledImageX, panelY, null)

        } else {
            // tall screen - x to the max
            val scaledImageY = (panelX / imageAspectRatio).toInt()
            g2.drawImage(outImage, 0, (panelY - scaledImageY) / 2, panelX, scaledImageY, null)
        }
    }
}

private fun connect(): Webcam? {
    var webcam: Webcam? = null
    try {

        if (isRemote) {
            Webcam.setDriver(IpCamDriver())
            val checkServer = IpCamDeviceRegistry.register("RoboRioCamTest", robotAddr, IpCamMode.PUSH)
            while (!checkServer.isOnline) {
                println("Waiting on Server")
                sleep(250)
            }
            IpCamDeviceRegistry.register("RoboRioCam", "$robotAddr:1181/stream.mjpg", IpCamMode.PUSH)
        }

        val webcams = Webcam.getWebcams()
        webcams
            .map { it.name }
            .forEachIndexed { i, cam -> println("$i: $cam") }

        webcam = webcams.first()
        webcam.setCustomViewSizes(Dimension(640, 480))
        webcam.viewSize = Dimension(640, 480)
        webcam.open();
    } catch (ex: Exception) {
        println("could not connect")
        sleep(250)
    }
    return webcam
}

fun sendToNetworkTables(imageInfo: ImageInfo) {
    if (isRemote) {
        seenEntry?.setBoolean(imageInfo.seen)
        centerXEntry?.setDouble(imageInfo.centerX)
        centerYEntry?.setDouble(imageInfo.centerY)
        distanceEntry?.setDouble(imageInfo.distance)
    }
}

fun process(
    targetDisplayColor: Color,
    inputImage: BufferedImage,
    isColor: (r: Int, g: Int, b: Int) -> Boolean
): TargetingOutput {
    val outputImage = BufferedImage(
        inputImage.width, inputImage.height,
        BufferedImage.TYPE_INT_RGB
    )

    val pixelRange = ((0.1 * inputImage.height).toInt())..((.9 * inputImage.height).toInt())

    var xSum = 0.0
    var ySum = 0.0
    var totalCount = 0.0

    // critical code, run on every pixel, every frame
    for (y in 0 until inputImage.height) {
        for (x in 0 until inputImage.width) {
            val xMult = 1 - abs((x.toDouble() / inputImage.width) - 0.5)
            val rgb = inputImage.getRGB(x, y)
            if (y !in pixelRange) {
                outputImage.setRGB(x, y, rgb)
                continue
            }
            // Extract color channels 0-255.
            val r = rgb shr 16 and 0x000000FF
            val g = rgb shr 8 and 0x000000FF
            val b = rgb and 0x000000FF

            if (isColor(r, g, b)) {
                outputImage.setRGB(x, y, targetDisplayColor.rgb)
                xSum += x * xMult
                ySum += y * xMult
                totalCount += xMult

            } else {
                outputImage.setRGB(x, y, rgb)
            }
        }
    }

    val xCenter: Int
    val yCenter: Int

    var seen = false
    var rightExtension = inputImage.width
    var leftExtension = inputImage.width

    if (totalCount <= 50) {
        xCenter = inputImage.width / 2
        yCenter = inputImage.height / 2
    } else {
        xCenter = (xSum / totalCount).toInt()
        yCenter = (ySum / totalCount).toInt()

        // left
        for (x in (xCenter downTo 0)) {
            if (outputImage.getRGB(x, yCenter) != targetDisplayColor.rgb) {
                leftExtension = xCenter - x
                break
            }
        }

        // right
        for (x in xCenter until inputImage.width) {
            if (outputImage.getRGB(x, yCenter) != targetDisplayColor.rgb) {
                rightExtension = x - xCenter
                break
            }
        }

        val total = rightExtension + leftExtension
        if (total < inputImage.width) {
            seen = true
        }
    }

    val xLeft = xCenter - leftExtension
    val xRight = xCenter + rightExtension
    val xCenterAvg = (xLeft + xRight) / 2
    val distance = 547.0 * (leftExtension + rightExtension).toDouble().pow(-1.08)

    return TargetingOutput(
        imageWidth = inputImage.width,
        imageHeight = inputImage.height,
        xCenterColor = xCenter,
        yCenterColor = yCenter,
        xCenterAvg = xCenterAvg,
        yCenterAvg = yCenter,
        targetDistance = (distance),
        processedImage = outputImage,
        seen = seen,
        rightExtension = rightExtension,
        leftExtension = leftExtension
    )
}