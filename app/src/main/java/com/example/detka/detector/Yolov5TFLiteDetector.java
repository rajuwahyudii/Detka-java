package com.example.detka.detector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import com.example.detka.MainActivity;
import com.example.detka.utils.Recognition;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.DequantizeOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.common.ops.QuantizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.metadata.MetadataExtractor;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;


public class Yolov5TFLiteDetector {
    static MediaPlayer player;
    int number = 0;
    private final Size INPNUT_SIZE = new Size(320, 320);
    private  int[] OUTPUT_SIZE = new int[]{1, 6300, 196};
    private Boolean IS_INT8 = false;
    private final float DETECT_THRESHOLD = 0.02f;
    private final float IOU_THRESHOLD = 0.02f;
    private final float IOU_CLASS_DUPLICATED_THRESHOLD = 0.1f;
    private final String MODEL_YOLOV5S = "best-fp16-320.tflite";
//    private final String MODEL_YOLOV5S = "yolov5s-dynamic.tflite";
    private final String MODEL_YOLOV5N =  "320-best-fp16.tflite";
    private final String MODEL_YOLOV5M = "best-fp16-320.tflite";
    private final String MODEL_YOLOV5S_INT8 = "yolov5s-int8-320.tflite";
    private  String LABEL_FILE = "model.txt";
    MetadataExtractor.QuantizationParams input5SINT8QuantParams = new MetadataExtractor.QuantizationParams(0.003921568859368563f, 0);
    MetadataExtractor.QuantizationParams output5SINT8QuantParams = new MetadataExtractor.QuantizationParams(0.006305381190031767f, 5);
    private String MODEL_FILE;

    private Interpreter tflite;
    private List<String> associatedAxisLabels;
    Interpreter.Options options = new Interpreter.Options();

    public String getModelFile() {
        return this.MODEL_FILE;
    }

    public void setModelFile(String modelFile){
        switch (modelFile) {
            case "model1":
                OUTPUT_SIZE = new int[]{1, 6300, 196};
                IS_INT8 = false;
                MODEL_FILE = MODEL_YOLOV5S;
                LABEL_FILE = "model.txt";
                break;
            case "model2":
                IS_INT8 = false;
                OUTPUT_SIZE = new int[]{1, 6300, 197};
                MODEL_FILE = MODEL_YOLOV5N;
                LABEL_FILE = "modell.txt";
                break;
//            case "yolov5m":
//                IS_INT8 = false;
//                MODEL_FILE = MODEL_YOLOV5M;
//                break;
//            case "yolov5s-int8":
//                IS_INT8 = false;
//                MODEL_FILE = MODEL_YOLOV5M;
//                break;
//            default:
//                Log.i("tfliteSupport", "Only yolov5s/n/m/sint8 can be load!");
        }
    }

    public String getLabelFile() {
        return this.LABEL_FILE;
    }

    public Size getInputSize(){return this.INPNUT_SIZE;}
    public int[] getOutputSize(){return this.OUTPUT_SIZE;}

    /**
     * ???????????????, ???????????? addNNApiDelegate(), addGPUDelegate()????????????????????????
     *
     * @param activity
     */
    public void initialModel(Context activity) {
        // Initialise the model
        try {

            ByteBuffer tfliteModel = FileUtil.loadMappedFile(activity, MODEL_FILE);
            tflite = new Interpreter(tfliteModel, options);
            Log.i("tfliteSupport", "Success reading model: " + MODEL_FILE);

            associatedAxisLabels = FileUtil.loadLabels(activity, LABEL_FILE);
            Log.i("tfliteSupport", "Success reading label: " + LABEL_FILE);

        } catch (IOException e) {
            Log.e("tfliteSupport", "Error reading model or label: ", e);
            Toast.makeText(activity, "load model error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * ????????????
     *
     * @param bitmap
     * @return
     */
    public ArrayList<Recognition> detect(Bitmap bitmap) {

        // yolov5s-tflite????????????:[1, 320, 320,3], ??????????????????????????????resize,????????????
        TensorImage yolov5sTfliteInput;
        ImageProcessor imageProcessor;
        if(IS_INT8){
            imageProcessor =
                    new ImageProcessor.Builder()
                            .add(new ResizeOp(INPNUT_SIZE.getHeight(), INPNUT_SIZE.getWidth(), ResizeOp.ResizeMethod.BILINEAR))
                            .add(new NormalizeOp(0, 255))
                            .add(new QuantizeOp(input5SINT8QuantParams.getZeroPoint(), input5SINT8QuantParams.getScale()))
                            .add(new CastOp(DataType.UINT8))
                            .build();
            yolov5sTfliteInput = new TensorImage(DataType.UINT8);
        }else{
            imageProcessor =
                    new ImageProcessor.Builder()
                            .add(new ResizeOp(INPNUT_SIZE.getHeight(), INPNUT_SIZE.getWidth(), ResizeOp.ResizeMethod.BILINEAR))
                            .add(new NormalizeOp(0, 255))
                            .build();
            yolov5sTfliteInput = new TensorImage(DataType.FLOAT32);
        }

        yolov5sTfliteInput.load(bitmap);
        yolov5sTfliteInput = imageProcessor.process(yolov5sTfliteInput);


        // yolov5s-tflite????????????:[1, 6300, 85], ?????????v5???GitHub release???????????????tflite??????, ?????????[0,1], ?????????320.
        TensorBuffer probabilityBuffer;
        if(IS_INT8){
            probabilityBuffer = TensorBuffer.createFixedSize(OUTPUT_SIZE, DataType.UINT8);
        }else{
            probabilityBuffer = TensorBuffer.createFixedSize(OUTPUT_SIZE, DataType.FLOAT32);
        }

        // ????????????
        if (null != tflite) {
            // ??????tflite??????????????????batch=1?????????
            tflite.run(yolov5sTfliteInput.getBuffer(), probabilityBuffer.getBuffer());
        }

        // ?????????????????????,???????????????tflite.run????????????.
        if(IS_INT8){
            TensorProcessor tensorProcessor = new TensorProcessor.Builder()
                    .add(new DequantizeOp(output5SINT8QuantParams.getZeroPoint(), output5SINT8QuantParams.getScale()))
                    .build();
            probabilityBuffer = tensorProcessor.process(probabilityBuffer);
        }

        // ??????????????????????????????
        float[] recognitionArray = probabilityBuffer.getFloatArray();
        // ?????????flatten?????????????????????(xywh,obj,classes).
        ArrayList<Recognition> allRecognitions = new ArrayList<>();
        for (int i = 0; i < OUTPUT_SIZE[1]; i++) {
            int gridStride = i * OUTPUT_SIZE[2];
            // ??????yolov5???????????????tflite???????????????????????????image size, ???????????????????????????
            float x = recognitionArray[0 + gridStride] * INPNUT_SIZE.getWidth();
            float y = recognitionArray[1 + gridStride] * INPNUT_SIZE.getHeight();
            float w = recognitionArray[2 + gridStride] * INPNUT_SIZE.getWidth();
            float h = recognitionArray[3 + gridStride] * INPNUT_SIZE.getHeight();
            int xmin = (int) Math.max(0, x - w / 2.);
            int ymin = (int) Math.max(0, y - h / 2.);
            int xmax = (int) Math.min(INPNUT_SIZE.getWidth(), x + w / 2.);
            int ymax = (int) Math.min(INPNUT_SIZE.getHeight(), y + h / 2.);
            float confidence = recognitionArray[4 + gridStride];
            float[] classScores = Arrays.copyOfRange(recognitionArray, 5 + gridStride, this.OUTPUT_SIZE[2] + gridStride);
//            if(i % 1000 == 0){
//                Log.i("tfliteSupport","x,y,w,h,conf:"+x+","+y+","+w+","+h+","+confidence);
//            }
            int labelId = 0;
            float maxLabelScores = 0.f;
            for (int j = 0; j < classScores.length; j++) {
                if (classScores[j] > maxLabelScores) {
                    maxLabelScores = classScores[j];
                    labelId = j;


                }
            }
//

            Recognition r = new Recognition(
                    labelId,
                    "",
                    maxLabelScores,
                    confidence,
                    new RectF(xmin, ymin, xmax, ymax));
            allRecognitions.add(
                    r);

        }

//        Log.i("tfliteSupport", "recognize data size: "+allRecognitions.size());

        // ?????????????????????
        ArrayList<Recognition> nmsRecognitions = nms(allRecognitions);
        // ????????????????????????, ?????????????????????????????????2???????????????????????????????????????
        ArrayList<Recognition> nmsFilterBoxDuplicationRecognitions = nmsAllClass(nmsRecognitions);

        // ??????label??????
        for(Recognition recognition : nmsFilterBoxDuplicationRecognitions) {
            int labelId = recognition.getLabelId();
            String labelName = associatedAxisLabels.get(labelId);
            recognition.setLabelName(labelName);
        }

        return nmsFilterBoxDuplicationRecognitions;
    }

    /**
     * ???????????????
     *
     * @param allRecognitions
     * @return
     */
    protected ArrayList<Recognition> nms(ArrayList<Recognition> allRecognitions) {
        ArrayList<Recognition> nmsRecognitions = new ArrayList<Recognition>();

        // ??????????????????, ?????????????????????nms
        for (int i = 0; i < OUTPUT_SIZE[2]-5; i++) {
            // ????????????????????????????????????, ???labelScore???????????????
            PriorityQueue<Recognition> pq =
                    new PriorityQueue<Recognition>(
                            6400,
                            new Comparator<Recognition>() {
                                @Override
                                public int compare(final Recognition l, final Recognition r) {
                                    // Intentionally reversed to put high confidence at the head of the queue.
                                    return Float.compare(r.getConfidence(), l.getConfidence());
                                }
                            });

            // ???????????????????????????, ???obj????????????????????????
            for (int j = 0; j < allRecognitions.size(); ++j) {
//                if (allRecognitions.get(j).getLabelId() == i) {
                if (allRecognitions.get(j).getLabelId() == i && allRecognitions.get(j).getConfidence() > DETECT_THRESHOLD) {
                    pq.add(allRecognitions.get(j));
//                    Log.i("tfliteSupport", allRecognitions.get(j).toString());
                }
            }

            // nms????????????
            while (pq.size() > 0) {
                // ???????????????????????????
                Recognition[] a = new Recognition[pq.size()];
                Recognition[] detections = pq.toArray(a);
                Recognition max = detections[0];
                nmsRecognitions.add(max);
                pq.clear();

                for (int k = 1; k < detections.length; k++) {
                    Recognition detection = detections[k];
                    if (boxIou(max.getLocation(), detection.getLocation()) < IOU_THRESHOLD) {
                        pq.add(detection);
                    }
                }
            }
        }
        return nmsRecognitions;
    }

    /**
     * ????????????????????????????????????????????????
     *
     * @param allRecognitions
     * @return
     */
    protected ArrayList<Recognition> nmsAllClass(ArrayList<Recognition> allRecognitions) {
        ArrayList<Recognition> nmsRecognitions = new ArrayList<Recognition>();

        PriorityQueue<Recognition> pq =
                new PriorityQueue<Recognition>(
                        10,
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(final Recognition l, final Recognition r) {
                                // Intentionally reversed to put high confidence at the head of the queue.
                                return Float.compare(r.getConfidence(), l.getConfidence());
                            }
                        });

        // ???????????????????????????, ???obj????????????????????????
        for (int j = 0; j < allRecognitions.size(); ++j) {
            if (allRecognitions.get(j).getConfidence() > DETECT_THRESHOLD) {
                pq.add(allRecognitions.get(j));
            }
        }

        while (pq.size() > 0) {
            // ???????????????????????????
            Recognition[] a = new Recognition[pq.size()];
            Recognition[] detections = pq.toArray(a);
            Recognition max = detections[0];
            nmsRecognitions.add(max);
            pq.clear();

            for (int k = 1; k < detections.length; k++) {
                Recognition detection = detections[k];
                if (boxIou(max.getLocation(), detection.getLocation()) < IOU_CLASS_DUPLICATED_THRESHOLD) {
                    pq.add(detection);
                }
            }
        }
        return nmsRecognitions;
    }


    protected float boxIou(RectF a, RectF b) {
        float intersection = boxIntersection(a, b);
        float union = boxUnion(a, b);
        if (union <= 0) return 1;
        return intersection / union;
    }

    protected float boxIntersection(RectF a, RectF b) {
        float maxLeft = a.left > b.left ? a.left : b.left;
        float maxTop = a.top > b.top ? a.top : b.top;
        float minRight = a.right < b.right ? a.right : b.right;
        float minBottom = a.bottom < b.bottom ? a.bottom : b.bottom;
        float w = minRight -  maxLeft;
        float h = minBottom - maxTop;

        if (w < 0 || h < 0) return 0;
        float area = w * h;
        return area;
    }

    protected float boxUnion(RectF a, RectF b) {
        float i = boxIntersection(a, b);
        float u = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - i;

        return u;
    }

    /**
     * ??????NNapi??????
     */
    public void addNNApiDelegate() {
        NnApiDelegate nnApiDelegate = null;
        // Initialize interpreter with NNAPI delegate for Android Pie or above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            NnApiDelegate.Options nnApiOptions = new NnApiDelegate.Options();
            nnApiOptions.setAllowFp16(true);
            nnApiOptions.setUseNnapiCpu(true);
            //ANEURALNETWORKS_PREFER_LOW_POWER?????????????????????????????????????????????????????????????????????????????????????????????????????????
            //ANEURALNETWORKS_PREFER_FAST_SINGLE_ANSWER??????????????????????????????????????????????????????????????????????????????????????????
            //ANEURALNETWORKS_PREFER_SUSTAINED_SPEED?????????????????????????????????????????????????????????????????????????????????????????????????????????
            nnApiOptions.setExecutionPreference(NnApiDelegate.Options.EXECUTION_PREFERENCE_SUSTAINED_SPEED);
            nnApiDelegate = new NnApiDelegate(nnApiOptions);
            nnApiDelegate = new NnApiDelegate();
            options.addDelegate(nnApiDelegate);
            addThread(1);
            Log.i("tfliteSupport", "using nnapi delegate.");
        }else {
            addThread(1);
        }
    }

    /**
     * ??????GPU??????
     */
    public void addGPUDelegate() {
        CompatibilityList compatibilityList = new CompatibilityList();
        if(compatibilityList.isDelegateSupportedOnThisDevice()){
            GpuDelegate.Options delegateOptions = compatibilityList.getBestOptionsForThisDevice();
            GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
            options.addDelegate(gpuDelegate);
            addThread(1);
            Log.i("tfliteSupport", "using gpu delegate.");
        } else {
            addThread(1);
        }
    }

    /**
     * ???????????????
     * @param thread
     */
    public void addThread(int thread) {
        options.setNumThreads(thread);
    }

    public  void play(int index, int posisi, int number){
        String audio1 = "https://firebasestorage.googleapis.com/v0/b/detka-60b41.appspot.com/o/putus.mp3?alt=media&token=3c8efefd-f5f1-455e-9ace-8c2e21e95d92";
        String audio2 = "https://firebasestorage.googleapis.com/v0/b/detka-60b41.appspot.com/o/membujur%20penuh.mp3?alt=media&token=7fc6837e-941e-4537-a0b8-0282cade2a4d";
        String audio3 = "https://firebasestorage.googleapis.com/v0/b/detka-60b41.appspot.com/o/simbol-panah.mp3?alt=media&token=cd62c785-dced-47f3-9665-94084c9343e4";
        String audio4 = "https://firebasestorage.googleapis.com/v0/b/detka-60b41.appspot.com/o/cevron%20line.mp3?alt=media&token=49f35306-ddc3-45a6-a708-012dd7c19e4f";
        String audio5 = "https://firebasestorage.googleapis.com/v0/b/detka-60b41.appspot.com/o/cevron%20line.mp3?alt=media&token=49f35306-ddc3-45a6-a708-012dd7c19e4f";
        String audio6 = "https://firebasestorage.googleapis.com/v0/b/detka-60b41.appspot.com/o/pelanggaran.mp3?alt=media&token=2335de09-cfb2-4169-990c-8868cadc70a9";
        String song = audio1;
        player = new MediaPlayer();

//        player.setAudioStreamType(AudioManager.STREAM_MUSIC);

        MediaPlayer player = MediaPlayer.create(null, Uri.parse(index == 1 ? audio1:index == 2 ? audio2 : index == 3 ? audio3 : index ==4 ? audio4 : index ==5 ? audio5 : audio6));
        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.start();
            }
        });
//        try {
//            player.setDataSource("audio1");
//            player.prepare();
//            player.start();
//        }catch (IOException e) {
//            e.printStackTrace();
//        }
    }
}
