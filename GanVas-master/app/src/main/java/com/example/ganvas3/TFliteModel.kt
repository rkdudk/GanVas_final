import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.ByteOrder

class TFLiteModel(context: Context, generatorModelName: String, discriminatorModelName: String) {

    val generator: Interpreter
    val discriminator: Interpreter

    init {
        generator = try {
            loadModel(context, "ml/$generatorModelName")
        } catch (e: IOException) {
            Log.e("TFLiteModel", "Error loading generator model", e)
            throw RuntimeException("Error initializing TensorFlow Lite model: $generatorModelName", e)
        }

        discriminator = try {
            loadModel(context, "ml/$discriminatorModelName")
        } catch (e: IOException) {
            Log.e("TFLiteModel", "Error loading discriminator model", e)
            throw RuntimeException("Error initializing TensorFlow Lite model: $discriminatorModelName", e)
        }
    }

    private fun loadModel(context: Context, modelName: String): Interpreter {
        Log.d("TFLiteModel", "Loading model: $modelName")
        val assetFileDescriptor = context.assets.openFd(modelName)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        val model: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        Log.d("TFLiteModel", "Model loaded: $modelName")
        model.order(ByteOrder.nativeOrder())
        return Interpreter(model)
    }

    fun runGenerator(inputBitmap: Bitmap, outputBitmap: Bitmap) {
        Log.d("TFLiteModel", "Running generator")

        // 모델의 입력 크기 설정
        val inputSize = 256

        // 입력 이미지를 모델의 입력 크기로 리사이즈
        val resizedBitmap = resizeBitmap(inputBitmap, inputSize, inputSize)

        val inputTensor = TensorBuffer.createFixedSize(intArrayOf(1, inputSize, inputSize, 3), DataType.FLOAT32)
        val outputTensor = TensorBuffer.createFixedSize(intArrayOf(1, inputSize, inputSize, 3), DataType.FLOAT32)

        // 입력 텐서의 형상 확인
        val inputShape = generator.getInputTensor(0).shape()
        Log.d("TFLiteModel", "Input tensor shape: ${inputShape.joinToString(", ")}")

        // 실제 입력 데이터의 형상 확인
        val actualInputShape = intArrayOf(1, inputSize, inputSize, 3)
        Log.d("TFLiteModel", "Actual input data shape: ${actualInputShape.joinToString(", ")}")

        // 형상 비교
        if (!inputShape.contentEquals(actualInputShape)) {
            Log.e("TFLiteModel", "Input shape does not match! Expected: ${inputShape.joinToString(", ")}, but got: ${actualInputShape.joinToString(", ")}")
            return
        }

        val inputByteBuffer = convertBitmapToByteBuffer(resizedBitmap)
        generator.run(inputByteBuffer, outputTensor.buffer.rewind())
        convertByteBufferToBitmap(outputTensor.buffer, outputBitmap)
        Log.d("TFLiteModel", "Generator run complete")
    }


    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputByteBuffer = ByteBuffer.allocateDirect(4 * bitmap.width * bitmap.height * 3)
        inputByteBuffer.order(ByteOrder.nativeOrder())
        inputByteBuffer.rewind()

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixelValue = bitmap.getPixel(x, y)
                inputByteBuffer.putFloat((pixelValue shr 16 and 0xFF) / 255.0f)
                inputByteBuffer.putFloat((pixelValue shr 8 and 0xFF) / 255.0f)
                inputByteBuffer.putFloat((pixelValue and 0xFF) / 255.0f)
            }
        }

        inputByteBuffer.rewind()
        return inputByteBuffer
    }

    private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun convertByteBufferToBitmap(byteBuffer: ByteBuffer, bitmap: Bitmap) {
        byteBuffer.rewind()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val r = (byteBuffer.float * 255.0f).toInt()
                val g = (byteBuffer.float * 255.0f).toInt()
                val b = (byteBuffer.float * 255.0f).toInt()
                val color = (r shl 16) or (g shl 8) or b
                bitmap.setPixel(x, y, color)
            }
        }
    }

    fun close() {
        Log.d("TFLiteModel", "Closing models")
        generator.close()
        discriminator.close()
    }
}
