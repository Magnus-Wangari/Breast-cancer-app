package com.android.example.BreastCancer.ml;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import com.android.example.BreastCancer.models.Result;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class Classifier {

    private final Interpreter mInterpreter;

    private Classifier(Interpreter interpreter) {
        mInterpreter = interpreter;
    }

    public static Classifier createClassifier(AssetManager assetManager, String modelPath) throws IOException {
        MappedByteBuffer byteBuffer = loadModelFile(assetManager, modelPath);
        Interpreter interpreter = new Interpreter(byteBuffer);
        return new Classifier(interpreter);
    }

    private static MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(ModelConfig.MODEL_INPUT_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] pixels = new int[ModelConfig.INPUT_IMG_SIZE_WIDTH * ModelConfig.INPUT_IMG_SIZE_HEIGHT];
        //Resize Image
        Bitmap resizedInput = Bitmap.createScaledBitmap(bitmap, ModelConfig.INPUT_IMG_SIZE_WIDTH , ModelConfig.INPUT_IMG_SIZE_HEIGHT, true);;
        resizedInput.getPixels(pixels, 0, resizedInput.getWidth(), 0, 0, resizedInput.getWidth(), resizedInput.getHeight());
        //edit Over
        for (int pixel : pixels) {
            float rChannel = ((pixel >> 16) & 0xFF) / ModelConfig.IMAGE_STD;
            float gChannel = ((pixel >> 8) & 0xFF) / ModelConfig.IMAGE_STD;
            float bChannel = ((pixel) & 0xFF) / ModelConfig.IMAGE_STD;

            byteBuffer.putFloat(rChannel);
            byteBuffer.putFloat(gChannel);
            byteBuffer.putFloat(bChannel);
        }
        return byteBuffer;
    }

    public List<Result> recognizeImage(Bitmap bitmap) {
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(bitmap);
        float[][] result = new float[1][(ModelConfig.OUTPUT_LABELS.size())]; // Softmax Classification

//        float[][] result = new float[1][1]; // For sigmoid Yes/No

        mInterpreter.run(byteBuffer, result);
        return getSortedResult(result);
    }

    private List<Result> getSortedResult(float[][] result) {
        PriorityQueue<Result> sortedResults = new PriorityQueue<>(
                ModelConfig.MAX_CLASSIFICATION_RESULTS,
                (lhs, rhs) -> Float.compare(rhs.mConfidence, lhs.mConfidence)
        );

        for (int i = 0; i < ModelConfig.OUTPUT_LABELS.size(); ++i) {
            float confidence = result[0][i];
            if (confidence > ModelConfig.CLASSIFICATION_THRESHOLD) {
                ModelConfig.OUTPUT_LABELS.size();
                sortedResults.add(new Result(ModelConfig.OUTPUT_LABELS.get(i), confidence));
//                sortedResults.add(new Result(ModelConfig.OUTPUT_LABELS.get(1), confidence)); // Added for sigmoid
            }
//            // Added for sigmoid
//            else{
//                sortedResults.add(new Result(ModelConfig.OUTPUT_LABELS.get(0), (1-confidence)));
//            }

        }

        return new ArrayList<>(sortedResults);
    }

}
