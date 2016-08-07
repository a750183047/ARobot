package com.neu.aroboot.Roobt;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;

import com.iflytek.sunflower.FlowerCollector;
import com.neu.aroboot.R;
import com.neu.aroboot.util.ApkInstaller;
import com.neu.aroboot.util.Code;
import com.neu.aroboot.util.JsonParser;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends Activity implements OnClickListener{
    private static String TAG = MainActivity.class.getSimpleName();
    // 语音识别对象
    private SpeechRecognizer mAsr;
    private Toast mToast;
    private String mEngineType = null;
    // 语记安装助手类
    ApkInstaller mInstaller ;


    private static UsbSerialDriver sDriver = null;  //usbDriver
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();    //线程池
    private SerialInputOutputManager mSerialIoManager;
    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                           // SerialConsoleActivity.this.updateReceivedData(data);
                        }
                    });

                }
            };


    @SuppressLint("ShowToast")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        initLayout();

        // 初始化识别对象
        mAsr = SpeechRecognizer.createRecognizer(MainActivity.this, null);
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        mAsr.setParameter(SpeechConstant.SUBJECT, "asr");


        mToast = Toast.makeText(this,"",Toast.LENGTH_SHORT);


    }


//    @Override
//    protected void onPause() {
//        super.onPause();
//        stopIoManager();
//        if (sDriver != null) {
//            try {
//                sDriver.close();
//            } catch (IOException e) {
//                // Ignore.
//            }
//            sDriver = null;
//        }
//        finish();
//    }







    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }


    /**
     * 初始化Layout。
     */
    private void initLayout() {
        findViewById(R.id.isr_recognize).setOnClickListener(MainActivity.this);
        findViewById(R.id.isr_stop).setOnClickListener(MainActivity.this);
        findViewById(R.id.isr_cancel).setOnClickListener(MainActivity.this);

        mEngineType = SpeechConstant.TYPE_CLOUD;

    }

    // 函数调用返回值
    int ret = 0;

    @Override
    public void onClick(View view) {
        if(null == mEngineType) {
            showTip("请先选择识别引擎类型");
            return;
        }
        switch(view.getId())
        {

            // 开始识别
            case R.id.isr_recognize:
                ((EditText)findViewById(R.id.isr_text)).setText(null);// 清空显示内容

                ret = mAsr.startListening(mRecognizerListener);
                if (ret != ErrorCode.SUCCESS) {
                    if(ret == ErrorCode.ERROR_COMPONENT_NOT_INSTALLED){
                        //未安装则跳转到提示安装页面
                        mInstaller.install();
                    }else {
                        showTip("识别失败,错误码: " + ret);
                    }
                }
                break;
            // 停止识别
            case R.id.isr_stop:
                mAsr.stopListening();
                showTip("停止识别");
                break;
            // 取消识别
            case R.id.isr_cancel:
                mAsr.cancel();
                showTip("取消识别");
                break;
        }
    }

    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败,错误码："+code);
            }
        }
    };


    /**
     * 识别监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
            Log.d(TAG, "返回音频数据："+data.length);
        }

        @Override
        public void onResult(final RecognizerResult result, boolean isLast) {
            if (null != result) {
                Log.d(TAG, "recognizer result：" + result.getResultString());
                String text ;
                if("cloud".equalsIgnoreCase(mEngineType)){
                    text = JsonParser.parseGrammarResult(result.getResultString());
                }else {
                    text = JsonParser.parseLocalGrammarResult(result.getResultString());
                }

                // 显示
                ((EditText)findViewById(R.id.isr_text)).setText(text);
                byte[] bytes =  Code.hexStringToBytes("AA000001");
                try {
                    sDriver.write(bytes,500);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                Log.d(TAG, "recognizer result : null");
            }
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
        }

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            showTip("onError Code："	+ error.getErrorCode());
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }

    };



    private void showTip(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 退出时释放连接
        mAsr.cancel();
        mAsr.destroy();
    }

    @Override
    protected void onResume() {
        //移动数据统计分析
        FlowerCollector.onResume(MainActivity.this);
        FlowerCollector.onPageStart(TAG);
        super.onResume();

        if (sDriver == null) {
          //  mTitleTextView.setText("No serial device.");
            Log.e(TAG, "No serial device");
        } else {
            try {
                sDriver.open();
                sDriver.setParameters(115200, 8, UsbSerialDriver.STOPBITS_1, UsbSerialDriver.PARITY_NONE);  //设置波特率 数据位 停止位 奇偶校验
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
            //    mTitleTextView.setText("Error opening device: " + e.getMessage());
                Log.e(TAG, "Error opening device: " + e.getMessage());
                try {
                    sDriver.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sDriver = null;
                return;
            }
           // mTitleTextView.setText("Serial device: " + sDriver.getClass().getSimpleName());
            Log.e(TAG, "Serial device: " + sDriver.getClass().getSimpleName());
        }


        onDeviceStateChange();
    }


    /**
     * 启动IO管理器
     */
    private void startIoManager() {
        if (sDriver != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sDriver, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    @Override
    protected void onPause() {
        //移动数据统计分析
        FlowerCollector.onPageEnd(TAG);
        FlowerCollector.onPause(MainActivity.this);
        super.onPause();
        //usb
        stopIoManager();
        if (sDriver != null) {
            try {
                sDriver.close();
            } catch (IOException e) {
                // Ignore.
            }
            sDriver = null;
        }
        finish();

    }


    /**
     * Starts the activity, using the supplied driver instance.
     *
     * @param context
     * @param driver
     */
    public static void show(Context context, UsbSerialDriver driver) {
        sDriver = driver;
        final Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }

}
